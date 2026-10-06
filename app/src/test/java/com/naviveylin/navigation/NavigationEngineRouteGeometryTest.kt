package com.naviveylin.navigation

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.location.LocationService
import com.naviveylin.core.EngineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import com.naviveylin.test.engineUnderTest

/**
 * Verifies the phone/car parity contract (spec: reroute-route-visibility —
 * "Phone and Android Auto parity for route geometry state"):
 * [NavigationEngine.start] populates
 * `NavigationState.routeLats`/`routeLons` on start (mirroring
 * the engine start) and clears them on stop.
 *
 * Default Robolectric sandbox (no @Config, no @GraphicsMode) per the AGENTS.md
 * "JNI stub for unit tests" classloader rule — this class instantiates
 * [FakeOSMScoutClient].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NavigationEngineRouteGeometryTest {

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
            { client },
            locationService,
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
        latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
        longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
        distance = 5000.0
    }

    @Test
    fun startNavigation_populatesRouteGeometry() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val vm = buildViewModel(client)

        vm.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)

        awaitState { vm.state.value.routeLats != null }
        assertArrayEquals(
            doubleArrayOf(52.5200, 52.5230, 52.5300),
            vm.state.value.routeLats!!, 0.0
        )
        assertArrayEquals(
            doubleArrayOf(13.4050, 13.4080, 13.4100),
            vm.state.value.routeLons!!, 0.0
        )
    }

    @Test
    fun stopNavigation_clearsRouteGeometry() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val vm = buildViewModel(client)

        vm.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)
        awaitState { vm.state.value.isNavigating }

        vm.stopNavigation()

        assertNull(vm.state.value.routeLats)
        assertNull(vm.state.value.routeLons)
    }
}
