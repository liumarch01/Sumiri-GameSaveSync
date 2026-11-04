package com.sumiri.gss.ui

import android.os.Environment
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 主页, 1: 文件管理, 2: 设置

    // 加载持久化本地配置
    val (loadedServer, loadedShare) = remember { prefsManager.loadServer() }
    var currentServer by remember { mutableStateOf(loadedServer) }
    var shareName by remember { mutableStateOf(loadedShare) }

    val defaultLocalPath = remember { Environment.getExternalStorageDirectory().absolutePath }
    var currentTask by remember { mutableStateOf(prefsManager.loadTask(defaultLocalPath)) }

    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "GameSaveSync - 主页"
                            1 -> "文件管理 & Task 映射"
                            else -> "系统设置"
                        }
                    )
                },
                actions = {
                    if (selectedTab == 2) {
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
                task = currentTask,
                shareName = shareName,
                modifier = Modifier.padding(innerPadding)
            )
            1 -> FileManagerScreen(
                server = currentServer,
                shareName = shareName,
                currentTask = currentTask,
                onTaskCreated = { newTask ->
                    currentTask = newTask
                    prefsManager.saveTask(newTask)
                    selectedTab = 0 // 映射成功后返回主页
                },
                modifier = Modifier.padding(innerPadding)
            )
            else -> SettingsScreen(
                server = currentServer,
                shareName = shareName,
                onServerChanged = { newServer, newShare ->
                    currentServer = newServer
                    shareName = newShare
                    prefsManager.saveServer(newServer, newShare)
                },
                onResetAll = {
                    showResetDialog = true
                },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置配置") },
            text = { Text("确定要清除保存的所有服务器与 Task ID 映射配置吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefsManager.clearAll()
                        val (resetServer, resetShare) = prefsManager.loadServer()
                        currentServer = resetServer
                        shareName = resetShare
                        currentTask = prefsManager.loadTask(defaultLocalPath)
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
