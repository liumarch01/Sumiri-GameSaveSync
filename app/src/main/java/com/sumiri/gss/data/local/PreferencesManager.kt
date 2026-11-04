package com.sumiri.gss.data.local

import android.content.Context
import android.content.SharedPreferences
import com.sumiri.gss.data.model.ProtocolType
import com.sumiri.gss.data.model.RemoteServer
import com.sumiri.gss.data.model.SyncTask

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gss_prefs", Context.MODE_PRIVATE)

    fun saveServer(server: RemoteServer, shareName: String) {
        prefs.edit()
            .putString("server_ip", server.baseUrl)
            .putString("server_share", shareName)
            .putString("server_user", server.username)
            .putString("server_pass", server.password)
            .apply()
    }

    fun loadServer(): Pair<RemoteServer, String> {
        val ip = prefs.getString("server_ip", "") ?: ""
        val share = prefs.getString("server_share", "D$") ?: "D$"
        val user = prefs.getString("server_user", "") ?: ""
        val pass = prefs.getString("server_pass", "") ?: ""

        val server = RemoteServer(
            id = "default_server",
            name = "Windows PC Share",
            protocol = ProtocolType.SMB,
            baseUrl = ip,
            username = user,
            password = pass
        )
        return Pair(server, share)
    }

    fun saveTask(task: SyncTask) {
        prefs.edit()
            .putString("task_id", task.id)
            .putString("task_local_uri", task.localUri)
            .putString("task_remote_path", task.remotePath)
            .apply()
    }

    fun loadTask(defaultLocalPath: String): SyncTask {
        val id = prefs.getString("task_id", "default-game-slot") ?: "default-game-slot"
        val localUri = prefs.getString("task_local_uri", defaultLocalPath) ?: defaultLocalPath
        val remotePath = prefs.getString("task_remote_path", "GameSS") ?: "GameSS"

        return SyncTask(
            id = id,
            displayName = id,
            localUri = localUri,
            serverId = "default_server",
            remotePath = remotePath
        )
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
