package com.sumiri.gss.data.model

/**
 * 虚拟同步任务（Task 槽位）
 */
data class SyncTask(
    val id: String,          // 唯一标识/任务名（两台设备一致，如 "celeste-save"）
    val displayName: String, // 用户自定义显示名称
    val localUri: String,    // 本地文件夹 SAF Uri 或绝对路径
    val serverId: String,    // 绑定的远端服务器 ID
    val remotePath: String   // 远端存储上的相对/绝对目录路径
)
