package com.sumiri.gss.ui

import android.app.Activity
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.sumiri.gss.data.local.PreferencesManager
import com.sumiri.gss.data.model.SyncTask
import com.sumiri.gss.ui.theme.GameSSTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onThemeModeChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 主页, 1: 文件管理, 2: 设置

    // 系统返回手势/按键拦截逻辑
    BackHandler(enabled = selectedTab == 0) {
        (context as? Activity)?.moveTaskToBack(true)
    }

    BackHandler(enabled = selectedTab == 2) {
        selectedTab = 0
    }

    // 主题控制
    var themeMode by remember { mutableStateOf(prefsManager.getThemeMode()) }
    val isDarkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }

    // 加载持久化服务器与共享名配置
    val (loadedServer, loadedShare) = remember { prefsManager.loadServer() }
    var currentServer by remember { mutableStateOf(loadedServer) }
    var shareName by remember { mutableStateOf(loadedShare) }

    // 加载隐藏文件显示设置
    var showHiddenFiles by remember { mutableStateOf(prefsManager.getShowHiddenFiles()) }

    // 加载 Task ID 任务映射列表与当前激活 ID（取消默认规则，未添加时为空列表）
    val defaultLocalPath = remember { Environment.getExternalStorageDirectory().absolutePath }
    var taskList by remember { mutableStateOf(prefsManager.loadTaskList(defaultLocalPath)) }
    var activeTaskId by remember { mutableStateOf(prefsManager.getActiveTaskId()) }

    var showResetDialog by remember { mutableStateOf(false) }
    var showCreateTaskDialogInTab1 by remember { mutableStateOf(false) }
    // 文件管理页的状态提升（保证切换到其他 Tab 再回来时，路径不丢失）-Deepseek
    var fmActivePane by remember { mutableIntStateOf(0) }
    var fmCurrentLocalDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var fmCurrentRemotePath by remember { mutableStateOf("") }

    GameSSTheme(darkTheme = isDarkTheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (selectedTab) {
                                0 -> "GameSS主页"
                                1 -> "文件管理&映射"
                                else -> "系统设置"
                            }
                        )
                    },
                    actions = {
                        if (selectedTab == 1) {
                            TextButton(onClick = { showCreateTaskDialogInTab1 = true }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "添加映射"
                                )
                                Text("添加映射")
                            }
                        } else if (selectedTab == 2) {
                            TextButton(onClick = { showResetDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "重置",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text("重置", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "主页") },
                        label = { Text("主页") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "文件管理") },
                        label = { Text("文件管理") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                        label = { Text("设置") }
                    )
                }
            },
            modifier = modifier
        ) { innerPadding ->
            when (selectedTab) {
                0 -> HomeScreen(
                    server = currentServer,
                    shareName = shareName,
                    taskList = taskList,
                    activeTaskId = activeTaskId,
                    onActiveTaskSelected = { newTaskId ->
                        activeTaskId = newTaskId
                        prefsManager.saveActiveTaskId(newTaskId)
                    },
                    onTaskDeleted = { taskToDelete ->
                        val updated = taskList.filter { it.id != taskToDelete.id }
                        taskList = updated
                        prefsManager.saveTaskList(updated)
                        if (activeTaskId == taskToDelete.id) {
                            val nextId = updated.firstOrNull()?.id ?: ""
                            activeTaskId = nextId
                            prefsManager.saveActiveTaskId(nextId)
                        }
                    },
                    onTaskRenamed = { targetTask, newTaskId ->
                        val updated = taskList.map {
                            if (it.id == targetTask.id) it.copy(id = newTaskId, displayName = newTaskId) else it
                        }
                        taskList = updated
                        prefsManager.saveTaskList(updated)
                        if (activeTaskId == targetTask.id) {
                            activeTaskId = newTaskId
                            prefsManager.saveActiveTaskId(newTaskId)
                        }
                    },
                    modifier = Modifier.padding(innerPadding)
                )
                1 -> {
                    val activeTask = taskList.find { it.id == activeTaskId } ?: taskList.firstOrNull() ?: SyncTask(
                        id = "new-task",
                        displayName = "新建任务",
                        localUri = defaultLocalPath,
                        serverId = currentServer.id,
                        remotePath = "GameSS"
                    )
                    FileManagerScreen(
                        server = currentServer,
                        shareName = shareName,
                        currentTask = activeTask,
                        showHiddenFiles = showHiddenFiles,
                        showCreateTaskDialog = showCreateTaskDialogInTab1,

                        // === 补充缺失的状态传入与更新回调 ===
                        activePane = fmActivePane,
                        onActivePaneChange = { fmActivePane = it },
                        currentLocalDir = fmCurrentLocalDir,
                        onLocalDirChange = { fmCurrentLocalDir = it },
                        currentRemotePath = fmCurrentRemotePath,
                        onRemotePathChange = { fmCurrentRemotePath = it },
                        // ======================================

                        onTaskCreated = { newTask ->
                            val updated = taskList.filter { it.id != newTask.id } + newTask
                            taskList = updated
                            activeTaskId = newTask.id
                            prefsManager.saveTaskList(updated)
                            prefsManager.saveActiveTaskId(newTask.id)
                            showCreateTaskDialogInTab1 = false
                            selectedTab = 0 // 映射成功后返回主页
                        },
                        onDismissCreateTaskDialog = {
                            showCreateTaskDialogInTab1 = false
                        },
                        onNavigateToHome = {
                            selectedTab = 0
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                else -> SettingsScreen(
                    server = currentServer,
                    shareName = shareName,
                    showHiddenFiles = showHiddenFiles,
                    themeMode = themeMode,
                    onServerChanged = { newServer, newShare ->
                        currentServer = newServer
                        shareName = newShare
                        prefsManager.saveServer(newServer, newShare)
                    },
                    onShowHiddenFilesChanged = { show ->
                        showHiddenFiles = show
                        prefsManager.setShowHiddenFiles(show)
                    },
                    onThemeModeChanged = { mode ->
                        themeMode = mode
                        prefsManager.setThemeMode(mode)
                        onThemeModeChanged(mode)
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置配置") },
            text = { Text("确定要清除保存的所有服务器与 Task ID 映射配置吗？此操作无法撤销！") },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefsManager.clearAll()
                        val (resetServer, resetShare) = prefsManager.loadServer()
                        currentServer = resetServer
                        shareName = resetShare
                        taskList = prefsManager.loadTaskList(defaultLocalPath)
                        activeTaskId = prefsManager.getActiveTaskId()
                        showHiddenFiles = prefsManager.getShowHiddenFiles()
                        themeMode = prefsManager.getThemeMode()
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
