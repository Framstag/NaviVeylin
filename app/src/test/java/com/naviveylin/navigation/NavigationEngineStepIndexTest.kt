package com.naviveylin.navigation

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RouteStep
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
 * Verifies the phone navigation engine's reroute-restart state handling
 * (spec: auto/navigation-view — "Distance correct after reroute", next-turn-overlay): starting
 * navigation on a new route clears the previous route's steps, and the live step-index lookup
 * advances monotonically through repeated manoeuvres.
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
            client = { client },
            locationService = locationService,
            context = ApplicationProvider.getApplicationContext(),
            dispatchers = engineDispatchers
        )
    }

    /** Drains the engine's schedulers until [condition] holds. */
    private fun awaitState(condition: () -> Boolean) {
        repeat(50) {
            shadowOf(Looper.getMainLooper()).idle()
            ioDispatcher.scheduler.advanceUntilIdle()
            computeDispatcher.scheduler.advanceUntilIdle()
            if (condition()) return
        }
        assertTrue("the state never reached the awaited condition", condition())
    }

    @Suppress("unused")
    private fun routeEntry() = RouteEntry().apply {
        routeHandle = 1L
        latitudes = doubleArrayOf(52.5200, 52.5230)
        longitudes = doubleArrayOf(13.4050, 13.4080)
        distance = 1000.0
    }

    /** A left turn at [distanceFromStart], so the live step carries a stable identity. */
    private fun step(distanceFromStart: Double = 100.0) = RouteStep().apply {
        kind = RouteStep.Kind.TURN
        turn = TurnType.LEFT
        legDistance = 100.0
        this.distanceFromStart = distanceFromStart
        name = RouteStep.Name("Main St", "")
    }

    @Test
    fun startNavigation_clearsStaleStepsFromPreviousRoute() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val vm = buildViewModel(client)

        vm.acquire(52.5200, 13.4050, 52.5230, 13.4080, Vehicle.CAR)
        awaitState { vm.state.value.isNavigating }

        // Simulate steps from the first route reaching the UI.
        val listener = client.navigationListener!!
        listener.onRouteSteps(arrayOf(step(100.0), step(200.0)))
        awaitState {
            vm.state.value.steps.isNotEmpty() && vm.state.value.nextStep != null
        }

        // A reroute restart starts a fresh engine — stale steps must not survive.
        vm.start(routeEntry(), Vehicle.CAR)

        assertTrue(vm.state.value.steps.isEmpty())
        assertNull(vm.state.value.nextStep)
        assertEquals(0, vm.state.value.currentStepIndex)
    }

    @Test
    fun onNextRouteStep_advancesIndexMonotonicallyThroughRepeatedManoeuvres() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val vm = buildViewModel(client)

        vm.acquire(52.5200, 13.4050, 52.5230, 13.4080, Vehicle.CAR)
        awaitState { vm.state.value.isNavigating }

        val listener = client.navigationListener!!
        listener.onRouteSteps(arrayOf(step(100.0), step(300.0), step(700.0)))
        awaitState { vm.state.value.steps.size == 3 }

        // Advance to the middle step.
        listener.onNextRouteStep(step(300.0))
        awaitState { vm.state.value.currentStepIndex == 1 }

        // The live step jumps to the third manoeuvre.
        listener.onNextRouteStep(step(700.0))
        awaitState { vm.state.value.currentStepIndex == 2 }

        // A step that matches no list entry keeps the current index instead of thrashing.
        listener.onNextRouteStep(step(9_000.0))
        awaitState { vm.state.value.currentStepIndex == 2 }
    }
}
