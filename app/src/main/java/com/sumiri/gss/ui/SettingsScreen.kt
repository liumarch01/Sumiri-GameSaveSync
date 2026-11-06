package com.sumiri.gss.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sumiri.gss.data.model.RemoteServer
import com.sumiri.gss.data.remote.SmbStorageClient
import kotlinx.coroutines.launch

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
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var ipAddress by remember(server) { mutableStateOf(server.baseUrl) }
    var shareInput by remember(shareName) { mutableStateOf(shareName) }
    var username by remember(server) { mutableStateOf(server.username) }
    var password by remember(server) { mutableStateOf(server.password) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }

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
            Text("远端存储配置 (Windows SMB)", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = ipAddress,
                onValueChange = {
                    ipAddress = it
                    onServerChanged(server.copy(baseUrl = it), shareInput)
                },
                label = { Text("Windows 电脑局域网 IP 地址") },
                placeholder = { Text("例如: 192.168.1.91") },
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
                label = { Text("Windows 账号用户名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    onServerChanged(server.copy(password = it), shareInput)
                },
                label = { Text("Windows 账号密码 (无密码账户留空)") },
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
                            testResult = "连接成功！网络及共享访问正常。"
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
                headlineContent = { Text("系统语言") },
                supportingContent = { Text("简体中文 (默认)") },
                colors = listItemColors
            )

            ListItem(
                headlineContent = { Text("关于 GameSaveSync (GameSS)") },
                supportingContent = { Text("版本 0.0.2") },
                colors = listItemColors
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
}
