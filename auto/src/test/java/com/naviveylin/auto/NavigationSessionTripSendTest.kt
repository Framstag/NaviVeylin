package com.naviveylin.auto

import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.model.Trip
import com.naviveylin.core.NavigationState
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins the one host-facing send that is deliberately **not** bounded by the session being
 * started (spec: `car-host-fault-isolation` — Trip metadata publishing is bounded by the
 * navigation period; issue #71).
 *
 * The trip collector publishes to the host for every navigation-state emission so the vehicle
 * cluster and the heads-up display keep showing guidance while another car app is in front. The
 * sibling navigation-state observer does the opposite — it asks `SessionHostGate.allowHostMutation()`
 * and defers — which makes the seam easy to "fix" by copying that question onto the trip collector.
 *
 * What these cases pin: the **decision** ([tripSendAllowed] and everything reachable from it here —
 * the send itself, the absence of a deferred sync, and the absence of a start/end call). What they
 * do **not** pin: the collector's own line at `NavigationSession.kt:732`, which no unit test can
 * reach ([NavigationSession] cannot be constructed in Robolectric). Replacing that call with
 * `hostGate.allowHostMutation()` therefore still leaves this class green — the residual gap is
 * recorded in `design.md`, D2.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationSessionTripSendTest {

    private fun startedManager(): NavigationManager = mockk<NavigationManager>().apply {
        every { setNavigationManagerCallback(any()) } just runs
        every { navigationStarted() } just runs
        every { navigationEnded() } just runs
        every { clearNavigationManagerCallback() } just runs
        every { updateTrip(any()) } just runs
    }

    /** Navigation is active — the state the cluster/heads-up path publishes for. */
    private fun navigatingState() = NavigationState(isNavigating = true, remainingDistance = 250.0)

    @Test
    fun tripKeepsUpdatingWhileTheSessionIsNotStarted() {
        // The session is stopped (the app is backgrounded): the gate would defer a host mutation.
        val gate = SessionHostGate()
        gate.onSessionStart()
        gate.onSessionStop()
        assertFalse("the session is not started", gate.started)

        // A displayed trip value changes while a navigation is active, and the update reaches the host.
        val navigationManager = startedManager()
        val controller = NavigationManagerController(navigationManager) {}
        controller.onNavigationStarted()
        val state = navigatingState()
        assertTrue(
            "a stopped session does not withdraw the trip send",
            tripSendAllowed(state.isNavigating, gate.started)
        )
        controller.publishTrip(state) { mockk<Trip>(relaxed = true) }
        verify(exactly = 1) { navigationManager.updateTrip(any()) }

        // The scenario's AND line: this send starts or ends nothing — only the navigation start did —
        // and it owes the gate no deferred sync, because it never asked the gate for a mutation.
        verify(exactly = 1) { navigationManager.navigationStarted() }
        verify(exactly = 0) { navigationManager.navigationEnded() }
        assertFalse("the trip send owes no deferred host sync", gate.syncPending)
    }

    @Test
    fun noTripSendOutsideTheNavigationPeriod() {
        // The decision is taken by the navigation period, never by the session state.
        assertFalse(tripSendAllowed(navigating = false, sessionStarted = false))
        assertFalse(tripSendAllowed(navigating = false, sessionStarted = true))

        // And nothing reaches the host: a controller that never started navigation publishes no trip.
        val navigationManager = startedManager()
        NavigationManagerController(navigationManager) {}.publishTrip(
            NavigationState(isNavigating = false, remainingDistance = 250.0)
        ) { mockk<Trip>(relaxed = true) }
        verify(exactly = 0) { navigationManager.updateTrip(any()) }
    }
}
