package com.sumiri.gss.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sumiri.gss.data.model.AppConfig
import com.sumiri.gss.data.model.RemoteServer
import com.sumiri.gss.data.remote.SmbStorageClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun SettingsScreen(
    server: RemoteServer,
    shareName: String,
    showHiddenFiles: Boolean,
    themeMode: String,
    onServerChanged: (RemoteServer, String) -> Unit,
    onShowHiddenFilesChanged: (Boolean) -> Unit,
    onThemeModeChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var ipAddress by remember(server) { mutableStateOf(server.baseUrl) }
    var shareInput by remember(shareName) { mutableStateOf(shareName) }
    var username by remember(server) { mutableStateOf(server.username) }
    var password by remember(server) { mutableStateOf(server.password) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    var currentLanguage by remember { mutableStateOf("ZH") }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    // 1. 这里修改类型为 UpdateStatus?
    var updateResultStatus by remember { mutableStateOf<UpdateStatus?>(null) }

    val smbClient = remember { SmbStorageClient() }
    val listItemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("远端存储配置 (SMB)", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = ipAddress,
                onValueChange = {
                    ipAddress = it
                    onServerChanged(server.copy(baseUrl = it), shareInput)
                },
                label = { Text("远端存储的 IP 地址") },
                placeholder = { Text("例如: 192.168.78.91") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = shareInput,
                onValueChange = {
                    shareInput = it
                    onServerChanged(server, it)
                },
                label = { Text("共享文件夹名称") },
                placeholder = { Text("例如: D$(整个D盘) 或 GameSaves") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    onServerChanged(server.copy(username = it), shareInput)
                },
                label = { Text("账号用户名") },
                placeholder = { Text("例如: Administrator(默认值)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    onServerChanged(server.copy(password = it), shareInput)
                },
                label = { Text("账号密码 (无密码账户留空)") },
                placeholder = { Text("默认无密码，无密码则留空") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    coroutineScope.launch {
                        isTesting = true
                        val effectiveShare = shareInput.ifBlank { "D$" }
                        val effectiveUsername = username.ifBlank { "Administrator" }
                        testResult = "正在连接到 $ipAddress/$effectiveShare ..."
                        val newServer = server.copy(
                            baseUrl = ipAddress,
                            username = effectiveUsername,
                            password = password
                        )
                        val res = smbClient.testConnection(newServer, effectiveShare)
                        if (res.getOrDefault(false)) {
                            testResult = "连接成功！网络及共享访问正常"
                        } else {
                            testResult = res.exceptionOrNull()?.message ?: "连接失败"
                        }
                        isTesting = false
                    }
                },
                enabled = !isTesting && ipAddress.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("测试 SMB 服务器连接")
            }

            testResult?.let {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        HorizontalDivider()

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "应用设置",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            ListItem(
                headlineContent = { Text("在文件管理中显示隐藏文件夹") },
                supportingContent = { Text("包含以点 (.) 开头的配置文件与系统回收站文件") },
                trailingContent = {
                    Switch(
                        checked = showHiddenFiles,
                        onCheckedChange = { onShowHiddenFilesChanged(it) }
                    )
                },
                colors = listItemColors
            )

            ListItem(
                headlineContent = { Text("颜色模式") },
                supportingContent = {
                    Text(
                        when (themeMode) {
                            "LIGHT" -> "浅色模式"
                            "DARK" -> "深色模式"
                            else -> "跟随系统"
                        }
                    )
                },
                colors = listItemColors,
                modifier = Modifier.clickable { showThemeDialog = true }
            )

            ListItem(
                headlineContent = { Text("软件语言") },
                supportingContent = {
                    Text(
                        when (currentLanguage) {
                            "EN" -> "English (没做)"
                            "JA" -> "日本語 (没做)"
                            else -> "简体中文 (默认)"
                        }
                    )
                },
                colors = listItemColors,
                modifier = Modifier.clickable { showLanguageDialog = true }
            )

            ListItem(
                headlineContent = { Text("关于 GameSaveSync (GameSS)") },
                supportingContent = { Text("版本 ${AppConfig.APP_VERSION}") },
                colors = listItemColors,
                modifier = Modifier.clickable { showAboutDialog = true }
            )
        }
    }

    // 颜色模式选择弹窗
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("选择颜色模式") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onThemeModeChanged("SYSTEM")
                                showThemeDialog = false
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        RadioButton(selected = themeMode == "SYSTEM", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("跟随系统")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onThemeModeChanged("LIGHT")
                                showThemeDialog = false
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        RadioButton(selected = themeMode == "LIGHT", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("浅色模式")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onThemeModeChanged("DARK")
                                showThemeDialog = false
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        RadioButton(selected = themeMode == "DARK", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("深色模式")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 系统语言选择弹窗
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("选择软件界面语言") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentLanguage = "ZH"
                                showLanguageDialog = false
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        RadioButton(selected = currentLanguage == "ZH", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("简体中文 (默认)")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentLanguage = "EN"
                                showLanguageDialog = false
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        RadioButton(selected = currentLanguage == "EN", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("English（没做）")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentLanguage = "JA"
                                showLanguageDialog = false
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        RadioButton(selected = currentLanguage == "JA", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("日本語（没做）")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 关于详情弹窗 (GitHub / 检查更新 / 更多信息)
    if (showAboutDialog) {
        Dialog(onDismissRequest = { showAboutDialog = false }) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("关于 GameSaveSync", style = MaterialTheme.typography.headlineSmall)
                    Text("版本号: ${AppConfig.APP_VERSION}")

                    // 2. 根据不同的 UpdateStatus 展示不同的提示框
                    if (isCheckingUpdate) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("正在查询 GitHub Releases...", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        updateResultStatus?.let { status ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                when (status) {
                                    is UpdateStatus.NewVersion -> {
                                        // 发现新版本：设置蓝色文字 + 点击跳转 URL 浏览器
                                        Text(
                                            text = status.message,
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier
                                                .clickable {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(status.url))
                                                    context.startActivity(intent)
                                                }
                                                .padding(8.dp)
                                        )
                                    }
                                    is UpdateStatus.UpToDate -> {
                                        Text(
                                            text = status.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                    is UpdateStatus.Error -> {
                                        Text(
                                            text = status.message,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 大按钮：检查更新
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isCheckingUpdate = true
                                updateResultStatus = checkGithubRelease()
                                isCheckingUpdate = false
                            }
                        },
                        enabled = !isCheckingUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("检查更新")
                    }

                    // 大按钮：更多信息
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.MORE_INFO_URL))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("使用指南（别点...）")
                    }

                    // 底部超链接样式：GitHub 仓库
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.GITHUB_REPO_URL))
                                context.startActivity(intent)
                            }
                        ) {
                            Text(
                                text = "GitHub：GameSaveSync",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

// 结果密封类
sealed class UpdateStatus {
    data class NewVersion(val message: String, val url: String) : UpdateStatus()
    data class UpToDate(val message: String) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

private suspend fun checkGithubRelease(): UpdateStatus = withContext(Dispatchers.IO) {
    try {
        val url = URL(AppConfig.GITHUB_RELEASES_API)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "GameSaveSync-App")
        conn.connectTimeout = 5000
        conn.readTimeout = 5000

        if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val tagName = response.substringAfter("\"tag_name\":\"").substringBefore("\"")

            // Releases 页面跳转链接
            val releaseWebUrl = "https://github.com/liumarch01/Sumiri-GameSaveSync/releases/tag/$tagName"

            if (tagName.isNotBlank() && tagName != "v${AppConfig.APP_VERSION}" && tagName != AppConfig.APP_VERSION) {
                UpdateStatus.NewVersion("发现新版本: $tagName！点击前往 GitHub 下载", releaseWebUrl)
            } else {
                UpdateStatus.UpToDate("当前已是最新版本 (${AppConfig.APP_VERSION})")
            }
        } else {
            UpdateStatus.Error("获取更新失败 (HTTP ${conn.responseCode})")
        }
    } catch (e: Exception) {
        UpdateStatus.Error("检查更新失败: ${e.message ?: "网络异常"}")
    }
}