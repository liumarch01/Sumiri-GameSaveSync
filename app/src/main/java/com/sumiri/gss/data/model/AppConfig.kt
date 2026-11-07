package com.sumiri.gss.data.model

/**
 * 应用全局配置接口，所有版本信息和全局链接统一在此集中维护
 * 你可以在这里直接修改网址与版本号，全应用会自动读取
 */
object AppConfig {
    /** 当前应用版本名称（修改此处的数值可影响全局所有涉及版本号显示的页面） */
    const val APP_VERSION = "0.0.3"

    /** GitHub 仓库相关链接（填入你自己的 GitHub 用户名和仓库名） */
    const val GITHUB_RELEASES_API = "https://api.github.com/repos/liumarch01/Sumiri-GameSaveSync/releases/latest"
    const val GITHUB_REPO_URL = "https://github.com/liumarch01/Sumiri-GameSaveSync"
    const val MORE_INFO_URL = "bilibili://video/BV1GJ411x7h7"
}
