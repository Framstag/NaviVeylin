package com.naviveylin.data

import com.naviveylin.core.AutoSettings
import com.naviveylin.core.VehicleAnchorPosition
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the shared-settings mapping between the phone app's [AppSettings]
 * and the Android Auto process's [AutoSettings]: every car-relevant field —
 * including the overspeed warning delta — survives a full round trip in both
 * directions (spec: auto-map-layout — settings shared with the phone; the
 * delta applies globally to both surfaces).
 */
class AutoSettingsMappingTest {

    @Test
    fun appToAutoCopiesOverspeedWarningDelta() {
        val app = AppSettings(overspeedWarningDeltaKmh = 12)
        val auto = app.toAutoSettings()
        assertEquals(12, auto.overspeedWarningDeltaKmh)
    }

    @Test
    fun autoToAppCopiesOverspeedWarningDelta() {
        val base = AppSettings()
        val auto = AutoSettings(overspeedWarningDeltaKmh = 3)
        val app = auto.toAppSettings(base)
        assertEquals(3, app.overspeedWarningDeltaKmh)
    }

    @Test
    fun defaultDeltaRoundTripsUnchanged() {
        val app = AppSettings()
        assertEquals(5, app.overspeedWarningDeltaKmh)
        assertEquals(5, app.toAutoSettings().overspeedWarningDeltaKmh)
        assertEquals(5, app.toAutoSettings().toAppSettings(app).overspeedWarningDeltaKmh)
    }

    @Test
    fun appToAutoPreservesDeltaWithOtherFields() {
        val app = AppSettings(followMode = true, autoZoomEnabled = false, overspeedWarningDeltaKmh = 30)
        val auto = app.toAutoSettings()
        assertEquals(true, auto.followMode)
        assertEquals(false, auto.autoZoomEnabled)
        assertEquals(30, auto.overspeedWarningDeltaKmh)
    }

    @Test
    fun autoToAppPreservesPhoneOnlyFields() {
        val base = AppSettings(keepScreenOn = false, ambientLightSensitivity = AmbientLightSensitivity.HIGH, followMode = true)
        val auto = AutoSettings(followMode = false, overspeedWarningDeltaKmh = 0)
        val app = auto.toAppSettings(base)
        // Fields the car never edits survive the mapping untouched.
        assertEquals(false, app.keepScreenOn)
        assertEquals(AmbientLightSensitivity.HIGH, app.ambientLightSensitivity)
        assertEquals(false, app.followMode)
        assertEquals(0, app.overspeedWarningDeltaKmh)
    }

    @Test
    fun appToAutoCopiesVehicleAnchors() {
        val app = AppSettings(routingAnchorId = "bottom-right", freeDrivingAnchorId = "top-center")
        val auto = app.toAutoSettings()
        assertEquals("bottom-right", auto.routingAnchorId)
        assertEquals("top-center", auto.freeDrivingAnchorId)
    }

    @Test
    fun autoToAppCopiesVehicleAnchors() {
        val base = AppSettings()
        val auto = AutoSettings(routingAnchorId = "bottom-far-left", freeDrivingAnchorId = "top-far-right")
        val app = auto.toAppSettings(base)
        assertEquals("bottom-far-left", app.routingAnchorId)
        assertEquals("top-far-right", app.freeDrivingAnchorId)
    }

    @Test
    fun defaultAnchorsRoundTripUnchanged() {
        val app = AppSettings()
        assertEquals(VehicleAnchorPosition.DEFAULT.id, app.routingAnchorId)
        assertEquals(VehicleAnchorPosition.DEFAULT.id, app.freeDrivingAnchorId)
        assertEquals(VehicleAnchorPosition.DEFAULT.id, app.toAutoSettings().routingAnchorId)
        assertEquals(
            VehicleAnchorPosition.DEFAULT.id,
            app.toAutoSettings().toAppSettings(app).freeDrivingAnchorId
        )
    }

    @Test
    fun everyAnchorIdSurvivesRoundTripInBothDirections() {
        for (anchor in VehicleAnchorPosition.entries) {
            val app = AppSettings(routingAnchorId = anchor.id, freeDrivingAnchorId = anchor.id)
            val throughAuto = app.toAutoSettings().toAppSettings(app)
            assertEquals(anchor.id, throughAuto.routingAnchorId)
            assertEquals(anchor.id, throughAuto.freeDrivingAnchorId)
            val auto = AutoSettings(routingAnchorId = anchor.id, freeDrivingAnchorId = anchor.id)
            assertEquals(anchor.id, auto.toAppSettings(AppSettings()).routingAnchorId)
            // And back through the car-relevant subset.
            assertEquals(anchor.id, auto.toAppSettings(AppSettings()).toAutoSettings().freeDrivingAnchorId)
        }
    }
}
