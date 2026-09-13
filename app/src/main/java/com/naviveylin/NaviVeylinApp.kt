package com.naviveylin

import android.app.Application
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.navigation.NavigationNotificationController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NaviVeylinApp : Application() {

    @Inject
    lateinit var notificationController: NavigationNotificationController

    override fun onCreate() {
        DiagnosticsLog.init(this)
        DiagnosticsLog.log(TAG, "Process started")
        DiagnosticsLog.time("Application.onCreate") {
            // Forward native libosmscout osmscout::log output to Logcat
            // before any native work starts (DB open, rendering, routing).
            NativeLogBridge.install()
            super.onCreate()
            DiagnosticsLog.installCrashHandler()
        }
        // Eager: the ongoing-navigation-notification controller must observe
        // driving-state transitions from process start, including sessions
        // started only from the car (deep link, no phone UI).
        DiagnosticsLog.time("NotificationController.activate") {
            notificationController
        }
    }

    companion object {
        private const val TAG = "NaviVeylinApp"
    }
}
