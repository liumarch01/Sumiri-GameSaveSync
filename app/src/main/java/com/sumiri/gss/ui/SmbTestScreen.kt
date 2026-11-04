package com.sumiri.gss.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sumiri.gss.data.model.ProtocolType
import com.sumiri.gss.data.model.RemoteServer
import com.sumiri.gss.data.model.SyncTask
import com.sumiri.gss.data.remote.SmbStorageClient
import com.sumiri.gss.sync.SyncController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmbTestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // 输入框状态
    var ipAddress by remember { mutableStateOf("192.168.1.100") }
    var shareName by remember { mutableStateOf("GameSaves") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var taskId by remember { mutableStateOf("test-game-slot") }
    var remoteFolder by remember { mutableStateOf("Celeste") }

    // 运行状态与日志
    var isOperating by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("准备就绪。请输入 Windows PC 共享信息。") }

    val smbClient = remember { SmbStorageClient() }
    val syncController = remember { SyncController(smbClient) }

    // 本地测试文件夹应用私有存储
    val localTestDir = remember(context) {
        context.getExternalFilesDir("test_saves") ?: context.filesDir
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Windows 共享文件夹 (SMB) 测试") }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Windows 电脑共享配置", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text("Windows 电脑局域网 IP 地址") },
                placeholder = { Text("例如: 192.168.1.100") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = shareName,
                onValueChange = { shareName = it },
                label = { Text("共享文件夹名称") },
                placeholder = { Text("例如: GameSaves") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Windows 账号用户名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Windows 账号密码") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            Text("虚拟同步任务配置 (Task Slot)", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = taskId,
                onValueChange = { taskId = it },
                label = { Text("Task ID (任务槽位)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = remoteFolder,
                onValueChange = { remoteFolder = it },
                label = { Text("远端游戏目录") },
                placeholder = { Text("例如: Celeste") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                text = "本地测试存档目录: ${localTestDir.absolutePath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 测试连接按钮
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isOperating = true
                            logText = "正在连接到 $ipAddress/$shareName ..."
                            val server = RemoteServer(
                                id = "test_server",
                                name = "Test PC",
                                protocol = ProtocolType.SMB,
                                baseUrl = ipAddress,
                                username = username,
                                password = password
                            )
                            val res = smbClient.testConnection(server, shareName)
                            if (res.getOrDefault(false)) {
                                logText = "✅ 连接成功！Windows 共享文件夹可用。"
                            } else {
                                logText = "❌ 连接失败: ${res.exceptionOrNull()?.message ?: "未知错误，请检查 IP/密码/共享权限"}"
                            }
                            isOperating = false
                        }
                    },
                    enabled = !isOperating,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("测试连接")
                }

                // 创建示例存档文件按钮
                OutlinedButton(
                    onClick = {
                        localTestDir.mkdirs()
                        val saveFile = java.io.File(localTestDir, "save_slot_1.dat")
                        saveFile.writeText("Game Save Data Timestamp: ${System.currentTimeMillis()}\nScore: 9999")
                        logText = "已在本地生成测试存档: ${saveFile.name}"
                    },
                    enabled = !isOperating,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("生成测试存档")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 推送按钮
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isOperating = true
                            val server = RemoteServer(
                                id = "test_server",
                                name = "Test PC",
                                protocol = ProtocolType.SMB,
                                baseUrl = ipAddress,
                                username = username,
                                password = password
                            )
                            val task = SyncTask(
                                id = taskId,
                                displayName = taskId,
                                localUri = localTestDir.absolutePath,
                                serverId = server.id,
                                remotePath = remoteFolder
                            )
                            syncController.pushToSmb(task, server, shareName, localTestDir).collect { progress ->
                                logText = if (progress.error != null) {
                                    "❌ 推送出错: ${progress.error}"
                                } else {
                                    "🔄 ${progress.statusText}"
                                }
                            }
                            isOperating = false
                        }
                    },
                    enabled = !isOperating,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("推送 (Push)")
                }

                // 拉取按钮
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isOperating = true
                            val server = RemoteServer(
                                id = "test_server",
                                name = "Test PC",
                                protocol = ProtocolType.SMB,
                                baseUrl = ipAddress,
                                username = username,
                                password = password
                            )
                            val task = SyncTask(
                                id = taskId,
                                displayName = taskId,
                                localUri = localTestDir.absolutePath,
                                serverId = server.id,
                                remotePath = remoteFolder
                            )
                            syncController.pullFromSmb(task, server, shareName, localTestDir).collect { progress ->
                                logText = if (progress.error != null) {
                                    "❌ 拉取出错: ${progress.error}"
                                } else {
                                    "🔄 ${progress.statusText}"
                                }
                            }
                            isOperating = false
                        }
                    },
                    enabled = !isOperating,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("拉取 (Pull)")
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = logText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
