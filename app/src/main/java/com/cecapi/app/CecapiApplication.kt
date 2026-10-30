package com.cecapi.app

import android.app.Application
import com.cecapi.app.core.util.CrashGuard
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CecapiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
    }
}
