package com.naviveylin.auto

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.DiagnosticsLog
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the host day/night mapping and logging of [NavigationSession]'s
 * [NavigationSession.onCarConfigurationChanged] path. The session itself
 * needs a host-provided CarContext and cannot be constructed in Robolectric,
 * so the pure mapping ([isNightUiMode]) and the log facade ([SessionLog])
 * are tested directly — the same pattern as [SessionLogTest].
 */
@RunWith(RobolectricTestRunner::class)
class NavigationSessionDarkModeTest {

    private lateinit var logFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        logFile = File(context.filesDir, "diagnostics/session-dark-test.log")
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
    fun nightUiModeMapsToDark() {
        val config = Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
        }
        assertTrue(isNightUiMode(config))
    }

    @Test
    fun dayUiModeMapsToLight() {
        val config = Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL
        }
        assertFalse(isNightUiMode(config))
    }

    @Test
    fun unknownUiModeMapsToLight() {
        val config = Configuration().apply {
            uiMode = Configuration.UI_MODE_TYPE_NORMAL
        }
        assertFalse(isNightUiMode(config))
    }

    @Test
    fun hostDarkChangeIsLogged() {
        SessionLog.hostDarkChanged(true)
        SessionLog.hostDarkChanged(false)
        val entries = DiagnosticsLog.readEntries()
        assertTrue(entries.any { it.contains("Host dark mode changed to true") })
        assertTrue(entries.any { it.contains("Host dark mode changed to false") })
    }
}
