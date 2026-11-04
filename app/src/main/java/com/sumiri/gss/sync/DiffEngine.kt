package com.sumiri.gss.sync

import com.sumiri.gss.data.model.FileEntry
import com.sumiri.gss.data.model.Manifest

/**
 * 增量同步 Diff 结果
 */
data class DiffResult(
    val toUpload: List<FileEntry>,   // 本地新增或修改的文件（需推送）
    val toDownload: List<FileEntry>, // 远端新增或修改的文件（需拉取）
    val toDeleteLocal: List<FileEntry>, // 远端已删除，拉取时本地需同步删除的文件
    val unchanged: List<FileEntry>   // 未变动的文件
)

object DiffEngine {

    /**
     * 对比本地 Manifest 和远端 Manifest
     */
    fun compare(local: Manifest?, remote: Manifest?): DiffResult {
        val localMap = local?.files?.associateBy { it.relativePath } ?: emptyMap()
        val remoteMap = remote?.files?.associateBy { it.relativePath } ?: emptyMap()

        val toUpload = mutableListOf<FileEntry>()
        val toDownload = mutableListOf<FileEntry>()
        val unchanged = mutableListOf<FileEntry>()

        // 1. 遍历本地文件
        for ((relativePath, localFile) in localMap) {
            val remoteFile = remoteMap[relativePath]
            if (remoteFile == null) {
                // 远端没有，需要上传
                toUpload.add(localFile)
            } else if (localFile.md5 != remoteFile.md5) {
                // MD5 不一致，发生了修改
                toUpload.add(localFile)     // 推送时用 localFile
                toDownload.add(remoteFile)   // 拉取时用 remoteFile
            } else {
                unchanged.add(localFile)
            }
        }

        // 2. 遍历远端独有的文件
        for ((relativePath, remoteFile) in remoteMap) {
            if (!localMap.containsKey(relativePath)) {
                // 本地没有，需要下载
                toDownload.add(remoteFile)
            }
        }

        return DiffResult(
            toUpload = toUpload,
            toDownload = toDownload,
            toDeleteLocal = emptyList(), // 可后续完善删除逻辑
            unchanged = unchanged
        )
    }
}
