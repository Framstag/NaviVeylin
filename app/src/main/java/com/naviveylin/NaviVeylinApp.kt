package com.naviveylin

import android.app.Application
import com.naviveylin.core.DiagnosticsLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NaviVeylinApp : Application() {

    override fun onCreate() {
        DiagnosticsLog.init(this)
        DiagnosticsLog.log(TAG, "Process started")
        DiagnosticsLog.time("Application.onCreate") {
            super.onCreate()
            DiagnosticsLog.installCrashHandler()
        }
    }

    companion object {
        private const val TAG = "NaviVeylinApp"
    }
}
