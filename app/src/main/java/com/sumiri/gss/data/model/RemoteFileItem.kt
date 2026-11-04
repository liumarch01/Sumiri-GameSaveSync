package com.sumiri.gss.data.model

/**
 * 存储项（文件或文件夹元数据，用于文件管理浏览）
 */
data class RemoteFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0
)
