package com.loyisagate.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import com.loyisagate.data.PreferencesManager
import com.loyisagate.service.ForegroundService
import com.loyisagate.service.NotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class SetupStep {
    COMPLETE, NOTIFICATION, APP_SELECTION, BACKGROUND
}

data class UiState(
    val isFirstLaunch: Boolean = true,
    val webhookUrl: String = "",
    val isEnabled: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val appsPermissionGranted: Boolean = false,
    val backgroundPermissionGranted: Boolean = false,
    val monitoredPackages: Set<String> = emptySet(),
    val showAppSelection: Boolean = false,
    val setupStep: SetupStep = SetupStep.COMPLETE,
    val isEditing: Boolean = false,
    val latestNotifications: List<com.loyisagate.data.NotificationLog> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        loadState()
    }

    private fun loadState() {
        val notificationGranted = prefs.notificationPermissionGranted
        val appsGranted = prefs.appsPermissionGranted || prefs.getMonitoredPackages().isNotEmpty()
        val bgGranted = prefs.backgroundPermissionGranted
        val enabled = prefs.isEnabled

        val step = if (!enabled) SetupStep.COMPLETE
        else if (!notificationGranted) SetupStep.NOTIFICATION
        else if (!appsGranted) SetupStep.APP_SELECTION
        else if (!bgGranted) SetupStep.BACKGROUND
        else SetupStep.COMPLETE

        _state.value = UiState(
            isFirstLaunch = prefs.isFirstLaunch,
            webhookUrl = prefs.webhookUrl,
            isEnabled = enabled,
            notificationPermissionGranted = notificationGranted,
            appsPermissionGranted = appsGranted,
            backgroundPermissionGranted = bgGranted,
            monitoredPackages = prefs.getMonitoredPackages(),
            setupStep = step,
            isEditing = _state.value.isEditing,
            latestNotifications = prefs.getNotificationLogs()
        )
    }

    fun saveSetup(url: String, enabled: Boolean) {
        prefs.webhookUrl = url
        prefs.isEnabled = enabled
        prefs.isFirstLaunch = false
        _state.value = _state.value.copy(isEditing = false)
        loadState()
        if (enabled) {
            advanceSetup()
        }
    }

    fun enterEditMode() {
        _state.value = _state.value.copy(isEditing = true)
    }

    private fun advanceSetup() {
        when (_state.value.setupStep) {
            SetupStep.NOTIFICATION -> {
                requestNotificationPermission()
            }
            SetupStep.APP_SELECTION -> {
                _state.value = _state.value.copy(showAppSelection = true)
            }
            SetupStep.BACKGROUND -> {
                requestBackgroundPermission()
            }
            SetupStep.COMPLETE -> {
                updateServiceState()
            }
        }
    }

    fun toggleEnabled(enabled: Boolean) {
        prefs.isEnabled = enabled
        loadState()
        if (enabled) advanceSetup()
        else updateServiceState()
    }

    fun requestNotificationPermission() {
        val context = getApplication<Application>()
        val granted = NotificationListener.isPermissionGranted(context)
        prefs.notificationPermissionGranted = granted
        if (!granted) {
            NotificationListener.openSettings(context)
        }
        loadState()
    }

    fun checkNotificationPermission() {
        val context = getApplication<Application>()
        val granted = NotificationListener.isPermissionGranted(context)
        prefs.notificationPermissionGranted = granted
        loadState()
        if (granted) {
            advanceSetup()
        }
    }

    fun showAppSelection() {
        _state.value = _state.value.copy(showAppSelection = true)
    }

    fun hideAppSelection() {
        _state.value = _state.value.copy(showAppSelection = false)
    }

    fun saveSelectedApps(packages: Set<String>) {
        prefs.setMonitoredPackages(packages)
        prefs.appsPermissionGranted = packages.isNotEmpty()
        loadState()
        advanceSetup()
    }

    fun requestBackgroundPermission() {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        prefs.backgroundPermissionGranted = true
        loadState()
        advanceSetup()
    }

    fun checkBackgroundPermission(): Boolean {
        val context = getApplication<Application>()
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun resendNotification(log: com.loyisagate.data.NotificationLog) {
        val webhookUrl = prefs.webhookUrl
        if (webhookUrl.isBlank()) return

        val payload = JSONObject().apply {
            put("package_name", log.packageName)
            put("title", log.title)
            put("message", log.message)
            put("sub_text", log.subText)
            put("summary_text", log.summaryText)
            put("timestamp", log.timestamp)
            put("app_name", log.appName)
            put("notification_id", log.notificationId)
            put("tag", log.tag ?: JSONObject.NULL)
            put("is_group", log.isGroup)
            put("group_key", log.groupKey ?: JSONObject.NULL)
            put("is_resend", true)
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
                    if (!response.isSuccessful) {
                        showErrorNotification("HTTP ${response.code}: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                showErrorNotification(e.message ?: "Unknown network error")
            }
        }
    }

    private fun showErrorNotification(reason: String) {
        val context = getApplication<Application>()
        val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        // Use the same channel as NotificationListener
        val channelId = "api_error_channel"
        
        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setContentTitle("Api Error (Manual Resend)")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun updateServiceState() {
        val context = getApplication<Application>()
        val intent = Intent(context, ForegroundService::class.java)

        if (prefs.isEnabled &&
            prefs.notificationPermissionGranted &&
            prefs.appsPermissionGranted &&
            prefs.backgroundPermissionGranted
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }
}
