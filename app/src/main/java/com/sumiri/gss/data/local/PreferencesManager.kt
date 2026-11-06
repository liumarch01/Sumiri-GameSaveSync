package com.sumiri.gss.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sumiri.gss.data.model.ProtocolType
import com.sumiri.gss.data.model.RemoteServer
import com.sumiri.gss.data.model.SyncTask

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gss_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

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
        val share = prefs.getString("server_share", "") ?: ""
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

    fun saveTaskList(taskList: List<SyncTask>) {
        val json = gson.toJson(taskList)
        prefs.edit().putString("task_list_json", json).apply()
    }

    fun loadTaskList(defaultLocalPath: String): List<SyncTask> {
        val json = prefs.getString("task_list_json", null)
        if (json.isNull_or_blank()) {
            return emptyList() // 取消默认规则，由用户手动添加
        }
        val type = object : TypeToken<List<SyncTask>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun saveActiveTaskId(taskId: String) {
        prefs.edit().putString("active_task_id", taskId).apply()
    }

    fun getActiveTaskId(): String {
        return prefs.getString("active_task_id", "") ?: ""
    }

    fun setShowHiddenFiles(show: Boolean) {
        prefs.edit().putBoolean("show_hidden_files", show).apply()
    }

    fun getShowHiddenFiles(): Boolean {
        return prefs.getBoolean("show_hidden_files", false)
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun getThemeMode(): String {
        return prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
