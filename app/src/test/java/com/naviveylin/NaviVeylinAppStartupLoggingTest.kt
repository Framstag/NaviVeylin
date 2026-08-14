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
 * before each test, so its `onCreate` has already written the lines to the
 * real diagnostics log by the time the test body runs.
 */
@RunWith(RobolectricTestRunner::class)
class NaviVeylinAppStartupLoggingTest {

    @Test
    fun startupMarkerAndTimingAreLogged() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val realLog = File(context.filesDir, "diagnostics/app.log")
        assertTrue("diagnostics log missing", realLog.exists())

        val entries = realLog.readLines()
        assertTrue(entries.any { it.contains("NaviVeylinApp Process started") })
        assertTrue(entries.any { it.contains("Application.onCreate took") })
    }
}
