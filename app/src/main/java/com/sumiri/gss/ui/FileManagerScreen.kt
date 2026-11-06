package com.sumiri.gss.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sumiri.gss.data.model.RemoteFileItem
import com.sumiri.gss.data.model.RemoteServer
import com.sumiri.gss.data.model.SyncTask
import com.sumiri.gss.data.remote.SmbStorageClient
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    server: RemoteServer,
    shareName: String,
    currentTask: SyncTask,
    showHiddenFiles: Boolean,
    showCreateTaskDialog: Boolean,
    onTaskCreated: (SyncTask) -> Unit,
    onDismissCreateTaskDialog: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 本地, 1: 远端 SMB

    // 本地真根目录路径层级管理 (/storage/emulated/0/)
    val rootDir = remember { Environment.getExternalStorageDirectory() }
    var currentLocalDir by remember { mutableStateOf(rootDir) }

    // 远端 SMB 目录路径层级管理
    var currentRemotePath by remember { mutableStateOf("") }

    // 系统返回手势/按键拦截逻辑
    BackHandler {
        if (selectedTab == 0 && currentLocalDir.parentFile != null && currentLocalDir != rootDir) {
            currentLocalDir = currentLocalDir.parentFile ?: rootDir
        } else if (selectedTab == 1 && currentRemotePath.isNotBlank()) {
            val lastSlash = currentRemotePath.lastIndexOf('/')
            currentRemotePath = if (lastSlash > 0) currentRemotePath.substring(0, lastSlash) else ""
        } else {
            onNavigateToHome()
        }
    }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    // 长按选中的文件项与弹窗
    var itemForMenu by remember { mutableStateOf<RemoteFileItem?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 刷新计数器
    var refreshCounter by remember { mutableIntStateOf(0) }

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

    val localFiles = remember(currentLocalDir, hasStoragePermission, showHiddenFiles, refreshCounter) {
        if (!hasStoragePermission) emptyList()
        else {
            currentLocalDir.listFiles()
                ?.filter { file -> showHiddenFiles || !isHiddenFile(file.name) }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
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
    LaunchedEffect(selectedTab, server, shareName, currentRemotePath, showHiddenFiles, refreshCounter) {
        if (selectedTab == 1 && server.baseUrl.isNotBlank()) {
            isRemoteLoading = true
            remoteError = null
            val res = smbClient.listRemoteFiles(server, shareName, currentRemotePath)
            res.onSuccess { list ->
                remoteFiles = list
                    .filter { item -> showHiddenFiles || !isHiddenFile(item.name) }
                    .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            }.onFailure { err ->
                remoteError = err.message ?: "读取远端目录失败"
            }
            isRemoteLoading = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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

            // 紧凑导航与路径栏
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedTab == 0) {
                        if (currentLocalDir.parentFile != null && currentLocalDir != rootDir) {
                            TextButton(
                                onClick = { currentLocalDir = currentLocalDir.parentFile ?: rootDir },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("返回上一级", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text(
                            text = "路径: ${currentLocalDir.absolutePath}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        if (currentRemotePath.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    val lastSlash = currentRemotePath.lastIndexOf('/')
                                    currentRemotePath = if (lastSlash > 0) currentRemotePath.substring(0, lastSlash) else ""
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("返回上一级", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text(
                            text = "远端路径: /${currentRemotePath}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                        activeItemForMenu = itemForMenu,
                        onItemClick = { item ->
                            if (item.isDirectory) {
                                currentLocalDir = File(item.path)
                            }
                        },
                        onItemLongClick = { item ->
                            itemForMenu = item
                        },
                        onDismissMenu = {
                            itemForMenu = null
                        },
                        onRenameClick = {
                            showRenameDialog = true
                        },
                        onDeleteClick = {
                            showDeleteDialog = true
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
                                    text = remoteError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        else -> {
                            FileListView(
                                items = remoteFiles,
                                emptyText = "远端目录为空",
                                activeItemForMenu = itemForMenu,
                                onItemClick = { item ->
                                    if (item.isDirectory) {
                                        currentRemotePath = item.path
                                    }
                                },
                                onItemLongClick = { item ->
                                    itemForMenu = item
                                },
                                onDismissMenu = {
                                    itemForMenu = null
                                },
                                onRenameClick = {
                                    showRenameDialog = true
                                },
                                onDeleteClick = {
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // 右下角仅 UI 图标的【新建文件夹】悬浮按钮 (FAB)
        FloatingActionButton(
            onClick = { showNewFolderDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = "新建文件夹")
        }
    }

    // 新建文件夹弹窗
    if (showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { folderName ->
                showNewFolderDialog = false
                coroutineScope.launch {
                    if (selectedTab == 0) {
                        File(currentLocalDir, folderName).mkdirs()
                        refreshCounter++
                    } else {
                        val remoteFolderPath = if (currentRemotePath.isEmpty()) folderName else "$currentRemotePath/$folderName"
                        smbClient.createDirectory(server, shareName, remoteFolderPath)
                        refreshCounter++
                    }
                }
            }
        )
    }

    // 新建 Task ID 映射弹窗
    if (showCreateTaskDialog) {
        CreateTaskDialog(
            currentLocalPath = currentLocalDir.absolutePath,
            currentRemotePath = currentRemotePath.ifBlank { "GameSS" },
            defaultTask = currentTask,
            onDismiss = onDismissCreateTaskDialog,
            onConfirm = { newTask ->
                onTaskCreated(newTask)
            }
        )
    }

    // 重命名弹窗
    if (showRenameDialog && itemForMenu != null) {
        val targetItem = itemForMenu!!
        var newNameInput by remember { mutableStateOf(targetItem.name) }

        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                itemForMenu = null
            },
            title = { Text("重命名 '${targetItem.name}'") },
            text = {
                OutlinedTextField(
                    value = newNameInput,
                    onValueChange = { newNameInput = it },
                    label = { Text("新名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newNameInput.isNotBlank() && newNameInput.trim() != targetItem.name) {
                            val newName = newNameInput.trim()
                            coroutineScope.launch {
                                if (selectedTab == 0) {
                                    val oldFile = File(targetItem.path)
                                    val newFile = File(oldFile.parentFile, newName)
                                    oldFile.renameTo(newFile)
                                    refreshCounter++
                                } else {
                                    smbClient.renameItem(server, shareName, targetItem.path, newName, targetItem.isDirectory)
                                    refreshCounter++
                                }
                                showRenameDialog = false
                                itemForMenu = null
                            }
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    itemForMenu = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    // 删除确认弹窗
    if (showDeleteDialog && itemForMenu != null) {
        val targetItem = itemForMenu!!
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                itemForMenu = null
            },
            title = { Text("确认删除") },
            text = { Text("确定要删除 '${targetItem.name}' 吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        val deletingItem = targetItem
                        itemForMenu = null
                        coroutineScope.launch {
                            if (selectedTab == 0) {
                                File(deletingItem.path).deleteRecursively()
                                refreshCounter++
                            } else {
                                smbClient.deleteItem(server, shareName, deletingItem.path, deletingItem.isDirectory)
                                refreshCounter++
                            }
                        }
                    }
                ) {
                    Text("确定删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    itemForMenu = null
                }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun isHiddenFile(name: String): Boolean {
    val upper = name.uppercase()
    return name.startsWith(".") ||
            upper.startsWith("%RECYCLE") ||
            upper.startsWith("\$RECYCLE") ||
            upper == "SYSTEM VOLUME INFORMATION" ||
            upper == "THUMBS.DB"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListView(
    items: List<RemoteFileItem>,
    emptyText: String,
    activeItemForMenu: RemoteFileItem?,
    onItemClick: (RemoteFileItem) -> Unit,
    onItemLongClick: (RemoteFileItem) -> Unit,
    onDismissMenu: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items) { item ->
                Box(modifier = Modifier.fillMaxWidth()) {
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
                        modifier = Modifier.combinedClickable(
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) }
                        )
                    )

                    // 靠右弹出且距离屏幕右边缘保持 16.dp 边距的长按浮动卡片 (图 2 样式)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                    ) {
                        DropdownMenu(
                            expanded = (activeItemForMenu?.path == item.path),
                            onDismissRequest = onDismissMenu
                        ) {
                            DropdownMenuItem(
                                text = { Text("重命名") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = onRenameClick
                            )
                            DropdownMenuItem(
                                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = onDeleteClick
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun NewFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件夹") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("文件夹名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (folderName.isNotBlank()) {
                        onConfirm(folderName.trim())
                    }
                }
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
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
        currentLocalPath.substringAfterLast('/').ifBlank { "GameSave" }
    }

    var taskId by remember { mutableStateOf(suggestedTaskId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建 Task ID 映射规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = currentLocalPath,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("映射当前本地路径") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = currentRemotePath,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("映射当前远端路径") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = taskId,
                    onValueChange = { taskId = it },
                    label = { Text("Task ID (虚拟任务名称)") },
                    placeholder = { Text("如: Celeste") },
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
