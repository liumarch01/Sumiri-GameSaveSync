package com.sumiri.gss.sync

import com.sumiri.gss.data.model.FileEntry
import com.sumiri.gss.data.model.Manifest

/**
 * 3-Way (Base, Local, Remote) 三方增量 Diff 对比结果
 */
data class Diff3WayResult(
    val pushUpload: List<FileEntry>,        // 本地新增或被修改，推送时需上传到远端
    val pushDeleteRemote: List<FileEntry>,  // 本地已删除，推送时需在远端删除
    val pullDownload: List<FileEntry>,      // 远端新增或被修改，拉取时需下载到本地
    val pullDeleteLocal: List<FileEntry>    // 远端已删除，拉取时需在本地删除
)

object DiffEngine {

    /**
     * 核心 3-Way 三方增量 Diff 算法 (Git 级基准对比)
     * @param base 上一次同步成功时的标准快照
     * @param local 本地当前文件状态
     * @param remote 远端当前文件状态
     */
    fun compare3Way(base: Manifest?, local: Manifest?, remote: Manifest?): Diff3WayResult {
        val baseMap = base?.files?.associateBy { it.relativePath } ?: emptyMap()
        val localMap = local?.files?.associateBy { it.relativePath } ?: emptyMap()
        val remoteMap = remote?.files?.associateBy { it.relativePath } ?: emptyMap()

        val pushUpload = mutableListOf<FileEntry>()
        val pushDeleteRemote = mutableListOf<FileEntry>()
        val pullDownload = mutableListOf<FileEntry>()
        val pullDeleteLocal = mutableListOf<FileEntry>()

        // ==================== 1. 分析本地变动 (Push 视角: Local vs Base) ====================
        for ((relativePath, localFile) in localMap) {
            val baseFile = baseMap[relativePath]
            val remoteFile = remoteMap[relativePath]

            if (baseFile == null) {
                // 情况 1: 本地新增
                if (remoteFile == null || localFile.md5 != remoteFile.md5) {
                    pushUpload.add(localFile)
                }
            } else if (localFile.md5 != baseFile.md5) {
                // 情况 2: 本地被修改
                if (remoteFile == null || localFile.lastModified >= remoteFile.lastModified) {
                    pushUpload.add(localFile)
                }
            }
        }

        // 分析本地删除项: Base 中存在，但 Local 中被删除了
        for ((relativePath, baseFile) in baseMap) {
            if (!localMap.containsKey(relativePath)) {
                if (remoteMap.containsKey(relativePath)) {
                    pushDeleteRemote.add(baseFile)
                }
            }
        }

        // ==================== 2. 分析远端变动 (Pull 视角: Remote vs Base) ====================
        for ((relativePath, remoteFile) in remoteMap) {
            val baseFile = baseMap[relativePath]
            val localFile = localMap[relativePath]

            if (baseFile == null) {
                // 情况 1: 远端新增
                if (localFile == null || remoteFile.md5 != localFile.md5) {
                    pullDownload.add(remoteFile)
                }
            } else if (remoteFile.md5 != baseFile.md5) {
                // 情况 2: 远端被修改
                if (localFile == null || remoteFile.lastModified >= localFile.lastModified) {
                    pullDownload.add(remoteFile)
                }
            }
        }

        // 分析远端删除项: Base 中存在，但 Remote 中被删除了
        for ((relativePath, baseFile) in baseMap) {
            if (!remoteMap.containsKey(relativePath)) {
                if (localMap.containsKey(relativePath)) {
                    pullDeleteLocal.add(baseFile)
                }
            }
        }

        return Diff3WayResult(
            pushUpload = pushUpload.distinctBy { it.relativePath },
            pushDeleteRemote = pushDeleteRemote.distinctBy { it.relativePath },
            pullDownload = pullDownload.distinctBy { it.relativePath },
            pullDeleteLocal = pullDeleteLocal.distinctBy { it.relativePath }
        )
    }
}
