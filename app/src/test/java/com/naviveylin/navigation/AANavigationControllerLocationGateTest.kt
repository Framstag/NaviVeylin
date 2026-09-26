package com.naviveylin.navigation

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.core.NavigationState
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The car surface's half of the navigation gate (spec: `location-permissions` —
 * Starting navigation requires precise location / Car shows the message without
 * launching settings): a car-only session has no phone UI, so the refusal has to
 * appear on the shared state the car session already renders, and nothing may be
 * sent to the routing engine.
 *
 * Default Robolectric sandbox (no `@Config`) — this class instantiates
 * [FakeOSMScoutClient] and therefore loads the JNI stub, per the AGENTS.md
 * classloader rule.
 */
@RunWith(RobolectricTestRunner::class)
class AANavigationControllerLocationGateTest {

    private fun app(): Application = ApplicationProvider.getApplicationContext()

    private fun buildController(
        client: FakeOSMScoutClient,
        locationService: LocationService
    ): AANavigationController =
        AANavigationController(client, NavigationStateProvider(), locationService, app())

    private fun locationServiceWithFix(): LocationService =
        LocationService(app()).apply {
            setGpsFixForTest(
                GpsFix(52.5200, 13.4050, 10.0, 50.0, Double.NaN, Double.NaN, System.currentTimeMillis())
            )
        }

    private fun awaitState(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("State condition not met within 5s")
    }

    @Test
    fun approximateGrant_publishesTheRefusalWithoutReachingTheEngine() {
        val app = app()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val client = FakeOSMScoutClient()
        val locationService = locationServiceWithFix()
        val controller = buildController(client, locationService)

        controller.navigateTo(52.5300, 13.4100, "Destination")

        assertEquals("no route request may reach the engine", 0, client.routeCalculationCount)
        assertFalse(controller.state.value.isNavigating)
        assertEquals(
            "the notice uses the same wording as the phone dialog",
            app.getString(com.naviveylin.core.R.string.location_precise_required_navigation),
            controller.state.value.errorMessage
        )
        assertTrue(
            "a refused route must not lease GPS",
            locationService.heldLeaseConsumers().isEmpty()
        )
    }

    @Test
    fun noGrant_publishesTheRefusal() {
        val app = app()
        shadowOf(app).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val client = FakeOSMScoutClient()
        val controller = buildController(client, LocationService(app))

        controller.navigateTo(52.5300, 13.4100, null)

        assertEquals(0, client.routeCalculationCount)
        assertNotNull(controller.state.value.errorMessage)
    }

    @Test
    fun preciseGrant_calculatesTheRoute() {
        val app = app()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val client = FakeOSMScoutClient().apply {
            routeToDeliver = RouteEntry().apply {
                routeHandle = 1L
                latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
                longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
                distance = 5000.0
            }
        }
        val controller = buildController(client, locationServiceWithFix())

        controller.navigateTo(52.5300, 13.4100, "Destination")

        awaitState { controller.state.value.isNavigating }
        assertEquals(1, client.routeCalculationCount)
        assertEquals(NavigationState().errorMessage, controller.state.value.errorMessage)
    }
}
