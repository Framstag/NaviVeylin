package com.naviveylin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
 * test waits for it (change `fix-diagnostics-log-host-path-io`).
 */
@RunWith(RobolectricTestRunner::class)
class NaviVeylinAppStartupLoggingTest {

    @Test
    fun startupMarkerAndTimingAreLogged() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val realLog = File(context.filesDir, "diagnostics/app.log")

        // The lines are buffered and flushed by the worker within its flush bound.
        val deadline = System.currentTimeMillis() + 5_000
        var entries: List<String> = emptyList()
        while (System.currentTimeMillis() < deadline) {
            if (realLog.exists()) {
                entries = realLog.readLines()
                if (entries.any { it.contains("NaviVeylinApp Process started") } &&
                    entries.any { it.contains("Application.onCreate took") }
                ) {
                    return
                }
            }
            Thread.sleep(20)
        }

        assertTrue("diagnostics log missing", realLog.exists())
        assertTrue("startup marker missing: $entries", entries.any { it.contains("NaviVeylinApp Process started") })
        assertTrue("onCreate timing missing: $entries", entries.any { it.contains("Application.onCreate took") })
    }
}
