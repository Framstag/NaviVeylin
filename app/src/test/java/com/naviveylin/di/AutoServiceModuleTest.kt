package com.naviveylin.di

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Verifies the Android Auto location provider mapping (spec:
 * gps-bearing-smoothing — Car map uses smoothed bearing): [AutoServiceModule]
 * maps a [GpsFix] to an [com.naviveylin.core.AutoPosition] with bearing =
 * `smoothedBearing`, so the car map rotation does not churn.
 */
@RunWith(RobolectricTestRunner::class)
class AutoServiceModuleTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun grantLocationPermission() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Test
    fun autoLocationProvider_mapsGpsFixToAutoPosition_withSmoothedBearing() {
        grantLocationPermission()
        val service = LocationService(context(), playServicesAvailable = true)
        val provider = AutoServiceModule.provideAutoLocationProvider(service)
        provider.start()

        service.setGpsFixForTest(
            GpsFix(
                lat = 51.5136,
                lon = 7.4653,
                accuracy = 8.0,
                speedKmH = 36.0,
                smoothedBearing = 45.0,
                markerBearing = 90.0,
                time = 1_000L
            )
        )

        val pos = runBlocking { provider.position().first { it != null } }!!
        assertEquals(51.5136, pos.lat, 1e-9)
        assertEquals(7.4653, pos.lon, 1e-9)
        assertEquals("car bearing must be the smoothed bearing, not the marker bearing", 45.0, pos.bearing, 1e-9)
        assertEquals(8.0, pos.accuracy, 1e-9)
        assertEquals(36.0, pos.speedKmH, 1e-9)

        provider.stop()
    }
}
