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
        assertEquals(fix.latitude, service.location.value!!.lat, 1e-9)
        assertEquals(fix.longitude, service.location.value!!.lon, 1e-9)

        // Same fix delivered again → dropped by shouldEmit.
        val duplicate = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.8566
            longitude = 2.3522
            time = 1000L
        }
        service.simulateFusedLocation(duplicate)
        assertEquals("duplicate fix must be dropped", fix.latitude, service.location.value!!.lat, 1e-9)
        assertEquals("duplicate fix must be dropped", fix.longitude, service.location.value!!.lon, 1e-9)
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
        assertEquals(fix.latitude, service.location.value!!.lat, 1e-9)
        assertEquals(fix.longitude, service.location.value!!.lon, 1e-9)

        // Same underlying fix delivered by a second provider (same time + position).
        val duplicate = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = 48.8566
            longitude = 2.3522
            time = 1000L
            elapsedRealtimeNanos = 1_000_000_000L
        }
        shadowLm.simulateLocation(duplicate)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("duplicate fix must be dropped", fix.latitude, service.location.value!!.lat, 1e-9)
        assertEquals("duplicate fix must be dropped", fix.longitude, service.location.value!!.lon, 1e-9)

        // Distinct fix (moved position, new timestamp) must pass through.
        val moved = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.8567
            longitude = 2.3523
            time = 2000L
            elapsedRealtimeNanos = 2_000_000_000L
        }
        shadowLm.simulateLocation(moved)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(moved.latitude, service.location.value!!.lat, 1e-9)
        assertEquals(moved.longitude, service.location.value!!.lon, 1e-9)
    }

    // --- BearingFilter (spec: gps-bearing-smoothing) ---

    @Test
    fun fusedPath_passesBearingThroughAsMarkerBearing() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = true)
        service.startLocationUpdates()
        assertTrue("Fused path must be active", service.isFusedActive())

        val fix = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.8566
            longitude = 2.3522
            bearing = 45f
            time = 1000L
        }
        service.simulateFusedLocation(fix)
        val emitted = service.location.value!!
        assertEquals("marker bearing must equal the Fused bearing (no added lag)", 45.0, emitted.markerBearing, 1e-9)
        assertEquals("smoothed bearing must start at the Fused bearing", 45.0, emitted.smoothedBearing, 1e-9)
    }

    @Test
    fun fusedPath_smoothedBearingLightlyFiltered_markerStaysRaw() {
        val filter = BearingFilter(BearingSource.FUSED)
        val (s1, m1) = filter.process(0.0, 0.0, 45.0, true)
        assertEquals(45.0, s1, 1e-9)
        assertEquals(45.0, m1, 1e-9)

        // 47° → smoothed moves halfway (alpha 0.5) → 46°; marker stays raw 47°.
        val (s2, m2) = filter.process(0.0, 0.0, 47.0, true)
        assertEquals(46.0, s2, 1e-9)
        assertEquals("marker must stay raw", 47.0, m2, 1e-9)
    }

    @Test
    fun managerPath_derivesSmoothedBearingFromStraightTrack() {
        val filter = BearingFilter(BearingSource.MANAGER)
        // Track heading north (lat increases, lon constant).
        var lat = 51.0
        val lon = 7.0
        var smoothed = Double.NaN
        for (i in 0 until 8) {
            val (s, _) = filter.process(lat, lon, -1.0, false)
            if (!s.isNaN()) smoothed = s
            lat += 0.001 // ~111 m north per step
        }
        assertTrue("smoothed bearing must be available", !smoothed.isNaN())
        assertEquals("north track → bearing ~0", 0.0, smoothed, 5.0)
    }

    @Test
    fun managerPath_turnResetClearsHistory() {
        val filter = BearingFilter(BearingSource.MANAGER)
        // Straight north track.
        var lat = 51.0
        val lon = 7.0
        for (i in 0 until 8) {
            filter.process(lat, lon, -1.0, false)
            lat += 0.001
        }
        // Sharp turn east: lat constant, lon increases.
        var eastLon = lon
        var smoothed = Double.NaN
        for (i in 0 until 8) {
            val (s, _) = filter.process(lat, eastLon, -1.0, false)
            if (!s.isNaN()) smoothed = s
            eastLon += 0.001
        }
        assertTrue("smoothed bearing must be available after the turn", !smoothed.isNaN())
        assertEquals("after a sharp turn → bearing ~90", 90.0, smoothed, 5.0)
    }

    @Test
    fun managerPath_turnResetKeepsFreshSegmentAsMarkerBearing() {
        val filter = BearingFilter(BearingSource.MANAGER)
        // Straight north track.
        var lat = 51.0
        val lon = 7.0
        for (i in 0 until 8) {
            filter.process(lat, lon, -1.0, false)
            lat += 0.001
        }
        // Sharp turn east: the segment that triggers the reset IS the new direction
        // and must be kept as the marker bearing — the marker points the new way at
        // the turn fix itself instead of falling back to the old direction.
        // Last point added was (51.007, 7.0); turn east from there: (51.007, 7.001).
        val (_, marker) = filter.process(51.007, 7.001, -1.0, false)
        assertEquals("marker must point the new direction at the turn fix", 90.0, marker, 5.0)
    }

    @Test
    fun managerPath_teleportResetClearsHistory() {
        val filter = BearingFilter(BearingSource.MANAGER)
        // Straight north track.
        var lat = 51.0
        val lon = 7.0
        for (i in 0 until 8) {
            filter.process(lat, lon, -1.0, false)
            lat += 0.001
        }
        // Teleport ~700 km east → history must reset, course restarts.
        val (sAfterJump, _) = filter.process(51.0, 17.0, -1.0, false)
        assertTrue("course must be cleared after a teleport", sAfterJump.isNaN())
        // Rebuild the course eastward.
        var eastLon = 17.0
        var smoothed = Double.NaN
        for (i in 0 until 8) {
            val (s, _) = filter.process(51.0, eastLon, -1.0, false)
            if (!s.isNaN()) smoothed = s
            eastLon += 0.001
        }
        assertTrue("smoothed bearing must rebuild after the teleport", !smoothed.isNaN())
        assertEquals("east track → bearing ~90", 90.0, smoothed, 5.0)
    }

    @Test
    fun managerPath_derivesBearingFromRawProviderPositions() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = false)
        service.startLocationUpdates()
        val shadowLm = shadowOf(locationManager())

        // Raw provider track heading north (lat increases, lon constant). The nav
        // engine would snap these fixes to the route — the bearing filter must use
        // the raw positions as delivered (LocationService has no nav-position input),
        // not any route-matched position.
        var lat = 51.0
        val lon = 7.0
        var time = 1_000L
        var smoothed = Double.NaN
        for (i in 0 until 8) {
            val fix = Location(LocationManager.GPS_PROVIDER).apply {
                this.latitude = lat
                this.longitude = lon
                time = time
                elapsedRealtimeNanos = time * 1_000_000L
            }
            shadowLm.simulateLocation(fix)
            shadowOf(Looper.getMainLooper()).idle()
            val emitted = service.location.value
            if (emitted != null && !emitted.smoothedBearing.isNaN()) smoothed = emitted.smoothedBearing
            lat += 0.001
            time += 1_000L
        }
        assertTrue("smoothed bearing must be derived from the raw track", !smoothed.isNaN())
        assertEquals("north raw track → bearing ~0", 0.0, smoothed, 5.0)
    }
}
