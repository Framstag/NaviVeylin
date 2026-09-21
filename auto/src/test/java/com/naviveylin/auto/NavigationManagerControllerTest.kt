package com.naviveylin.auto

import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Trip
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.NavigationState
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests [NavigationManagerController] (spec: auto/navigation-view — "Leave
 * navigation at any time"): the host ETA card stop button is delivered as
 * [NavigationManagerCallback.onStopNavigation], which the host only sends when
 * the app registered a callback and called [NavigationManager.navigationStarted].
 * The session itself needs a host-provided CarContext and cannot be
 * constructed in Robolectric, so the controller is the testable seam (same
 * pattern as [NavigationSessionDarkModeTest]).
 */
@RunWith(RobolectricTestRunner::class)
class NavigationManagerControllerTest {

    @Test
    fun navigationStartRegistersCallbackAndStarts() {
        val nm = mockk<NavigationManager>()
        every { nm.setNavigationManagerCallback(any()) } just Runs
        every { nm.navigationStarted() } just Runs

        NavigationManagerController(nm) {}.onNavigationStarted()

        verify { nm.setNavigationManagerCallback(any()) }
        verify { nm.navigationStarted() }
    }

    @Test
    fun navigationEndEndsThenClearsCallback() {
        val nm = mockk<NavigationManager>()
        every { nm.navigationEnded() } just Runs
        every { nm.clearNavigationManagerCallback() } just Runs

        NavigationManagerController(nm) {}.onNavigationEnded()

        // Order matters: clearNavigationManagerCallback() throws while
        // navigating, so navigationEnded() must run first.
        verify { nm.navigationEnded() }
        verify { nm.clearNavigationManagerCallback() }
    }

    @Test
    fun hostStopInvokesStopHandler() {
        val nm = mockk<NavigationManager>()
        every { nm.setNavigationManagerCallback(any()) } just Runs
        every { nm.navigationStarted() } just Runs
        var stopped = false
        val controller = NavigationManagerController(nm) { stopped = true }

        controller.onNavigationStarted()
        val callback = slot<NavigationManagerCallback>()
        verify { nm.setNavigationManagerCallback(capture(callback)) }

        callback.captured.onStopNavigation()

        assertTrue("host stop must stop navigation", stopped)
    }

    @Test
    fun destroyWhileNavigatingDoesNotThrow() {
        val nm = mockk<NavigationManager>()
        every { nm.navigationEnded() } just Runs
        every { nm.clearNavigationManagerCallback() } just Runs

        // Must not throw even though the controller may be mid-navigation.
        NavigationManagerController(nm) {}.onDestroy()

        verify { nm.navigationEnded() }
        verify { nm.clearNavigationManagerCallback() }
    }

    // ── Fault isolation (spec: car-host-fault-isolation — No fault escapes into
    // the host path; design D3): every host call and the trip construction are
    // guarded, because the library throws and the session's main-thread collector
    // would otherwise die with the process. ──

    @Test
    fun aThrowingTripIsNotPublishedAndPublishingRecovers() {
        val nm = mockk<NavigationManager>()
        every { nm.setNavigationManagerCallback(any()) } just Runs
        every { nm.navigationStarted() } just Runs
        every { nm.updateTrip(any()) } just Runs
        val controller = NavigationManagerController(nm) {}
        controller.onNavigationStarted()

        // A trip that cannot be built must not reach the host...
        controller.publishTrip(NavigationState(remainingDistance = 100.0)) {
            throw IllegalStateException("no usable step")
        }
        verify(exactly = 0) { nm.updateTrip(any()) }

        // ...and the next, buildable trip still does (the fault stayed local).
        controller.publishTrip(NavigationState(remainingDistance = 200.0)) {
            mockk<Trip>(relaxed = true)
        }
        verify(exactly = 1) { nm.updateTrip(any()) }
    }

    @Test
    fun aRejectedNavigationStartLeavesTheControllerNotPublishing() {
        val nm = mockk<NavigationManager>()
        every { nm.setNavigationManagerCallback(any()) } just Runs
        every { nm.navigationStarted() } throws IllegalStateException("No callback has been set")
        val controller = NavigationManagerController(nm) {}

        // Must not throw, and must not publish into a host that refused the session.
        controller.onNavigationStarted()
        controller.publishTrip(NavigationState(remainingDistance = 10.0)) {
            mockk<Trip>(relaxed = true)
        }

        verify(exactly = 0) { nm.updateTrip(any()) }
    }

    @Test
    fun aRejectedNavigationEndDoesNotEscape() {
        val nm = mockk<NavigationManager>()
        every { nm.navigationEnded() } throws IllegalStateException("Removing callback while navigating")
        every { nm.clearNavigationManagerCallback() } throws
            IllegalStateException("Removing callback while navigating")

        NavigationManagerController(nm) {}.onNavigationEnded()

        verify { nm.navigationEnded() }
        verify { nm.clearNavigationManagerCallback() }
    }

    @Test
    fun aRejectedTripUpdateEndsTripPublishing() {
        val nm = mockk<NavigationManager>()
        every { nm.setNavigationManagerCallback(any()) } just Runs
        every { nm.navigationStarted() } just Runs
        every { nm.updateTrip(any()) } throws IllegalStateException("Navigation is not started")
        val controller = NavigationManagerController(nm) {}
        controller.onNavigationStarted()

        controller.publishTrip(NavigationState(remainingDistance = 100.0)) {
            mockk<Trip>(relaxed = true)
        }
        // The rejected update means the host's session is gone: no further publishes.
        controller.publishTrip(NavigationState(remainingDistance = 200.0)) {
            mockk<Trip>(relaxed = true)
        }

        verify(exactly = 1) { nm.updateTrip(any()) }
    }

    @Test
    fun hostSendsAreRecordedForDiagnosis() {
        // Spec: car-host-fault-isolation — Host interaction is diagnosable.
        val file = java.io.File.createTempFile("diag", ".log")
        DiagnosticsLog.initForTest(file)
        try {
            val nm = mockk<NavigationManager>()
            every { nm.setNavigationManagerCallback(any()) } just Runs
            every { nm.navigationStarted() } just Runs
            every { nm.updateTrip(any()) } just Runs
            every { nm.navigationEnded() } just Runs
            every { nm.clearNavigationManagerCallback() } just Runs
            val controller = NavigationManagerController(nm) {}

            controller.onNavigationStarted()
            controller.publishTrip(NavigationState(remainingDistance = 100.0)) {
                mockk<Trip>(relaxed = true)
            }
            controller.onNavigationEnded()

            val entries = DiagnosticsLog.readEntries()
            assertTrue(entries.any { it.contains("HOST navigationStarted") })
            assertTrue(entries.any { it.contains("HOST trip update") })
            assertTrue(entries.any { it.contains("HOST navigationEnded") })
        } finally {
            DiagnosticsLog.reset()
            file.delete()
        }
    }
}
