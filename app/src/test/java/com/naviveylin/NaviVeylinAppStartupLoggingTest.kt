package com.naviveylin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.DiagnosticsLog
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies [NaviVeylinApp] logs its startup marker and `Application.onCreate`
 * duration to the diagnostics log (spec: auto-diagnostics — App startup
 * timing recorded). Robolectric creates the manifest-declared application
 * before each test, so its `onCreate` has already logged the lines by the time
 * the test body runs — the file write happens on the log's worker thread, so the
 * test awaits the worker's drain signal (change `speed-up-test-iteration`, task 3.2).
 */
@RunWith(RobolectricTestRunner::class)
class NaviVeylinAppStartupLoggingTest {

    @Test
    fun startupMarkerAndTimingAreLogged() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val realLog = File(context.filesDir, "diagnostics/app.log")

        // The lines are buffered and flushed by the logging worker: await the worker's own drain signal
        // instead of polling the file on the wall clock (spec `unit-test-suite-runtime` — Awaiting state,
        // not a deadline).
        assertTrue("the logging worker must drain within its own bound", DiagnosticsLog.awaitDrained())

        assertTrue("diagnostics log missing", realLog.exists())
        val entries = realLog.readLines()
        assertTrue("startup marker missing: $entries", entries.any { it.contains("NaviVeylinApp Process started") })
        assertTrue("onCreate timing missing: $entries", entries.any { it.contains("Application.onCreate took") })
    }
}
