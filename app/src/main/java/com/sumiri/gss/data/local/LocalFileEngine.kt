package com.sumiri.gss.data.local

import com.sumiri.gss.data.model.FileEntry
import com.sumiri.gss.data.model.Manifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * 本地文件处理与 MD5 计算引擎
 */
object LocalFileEngine {

    /**
     * 遍历本地文件夹并生成包含所有文件 MD5 的 Manifest 清单
     * @param taskId 任务 ID
     * @param baseDir 本地游戏存档根目录 File
     */
    suspend fun generateManifest(taskId: String, baseDir: File): Manifest = withContext(Dispatchers.IO) {
        val fileEntries = mutableListOf<FileEntry>()
        if (baseDir.exists() && baseDir.isDirectory) {
            traverseDirectory(baseDir, baseDir, fileEntries)
        }
        Manifest(
            taskId = taskId,
            timestamp = System.currentTimeMillis(),
            files = fileEntries
        )
    }

    /**
     * 计算输入流的 MD5 哈希值
     */
    fun calculateMd5(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 计算文件的 MD5 哈希值
     */
    fun calculateMd5(file: File): String {
        return file.inputStream().use { calculateMd5(it) }
    }

    private fun traverseDirectory(rootDir: File, currentDir: File, result: MutableList<FileEntry>) {
        val files = currentDir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                traverseDirectory(rootDir, file, result)
            } else if (file.isFile) {
                // 计算相对路径，如 "saves/slot1.sav"
                val relativePath = rootDir.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                val md5 = calculateMd5(file)
                result.add(
                    FileEntry(
                        relativePath = relativePath,
                        size = file.length(),
                        lastModified = file.lastModified(),
                        md5 = md5
                    )
                )
            }
        }
    }
}
