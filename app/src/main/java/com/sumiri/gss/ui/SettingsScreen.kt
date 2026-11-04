package com.sumiri.gss.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    onServerChanged: (RemoteServer, String) -> Unit,
    onResetAll: () -> Unit,
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
    var showResetDialog by remember { mutableStateOf(false) }

    val smbClient = remember { SmbStorageClient() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("远端存储配置 (Windows SMB)", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { showResetDialog = true }) {
                Text("重置配置", color = MaterialTheme.colorScheme.error)
            }
        }

        OutlinedTextField(
            value = ipAddress,
            onValueChange = {
                ipAddress = it
                onServerChanged(server.copy(baseUrl = it), shareInput)
            },
            label = { Text("Windows 电脑局域网 IP 地址") },
            placeholder = { Text("例如: 192.168.1.100") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = shareInput,
            onValueChange = {
                shareInput = it
                onServerChanged(server, it)
            },
            label = { Text("共享文件夹名称 (整盘共享填 D$ 或 D)") },
            placeholder = { Text("例如: D$ 或 GameSaves") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                onServerChanged(server.copy(username = it), shareInput)
            },
            label = { Text("Windows 账号用户名 (无密码账户可留空)") },
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
                    testResult = "正在连接到 $ipAddress/$shareInput ..."
                    val newServer = server.copy(
                        baseUrl = ipAddress,
                        username = username,
                        password = password
                    )
                    val res = smbClient.testConnection(newServer, shareInput)
                    if (res.getOrDefault(false)) {
                        testResult = "✅ 连接成功！网络及共享访问正常。"
                    } else {
                        testResult = "❌ ${res.exceptionOrNull()?.message ?: "连接失败"}"
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

        HorizontalDivider()

        Text("应用设置", style = MaterialTheme.typography.titleLarge)

        ListItem(
            headlineContent = { Text("系统语言") },
            supportingContent = { Text("简体中文 (默认)") }
        )

        ListItem(
            headlineContent = { Text("颜色模式") },
            supportingContent = { Text("跟随系统深色/浅色主题") }
        )

        ListItem(
            headlineContent = { Text("关于 GameSaveSync (GameSS)") },
            supportingContent = { Text("版本 1.0.0 (Pure Client 无后端架构)") }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置配置") },
            text = { Text("确定要清除保存的所有服务器与 Task ID 映射配置吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetAll()
                        showResetDialog = false
                    }
                ) {
                    Text("确定重置", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
