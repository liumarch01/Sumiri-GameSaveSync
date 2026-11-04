package com.sumiri.gss.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sumiri.gss.data.model.RemoteFileItem
import com.sumiri.gss.data.model.RemoteServer
import com.sumiri.gss.data.model.SyncTask
import com.sumiri.gss.data.remote.SmbStorageClient
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    server: RemoteServer,
    shareName: String,
    currentTask: SyncTask,
    onTaskCreated: (SyncTask) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 本地, 1: 远端 SMB
    var showCreateDialog by remember { mutableStateOf(false) }

    // 存储权限检查与申请 launcher
    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
    }

    // 本地真根目录路径层级管理 (/storage/emulated/0/)
    val rootDir = remember { Environment.getExternalStorageDirectory() }
    var currentLocalDir by remember { mutableStateOf(rootDir) }

    // 远端 SMB 目录路径层级管理
    var currentRemotePath by remember { mutableStateOf("") }

    val localFiles = remember(currentLocalDir, hasStoragePermission) {
        if (!hasStoragePermission) emptyList()
        else {
            currentLocalDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.map { file ->
                    RemoteFileItem(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = file.isDirectory,
                        size = file.length(),
                        lastModified = file.lastModified()
                    )
                } ?: emptyList()
        }
    }

    // 远端 SMB 文件列表状态
    val smbClient = remember { SmbStorageClient() }
    var remoteFiles by remember { mutableStateOf<List<RemoteFileItem>>(emptyList()) }
    var isRemoteLoading by remember { mutableStateOf(false) }
    var remoteError by remember { mutableStateOf<String?>(null) }

    // 切换到远端 Tab 或远端路径变化时自动加载列表
    LaunchedEffect(selectedTab, server, shareName, currentRemotePath) {
        if (selectedTab == 1 && server.baseUrl.isNotBlank()) {
            isRemoteLoading = true
            remoteError = null
            val res = smbClient.listRemoteFiles(server, shareName, currentRemotePath)
            res.onSuccess { list ->
                remoteFiles = list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            }.onFailure { err ->
                remoteError = err.message ?: "读取远端目录失败"
            }
            isRemoteLoading = false
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "新建映射") },
                text = { Text("新建 Task 映射") }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("本地文件 (Local)") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("远端存储 (SMB)") }
                )
            }

            // 权限请求红框 Banner
            if (!hasStoragePermission) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "读取存储中的游戏存档需要授予读取权限",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    requestPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_EXTERNAL_STORAGE,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        )
                                    )
                                }
                            }
                        ) {
                            Text("去授权")
                        }
                    }
                }
            }

            // 本地路径面包屑导航
            if (selectedTab == 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentLocalDir.parentFile != null && currentLocalDir != rootDir) {
                            TextButton(
                                onClick = { currentLocalDir = currentLocalDir.parentFile ?: rootDir }
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("返回上一级")
                            }
                        }
                        Text(
                            text = "路径: ${currentLocalDir.absolutePath}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // 远端 SMB 路径面包屑导航
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentRemotePath.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    val lastSlash = currentRemotePath.lastIndexOf('/')
                                    currentRemotePath = if (lastSlash > 0) currentRemotePath.substring(0, lastSlash) else ""
                                }
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("返回上一级")
                            }
                        }
                        Text(
                            text = "远端路径: /${currentRemotePath}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (selectedTab == 0) {
                    FileListView(
                        items = localFiles,
                        emptyText = if (hasStoragePermission) "当前目录为空" else "请先点击上方“去授权”开放文件权限",
                        onItemClick = { item ->
                            if (item.isDirectory) {
                                currentLocalDir = File(item.path)
                            }
                        }
                    )
                } else {
                    when {
                        server.baseUrl.isBlank() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("请先在【设置】中配置 Windows 电脑的 IP 与共享名称")
                            }
                        }
                        isRemoteLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        remoteError != null -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "❌ $remoteError",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        else -> {
                            FileListView(
                                items = remoteFiles,
                                emptyText = "远端目录为空",
                                onItemClick = { item ->
                                    if (item.isDirectory) {
                                        currentRemotePath = item.path
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 新建 Task ID 映射弹窗
    if (showCreateDialog) {
        CreateTaskDialog(
            currentLocalPath = currentLocalDir.absolutePath,
            currentRemotePath = currentRemotePath.ifBlank { "GameSS" },
            defaultTask = currentTask,
            onDismiss = { showCreateDialog = false },
            onConfirm = { newTask ->
                onTaskCreated(newTask)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun FileListView(
    items: List<RemoteFileItem>,
    emptyText: String,
    onItemClick: (RemoteFileItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items) { item ->
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = {
                        Text(if (item.isDirectory) "文件夹" else "大小: ${item.size} bytes")
                    },
                    leadingContent = {
                        Icon(
                            imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    },
                    modifier = Modifier.clickable { onItemClick(item) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CreateTaskDialog(
    currentLocalPath: String,
    currentRemotePath: String,
    defaultTask: SyncTask,
    onDismiss: () -> Unit,
    onConfirm: (SyncTask) -> Unit
) {
    val suggestedTaskId = remember(currentLocalPath) {
        val lastDir = currentLocalPath.substringAfterLast('/').ifBlank { "game-save" }
        "$lastDir-task"
    }

    var taskId by remember { mutableStateOf(suggestedTaskId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建 Task ID 映射规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 选中的本地路径
                OutlinedTextField(
                    value = currentLocalPath,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("映射当前本地路径") },
                    modifier = Modifier.fillMaxWidth()
                )

                // 选中的远端路径
                OutlinedTextField(
                    value = currentRemotePath,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("映射当前远端路径") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Task ID 输入框
                OutlinedTextField(
                    value = taskId,
                    onValueChange = { taskId = it },
                    label = { Text("Task ID (虚拟任务名称)") },
                    placeholder = { Text("如: celeste-task") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (taskId.isNotBlank()) {
                        onConfirm(
                            SyncTask(
                                id = taskId,
                                displayName = taskId,
                                localUri = currentLocalPath,
                                serverId = defaultTask.serverId,
                                remotePath = currentRemotePath
                            )
                        )
                    }
                }
            ) {
                Text("确定映射")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
