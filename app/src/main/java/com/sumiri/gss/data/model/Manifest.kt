package com.sumiri.gss.data.model

/**
 * 代表单条文件元数据
 */
data class FileEntry(
    val relativePath: String, // 相对路径，如 "saves/slot1.sav"
    val size: Long,           // 文件大小 (bytes)
    val lastModified: Long,   // 最后修改时间戳
    val md5: String           // 文件 MD5 哈希值
)

/**
 * 存档同步清单
 */
data class Manifest(
    val taskId: String,       // 任务 ID / 槽位名称
    val timestamp: Long,      // 清单生成时间戳
    val files: List<FileEntry> // 文件夹下所有文件的元数据列表
)
