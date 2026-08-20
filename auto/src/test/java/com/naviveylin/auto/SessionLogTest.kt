package com.naviveylin.auto

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.DiagnosticsLog
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the exact [SessionLog] lines written for Android Auto session
 * lifecycle events (task 1.5) — the logging the [NavigationSession] emits
 * cannot be exercised without a host-provided CarContext, so the facade it
 * delegates to is tested directly.
 */
@RunWith(RobolectricTestRunner::class)
class SessionLogTest {

    private lateinit var logFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        logFile = File(context.filesDir, "diagnostics/session-log-test.log")
        logFile.parentFile?.mkdirs()
        logFile.delete()
        DiagnosticsLog.initForTest(logFile)
    }

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
        logFile.delete()
    }

    @Test
    fun lifecycleEventsAreLogged() {
        val intent = Intent(Intent.ACTION_VIEW).setData(android.net.Uri.parse("geo:1.0,2.0"))

        SessionLog.sessionCreated()
        SessionLog.warmupStarted()
        SessionLog.onCreateScreen(intent, warmupCompleted = false, sinceSessionMs = 50)
        SessionLog.onNewIntent(intent, sinceSessionMs = 100)
        SessionLog.warmupDuration(123)
        SessionLog.warmupStep("Resolving Hilt entry point")
        SessionLog.warmupStep("Entry point resolved", 12)
        SessionLog.warmupStep("Building native client", 3)
        SessionLog.warmupStep("Native client ready", 400)
        SessionLog.warmupBlockDone(415)
        SessionLog.warmupComplete()
        SessionLog.push("NavigationScreen")
        SessionLog.popToRoot()
        SessionLog.errorOverlay("GPS required")
        SessionLog.retry()
        SessionLog.warmupCancelled()
        SessionLog.destroyed()

        val entries = DiagnosticsLog.readEntries()
        assertTrue(entries.any { it.contains("Session created") })
        assertTrue(entries.any { it.contains("Warmup started") })
        assertTrue(entries.any { it.contains("onCreateScreen action=android.intent.action.VIEW data=geo:1.0,2.0") })
        assertTrue(entries.any { it.contains("onCreateScreen") && it.contains("warmupCompleted=false") })
        assertTrue(entries.any { it.contains("onCreateScreen") && it.contains("sinceSessionCreate=50ms") })
        assertTrue(entries.any { it.contains("onNewIntent action=android.intent.action.VIEW") })
        assertTrue(entries.any { it.contains("onNewIntent") && it.contains("sinceSessionCreate=100ms") })
        assertTrue(entries.any { it.contains(SessionLog.WARMUP_TAG) && it.contains("took 123ms") })
        assertTrue(entries.any { it.contains(SessionLog.WARMUP_TAG) && it.contains("Resolving Hilt entry point") })
        assertTrue(entries.any { it.contains(SessionLog.WARMUP_TAG) && it.contains("Entry point resolved (+12ms)") })
        assertTrue(entries.any { it.contains(SessionLog.WARMUP_TAG) && it.contains("Building native client") })
        assertTrue(entries.any { it.contains(SessionLog.WARMUP_TAG) && it.contains("Native client ready (+400ms)") })
        assertTrue(entries.any { it.contains("returned to main thread") && it.contains("415ms") })
        assertTrue(entries.any { it.contains("Warmup complete") })
        assertTrue(entries.any { it.contains("Push NavigationScreen") })
        assertTrue(entries.any { it.contains("Pop to RootScreen") })
        assertTrue(entries.any { it.contains("Showing error overlay: GPS required") })
        assertTrue(entries.any { it.contains("Retry requested") })
        assertTrue(entries.any { it.contains("Warmup cancelled") })
        assertTrue(entries.any { it.contains("Session destroyed") })
    }

    @Test
    fun failuresAreLoggedWithThrowable() {
        SessionLog.failed("onCreateScreen", IllegalStateException("boom"))
        assertTrue(DiagnosticsLog.readEntries().any { it.contains("onCreateScreen failed") && it.contains("boom") })
    }

    @Test
    fun nullIntentIsLoggedSafely() {
        SessionLog.onCreateScreen(null)
        assertTrue(DiagnosticsLog.readEntries().any { it.contains("onCreateScreen action=null data=null") })
    }
}
