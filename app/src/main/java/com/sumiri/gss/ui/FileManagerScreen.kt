package com.sumiri.gss.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sumiri.gss.data.model.RemoteFileItem
import com.sumiri.gss.data.model.RemoteServer
import com.sumiri.gss.data.model.SyncTask
import com.sumiri.gss.data.remote.SmbStorageClient
import com.sumiri.gss.util.FileOpener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    server: RemoteServer,
    shareName: String,
    currentTask: SyncTask,
    showHiddenFiles: Boolean,
    showCreateTaskDialog: Boolean,
    currentLocalDir: File,
    onLocalDirChange: (File) -> Unit,
    currentRemotePath: String,
    onRemotePathChange: (String) -> Unit,
    activePane: Int,
    onActivePaneChange: (Int) -> Unit,
    onTaskCreated: (SyncTask) -> Unit,
    onDismissCreateTaskDialog: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showNewFolderDialog by remember { mutableStateOf(false) }

    // 长按选中的文件项与弹窗 (isLocal, item)
    var itemForMenu by remember { mutableStateOf<Pair<Boolean, RemoteFileItem>?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 1秒自动取消/覆盖的 Toast 引用
    var activeToast by remember { mutableStateOf<Toast?>(null) }

    fun showFastToast(msg: String) {
        activeToast?.cancel()
        val toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT)
        activeToast = toast
        toast.show()
        coroutineScope.launch {
            delay(1000L)
            if (activeToast == toast) {
                toast.cancel()
            }
        }
    }

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

    val rootDir = remember { Environment.getExternalStorageDirectory() }

    // 系统返回手势/按键拦截逻辑
    BackHandler {
        if (activePane == 0 && currentLocalDir.parentFile != null && currentLocalDir != rootDir) {
            onLocalDirChange(currentLocalDir.parentFile ?: rootDir)
        } else if (activePane == 1 && currentRemotePath.isNotBlank()) {
            val lastSlash = currentRemotePath.lastIndexOf('/')
            val newRemote = if (lastSlash > 0) currentRemotePath.substring(0, lastSlash) else ""
            onRemotePathChange(newRemote)
        } else {
            onNavigateToHome()
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

    val effectiveShareName = remember(shareName) { shareName.ifBlank { "D$" } }

    // 自动加载远端目录
    LaunchedEffect(server, effectiveShareName, currentRemotePath, showHiddenFiles, refreshCounter) {
        if (server.baseUrl.isNotBlank()) {
            isRemoteLoading = true
            remoteError = null
            val res = smbClient.listRemoteFiles(server, effectiveShareName, currentRemotePath)
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

            // 权限请求红框 Banner
            if (!hasStoragePermission) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "读取存储中的游戏存档需要授予读取权限",
                            style = MaterialTheme.typography.bodySmall,
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
                            Text("去授权", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // 28dp 超紧凑单栏合并路径条（跟随当前激活窗口）
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activePane == 0) {
                        if (currentLocalDir.parentFile != null && currentLocalDir != rootDir) {
                            TextButton(
                                onClick = { onLocalDirChange(currentLocalDir.parentFile ?: rootDir) },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("返回", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(
                            text = "本地路径: ${currentLocalDir.absolutePath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        if (currentRemotePath.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    val lastSlash = currentRemotePath.lastIndexOf('/')
                                    val newRemote = if (lastSlash > 0) currentRemotePath.substring(0, lastSlash) else ""
                                    onRemotePathChange(newRemote)
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("返回", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(
                            text = "远端路径: /${currentRemotePath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            //左右 50%/50% 双窗口高密度布局（非激活面板呈现 3dp 无重叠四边内凹阴影）
            Row(modifier = Modifier.fillMaxSize()) {

                // ==================== 左窗口: 本地文件目录 (50%) ====================
                val isLeftActive = activePane == 0
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.pressed }) {
                                        onActivePaneChange(0) // 手指一接触/滑动/点击，立刻激活！
                                    }
                                }
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            if (!isLeftActive) {
                                val s = 3.dp.toPx()
                                val shadowColor = Color.Black.copy(alpha = 0.15f)
                                // 1. 顶部阴影
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(shadowColor, Color.Transparent),
                                        startY = 0f,
                                        endY = s
                                    ),
                                    topLeft = Offset.Zero,
                                    size = Size(size.width, s)
                                )
                                // 2. 底部阴影
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, shadowColor),
                                        startY = size.height - s,
                                        endY = size.height
                                    ),
                                    topLeft = Offset(0f, size.height - s),
                                    size = Size(size.width, s)
                                )
                                // 3. 左侧阴影
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(shadowColor, Color.Transparent),
                                        startX = 0f,
                                        endX = s
                                    ),
                                    topLeft = Offset.Zero,
                                    size = Size(s, size.height)
                                )
                                // 4. 右侧阴影
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, shadowColor),
                                        startX = size.width - s,
                                        endX = size.width
                                    ),
                                    topLeft = Offset(size.width - s, 0f),
                                    size = Size(s, size.height)
                                )
                            }
                        },
                    color = if (isLeftActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    FilePaneListView(
                        items = localFiles,
                        emptyText = if (hasStoragePermission) "目录为空" else "请先授权",
                        isLocalPane = true,
                        activeItemForMenu = itemForMenu,
                        onItemClick = { item ->
                            onActivePaneChange(0)
                            if (item.isDirectory) onLocalDirChange(File(item.path))
                        },
                        onItemLongClick = { item ->
                            onActivePaneChange(0)
                            itemForMenu = Pair(true, item)
                        },
                        onDismissMenu = { itemForMenu = null },
                        onOpenWithClick = { item ->
                            itemForMenu = null
                            FileOpener.openFile(context, File(item.path))
                        },
                        onCopyClick = { item ->
                            itemForMenu = null
                            coroutineScope.launch {
                                showFastToast("正在复制到远端: ${item.name}...")
                                val targetRemotePath = if (currentRemotePath.isEmpty()) item.name else "$currentRemotePath/${item.name}"
                                val file = File(item.path)
                                if (file.isFile) {
                                    file.inputStream().use { input ->
                                        smbClient.uploadFile(server, effectiveShareName, targetRemotePath, input)
                                    }
                                }
                                showFastToast("已成功复制到远端: ${item.name}")
                                refreshCounter++
                            }
                        },
                        onRenameClick = { showRenameDialog = true },
                        onDeleteClick = { showDeleteDialog = true }
                    )
                }

                VerticalDivider(modifier = Modifier.fillMaxHeight())

                // ==================== 右窗口: 远端 SMB 目录 (50%) ====================
                val isRightActive = activePane == 1
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.pressed }) {
                                        onActivePaneChange(1) // 手指一接触/滑动/点击，立刻激活！
                                    }
                                }
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            if (!isRightActive) {
                                val s = 3.dp.toPx()
                                val shadowColor = Color.Black.copy(alpha = 0.15f)
                                // 1. 顶部阴影
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(shadowColor, Color.Transparent),
                                        startY = 0f,
                                        endY = s
                                    ),
                                    topLeft = Offset.Zero,
                                    size = Size(size.width, s)
                                )
                                // 2. 底部阴影
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, shadowColor),
                                        startY = size.height - s,
                                        endY = size.height
                                    ),
                                    topLeft = Offset(0f, size.height - s),
                                    size = Size(size.width, s)
                                )
                                // 3. 左侧阴影
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(shadowColor, Color.Transparent),
                                        startX = 0f,
                                        endX = s
                                    ),
                                    topLeft = Offset.Zero,
                                    size = Size(s, size.height)
                                )
                                // 4. 右侧阴影
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, shadowColor),
                                        startX = size.width - s,
                                        endX = size.width
                                    ),
                                    topLeft = Offset(size.width - s, 0f),
                                    size = Size(s, size.height)
                                )
                            }
                        },
                    color = if (isRightActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    when {
                        server.baseUrl.isBlank() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("未配置 SMB IP", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        isRemoteLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                        remoteError != null -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = remoteError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        else -> {
                            FilePaneListView(
                                items = remoteFiles,
                                emptyText = "远端目录为空",
                                isLocalPane = false,
                                activeItemForMenu = itemForMenu,
                                onItemClick = { item ->
                                    onActivePaneChange(1)
                                    if (item.isDirectory) onRemotePathChange(item.path)
                                },
                                onItemLongClick = { item ->
                                    onActivePaneChange(1)
                                    itemForMenu = Pair(false, item)
                                },
                                onDismissMenu = { itemForMenu = null },
                                onOpenWithClick = {
                                    itemForMenu = null
                                    showFastToast("远端文件需先复制到本地才能打开")
                                },
                                onCopyClick = { item ->
                                    itemForMenu = null
                                    coroutineScope.launch {
                                        showFastToast("正在复制到本地: ${item.name}...")
                                        val targetLocalFile = File(currentLocalDir, item.name)
                                        targetLocalFile.outputStream().use { output ->
                                            smbClient.downloadFile(server, effectiveShareName, item.path, output)
                                        }
                                        showFastToast("已成功复制到本地: ${item.name}")
                                        refreshCounter++
                                    }
                                },
                                onRenameClick = { showRenameDialog = true },
                                onDeleteClick = { showDeleteDialog = true }
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
            activePane = activePane,
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { folderName ->
                showNewFolderDialog = false
                coroutineScope.launch {
                    if (activePane == 0) {
                        File(currentLocalDir, folderName).mkdirs()
                    } else {
                        val remoteFolderPath = if (currentRemotePath.isEmpty()) folderName else "$currentRemotePath/$folderName"
                        smbClient.createDirectory(server, effectiveShareName, remoteFolderPath)
                    }
                    refreshCounter++
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
        val (isLocal, targetItem) = itemForMenu!!
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
                                if (isLocal) {
                                    val oldFile = File(targetItem.path)
                                    val newFile = File(oldFile.parentFile, newName)
                                    oldFile.renameTo(newFile)
                                } else {
                                    smbClient.renameItem(server, effectiveShareName, targetItem.path, newName, targetItem.isDirectory)
                                }
                                refreshCounter++
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
        val (isLocal, targetItem) = itemForMenu!!
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
                            if (isLocal) {
                                File(deletingItem.path).deleteRecursively()
                            } else {
                                smbClient.deleteItem(server, effectiveShareName, deletingItem.path, deletingItem.isDirectory)
                            }
                            refreshCounter++
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

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt().coerceAtMost(3)
    val pre = "KMGT"[exp - 1]
    val value = bytes / Math.pow(1024.0, exp.toDouble())
    return String.format(Locale.US, "%.1f %cB", value, pre)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilePaneListView(
    items: List<RemoteFileItem>,
    emptyText: String,
    isLocalPane: Boolean,
    activeItemForMenu: Pair<Boolean, RemoteFileItem>?,
    onItemClick: (RemoteFileItem) -> Unit,
    onItemLongClick: (RemoteFileItem) -> Unit,
    onDismissMenu: () -> Unit,
    onOpenWithClick: (RemoteFileItem) -> Unit,
    onCopyClick: (RemoteFileItem) -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(4.dp))
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(items) { item ->
                val isSelectedForMenu = activeItemForMenu?.first == isLocalPane && activeItemForMenu.second.path == item.path

                Box(modifier = Modifier.fillMaxWidth()) {
                    // MT管理器风格高密度行 (~38dp 高度)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 38.dp)
                            .combinedClickable(
                                onClick = { onItemClick(item) },
                                onLongClick = { onItemLongClick(item) }
                            )
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!item.isDirectory) {
                                Text(
                                    text = formatFileSize(item.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 靠右紧贴 16.dp 的浮动小卡片菜单
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                    ) {
                        DropdownMenu(
                            expanded = isSelectedForMenu,
                            onDismissRequest = onDismissMenu
                        ) {
                            if (!item.isDirectory && isLocalPane) {
                                DropdownMenuItem(
                                    text = { Text("打开方式") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                    onClick = { onOpenWithClick(item) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (isLocalPane) "复制 >" else "< 复制") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = { onCopyClick(item) }
                            )
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun NewFolderDialog(
    activePane: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(if (activePane == 0) "新建本地文件夹" else "新建远端文件夹") },
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
            TextButton(onClick = { onDismiss() }) {
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
    // 默认 Task ID: 优先使用远端存储目录名称
    val suggestedTaskId = remember(currentRemotePath, currentLocalPath) {
        val remoteName = currentRemotePath.substringAfterLast('/').ifBlank { "" }
        if (remoteName.isNotBlank() && remoteName != "GameSS") {
            remoteName
        } else {
            currentLocalPath.substringAfterLast('/').ifBlank { "GameSave" }
        }
    }

    var taskId by remember { mutableStateOf(suggestedTaskId) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
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
            TextButton(onClick = { onDismiss() }) {
                Text("取消")
            }
        }
    )
}
