package com.loyisagate

import android.app.Application
import com.loyisagate.data.PreferencesManager

class LoyisaGateApp : Application() {

    lateinit var prefs: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = PreferencesManager(this)
    }

    companion object {
        lateinit var instance: LoyisaGateApp
            private set
    }
}
