package com.loyisagate.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class NotificationLog(
    val packageName: String,
    val appName: String,
    val title: String,
    val message: String,
    val subText: String,
    val summaryText: String,
    val timestamp: Long,
    val notificationId: Int,
    val tag: String?,
    val isGroup: Boolean,
    val groupKey: String?
)

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("loyisa_gate_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBHOOK_URL, value).apply()

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()

    var notificationPermissionGranted: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_PERMISSION, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION, value).apply()

    var appsPermissionGranted: Boolean
        get() = prefs.getBoolean(KEY_APPS_PERMISSION, false)
        set(value) = prefs.edit().putBoolean(KEY_APPS_PERMISSION, value).apply()

    var backgroundPermissionGranted: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_PERMISSION, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_PERMISSION, value).apply()

    fun getMonitoredPackages(): MutableSet<String> {
        val json = prefs.getString(KEY_MONITORED_PACKAGES, null) ?: return mutableSetOf()
        val type = object : TypeToken<MutableSet<String>>() {}.type
        return try {
            gson.fromJson(json, type) ?: mutableSetOf()
        } catch (e: Exception) {
            mutableSetOf()
        }
    }

    fun setMonitoredPackages(packages: Set<String>) {
        prefs.edit().putString(KEY_MONITORED_PACKAGES, gson.toJson(packages)).apply()
    }

    fun getNotificationLogs(): List<NotificationLog> {
        val json = prefs.getString(KEY_NOTIFICATION_LOGS, null) ?: return emptyList()
        val type = object : TypeToken<List<NotificationLog>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addNotificationLog(log: NotificationLog) {
        val logs = getNotificationLogs().toMutableList()
        logs.add(0, log)
        if (logs.size > 10) {
            logs.removeAt(logs.size - 1)
        }
        prefs.edit().putString(KEY_NOTIFICATION_LOGS, gson.toJson(logs)).apply()
    }

    fun isSetupComplete(): Boolean {
        return webhookUrl.isNotBlank()
    }

    companion object {
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_NOTIFICATION_PERMISSION = "notification_permission"
        private const val KEY_APPS_PERMISSION = "apps_permission"
        private const val KEY_BACKGROUND_PERMISSION = "background_permission"
        private const val KEY_MONITORED_PACKAGES = "monitored_packages"
        private const val KEY_NOTIFICATION_LOGS = "notification_logs"
    }
}
