package com.sumiri.gss.data.remote

import com.google.gson.Gson
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.sumiri.gss.data.model.Manifest
import com.sumiri.gss.data.model.RemoteFileItem
import com.sumiri.gss.data.model.RemoteServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * Windows 共享文件夹 (SMB 2/3) 客户端（支持中文友好报错与免密降级）
 */
class SmbStorageClient(private val gson: Gson = Gson()) {

    /**
     * 测试 Windows 共享文件夹连接是否成功
     */
    suspend fun testConnection(server: RemoteServer, shareName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            executeOnShare(server, shareName) { share ->
                share.isConnected
            }
        }.mapFailure { translateException(it) }
    }

    /**
     * 列出 SMB 远端指定目录下的文件与文件夹
     */
    suspend fun listRemoteFiles(
        server: RemoteServer,
        shareName: String,
        folderPath: String = ""
    ): Result<List<RemoteFileItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanPath = normalizePath(folderPath)
            executeOnShare(server, shareName) { share ->
                val list = share.list(cleanPath)
                list.filter { it.fileName != "." && it.fileName != ".." }.map { info ->
                    val isDir = (info.fileAttributes and 0x10L) != 0L // FILE_ATTRIBUTE_DIRECTORY
                    val childPath = if (cleanPath.isEmpty()) info.fileName else "$cleanPath/${info.fileName}"
                    RemoteFileItem(
                        name = info.fileName,
                        path = childPath,
                        isDirectory = isDir,
                        size = info.endOfFile,
                        lastModified = info.changeTime.toEpochMillis()
                    )
                }
            }
        }.mapFailure { translateException(it) }
    }

    /**
     * 从 SMB 远端读取 Manifest 清单文件
     */
    suspend fun readManifest(
        server: RemoteServer,
        shareName: String,
        remoteTaskFolder: String
    ): Result<Manifest?> = withContext(Dispatchers.IO) {
        runCatching {
            val manifestPath = normalizePath("$remoteTaskFolder/manifest.json")
            val content = readFileToString(server, shareName, manifestPath) ?: return@runCatching null
            gson.fromJson(content, Manifest::class.java)
        }.mapFailure { translateException(it) }
    }

    /**
     * 将 Manifest 清单文件上传到 SMB 远端
     */
    suspend fun writeManifest(
        server: RemoteServer,
        shareName: String,
        remoteTaskFolder: String,
        manifest: Manifest
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val manifestPath = normalizePath("$remoteTaskFolder/manifest.json")
            val jsonString = gson.toJson(manifest)
            writeStringToFile(server, shareName, manifestPath, jsonString)
        }.mapFailure { translateException(it) }
    }

    /**
     * 上传单个文件流到 SMB 远端
     */
    suspend fun uploadFile(
        server: RemoteServer,
        shareName: String,
        remoteFilePath: String,
        inputStream: InputStream
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanPath = normalizePath(remoteFilePath)
            executeOnShare(server, shareName) { share ->
                ensureDirectoriesExist(share, getParentDirectory(cleanPath))
                val file = openFileForWrite(share, cleanPath)
                file.use { smbFile ->
                    smbFile.outputStream.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            Unit
        }.mapFailure { translateException(it) }
    }

    /**
     * 从 SMB 远端下载单个文件到输出流
     */
    suspend fun downloadFile(
        server: RemoteServer,
        shareName: String,
        remoteFilePath: String,
        outputStream: OutputStream
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanPath = normalizePath(remoteFilePath)
            executeOnShare(server, shareName) { share ->
                if (!share.fileExists(cleanPath)) {
                    error("远端文件不存在: $cleanPath")
                }
                val file = openFileForRead(share, cleanPath)
                file.use { smbFile ->
                    smbFile.inputStream.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            Unit
        }.mapFailure { translateException(it) }
    }

    // ==================== 中文报错翻译与内部辅助函数 ====================

    private fun translateException(e: Throwable): Exception {
        val msg = e.message ?: ""
        val friendlyMsg = when {
            msg.contains("STATUS_BAD_NETWORK_NAME") || msg.contains("0xc00000cc") ->
                "找不到指定的共享名。若您共享了整盘 (如 D 盘)，设置里的共享名请填 D$ 或 D（注意: 子文件夹请填在【映射/文件管理】的远端目录中）。"

            msg.contains("STATUS_LOGON_FAILURE") || msg.contains("0xc000006d") ->
                "登录失败，请检查用户名或密码。如果电脑未设密码，请将密码框留空。"

            msg.contains("STATUS_ACCOUNT_LOCKED_OUT") || msg.contains("0xc0000234") ->
                "账号因频繁失败被 Windows 临时锁定保护，请稍等片刻或检查用户名。"

            msg.contains("STATUS_ACCESS_DENIED") || msg.contains("0xc0000022") ->
                "访问被拒绝！请在 Windows 共享属性里确认是否开启了“读取/写入”权限。"

            msg.contains("ConnectException") || msg.contains("Timeout") || msg.contains("HostUnreachable") ->
                "网络连接超时/失败，请检查手机与电脑是否在同一个 Wi-Fi，以及 IP 地址是否正确。"

            else -> "SMB 连接异常: $msg"
        }
        return Exception(friendlyMsg, e)
    }

    private inline fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> {
        val exception = exceptionOrNull() ?: return this
        return Result.failure(transform(exception))
    }

    private fun readFileToString(server: RemoteServer, shareName: String, remoteFilePath: String): String? {
        return executeOnShare(server, shareName) { share ->
            if (!share.fileExists(remoteFilePath)) {
                return@executeOnShare null
            }
            openFileForRead(share, remoteFilePath).use { file ->
                file.inputStream.bufferedReader().use { it.readText() }
            }
        }
    }

    private fun writeStringToFile(server: RemoteServer, shareName: String, remoteFilePath: String, content: String) {
        executeOnShare(server, shareName) { share ->
            ensureDirectoriesExist(share, getParentDirectory(remoteFilePath))
            openFileForWrite(share, remoteFilePath).use { file ->
                file.outputStream.bufferedWriter().use { it.write(content) }
            }
        }
    }

    private fun <T> executeOnShare(server: RemoteServer, shareName: String, block: (DiskShare) -> T): T {
        SMBClient().use { client ->
            val connection = client.connect(server.baseUrl)
            val session = authenticateWithFallback(connection, server)
            (session.connectShare(shareName) as DiskShare).use { share ->
                return block(share)
            }
        }
    }

    private fun authenticateWithFallback(connection: Connection, server: RemoteServer): Session {
        val username = server.username.trim()
        val password = server.password

        if (username.isEmpty() || username.equals("guest", ignoreCase = true)) {
            return runCatching { connection.authenticate(AuthenticationContext.anonymous()) }
                .getOrElse { connection.authenticate(AuthenticationContext.guest()) }
        }

        return try {
            connection.authenticate(AuthenticationContext(username, password.toCharArray(), ""))
        } catch (e: Exception) {
            if (password.isEmpty()) {
                runCatching { connection.authenticate(AuthenticationContext.guest()) }
                    .getOrElse {
                        runCatching { connection.authenticate(AuthenticationContext.anonymous()) }
                            .getOrElse { throw e }
                    }
            } else {
                throw e
            }
        }
    }

    private fun openFileForRead(share: DiskShare, path: String): File {
        return share.openFile(
            path,
            setOf(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
    }

    private fun openFileForWrite(share: DiskShare, path: String): File {
        return share.openFile(
            path,
            setOf(AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null
        )
    }

    private fun ensureDirectoriesExist(share: DiskShare, folderPath: String) {
        if (folderPath.isBlank() || folderPath == ".") return
        val parts = folderPath.replace('/', '\\').split('\\').filter { it.isNotBlank() }
        var current = ""
        for (part in parts) {
            current = if (current.isEmpty()) part else "$current\\$part"
            if (!share.folderExists(current)) {
                share.mkdir(current)
            }
        }
    }

    private fun getParentDirectory(path: String): String {
        val lastSlash = path.lastIndexOf('/')
        return if (lastSlash > 0) path.substring(0, lastSlash) else ""
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trim('/')
    }
}
