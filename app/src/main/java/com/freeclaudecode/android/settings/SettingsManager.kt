package com.freeclaudecode.android.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.freeclaudecode.android.FreeClaudeCodeApplication
import com.google.gson.Gson

class SettingsManager(private val context: Context = FreeClaudeCodeApplication.instance) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val gson = Gson()

    companion object {
        const val KEY_PORT = "server_port"
        const val KEY_HOST = "server_host"
        const val KEY_AUTO_START = "auto_start_server"
        const val KEY_SHOW_NOTIFICATIONS = "show_notifications"
        const val DEFAULT_PORT = 8080
        const val DEFAULT_HOST = "127.0.0.1"
    }

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var host: String
        get() = prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
        set(value) = prefs.edit().putString(KEY_HOST, value).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var showNotifications: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_NOTIFICATIONS, value).apply()

    fun getServerUrl(): String {
        return "http://$host:$port"
    }

    fun getAdminUrl(): String {
        return "http://$host:$port/admin"
    }

    fun resetToDefaults() {
        prefs.edit()
            .putInt(KEY_PORT, DEFAULT_PORT)
            .putString(KEY_HOST, DEFAULT_HOST)
            .putBoolean(KEY_AUTO_START, false)
            .putBoolean(KEY_SHOW_NOTIFICATIONS, true)
            .apply()
    }
}