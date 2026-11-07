package com.sumiri.gss.sync

import android.content.Context
import com.google.gson.Gson
import com.sumiri.gss.data.local.LocalFileEngine
import com.sumiri.gss.data.model.Manifest
import com.sumiri.gss.data.model.RemoteServer
import com.sumiri.gss.data.model.SyncTask
import com.sumiri.gss.data.remote.SmbStorageClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * 3-Way 引擎进度事件
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
 * 核心 3-Way 同步控制器 (Push / Pull)
 */
class SyncController(
    private val smbClient: SmbStorageClient = SmbStorageClient(),
    private val gson: Gson = Gson()
) {

    /**
     * 执行 3-Way 推送 (Push: 本地 ➔ Windows 共享文件夹)
     */
    fun pushToSmb(
        context: Context,
        task: SyncTask,
        server: RemoteServer,
        shareName: String,
        localDir: File
    ): Flow<SyncProgress> = flow {
        emit(SyncProgress(statusText = "正在扫描本地存档并计算 MD5..."))

        // 1. 读取 Base 快照
        val baseManifest = loadBaseManifest(context, task.id)

        // 2. 生成本地 Manifest
        val localManifest = LocalFileEngine.generateManifest(task.id, localDir)

        emit(SyncProgress(statusText = "正在读取远端清单喵..."))
        // 3. 获取远端 Manifest
        val remoteManifest = smbClient.readManifest(server, shareName, task.remotePath).getOrNull()

        // 4. 3-Way 增量 Diff 比对
        val diff3Way = DiffEngine.compare3Way(baseManifest, localManifest, remoteManifest)
        val filesToUpload = diff3Way.pushUpload
        val filesToDeleteRemote = diff3Way.pushDeleteRemote

        if (filesToUpload.isEmpty() && filesToDeleteRemote.isEmpty()) {
            // 同步 Base
            saveBaseManifest(context, task.id, localManifest)
            emit(SyncProgress(statusText = "本地与远端存档一致，无需推送！", isCompleted = true))
            return@flow
        }

        val total = filesToUpload.size + filesToDeleteRemote.size
        var currentCount = 0

        // 5. 在远端删除本地已删的文件
        filesToDeleteRemote.forEach { fileEntry ->
            currentCount++
            emit(
                SyncProgress(
                    currentFile = fileEntry.relativePath,
                    transferredCount = currentCount,
                    totalCount = total,
                    statusText = "正在远端删除旧文件 ($currentCount/$total): ${fileEntry.relativePath}"
                )
            )
            val remotePath = "${task.remotePath}/${fileEntry.relativePath}"
            smbClient.deleteItem(server, shareName, remotePath, isDirectory = false)
        }

        // 6. 上传新增或修改的文件
        filesToUpload.forEach { fileEntry ->
            currentCount++
            emit(
                SyncProgress(
                    currentFile = fileEntry.relativePath,
                    transferredCount = currentCount,
                    totalCount = total,
                    statusText = "正在推送 ($currentCount/$total): ${fileEntry.relativePath}"
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

        // 7. 重新生成并更新 Base & 远端 Manifest
        emit(SyncProgress(statusText = "正在更新远端与本地快照 (manifest.json)..."))
        val updatedLocalManifest = LocalFileEngine.generateManifest(task.id, localDir)
        val writeResult = smbClient.writeManifest(server, shareName, task.remotePath, updatedLocalManifest)

        if (writeResult.isSuccess) {
            saveBaseManifest(context, task.id, updatedLocalManifest)
            emit(SyncProgress(statusText = "推送成功！共处理 $total 项变更喵~", isCompleted = true))
        } else {
            emit(SyncProgress(error = "远端清单更新失败: ${writeResult.exceptionOrNull()?.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 执行 3-Way 拉取 (Pull: Windows 共享文件夹 ➔ 本地)
     */
    fun pullFromSmb(
        context: Context,
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

        // 2. 读取 Base 快照
        val baseManifest = loadBaseManifest(context, task.id)

        // 3. 生成本地 Manifest
        emit(SyncProgress(statusText = "正在扫描本地文件..."))
        val localManifest = LocalFileEngine.generateManifest(task.id, localDir)

        // 4. 3-Way 增量 Diff 比对
        val diff3Way = DiffEngine.compare3Way(baseManifest, localManifest, remoteManifest)
        val filesToDownload = diff3Way.pullDownload
        val filesToDeleteLocal = diff3Way.pullDeleteLocal

        if (filesToDownload.isEmpty() && filesToDeleteLocal.isEmpty()) {
            saveBaseManifest(context, task.id, remoteManifest)
            emit(SyncProgress(statusText = "本地存档已是最新，无需拉取！", isCompleted = true))
            return@flow
        }

        val total = filesToDownload.size + filesToDeleteLocal.size
        var currentCount = 0

        // 5. 在本地删除远端已删的文件
        filesToDeleteLocal.forEach { fileEntry ->
            currentCount++
            emit(
                SyncProgress(
                    currentFile = fileEntry.relativePath,
                    transferredCount = currentCount,
                    totalCount = total,
                    statusText = "正在同步删除本地文件 ($currentCount/$total): ${fileEntry.relativePath}"
                )
            )
            val targetFile = File(localDir, fileEntry.relativePath)
            if (targetFile.exists()) {
                targetFile.delete()
            }
        }

        // 6. 下载新增或修改的文件
        filesToDownload.forEach { fileEntry ->
            currentCount++
            emit(
                SyncProgress(
                    currentFile = fileEntry.relativePath,
                    transferredCount = currentCount,
                    totalCount = total,
                    statusText = "正在同步 ($currentCount/$total): ${fileEntry.relativePath}"
                )
            )

            val localFile = File(localDir, fileEntry.relativePath)
            localFile.parentFile?.mkdirs()

            val remotePath = "${task.remotePath}/${fileEntry.relativePath}"
            localFile.outputStream().use { output ->
                val downloadResult = smbClient.downloadFile(server, shareName, remotePath, output)
                if (downloadResult.isFailure) {
                    val err = downloadResult.exceptionOrNull()?.message ?: "下载失败"
                    emit(SyncProgress(error = "同步文件 ${fileEntry.relativePath} 失败: $err"))
                    return@flow
                }
            }
        }

        // 7. 更新本地 Base 快照
        val finalLocalManifest = LocalFileEngine.generateManifest(task.id, localDir)
        saveBaseManifest(context, task.id, finalLocalManifest)
        emit(SyncProgress(statusText = "同步成功！共处理 $total 项变更。", isCompleted = true))
    }.flowOn(Dispatchers.IO)

    // ==================== Base 快照管理 ====================

    private fun loadBaseManifest(context: Context, taskId: String): Manifest? {
        val baseFile = File(context.filesDir, "manifest_base_$taskId.json")
        if (!baseFile.exists()) return null
        return runCatching {
            gson.fromJson(baseFile.readText(), Manifest::class.java)
        }.getOrNull()
    }

    private fun saveBaseManifest(context: Context, taskId: String, manifest: Manifest) {
        runCatching {
            val baseFile = File(context.filesDir, "manifest_base_$taskId.json")
            baseFile.writeText(gson.toJson(manifest))
        }
    }
}
