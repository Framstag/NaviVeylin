package com.naviveylin.navigation

import android.content.Context
import android.location.Location
import android.os.Looper
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.location.LocationService
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

/**
 * Tests for the car-only route calculation fallback in [NavigationViewModel]
 * (used when the phone [com.naviveylin.ui.route.RoutePanelViewModel] is not
 * wired — e.g. navigation started from Android Auto via a deep link).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationViewModelDirectRouteTest {

    private fun buildViewModel(
        client: FakeOSMScoutClient = FakeOSMScoutClient(),
        locationService: LocationService = LocationService(ApplicationProvider.getApplicationContext())
    ): NavigationViewModel {
        return NavigationViewModel(client, NavigationStateProvider(), locationService)
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

    /** Inject a location into the private LocationService flow (test only). */
    private fun injectGpsFix(locationService: LocationService, lat: Double, lon: Double) {
        val field = LocationService::class.java.getDeclaredField("_location")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(locationService) as MutableStateFlow<Location?>
        flow.value = Location("gps").apply {
            this.latitude = lat
            this.longitude = lon
            accuracy = 5f
            time = System.currentTimeMillis()
        }
    }

    @Test
    fun startDirectRoute_withRoute_startsNavigation() {
        val client = FakeOSMScoutClient().apply {
            routeToDeliver = RouteEntry().apply {
                routeHandle = 1L
                latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
                longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
                distance = 5000.0
                descriptions = arrayOf(
                    "Start navigation  [0.0 km, 0 min]",
                    "Destination reached  [0.0 km, 0 min]"
                )
            }
        }
        val vm = buildViewModel(client)

        vm.startDirectRoute(52.5200, 13.4050, 52.5300, 13.4100)

        awaitState { vm.state.value.isNavigating }
        assertEquals(1, client.routeCalculationCount)
        assertNull(vm.state.value.errorMessage)
        // Total distance is derived from route geometry (haversine), not RouteEntry.distance
        assertTrue(vm.state.value.totalDistance > 0.0)
    }

    @Test
    fun startDirectRoute_routeError_setsErrorMessage() {
        val client = FakeOSMScoutClient().apply {
            routeToDeliver = null
            deliverRouteError = "Route calculation failed. Try again."
        }
        val vm = buildViewModel(client)

        vm.startDirectRoute(52.5200, 13.4050, 52.5300, 13.4100)

        awaitState { vm.state.value.errorMessage != null }
        assertEquals(1, client.routeCalculationCount)
        assertTrue(!vm.state.value.isNavigating)
        assertEquals("Route calculation failed. Try again.", vm.state.value.errorMessage)
    }

    @Test
    fun navigateTo_withoutRoutePanelViewModelAndNoGps_reportsGpsError() {
        val vm = buildViewModel()
        // No GPS fix anywhere: state.position is null and LocationService has no location.

        vm.navigateTo(52.5300, 13.4100)

        assertTrue(!vm.state.value.isNavigating)
        assertNotNull(vm.state.value.errorMessage)
        assertTrue(vm.state.value.errorMessage!!.contains("GPS"))
    }

    @Test
    fun navigateTo_withoutRoutePanelViewModel_usesLocationServiceGps() {
        val client = FakeOSMScoutClient().apply {
            routeToDeliver = RouteEntry().apply {
                routeHandle = 1L
                latitudes = doubleArrayOf(52.5200, 52.5300)
                longitudes = doubleArrayOf(13.4050, 13.4100)
                distance = 4000.0
            }
        }
        val context: Context = ApplicationProvider.getApplicationContext()
        val locationService = LocationService(context)
        injectGpsFix(locationService, 52.5200, 13.4050)

        val vm = NavigationViewModel(client, NavigationStateProvider(), locationService)

        vm.navigateTo(52.5300, 13.4100)

        awaitState { vm.state.value.isNavigating }
        assertEquals(1, client.routeCalculationCount)
        assertNull(vm.state.value.errorMessage)
    }
}
