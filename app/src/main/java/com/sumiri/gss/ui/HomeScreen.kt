package com.sumiri.gss.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sumiri.gss.data.model.ProtocolType
import com.sumiri.gss.data.model.RemoteServer
import com.sumiri.gss.data.model.SyncTask
import com.sumiri.gss.data.remote.SmbStorageClient
import com.sumiri.gss.sync.SyncController
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun HomeScreen(
    server: RemoteServer,
    task: SyncTask,
    shareName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isOperating by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("欢迎使用 GameSaveSync。请确保已在【设置】中配置 SMB，在【文件管理】中添加 Task 映射。") }

    val smbClient = remember { SmbStorageClient() }
    val syncController = remember { SyncController(smbClient) }

    val localDir = remember(task.localUri, context) {
        val file = File(task.localUri)
        if (file.isAbsolute) file else context.getExternalFilesDir("test_saves") ?: context.filesDir
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部当前任务卡片
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "当前映射任务 (Task)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Task ID: ${task.id}",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "本地路径: ${localDir.absolutePath}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "远端服务器: ${server.baseUrl} (${task.remotePath})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // 核心一键推送 / 拉取操作区
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isOperating = true
                        localDir.mkdirs()
                        syncController.pushToSmb(task, server, shareName, localDir).collect { progress ->
                            logText = progress.error?.let { "❌ 推送失败: $it" } ?: "🔄 ${progress.statusText}"
                        }
                        isOperating = false
                    }
                },
                enabled = !isOperating && server.baseUrl.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("一键推送 (Push)")
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        isOperating = true
                        syncController.pullFromSmb(task, server, shareName, localDir).collect { progress ->
                            logText = progress.error?.let { "❌ 拉取失败: $it" } ?: "🔄 ${progress.statusText}"
                        }
                        isOperating = false
                    }
                },
                enabled = !isOperating && server.baseUrl.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("一键拉取 (Pull)")
            }
        }

        // 快捷测试辅助卡片
        OutlinedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("快捷操作: 在本地创建测试存档")
                OutlinedButton(
                    onClick = {
                        localDir.mkdirs()
                        val testFile = File(localDir, "save_slot_1.dat")
                        testFile.writeText("Test Save Game Data @ ${System.currentTimeMillis()}")
                        logText = "已生成测试文件: ${testFile.name}"
                    }
                ) {
                    Text("生成存档")
                }
            }
        }

        Text("实时同步日志", style = MaterialTheme.typography.titleMedium)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
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
