package com.naviveylin.location

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Verifies the strict provider fallback in [LocationService] (spec:
 * gps-provider-selection): Fused is the sole source when Play Services is
 * available, LocationManager is the sole source otherwise, and the two never
 * run simultaneously. Also verifies the duplicate-fix filter on the
 * LocationManager-only path.
 *
 * These tests do not touch OSMScoutClient statics, so they are free of the
 * FakeOSMScoutClient classloader constraint (see AGENTS.md).
 */
@RunWith(RobolectricTestRunner::class)
class LocationServiceTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun grantLocationPermission() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun locationManager(): LocationManager =
        context().getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Test
    fun fusedAvailable_requestsFusedOnly_neverLocationManager() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = true)

        service.startLocationUpdates()

        assertTrue("Fused path must be active", service.isFusedActive())
        val shadowLm = shadowOf(locationManager())
        assertTrue(
            "GPS must not be requested via LocationManager",
            shadowLm.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).isEmpty()
        )
        assertTrue(
            "NETWORK must not be requested via LocationManager",
            shadowLm.getLegacyLocationRequests(LocationManager.NETWORK_PROVIDER).isEmpty()
        )
        assertTrue(
            "PASSIVE must not be requested via LocationManager",
            shadowLm.getLegacyLocationRequests(LocationManager.PASSIVE_PROVIDER).isEmpty()
        )
    }

    @Test
    fun fusedUnavailable_requestsLocationManagerProviders_only() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = false)

        service.startLocationUpdates()

        assertFalse("Fused path must not be active", service.isFusedActive())
        val shadowLm = shadowOf(locationManager())
        assertTrue(
            "GPS must be requested via LocationManager",
            shadowLm.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).isNotEmpty()
        )
        assertTrue(
            "NETWORK must be requested via LocationManager",
            shadowLm.getLegacyLocationRequests(LocationManager.NETWORK_PROVIDER).isNotEmpty()
        )
        assertTrue(
            "PASSIVE must be requested via LocationManager",
            shadowLm.getLegacyLocationRequests(LocationManager.PASSIVE_PROVIDER).isNotEmpty()
        )
    }

    @Test
    fun fusedFixReachesConsumers_duplicateDropped() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = true)
        service.startLocationUpdates()
        assertTrue("Fused path must be active", service.isFusedActive())

        val fix = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.8566
            longitude = 2.3522
            time = 1000L
        }
        service.simulateFusedLocation(fix)
        assertEquals(fix.latitude, service.location.value!!.latitude, 1e-9)
        assertEquals(fix.longitude, service.location.value!!.longitude, 1e-9)

        // Same fix delivered again → dropped by shouldEmit.
        val duplicate = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.8566
            longitude = 2.3522
            time = 1000L
        }
        service.simulateFusedLocation(duplicate)
        assertEquals("duplicate fix must be dropped", fix.latitude, service.location.value!!.latitude, 1e-9)
        assertEquals("duplicate fix must be dropped", fix.longitude, service.location.value!!.longitude, 1e-9)
    }

    @Test
    fun duplicateFixDropped_distinctFixPasses() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = false)
        service.startLocationUpdates()
        val shadowLm = shadowOf(locationManager())

        val fix = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.8566
            longitude = 2.3522
            time = 1000L
            // ShadowLocationManager filters simulated deliveries by
            // elapsedRealtimeNanos (min interval 1000 ms) and distance.
            elapsedRealtimeNanos = 1_000_000_000L
        }
        shadowLm.simulateLocation(fix)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(fix.latitude, service.location.value!!.latitude, 1e-9)
        assertEquals(fix.longitude, service.location.value!!.longitude, 1e-9)

        // Same underlying fix delivered by a second provider (same time + position).
        val duplicate = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = 48.8566
            longitude = 2.3522
            time = 1000L
            elapsedRealtimeNanos = 1_000_000_000L
        }
        shadowLm.simulateLocation(duplicate)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("duplicate fix must be dropped", fix.latitude, service.location.value!!.latitude, 1e-9)
        assertEquals("duplicate fix must be dropped", fix.longitude, service.location.value!!.longitude, 1e-9)

        // Distinct fix (moved position, new timestamp) must pass through.
        val moved = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.8567
            longitude = 2.3523
            time = 2000L
            elapsedRealtimeNanos = 2_000_000_000L
        }
        shadowLm.simulateLocation(moved)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(moved.latitude, service.location.value!!.latitude, 1e-9)
        assertEquals(moved.longitude, service.location.value!!.longitude, 1e-9)
    }
}
