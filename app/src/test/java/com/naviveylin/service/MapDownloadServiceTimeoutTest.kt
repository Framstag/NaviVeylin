package com.naviveylin.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.DiagnosticsLog
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The download foreground service's platform-timeout path (spec:
 * map-download-infrastructure — Foreground service for download): when the platform
 * ends the service because its foreground type hit its runtime limit (Android 15+
 * caps `dataSync` at six hours per 24), the service ends cleanly, releases its wake
 * lock and leaves the download resumable — it never reports completion, because the
 * download managers own that state.
 *
 * The service is built through Robolectric (no Hilt test application needed: the
 * generated Hilt service class runs against the app's own generated component), and
 * the wake lock is observed through the PowerManager shadow.
 *
 * The spec's "a platform-ended service is not reported as a completed download" is a
 * structural invariant rather than a service-level assertion: this service owns no
 * download state, and only the download managers' listeners report completion
 * (covered by the basemap/map-manager download tests). What is asserted here is what
 * the service itself owns — ending cleanly, releasing its wake lock and leaving a
 * diagnosable trace.
 */
@RunWith(RobolectricTestRunner::class)
class MapDownloadServiceTimeoutTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DiagnosticsLog.initForTest(File(context.filesDir, "diagnostics/app.log"))
    }

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
    }

    /** The most recently created wake lock's held state (Robolectric shadow). */
    private fun wakeLockHeld(): Boolean =
        org.robolectric.shadows.ShadowPowerManager.getLatestWakeLock()?.isHeld == true

    private fun buildService(): MapDownloadService =
        Robolectric.buildService(MapDownloadService::class.java).create().get()

    @Test
    fun platformTimeout_releasesTheWakeLockAndStopsTheService() {
        val service = buildService()
        assertTrue("the running service holds its wake lock", wakeLockHeld())

        service.handleForegroundServiceTimeout()

        assertFalse(
            "a platform-ended service must not keep the CPU awake",
            wakeLockHeld()
        )
        assertTrue(
            "the service must stop itself, not linger",
            shadowOf(service).isStoppedBySelf
        )
    }

    @Test
    fun platformTimeout_isRecordedInTheDiagnosticsStream() {
        val service = buildService()

        service.handleForegroundServiceTimeout()

        val entries = DiagnosticsLog.readEntries()
        assertTrue(
            "a device run must be able to tell why the download stopped: $entries",
            entries.any { it.contains(MapDownloadService.TIMEOUT_TAG) }
        )
    }

    @Test
    fun platformTimeout_isIdempotentAndLeavesTheServiceStopped() {
        val service = buildService()

        service.handleForegroundServiceTimeout()
        service.handleForegroundServiceTimeout()

        assertTrue(shadowOf(service).isStoppedBySelf)
        assertFalse("a second timeout must not re-acquire anything", wakeLockHeld())
    }
}
