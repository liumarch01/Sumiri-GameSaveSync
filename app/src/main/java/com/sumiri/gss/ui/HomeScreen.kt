package com.sumiri.gss.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sumiri.gss.data.local.LocalFileEngine
import com.sumiri.gss.data.model.RemoteServer
import com.sumiri.gss.data.model.SyncTask
import com.sumiri.gss.data.remote.SmbStorageClient
import com.sumiri.gss.sync.DiffEngine
import com.sumiri.gss.sync.SyncController
import kotlinx.coroutines.launch
import java.io.File

const val TASK_SYNC_ALL_ID = "SYNC_ALL"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    server: RemoteServer,
    shareName: String,
    taskList: List<SyncTask>,
    activeTaskId: String,
    onActiveTaskSelected: (String) -> Unit,
    onTaskDeleted: (SyncTask) -> Unit,
    onTaskRenamed: (SyncTask, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isOperating by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("准备就绪。可在【文件管理&映射】中添加更多游戏存档映射。") }
    var showTaskManageDialog by remember { mutableStateOf(false) }

    // 长按绑定的 Task 菜单项目
    var taskForMenu by remember { mutableStateOf<SyncTask?>(null) }
    var showRenameTaskDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // 自动检测与手动刷新 trigger
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var autoCheckTip by remember { mutableStateOf<String?>(null) }
    var isSmbAvailable by remember { mutableStateOf(false) }
    var isCheckingDiff by remember { mutableStateOf(false) }

    val smbClient = remember { SmbStorageClient() }
    val syncController = remember { SyncController(smbClient) }

    val isSyncAllMode = activeTaskId == TASK_SYNC_ALL_ID
    val currentTask = remember(taskList, activeTaskId) {
        taskList.find { it.id == activeTaskId } ?: taskList.firstOrNull()
    }

    // 后台连通性预检与 3-Way 变动检测
    LaunchedEffect(currentTask, taskList, server, shareName, isSyncAllMode, refreshTrigger) {
        if (taskList.isEmpty()) {
            autoCheckTip = "当前无任何规则，请先在‘文件管理’中添加映射"
            isSmbAvailable = false
            return@LaunchedEffect
        }

        if (server.baseUrl.isBlank()) {
            autoCheckTip = "请先在【设置】中配置 Windows 电脑 IP 地址"
            isSmbAvailable = false
            return@LaunchedEffect
        }

        isCheckingDiff = true
        autoCheckTip = "正在测试远端存储连通性..."

        val testRes = smbClient.testConnection(server, shareName)
        if (testRes.isFailure || !testRes.getOrDefault(false)) {
            val errReason = testRes.exceptionOrNull()?.message ?: "无法连接"
            autoCheckTip = "无法连接至远端存储服务: $errReason"
            isSmbAvailable = false
            isCheckingDiff = false
            return@LaunchedEffect
        }

        isSmbAvailable = true

        if (!isSyncAllMode && currentTask != null) {
            val localDir = File(currentTask.localUri)
            val localManifest = LocalFileEngine.generateManifest(currentTask.id, localDir)
            val remoteManifest = smbClient.readManifest(server, shareName, currentTask.remotePath).getOrNull()
            val diff3Way = DiffEngine.compare3Way(null, localManifest, remoteManifest)

            autoCheckTip = when {
                diff3Way.pullDownload.isNotEmpty() || diff3Way.pullDeleteLocal.isNotEmpty() ->
                    "检测到远端有更新的存档变更 (${diff3Way.pullDownload.size + diff3Way.pullDeleteLocal.size} 项)，建议执行同步"
                diff3Way.pushUpload.isNotEmpty() || diff3Way.pushDeleteRemote.isNotEmpty() ->
                    "检测到本地有未推送的最新存档 (${diff3Way.pushUpload.size + diff3Way.pushDeleteRemote.size} 项)，建议执行推送"
                else -> "本地与远端存档完全同步，已是最新版本"
            }
        } else {
            autoCheckTip = "当前模式：全选所有任务"
        }
        isCheckingDiff = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 当前任务卡片
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showTaskManageDialog = true },
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "当前任务 (点击管理/切换)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (taskList.isEmpty()) {
                        Text("当前无任何映射规则", style = MaterialTheme.typography.titleLarge)
                        Text("请先在【文件管理&映射】中添加规则", style = MaterialTheme.typography.bodySmall)
                    } else if (isSyncAllMode) {
                        Text("全选所有任务 (${taskList.size} 个)", style = MaterialTheme.typography.titleLarge)
                    } else if (currentTask != null) {
                        Text("Task ID: ${currentTask.id}", style = MaterialTheme.typography.titleLarge)
                        Text("本地路径: ${currentTask.localUri}", style = MaterialTheme.typography.bodySmall)
                        Text("远端目录: ${server.baseUrl}/$shareName/${currentTask.remotePath}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "切换任务", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // 自动检测与刷新卡片
        autoCheckTip?.let { tipText ->
            val isError = taskList.isEmpty() || (!isSmbAvailable && server.baseUrl.isNotBlank())
            Surface(
                color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCheckingDiff) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = when {
                                isError -> Icons.Default.ErrorOutline
                                tipText.contains("同步") -> Icons.Default.CheckCircle
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tipText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { refreshTrigger++ },
                        enabled = !isCheckingDiff && taskList.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新检测状态"
                        )
                    }
                }
            }
        }

        // 推送与同步按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isOperating = true
                        val tasksToSync = if (isSyncAllMode) taskList else listOfNotNull(currentTask)
                        for ((index, taskItem) in tasksToSync.withIndex()) {
                            val targetLocalDir = File(taskItem.localUri)
                            targetLocalDir.mkdirs()
                            logText = "正在推送 (${index + 1}/${tasksToSync.size}): Task ${taskItem.id}..."
                            syncController.pushToSmb(context, taskItem, server, shareName, targetLocalDir).collect { progress ->
                                logText = progress.error?.let { "任务 ${taskItem.id} 推送失败: $it" } ?: progress.statusText
                            }
                        }
                        refreshTrigger++
                        isOperating = false
                    }
                },
                enabled = !isOperating && isSmbAvailable && taskList.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("推送")
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        isOperating = true
                        val tasksToSync = if (isSyncAllMode) taskList else listOfNotNull(currentTask)
                        for ((index, taskItem) in tasksToSync.withIndex()) {
                            val targetLocalDir = File(taskItem.localUri)
                            targetLocalDir.mkdirs()
                            logText = "正在拉取 (${index + 1}/${tasksToSync.size}): Task ${taskItem.id}..."
                            syncController.pullFromSmb(context, taskItem, server, shareName, targetLocalDir).collect { progress ->
                                logText = progress.error?.let { "任务 ${taskItem.id} 同步失败: $it" } ?: progress.statusText
                            }
                        }
                        refreshTrigger++
                        isOperating = false
                    }
                },
                enabled = !isOperating && isSmbAvailable && taskList.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("同步")
            }
        }

        Text("同步实时日志", style = MaterialTheme.typography.titleMedium)

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

    // 任务管理与选择弹窗
    if (showTaskManageDialog) {
        AlertDialog(
            onDismissRequest = { showTaskManageDialog = false },
            title = { Text("映射任务管理") },
            text = {
                if (taskList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "当前无任何规则，请先在‘文件管理’中添加映射",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 全选所有任务
                        Surface(
                            color = if (isSyncAllMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onActiveTaskSelected(TASK_SYNC_ALL_ID)
                                    showTaskManageDialog = false
                                }
                        ) {
                            Text(
                                text = "全选所有任务 (${taskList.size} 个)",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        HorizontalDivider()

                        taskList.forEach { task ->
                            val isSelected = !isSyncAllMode && task.id == currentTask?.id
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                onActiveTaskSelected(task.id)
                                                showTaskManageDialog = false
                                            },
                                            onLongClick = {
                                                taskForMenu = task
                                            }
                                        )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Task ID: ${task.id}", style = MaterialTheme.typography.titleSmall)
                                        Text("本地: ${task.localUri}", style = MaterialTheme.typography.bodySmall)
                                        Text("远端: ${task.remotePath}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                // 靠右紧贴任务卡片右侧的长按浮动菜单
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 4.dp)
                                ) {
                                    DropdownMenu(
                                        expanded = (taskForMenu?.id == task.id),
                                        onDismissRequest = { taskForMenu = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("修改 Task ID 名称") },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                            onClick = {
                                                showRenameTaskDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("删除映射任务", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                showDeleteConfirmDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTaskManageDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    // 重命名 Task ID 弹窗
    if (showRenameTaskDialog && taskForMenu != null) {
        val targetTask = taskForMenu!!
        var newTaskIdInput by remember { mutableStateOf(targetTask.id) }

        AlertDialog(
            onDismissRequest = {
                showRenameTaskDialog = false
                taskForMenu = null
            },
            title = { Text("修改 Task ID") },
            text = {
                OutlinedTextField(
                    value = newTaskIdInput,
                    onValueChange = { newTaskIdInput = it },
                    label = { Text("新 Task ID 名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTaskIdInput.isNotBlank()) {
                            onTaskRenamed(targetTask, newTaskIdInput.trim())
                            showRenameTaskDialog = false
                            taskForMenu = null
                        }
                    }
                ) {
                    Text("确定保存")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameTaskDialog = false
                    taskForMenu = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    // 删除映射确认弹窗
    if (showDeleteConfirmDialog && taskForMenu != null) {
        val targetTask = taskForMenu!!
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                taskForMenu = null
            },
            title = { Text("确认删除映射") },
            text = { Text("确定要删除映射任务 '${targetTask.id}' 吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTaskDeleted(targetTask)
                        showDeleteConfirmDialog = false
                        taskForMenu = null
                    }
                ) {
                    Text("确定删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    taskForMenu = null
                }) {
                    Text("取消")
                }
            }
        )
    }
}
