package com.naviveylin

import android.content.Context
import androidx.car.app.HostInfo
import androidx.car.app.SessionInfo
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.DiagnosticsLog
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric

/**
 * Verifies [NaviVeylinCarAppService] logs its lifecycle (task 1.5).
 * Create/destroy are exercised via Robolectric; the session-creation log
 * line is tested through the extracted [NaviVeylinCarAppService.sessionCreatedMessage]
 * seam — the full [onCreateSession] builds a real NavigationSession whose
 * Hilt/native graph cannot load in Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class NaviVeylinCarAppServiceLoggingTest {

    private lateinit var logFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        logFile = File(context.filesDir, "diagnostics/carapp-service-test.log")
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
    fun serviceCreateAndDestroyAreLogged() {
        val controller = Robolectric.buildService(NaviVeylinCarAppService::class.java)
        controller.create()
        assertTrue(
            DiagnosticsLog.readEntries().any { it.contains("CarAppService created") }
        )

        controller.destroy()
        assertTrue(
            DiagnosticsLog.readEntries().any { it.contains("CarAppService destroyed") }
        )
    }

    @Test
    fun sessionCreationMessageIncludesDisplayTypeAndHostFallback() {
        val service = NaviVeylinCarAppService()
        val sessionInfo = SessionInfo(SessionInfo.DISPLAY_TYPE_MAIN, "test-session")

        val msg = service.sessionCreatedMessage(sessionInfo, host = null)

        assertTrue(msg.contains("onCreateSession displayType=${SessionInfo.DISPLAY_TYPE_MAIN}"))
        assertTrue(msg.contains("host=unknown uid=-1"))
    }

    @Test
    fun sessionCreationMessageIncludesHostIdentity() {
        val service = NaviVeylinCarAppService()
        val sessionInfo = SessionInfo(SessionInfo.DISPLAY_TYPE_CLUSTER, "cluster")
        val host = HostInfo("com.google.android.projection.gearhead", 12345)

        val msg = service.sessionCreatedMessage(sessionInfo, host)

        assertTrue(msg.contains("displayType=${SessionInfo.DISPLAY_TYPE_CLUSTER}"))
        assertTrue(msg.contains("host=com.google.android.projection.gearhead uid=12345"))
    }
}
