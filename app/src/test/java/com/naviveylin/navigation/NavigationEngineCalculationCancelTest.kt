package com.naviveylin.navigation

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteCallback
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationConsumers
import com.naviveylin.location.LocationService
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.naviveylin.core.EngineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.robolectric.Shadows.shadowOf
import com.naviveylin.test.engineUnderTest

/**
 * Cancelling the route calculation in flight
 * (spec: `route-calculation-feedback` — Cancelling a calculation aborts it and releases
 * its resources; spec: `navigation-engine` — Surface-less route acquisition is
 * cancellable).
 *
 * Default Robolectric sandbox — no `@Config`, no `@GraphicsMode` (AGENTS.md: the JNI
 * stub for `OSMScoutClient` loads in exactly one sandbox classloader).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NavigationEngineCalculationCancelTest {

    /**
     * The engine's off-main work runs on these schedulers (production uses real pools), so a case
     * drains them instead of polling (spec: `unit-test-suite-runtime` — Awaiting state, not a
     * deadline).
     */
    private val computeDispatcher = StandardTestDispatcher()
    private val ioDispatcher = StandardTestDispatcher()
    private val engineDispatchers = EngineDispatchers(computeDispatcher, ioDispatcher)

    private fun context(): Application = ApplicationProvider.getApplicationContext()

    private fun buildEngine(
        client: FakeOSMScoutClient,
        locationService: LocationService
    ): NavigationEngine = engineUnderTest(
        { client }, locationService, context(),
        dispatchers = engineDispatchers
    )

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

    /**
     * Wait for the held route callback: the engine calculates on the injected compute dispatcher,
     * so the fake is handed its callback once that scheduler is drained.
     */
    private fun awaitCallback(client: FakeOSMScoutClient): RouteCallback {
        awaitState { client.pendingRouteCallbacks.isNotEmpty() }
        return client.pendingRouteCallbacks.first()
    }

    private fun grantPreciseLocation() {
        shadowOf(context()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** Inject a location into the LocationService flow (test only). */
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

    private fun routeEntry(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
        longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
        distance = 5000.0
    }

    @Test
    fun cancelOfASurfaceLessAcquisitionClearsTheWaitAndReleasesTheLease() {
        grantPreciseLocation()
        val client = FakeOSMScoutClient().apply { holdRouteDelivery = true }
        val locationService = LocationService(context())
        injectGpsFix(locationService, 52.5200, 13.4050)
        val engine = buildEngine(client, locationService)

        engine.navigateTo(52.5300, 13.4100)
        awaitState { engine.state.value.calculation != null }
        assertTrue(
            "the acquisition leased GPS for its start position",
            locationService.heldLeaseConsumers().contains(LocationConsumers.NAV_ENGINE)
        )
        val cancelled = awaitCallback(client)

        engine.cancelAcquisition()

        awaitState { engine.state.value.calculation == null }
        assertEquals(1, client.cancelRouteCount)
        assertFalse("no navigation may start", engine.state.value.isNavigating)
        assertTrue(
            "an aborted acquisition must not keep the location updates alive",
            locationService.heldLeaseConsumers().isEmpty()
        )

        // The native breaker is cooperative: the aborted calculation may still report.
        cancelled.onSuccess(routeEntry())
        cancelled.onCancel()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(
            "a late result of the cancelled calculation must not start navigation",
            engine.state.value.isNavigating
        )
        assertNull(engine.state.value.calculation)
        assertEquals(0, client.navigationStartCount)
    }

    @Test
    fun cancelWithNothingRunningChangesNothing() {
        val client = FakeOSMScoutClient()
        val locationService = LocationService(context())
        val engine = buildEngine(client, locationService)

        engine.cancelAcquisition()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("nothing to cancel, nothing to ask native", 0, client.cancelRouteCount)
        assertNull(engine.state.value.calculation)
        assertFalse(engine.state.value.isNavigating)
        assertNull(engine.state.value.errorMessage)
    }

    @Test
    fun cancelOfAnAcquisitionWhileNavigatingKeepsTheLiveGuidanceLease() {
        grantPreciseLocation()
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val locationService = LocationService(context())
        val engine = buildEngine(client, locationService)

        // A phone-style start hands the engine an already calculated route.
        engine.start(routeEntry(), Vehicle.CAR)
        awaitState { engine.state.value.isNavigating }
        assertTrue(
            "navigating holds the navigation lease",
            locationService.heldLeaseConsumers().contains(LocationConsumers.NAV_ENGINE)
        )

        // A second acquisition while navigating (what a reroute looks like) and its cancel.
        injectGpsFix(locationService, 52.5200, 13.4050)
        client.holdRouteDelivery = true
        engine.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)
        awaitState { engine.state.value.calculation != null }

        engine.cancelAcquisition()

        awaitState { engine.state.value.calculation == null }
        assertTrue(
            "guidance is still running — its lease must survive the cancelled acquisition",
            locationService.heldLeaseConsumers().contains(LocationConsumers.NAV_ENGINE)
        )
        assertTrue(engine.state.value.isNavigating)
        assertNull("the cancelled acquisition left no wait behind", engine.state.value.calculation)
    }
}
