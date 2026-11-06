package com.sumiri.gss.data.local

import com.sumiri.gss.data.model.FileEntry
import com.sumiri.gss.data.model.Manifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.abs

/**
 * 本地文件处理与 MD5 增量计算引擎
 */
object LocalFileEngine {

    /**
     * 遍历本地文件夹并生成包含所有文件 MD5 的 Manifest 清单（带有时间戳/大小快速复用机制）
     * @param taskId 任务 ID
     * @param baseDir 本地游戏存档根目录 File
     * @param cachedManifest 上一次已计算的 Manifest 缓存（可选）
     */
    suspend fun generateManifest(
        taskId: String,
        baseDir: File,
        cachedManifest: Manifest? = null
    ): Manifest = withContext(Dispatchers.IO) {
        val cachedMap = cachedManifest?.files?.associateBy { it.relativePath } ?: emptyMap()
        val fileEntries = mutableListOf<FileEntry>()

        if (baseDir.exists() && baseDir.isDirectory) {
            traverseDirectory(baseDir, baseDir, cachedMap, fileEntries)
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

    private fun traverseDirectory(
        rootDir: File,
        currentDir: File,
        cachedMap: Map<String, FileEntry>,
        result: MutableList<FileEntry>
    ) {
        val files = currentDir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                traverseDirectory(rootDir, file, cachedMap, result)
            } else if (file.isFile) {
                val relativePath = rootDir.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                val size = file.length()
                val lastModified = file.lastModified()

                // 时间戳与大小校验预判：如果与缓存完全匹配，跳过高昂的 MD5 文件流重算
                val cachedFile = cachedMap[relativePath]
                val md5 = if (cachedFile != null && cachedFile.size == size && abs(cachedFile.lastModified - lastModified) < 1000L) {
                    cachedFile.md5
                } else {
                    calculateMd5(file)
                }

                result.add(
                    FileEntry(
                        relativePath = relativePath,
                        size = size,
                        lastModified = lastModified,
                        md5 = md5
                    )
                )
            }
        }
    }
}
