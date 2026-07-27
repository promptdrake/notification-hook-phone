package com.loyisagate.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.loyisagate.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit

class NotificationListener : NotificationListenerService() {

    private lateinit var prefs: PreferencesManager
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val sentNotificationKeys = Collections.synchronizedMap(object : LinkedHashMap<String, Boolean>(101, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > 100
        }
    })

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        createErrorChannel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!prefs.isEnabled) return

        val key = sbn.key
        if (sentNotificationKeys.containsKey(key)) return

        val packageName = sbn.packageName
        val monitoredPackages = prefs.getMonitoredPackages()
        if (packageName !in monitoredPackages) return

        val notification = sbn.notification
        val extras = notification.extras ?: return

        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""
        val subText = extras.getString("android.subText") ?: ""
        val summaryText = extras.getString("android.summaryText") ?: ""

        val webhookUrl = prefs.webhookUrl
        if (webhookUrl.isBlank()) return

        val appName = getAppName(packageName)
        val isGroupSummary = (notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0
        
        prefs.addNotificationLog(
            com.loyisagate.data.NotificationLog(
                packageName = packageName,
                appName = appName,
                title = title,
                message = text,
                subText = subText,
                summaryText = summaryText,
                timestamp = System.currentTimeMillis(),
                notificationId = sbn.id,
                tag = sbn.tag,
                isGroup = isGroupSummary,
                groupKey = notification.group
            )
        )

        val payload = JSONObject().apply {
            put("package_name", packageName)
            put("title", title)
            put("message", text)
            put("sub_text", subText)
            put("summary_text", summaryText)
            put("timestamp", System.currentTimeMillis())
            put("app_name", appName)
            put("notification_id", sbn.id)
            put("tag", sbn.tag ?: JSONObject.NULL)
            put("is_group", isGroupSummary)
            put("group_key", notification.group ?: JSONObject.NULL)
        }

        scope.launch {
            try {
                val jsonMediaType = "application/json; charset=utf-8".toMediaType()
                val body = payload.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "LoyisaGate/1.0")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        sentNotificationKeys[key] = true
                    } else {
                        showErrorNotification("HTTP ${response.code}: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                showErrorNotification(e.message ?: "Unknown network error")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun createErrorChannel() {
        val channel = android.app.NotificationChannel(
            ERROR_CHANNEL_ID,
            "API Errors",
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for webhook delivery failures"
        }
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun showErrorNotification(reason: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val notification = androidx.core.app.NotificationCompat.Builder(this, ERROR_CHANNEL_ID)
            .setContentTitle("Api Error")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val ERROR_CHANNEL_ID = "api_error_channel"

        fun isPermissionGranted(context: Context): Boolean {
            return NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        }

        fun openSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
