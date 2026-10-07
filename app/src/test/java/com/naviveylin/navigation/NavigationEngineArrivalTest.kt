package com.naviveylin.navigation

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import com.naviveylin.test.engineUnderTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The arrival fact in the shared navigation state (spec: `navigation-engine` — Arrival is part of
 * the shared navigation state): the native engine's `onTargetReached` reaches the state, a reroute
 * does not lose it, a new navigation and a stop clear it, and arriving does not end the session.
 *
 * No surface participates in any case of this class — the fact is read from the engine's own state,
 * which is what makes it usable after a car session is gone (scenario "Arrival is observable without
 * a surface").
 *
 * Runs under Robolectric with the default sandbox (AGENTS.md classloader rule for the JNI stub `.so`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NavigationEngineArrivalTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var engine: NavigationEngine

    /** Injected clock: a case moves time instead of waiting for it (spec: `unit-test-suite-runtime`). */
    private var nowMillis = 1_000_000L
    private val clock = EngineTimeSource { nowMillis }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient().apply { routeToDeliver = route() }
        engine = engineUnderTest(
            { client },
            LocationService(context),
            context,
            timeSource = clock,
            dispatchers = EngineDispatchers(
                compute = mainDispatcherRule.dispatcher,
                io = mainDispatcherRule.dispatcher
            )
        )
    }

    private fun route(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        // A straight west-east line; the destination is its east end.
        latitudes = doubleArrayOf(52.5200, 52.5200)
        longitudes = doubleArrayOf(13.4000, 13.5000)
        distance = 7000.0
        descriptions = arrayOf(
            "Start navigation  [0.0 km, 0 min]",
            "Destination reached  [0.0 km, 0 min]"
        )
    }

    private fun advanceUntilIdleBlocking() {
        shadowOf(Looper.getMainLooper()).idle()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    private fun awaitState(condition: () -> Boolean) {
        advanceUntilIdleBlocking()
        if (!condition()) {
            throw AssertionError("State condition not met after draining the test scheduler")
        }
    }

    /** Start a session as a surface would (engine.start with a calculated route). */
    private fun startSession() {
        engine.start(route(), Vehicle.CAR)
        awaitState { client.navigationListener != null }
        awaitState { engine.state.value.isNavigating }
    }

    private fun listener() = client.navigationListener!!

    /** The native report of a reached destination (inside its approach radius, on route). */
    private fun reportArrival() {
        listener().onTargetReached(90.0, 12.0)
        advanceUntilIdleBlocking()
    }

    /**
     * Report an off-route position that confirms the fast path, then let the engine re-acquire.
     * A fresh, accurate fix is fed first, because the accuracy guard ignores off-route reports while
     * no fix has been seen. The re-acquisition runs through the same start path as a first start.
     */
    private fun requestReroute() {
        engine.processLocation(52.5200, 13.4500, 17.8, 5.0, nowMillis)
        listener().onRerouteRequest(52.5218, 13.4500, 90.0, 52.5200, 13.5000)
        awaitState { client.routeCalculationCount == 1 }
        advanceUntilIdleBlocking()
        awaitState { engine.state.value.isNavigating }
    }

    @Test
    fun destinationReachedIsReportedInTheSharedState() = runTest(mainDispatcherRule.dispatcher) {
        startSession()
        assertFalse(
            "a running session begins with the destination not reached",
            engine.state.value.hasReachedDestination
        )

        reportArrival()

        assertTrue(
            "the native target-reached report reaches the shared state",
            engine.state.value.hasReachedDestination
        )

        // The native engine reports it on every fix inside its approach radius.
        listener().onTargetReached(90.0, 9.0)
        advanceUntilIdleBlocking()

        assertTrue(
            "a repeated report leaves the fact set",
            engine.state.value.hasReachedDestination
        )
    }

    @Test
    fun arrivalIsObservableWithoutASurface() = runTest(mainDispatcherRule.dispatcher) {
        // No NavigationViewModel, no car screen, no notification is created anywhere in this class:
        // the fact must be readable from the engine's state alone, because the car session that
        // consumes it (spec: `auto/navigation-view` — Navigation ends when the car session ends
        // after arrival) reads it at the very moment it is destroyed.
        startSession()
        reportArrival()

        assertTrue(engine.state.value.hasReachedDestination)
    }

    @Test
    fun reachingTheDestinationDoesNotStopNavigation() = runTest(mainDispatcherRule.dispatcher) {
        startSession()
        val before = engine.state.value

        reportArrival()

        val after = engine.state.value
        assertTrue(
            "arrival alone is not a stop: the session keeps running",
            after.isNavigating
        )
        assertTrue(
            "the arrival fact is the only difference the report makes",
            after.copy(hasReachedDestination = false) == before
        )
    }

    @Test
    fun rerouteKeepsTheArrivalFact() = runTest(mainDispatcherRule.dispatcher) {
        startSession()
        reportArrival()

        requestReroute()

        assertTrue(
            "the reroute re-acquired a route to the destination already reached",
            engine.state.value.isNavigating
        )
        assertTrue(
            "the arrival fact belongs to the destination, not to the replaced route",
            engine.state.value.hasReachedDestination
        )
    }

    @Test
    fun newStartClearsTheArrivalFact() = runTest(mainDispatcherRule.dispatcher) {
        startSession()
        reportArrival()
        assertTrue(engine.state.value.hasReachedDestination)

        // A newly started navigation (not the engine's own reroute) replaces the session.
        engine.start(route(), Vehicle.CAR)
        awaitState { engine.state.value.isNavigating }

        assertFalse(
            "a new navigation begins with the destination not reached",
            engine.state.value.hasReachedDestination
        )
    }

    @Test
    fun stopClearsTheArrivalFact() = runTest(mainDispatcherRule.dispatcher) {
        startSession()
        reportArrival()

        engine.stopNavigation()
        advanceUntilIdleBlocking()

        assertFalse("the stopped session is not navigating", engine.state.value.isNavigating)
        assertFalse(
            "the arrival fact of the ended session goes with its state",
            engine.state.value.hasReachedDestination
        )
    }

    @Test
    fun lateArrivalReportAfterAStopIsIgnored() = runTest(mainDispatcherRule.dispatcher) {
        startSession()
        val stoppedListener = listener()
        engine.stopNavigation()
        advanceUntilIdleBlocking()

        // The native report of the already-stopped session arrives late.
        stoppedListener.onTargetReached(90.0, 12.0)
        advanceUntilIdleBlocking()

        assertFalse(
            "a report of a stopped session must not publish an arrival",
            engine.state.value.hasReachedDestination
        )
    }
}
