package com.naviveylin.navigation

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Looper
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.location.GpsFix
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
import com.naviveylin.core.EngineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.test.engineUnderTest

/**
 * Tests for the car-only route calculation fallback in [NavigationEngine]
 * (used when the phone [com.naviveylin.ui.route.RoutePanelViewModel] is not
 * wired — e.g. navigation started from Android Auto via a deep link).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationEngineAcquisitionTest {

    /**
     * The engine's off-main work runs on these schedulers (production uses real pools), so a case
     * drains them instead of polling (spec: `unit-test-suite-runtime` — Awaiting state, not a
     * deadline).
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

    /** Inject a location into the private LocationService flow (test only). */
    private fun injectGpsFix(locationService: LocationService, lat: Double, lon: Double) {
        val field = LocationService::class.java.getDeclaredField("_location")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(locationService) as MutableStateFlow<GpsFix?>
        flow.value = GpsFix(
            lat = lat,
            lon = lon,
            accuracy = 5.0,
            speedKmH = Double.NaN,
            smoothedBearing = Double.NaN,
            markerBearing = Double.NaN,
            time = System.currentTimeMillis()
        )
    }

    /**
     * The navigation gate requires the precise grant (spec: `location-permissions`
     * — Starting navigation requires precise location).
     */
    private fun grantPreciseLocation() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun grantApproximateLocationOnly() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Test
    fun navigateTo_withApproximateGrant_isRefusedBeforeTheEngineAndTheLease() {
        val client = FakeOSMScoutClient()
        val locationService = LocationService(ApplicationProvider.getApplicationContext())
        injectGpsFix(locationService, 52.5200, 13.4050)
        grantApproximateLocationOnly()
        val vm = buildViewModel(client, locationService)

        vm.navigateTo(52.5300, 13.4100)

        assertEquals("no route request may reach the engine", 0, client.routeCalculationCount)
        assertTrue("no navigation may start", !vm.state.value.isNavigating)
        assertEquals(
            "the refusal uses the shared wording (phone + car)",
            ApplicationProvider.getApplicationContext<Context>()
                .getString(com.naviveylin.core.R.string.location_precise_required_navigation),
            vm.state.value.errorMessage
        )
        assertTrue(
            "the gate must short-circuit before leasing GPS",
            locationService.heldLeaseConsumers().isEmpty()
        )
    }

    @Test
    fun navigateTo_withoutAnyGrant_isRefusedBeforeTheEngine() {
        val client = FakeOSMScoutClient()
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val vm = buildViewModel(client)

        vm.navigateTo(52.5300, 13.4100)

        assertEquals(0, client.routeCalculationCount)
        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun acquire_withRoute_startsNavigation() {
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

        vm.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)

        awaitState { vm.state.value.isNavigating }
        assertEquals(1, client.routeCalculationCount)
        assertNull(vm.state.value.errorMessage)
        // The total is the route's own length when its geometry is usable, not a second app-side sum
        // of that geometry (spec: osmscout-jni — One route length for a calculated route).
        assertTrue(vm.state.value.totalDistance > 0.0)
    }

    @Test
    fun acquire_routeError_setsErrorMessage() {
        val client = FakeOSMScoutClient().apply {
            routeToDeliver = null
            deliverRouteError = "Route calculation failed. Try again."
        }
        val vm = buildViewModel(client)

        vm.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)

        awaitState { vm.state.value.errorMessage != null }
        assertEquals(1, client.routeCalculationCount)
        assertTrue(!vm.state.value.isNavigating)
        assertEquals("Route calculation failed. Try again.", vm.state.value.errorMessage)
    }

    @Test
    fun navigateTo_withoutRoutePanelViewModelAndNoGps_reportsGpsError() {
        grantPreciseLocation()
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
        grantPreciseLocation()
        val locationService = LocationService(context)
        injectGpsFix(locationService, 52.5200, 13.4050)

        val vm = engineUnderTest(
            { client },
            locationService,
            ApplicationProvider.getApplicationContext(),
            dispatchers = engineDispatchers
        )

        vm.navigateTo(52.5300, 13.4100)

        awaitState { vm.state.value.isNavigating }
        assertEquals(1, client.routeCalculationCount)
        assertNull(vm.state.value.errorMessage)
    }
}
