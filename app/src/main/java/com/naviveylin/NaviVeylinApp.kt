package com.naviveylin

import android.app.Application
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.memory.MemoryPressureResponder
import com.naviveylin.navigation.NavigationNotificationController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NaviVeylinApp : Application() {

    @Inject
    lateinit var notificationController: NavigationNotificationController

    /**
     * Releases retained tile data when the platform reports memory pressure (spec:
     * `native-tile-data-cache` — Retention is released under platform memory pressure). The
     * Application forwards its `onTrimMemory` to registered callbacks, and the registration lives as
     * long as the process does.
     */
    @Inject
    lateinit var memoryPressureResponder: MemoryPressureResponder

    override fun onCreate() {
        DiagnosticsLog.init(this)
        DiagnosticsLog.log(TAG, "Process started")
        DiagnosticsLog.time("Application.onCreate") {
            // Forward native libosmscout osmscout::log output to Logcat
            // before any native work starts (DB open, rendering, routing).
            NativeLogBridge.install()
            super.onCreate()
            DiagnosticsLog.installCrashHandler()
            // Register the retention release as soon as the graph is injected (after
            // super.onCreate(), which is where Hilt injects this Application's fields), so a trim
            // signal during startup is not missed. The callback does no native work on the callback
            // thread and is dropped with the process.
            registerComponentCallbacks(memoryPressureResponder)
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
