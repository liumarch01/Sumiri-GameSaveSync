package com.sumiri.gss.sync

import com.sumiri.gss.data.local.LocalFileEngine
import com.sumiri.gss.data.model.RemoteServer
import com.sumiri.gss.data.model.SyncTask
import com.sumiri.gss.data.remote.SmbStorageClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * 同步进度事件
 */
data class SyncProgress(
    val currentFile: String = "",
    val transferredCount: Int = 0,
    val totalCount: Int = 0,
    val statusText: String = "",
    val isCompleted: Boolean = false,
    val error: String? = null
)

/**
 * 核心同步控制器 (Push / Pull)
 */
class SyncController(
    private val smbClient: SmbStorageClient = SmbStorageClient()
) {

    /**
     * 执行推送 (Push: 本地 ➔ Windows 共享文件夹)
     */
    fun pushToSmb(
        task: SyncTask,
        server: RemoteServer,
        shareName: String,
        localDir: File
    ): Flow<SyncProgress> = flow {
        emit(SyncProgress(statusText = "正在扫描本地存档并计算 MD5..."))

        // 1. 生成本地 Manifest
        val localManifest = LocalFileEngine.generateManifest(task.id, localDir)

        emit(SyncProgress(statusText = "正在读取远端清单..."))
        // 2. 获取远端 Manifest
        val remoteManifest = smbClient.readManifest(server, shareName, task.remotePath).getOrNull()

        // 3. Diff 对比
        val diff = DiffEngine.compare(localManifest, remoteManifest)
        val filesToUpload = diff.toUpload

        if (filesToUpload.isEmpty()) {
            emit(SyncProgress(statusText = "所有文件均为最新，无需推送！", isCompleted = true))
            return@flow
        }

        // 4. 上传需变动的文件
        val total = filesToUpload.size
        filesToUpload.forEachIndexed { index, fileEntry ->
            emit(
                SyncProgress(
                    currentFile = fileEntry.relativePath,
                    transferredCount = index + 1,
                    totalCount = total,
                    statusText = "正在推送 (${index + 1}/$total): ${fileEntry.relativePath}"
                )
            )

            val localFile = File(localDir, fileEntry.relativePath)
            val remotePath = "${task.remotePath}/${fileEntry.relativePath}"

            localFile.inputStream().use { input ->
                val uploadResult = smbClient.uploadFile(server, shareName, remotePath, input)
                if (uploadResult.isFailure) {
                    val err = uploadResult.exceptionOrNull()?.message ?: "上传失败"
                    emit(SyncProgress(error = "推送文件 ${fileEntry.relativePath} 失败: $err"))
                    return@flow
                }
            }
        }

        // 5. 更新远端 Manifest
        emit(SyncProgress(statusText = "正在更新远端清单 (manifest.json)..."))
        val writeResult = smbClient.writeManifest(server, shareName, task.remotePath, localManifest)

        if (writeResult.isSuccess) {
            emit(SyncProgress(statusText = "推送成功！已同步 $total 个文件。", isCompleted = true))
        } else {
            emit(SyncProgress(error = "远端清单更新失败: ${writeResult.exceptionOrNull()?.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 执行拉取 (Pull: Windows 共享文件夹 ➔ 本地)
     */
    fun pullFromSmb(
        task: SyncTask,
        server: RemoteServer,
        shareName: String,
        localDir: File
    ): Flow<SyncProgress> = flow {
        emit(SyncProgress(statusText = "正在读取远端清单..."))

        // 1. 获取远端 Manifest
        val remoteManifest = smbClient.readManifest(server, shareName, task.remotePath).getOrElse { err ->
            emit(SyncProgress(error = "无法读取远端清单: ${err.message}"))
            return@flow
        }

        if (remoteManifest == null || remoteManifest.files.isEmpty()) {
            emit(SyncProgress(error = "远端没有找到任何存档数据"))
            return@flow
        }

        // 2. 生成本地 Manifest
        emit(SyncProgress(statusText = "正在扫描本地文件..."))
        val localManifest = LocalFileEngine.generateManifest(task.id, localDir)

        // 3. Diff 对比
        val diff = DiffEngine.compare(localManifest, remoteManifest)
        val filesToDownload = diff.toDownload

        if (filesToDownload.isEmpty()) {
            emit(SyncProgress(statusText = "本地存档已是最新，无需拉取！", isCompleted = true))
            return@flow
        }

        // 4. 下载需变动的文件
        val total = filesToDownload.size
        filesToDownload.forEachIndexed { index, fileEntry ->
            emit(
                SyncProgress(
                    currentFile = fileEntry.relativePath,
                    transferredCount = index + 1,
                    totalCount = total,
                    statusText = "正在拉取 (${index + 1}/$total): ${fileEntry.relativePath}"
                )
            )

            val localFile = File(localDir, fileEntry.relativePath)
            localFile.parentFile?.mkdirs()

            val remotePath = "${task.remotePath}/${fileEntry.relativePath}"
            localFile.outputStream().use { output ->
                val downloadResult = smbClient.downloadFile(server, shareName, remotePath, output)
                if (downloadResult.isFailure) {
                    val err = downloadResult.exceptionOrNull()?.message ?: "下载失败"
                    emit(SyncProgress(error = "拉取文件 ${fileEntry.relativePath} 失败: $err"))
                    return@flow
                }
            }
        }

        emit(SyncProgress(statusText = "拉取成功！已同步 $total 个文件。", isCompleted = true))
    }.flowOn(Dispatchers.IO)
}
