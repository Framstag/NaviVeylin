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
    fun aTransientTripRejectionRearmsPublishing() {
        // Spec: car-host-fault-isolation — "A rejected trip update re-arms publishing within
        // a retry budget": one refusal does not end the navigation period by itself.
        val nm = mockk<NavigationManager>()
        every { nm.setNavigationManagerCallback(any()) } just Runs
        every { nm.navigationStarted() } just Runs
        var hostAccepts = false
        every { nm.updateTrip(any()) } answers {
            if (!hostAccepts) throw IllegalStateException("Navigation is not started")
        }
        val controller = NavigationManagerController(nm) {}
        controller.onNavigationStarted()

        controller.publishTrip(NavigationState(remainingDistance = 100.0)) {
            mockk<Trip>(relaxed = true)
        }
        // The host session is healthy again: the changed trip is sent a second time.
        hostAccepts = true
        controller.publishTrip(NavigationState(remainingDistance = 200.0)) {
            mockk<Trip>(relaxed = true)
        }

        verify(exactly = 2) { nm.updateTrip(any()) }
    }

    @Test
    fun aRejectedTripIsNotResentUntilItsContentChanges() {
        // Spec: car-host-fault-isolation — the re-arm does not poll the host at the
        // position update rate: content the host already refused is not resent.
        val nm = mockk<NavigationManager>()
        every { nm.setNavigationManagerCallback(any()) } just Runs
        every { nm.navigationStarted() } just Runs
        every { nm.updateTrip(any()) } throws IllegalStateException("Navigation is not started")
        val controller = NavigationManagerController(nm) {}
        controller.onNavigationStarted()

        controller.publishTrip(NavigationState(remainingDistance = 100.0)) {
            mockk<Trip>(relaxed = true)
        }
        // The changed content is the re-arm...
        controller.publishTrip(NavigationState(remainingDistance = 200.0)) {
            mockk<Trip>(relaxed = true)
        }
        // ...and the same content again is not resent, so the re-arm does not poll the host.
        controller.publishTrip(NavigationState(remainingDistance = 200.0)) {
            mockk<Trip>(relaxed = true)
        }

        verify(exactly = 2) { nm.updateTrip(any()) }
    }

    @Test
    fun aRepeatedlyRejectedHostStopsPublishingAfterTheRetryBudget() {
        // Spec: car-host-fault-isolation — the re-arm is bounded: a dead host is not
        // polled for the whole navigation period.
        val nm = mockk<NavigationManager>()
        every { nm.setNavigationManagerCallback(any()) } just Runs
        every { nm.navigationStarted() } just Runs
        every { nm.updateTrip(any()) } throws IllegalStateException("Navigation is not started")
        val controller = NavigationManagerController(nm) {}
        controller.onNavigationStarted()

        // Five changed trip contents against a host that refuses every send. 100 m steps:
        // DistanceFormat rounds to 50 m below 1 km, so each step is a distinct bucket.
        for (step in 1..5) {
            controller.publishTrip(NavigationState(remainingDistance = step * 100.0)) {
                mockk<Trip>(relaxed = true)
            }
        }

        // Three refused sends are the budget; the fourth is not attempted.
        verify(exactly = 3) { nm.updateTrip(any()) }
    }

    @Test
    fun aDeliveredTripResetsTheRejectionBudget() {
        // Spec: car-host-fault-isolation — the budget counts *consecutive* refusals,
        // "a run with no accepted send in between": an accepted send ends the run, so a
        // counter that never resets stops earlier than the contract says.
        val nm = mockk<NavigationManager>()
        every { nm.setNavigationManagerCallback(any()) } just Runs
        every { nm.navigationStarted() } just Runs
        var hostAccepts = false
        every { nm.updateTrip(any()) } answers {
            if (!hostAccepts) throw IllegalStateException("Navigation is not started")
        }
        val controller = NavigationManagerController(nm) {}
        controller.onNavigationStarted()

        fun publish(metres: Double) {
            controller.publishTrip(NavigationState(remainingDistance = metres)) {
                mockk<Trip>(relaxed = true)
            }
        }

        publish(100.0) // refused — 1 in the run
        hostAccepts = true
        publish(200.0) // accepted — the run ends here, so the counter resets
        hostAccepts = false
        publish(300.0) // refused — 1 in the new run
        publish(400.0) // refused — 2 in the new run
        publish(500.0) // refused — 3 in the new run, sent: the budget of this run is spent here

        // Five sends: the accepted trip reset the count, so the third refusal of the new
        // run is still attempted. A counter that never resets stops after four.
        verify(exactly = 5) { nm.updateTrip(any()) }
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
