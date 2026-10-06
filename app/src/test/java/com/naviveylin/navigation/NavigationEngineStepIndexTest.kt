package com.naviveylin.navigation

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.location.LocationService
import com.naviveylin.core.EngineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import com.naviveylin.test.engineUnderTest

/**
 * Verifies the phone navigation VM's reroute-restart state handling
 * (spec: auto/navigation-view — "Distance correct after reroute",
 * next-turn-overlay): starting navigation on a new route clears the previous
 * route's steps, and live step-index lookup advances monotonically through
 * duplicate descriptions.
 *
 * Default Robolectric sandbox (no @Config, no @GraphicsMode) per the AGENTS.md
 * "JNI stub for unit tests" classloader rule — this class instantiates
 * [FakeOSMScoutClient] and [NavigationEngine].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NavigationEngineStepIndexTest {

    /**
     * The engine's off-main work runs on these schedulers, so a case drains them instead of
     * polling real threads (spec: `unit-test-suite-runtime` — Awaiting state, not a deadline).
     */
    private val computeDispatcher = StandardTestDispatcher()
    private val ioDispatcher = StandardTestDispatcher()
    private val engineDispatchers = EngineDispatchers(computeDispatcher, ioDispatcher)

    private fun buildViewModel(
        client: FakeOSMScoutClient = FakeOSMScoutClient(),
        locationService: LocationService = LocationService(ApplicationProvider.getApplicationContext())
    ): NavigationEngine {
        return engineUnderTest(
            { client }, locationService,
            ApplicationProvider.getApplicationContext(),
            dispatchers = engineDispatchers
        )
    }

    /** Drain the injected schedulers and the looper, then assert — no wall-clock deadline. */
    private fun awaitState(condition: () -> Boolean) {
        repeat(2) {
            computeDispatcher.scheduler.advanceUntilIdle()
            ioDispatcher.scheduler.advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
        }
        if (!condition()) {
            throw AssertionError("State condition not met after draining the test scheduler")
        }
    }

    private fun routeEntry(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        latitudes = doubleArrayOf(52.5200, 52.5230)
        longitudes = doubleArrayOf(13.4050, 13.4080)
        distance = 1000.0
    }

    private fun instr(description: String, distance: Double = 100.0) =
        RouteInstruction(distance, TurnType.LEFT, "Main St", description, "Turn left")

    @Test
    fun startNavigation_clearsStaleStepsFromPreviousRoute() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val vm = buildViewModel(client)

        vm.acquire(52.5200, 13.4050, 52.5230, 13.4080, Vehicle.CAR)
        awaitState { vm.state.value.isNavigating }

        // Simulate steps from the first route reaching the UI.
        val listener = client.navigationListener!!
        listener.onRouteInstructions(arrayOf(instr("old step 1"), instr("old step 2")))
        awaitState {
            vm.state.value.instructions.isNotEmpty() && vm.state.value.nextInstruction != null
        }

        // A reroute restart starts a fresh engine — stale steps must not survive.
        vm.start(routeEntry(), Vehicle.CAR)

        assertTrue(vm.state.value.instructions.isEmpty())
        assertNull(vm.state.value.nextInstruction)
        assertEquals(0, vm.state.value.currentStepIndex)
    }

    @Test
    fun onNextRouteInstruction_advancesIndexMonotonicallyThroughDuplicates() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val vm = buildViewModel(client)

        vm.acquire(52.5200, 13.4050, 52.5230, 13.4080, Vehicle.CAR)
        awaitState { vm.state.value.isNavigating }

        val listener = client.navigationListener!!
        listener.onRouteInstructions(arrayOf(
            instr("Turn left into A"),
            instr("Straight on"),
            instr("Turn left into A")
        ))
        awaitState { vm.state.value.instructions.size == 3 }

        // Advance to the middle step ("Straight on").
        listener.onNextRouteInstruction(instr("Straight on"))
        awaitState { vm.state.value.currentStepIndex == 1 }

        // The live instruction re-repeats "Turn left into A": its real
        // occurrence is the second one (index 2) — the lookup must NOT fall
        // back to the already-passed index 0.
        listener.onNextRouteInstruction(instr("Turn left into A"))
        awaitState { vm.state.value.currentStepIndex == 2 }

        // Unknown description keeps the current index instead of thrashing.
        listener.onNextRouteInstruction(instr("Unknown maneuver"))
        awaitState { vm.state.value.currentStepIndex == 2 }
    }
}
