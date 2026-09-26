package com.naviveylin.location

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.LocationRequest
import com.naviveylin.core.AccuracyClass
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.LocationGrant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    /**
     * Acquire a lease the way a production consumer does (spec:
     * `location-updates-lease`). The returned lease stays held for the test's
     * lifetime unless the case releases it explicitly.
     */
    private fun LocationService.startForTest(
        consumer: String = LocationConsumers.PHONE_MAP
    ): LocationLease = acquire(consumer)

    @Test
    fun fusedAvailable_requestsFusedOnly_neverLocationManager() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = true)

        service.startForTest()

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

        service.startForTest()

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
        service.startForTest()
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
        service.startForTest()
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

    // --- SpeedSanity wiring (spec: gps-speed-priority — stationary reads zero) ---

    @Test
    fun fusedPath_snapStationaryResidualSpeedToZero() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = true)
        service.startForTest()
        assertTrue("Fused path must be active", service.isFusedActive())

        // Moving fix: 30 km/h passes through sanitized.
        deliver(service, 48.8566, 2.3522, speed = 30.0f, time = 1_000L)
        assertEquals(30.0, service.location.value!!.speedKmH, 1e-2)

        // Stationary (~1 m jitter) with a residual 7 km/h velocity estimate
        // → must snap to 0.
        deliver(service, 48.8566 + (1.0 / 111_320.0), 2.3522, speed = 7.0f, time = 2_000L)
        assertEquals(0.0, service.location.value!!.speedKmH, 1e-9)

        // Real movement (10 m) with low reported speed → stays reported.
        deliver(service, 48.8566 + (10.0 / 111_320.0), 2.3522, speed = 2.0f, time = 3_000L)
        assertEquals(2.0, service.location.value!!.speedKmH, 1e-2)

        // Real movement at speed → passes through.
        deliver(service, 48.8566 + (30.0 / 111_320.0), 2.3522, speed = 50.0f, time = 4_000L)
        assertEquals(50.0, service.location.value!!.speedKmH, 1e-2)
    }

    @Test
    fun fusedPath_unknownSpeedStaysNaN() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = true)
        service.startForTest()
        assertTrue("Fused path must be active", service.isFusedActive())

        deliver(service, 48.8566, 2.3522, speed = 30.0f, time = 1_000L)
        val noSpeed = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.8566 + (1.0 / 111_320.0)
            longitude = 2.3522
            time = 2_000L
            // no speed set → hasSpeed() false
        }
        service.simulateFusedLocation(noSpeed)
        assertTrue("missing speed must stay NaN (native -1.0 contract)", service.location.value!!.speedKmH.isNaN())
    }

    /** Deliver a speed-bearing fix through the active Fused callback. */
    private fun deliver(service: LocationService, lat: Double, lon: Double, speed: Float, time: Long) {
        val fix = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = lat
            longitude = lon
            this.time = time
            this.speed = speed / 3.6f
        }
        service.simulateFusedLocation(fix)
    }

    // --- BearingFilter (spec: gps-bearing-smoothing) ---

    @Test
    fun fusedPath_passesBearingThroughAsMarkerBearing() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = true)
        service.startForTest()
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
        service.startForTest()
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

    // --- Grant class (spec: location-permissions — The granted accuracy class governs provider updates) ---

    private fun grantCoarseOnly() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun denyAllLocationGrants() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    @Test
    @Suppress("DEPRECATION") // PRIORITY_*: the request constants have no non-deprecated form below API 34
    fun fusedPriorityFor_onlyThePreciseGrantBuysHighAccuracy() {
        val service = LocationService(context(), playServicesAvailable = true)

        assertEquals(
            "precise grant → high accuracy",
            LocationRequest.PRIORITY_HIGH_ACCURACY,
            service.fusedPriorityFor(AccuracyClass.PRECISE)
        )
        assertEquals(
            "approximate grant → balanced (minimum scope)",
            LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY,
            service.fusedPriorityFor(AccuracyClass.APPROXIMATE)
        )
        assertEquals(
            "no grant → balanced (updates are not started either way)",
            LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY,
            service.fusedPriorityFor(AccuracyClass.NONE)
        )
    }

    @Test
    fun approximateGrantIsAWorkingState_notAFailureState() {
        grantCoarseOnly()
        val service = LocationService(context(), playServicesAvailable = true)

        assertEquals(AccuracyClass.APPROXIMATE, service.grantedPrecision)
        assertTrue("any grant must pass the provider guard", service.hasPermission)
        assertFalse("only the precise grant passes the navigation gate", LocationGrant.hasPrecise(context()))
    }

    @Test
    fun approximateGrant_fallbackRequestsNoGps_andStillDeliversFixes() {
        grantCoarseOnly()
        val service = LocationService(context(), playServicesAvailable = false)

        service.startForTest()

        val shadowLm = shadowOf(locationManager())
        assertFalse("the fallback path must not be the Fused one", service.isFusedActive())
        assertTrue(
            "GPS requires the precise grant and must not be requested",
            shadowLm.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).isEmpty()
        )
        assertTrue(
            "NETWORK is allowed by the approximate grant",
            shadowLm.getLegacyLocationRequests(LocationManager.NETWORK_PROVIDER).isNotEmpty()
        )
        assertTrue(
            "PASSIVE is allowed by the approximate grant",
            shadowLm.getLegacyLocationRequests(LocationManager.PASSIVE_PROVIDER).isNotEmpty()
        )

        // The map must still follow the vehicle on an approximate-only device.
        shadowLm.simulateLocation(
            Location(LocationManager.NETWORK_PROVIDER).apply {
                latitude = 51.5
                longitude = 7.4
                time = 1_000L
                accuracy = 1_500f
            }
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("approximate fixes must still reach consumers", service.location.value)
    }

    @Test
    fun grantUpgrade_isHonouredOnTheNextUpdateCycle_withoutRestart() {
        grantCoarseOnly()
        val service = LocationService(context(), playServicesAvailable = false)
        val coarseLease = service.startForTest()
        val shadowLm = shadowOf(locationManager())
        assertTrue(
            "an approximate-only start must not request GPS",
            shadowLm.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).isEmpty()
        )

        // The user upgrades the grant in system settings and returns to the app.
        coarseLease.release()
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        service.startForTest()

        assertEquals(AccuracyClass.PRECISE, service.grantedPrecision)
        assertTrue(
            "the next update cycle must use the precise request",
            shadowLm.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).isNotEmpty()
        )
    }

    @Test
    fun noGrant_startsNoProviderUpdates() {
        denyAllLocationGrants()
        val service = LocationService(context(), playServicesAvailable = false)

        service.startForTest()

        assertEquals(AccuracyClass.NONE, service.grantedPrecision)
        assertFalse("no grant must start no provider path", service.isFusedActive())
        val shadowLm = shadowOf(locationManager())
        assertTrue(shadowLm.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
        assertTrue(shadowLm.getLegacyLocationRequests(LocationManager.NETWORK_PROVIDER).isEmpty())
        assertTrue(shadowLm.getLegacyLocationRequests(LocationManager.PASSIVE_PROVIDER).isEmpty())
    }

    // --- Leases (spec: location-updates-lease) ---

    @Test
    fun firstLeaseStartsUpdates_andRepeatedAcquireIsIdempotent() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = true)

        val first = service.startForTest(LocationConsumers.PHONE_MAP)
        val second = service.startForTest(LocationConsumers.PHONE_MAP)

        assertTrue("the provider path must be active", service.isFusedActive())
        assertEquals("one lease for one consumer", 1, service.heldLeaseCount())
        assertEquals("repeated acquire must return the held lease", first, second)
    }

    @Test
    fun oneConsumerReleaseKeepsUpdatesForAnother() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = true)
        val mapLease = service.startForTest(LocationConsumers.PHONE_MAP)
        service.startForTest(LocationConsumers.PHONE_NAV)
        assertEquals(2, service.heldLeaseCount())

        mapLease.release()

        assertTrue(
            "navigation must keep its fixes after the map surface releases",
            service.isFusedActive()
        )
        assertEquals(setOf(LocationConsumers.PHONE_NAV), service.heldLeaseConsumers())
    }

    @Test
    fun lastReleaseStopsUpdates_andDoubleReleaseIsHarmless() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = true)
        val lease = service.startForTest(LocationConsumers.CAR_SESSION)
        assertTrue(service.isFusedActive())

        lease.release()

        assertFalse("the last release must stop the provider path", service.isFusedActive())
        assertEquals(0, service.heldLeaseCount())
        assertTrue(
            "no LocationManager request may remain either",
            shadowOf(locationManager())
                .getLegacyLocationRequests(LocationManager.GPS_PROVIDER).isEmpty()
        )

        // Idempotent: a second release must not throw or change state.
        lease.release()
        assertEquals(0, service.heldLeaseCount())
    }

    @Test
    fun aReleasedConsumerCanLeaseAgain() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = true)
        val lease = service.startForTest(LocationConsumers.CAR_SESSION)
        lease.release()
        assertFalse(service.isFusedActive())

        service.startForTest(LocationConsumers.CAR_SESSION)

        assertTrue("a new lease must restart the provider path", service.isFusedActive())
        assertEquals(1, service.heldLeaseCount())
    }

    @Test
    fun leaseWithoutPermissionHoldsNoProviderRequest() {
        // No permission granted: no provider request, but the lease is tracked so
        // its release cannot stop another consumer's updates.
        val service = LocationService(context(), playServicesAvailable = true)

        val lease = service.startForTest(LocationConsumers.PHONE_NAV)

        assertFalse(service.isFusedActive())
        assertEquals(1, service.heldLeaseCount())

        lease.release()
        assertEquals(0, service.heldLeaseCount())
    }

    @Test
    fun leaseAcquireAndReleaseAreRecordedInTheDiagnosticsStream() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = true)
        val lease = service.startForTest(LocationConsumers.CAR_NAV)
        lease.release()

        val entries = runBlocking { DiagnosticsLog.readEntries() }

        assertTrue(
            "the acquire must name the consumer",
            entries.any { it.contains("location lease acquire: ${LocationConsumers.CAR_NAV}") }
        )
        assertTrue(
            "the release must name the consumer",
            entries.any { it.contains("location lease release: ${LocationConsumers.CAR_NAV}") }
        )
    }
}
