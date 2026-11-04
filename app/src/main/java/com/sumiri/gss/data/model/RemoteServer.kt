package com.sumiri.gss.data.model

enum class ProtocolType {
    WEBDAV,
    SMB
}

/**
 * 远端服务器配置
 */
data class RemoteServer(
    val id: String,
    val name: String,         // 服务器名称，如 "我的 NAS / 路由器"
    val protocol: ProtocolType,
    val baseUrl: String,      // 服务器地址/URL
    val username: String = "",
    val password: String = ""
)
