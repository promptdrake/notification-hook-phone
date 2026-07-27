package com.loyisagate.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.loyisagate.data.PreferencesManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferencesManager(context)
            if (prefs.isEnabled && prefs.isSetupComplete()) {
                val serviceIntent = Intent(context, ForegroundService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
