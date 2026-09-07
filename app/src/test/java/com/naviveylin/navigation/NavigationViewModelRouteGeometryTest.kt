package com.naviveylin.navigation

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.location.LocationService
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Verifies the phone/car parity contract (spec: reroute-route-visibility —
 * "Phone and Android Auto parity for route geometry state"):
 * [NavigationViewModel.startNavigation] populates
 * `NavigationState.routeLats`/`routeLons` on start (mirroring
 * `AANavigationController.startNavigation`) and clears them on stop.
 *
 * Default Robolectric sandbox (no @Config, no @GraphicsMode) per the AGENTS.md
 * "JNI stub for unit tests" classloader rule — this class instantiates
 * [FakeOSMScoutClient].
 */
@RunWith(RobolectricTestRunner::class)
class NavigationViewModelRouteGeometryTest {

    private fun buildViewModel(
        client: FakeOSMScoutClient = FakeOSMScoutClient(),
        locationService: LocationService = LocationService(ApplicationProvider.getApplicationContext())
    ): NavigationViewModel {
        return NavigationViewModel(client, NavigationStateProvider(), locationService, ApplicationProvider.getApplicationContext())
    }

    /** Pump Robolectric's paused main looper until [condition] holds or timeout. */
    private fun awaitState(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("State condition not met within 5s")
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

        vm.startDirectRoute(52.5200, 13.4050, 52.5300, 13.4100)

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

        vm.startDirectRoute(52.5200, 13.4050, 52.5300, 13.4100)
        awaitState { vm.state.value.isNavigating }

        vm.stopNavigation()

        assertNull(vm.state.value.routeLats)
        assertNull(vm.state.value.routeLons)
    }
}
