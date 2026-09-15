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
 * delta applies globally to both surfaces). The vehicle anchors are the
 * exception: they are stored PER SURFACE, so a car-side write lands in the
 * car's own fields and the car inherits the phone's value only until it has
 * one of its own (spec: auto-map-layout, change
 * `anchor-per-surface-visible-area`).
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
    fun appToAutoResolvesTheCarAnchorFromThePhoneUntilTheCarHasItsOwn() {
        // No car value yet → the car inherits the phone's value (pre-split behavior,
        // no migration write needed).
        val app = AppSettings(routingAnchorId = "bottom-right", freeDrivingAnchorId = "top-center")
        val auto = app.toAutoSettings()
        assertEquals("bottom-right", auto.routingAnchorId)
        assertEquals("top-center", auto.freeDrivingAnchorId)

        // Once the car has its own value, that value wins.
        val own = app.copy(autoRoutingAnchorId = "middle-far-left", autoFreeDrivingAnchorId = "top-right")
        assertEquals("middle-far-left", own.toAutoSettings().routingAnchorId)
        assertEquals("top-right", own.toAutoSettings().freeDrivingAnchorId)
    }

    @Test
    fun autoToAppWritesOnlyTheCarAnchorFields() {
        // Per-surface split: the car view carries the car's own value, and a generic
        // write-back leaves both the phone's and the car's stored anchors alone (the
        // car's value is written by AutoSettingsProvider.saveCarAnchor).
        val base = AppSettings(routingAnchorId = "bottom-right", freeDrivingAnchorId = "top-center")
        val auto = AutoSettings(routingAnchorId = "bottom-far-left", freeDrivingAnchorId = "top-far-right")
        val app = auto.toAppSettings(base)
        assertEquals("bottom-right", app.routingAnchorId)
        assertEquals("top-center", app.freeDrivingAnchorId)
        assertEquals(null, app.autoRoutingAnchorId)
        assertEquals(null, app.autoFreeDrivingAnchorId)
        // and the car view resolves to what it was shown.
        assertEquals("bottom-right", app.toAutoSettings().routingAnchorId)
        assertEquals("top-center", app.toAutoSettings().freeDrivingAnchorId)
    }

    @Test
    fun genericCarWriteNeverTouchesTheCarAnchorFields() {
        // An unrelated settings write on the car (dark mode) re-submits the resolved
        // value it was shown; that must not freeze the car's own anchor, otherwise a
        // later phone change would stop reaching the car. Freezing happens only in
        // AutoSettingsProvider.saveCarAnchor (anchor picker).
        val base = AppSettings(
            routingAnchorId = "middle-far-right",
            freeDrivingAnchorId = "top-left",
            darkMode = DarkModePreference.ON
        )
        val resolved = base.toAutoSettings()
        val written = resolved.copy(darkMode = DarkModePreference.OFF.name).toAppSettings(base)
        assertEquals(null, written.autoRoutingAnchorId)
        assertEquals(null, written.autoFreeDrivingAnchorId)
        assertEquals("middle-far-right", written.routingAnchorId)
        // The car still follows a later phone change (until an anchor is picked there).
        assertEquals(
            "bottom-center",
            written.copy(routingAnchorId = "bottom-center").toAutoSettings().routingAnchorId
        )
        // Once the car has its own value (written by the picker), the phone no longer
        // influences it.
        val frozen = written.copy(autoRoutingAnchorId = "top-center")
        assertEquals("top-center", frozen.toAutoSettings().routingAnchorId)
        assertEquals(
            "top-center",
            frozen.copy(routingAnchorId = "bottom-left").toAutoSettings().routingAnchorId
        )
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
            // Phone value → car view → back to the phone's own value.
            val app = AppSettings(routingAnchorId = anchor.id, freeDrivingAnchorId = anchor.id)
            val throughAuto = app.toAutoSettings().toAppSettings(app)
            assertEquals(anchor.id, throughAuto.routingAnchorId)
            assertEquals(anchor.id, throughAuto.freeDrivingAnchorId)
            // Car value → car's own fields (frozen by the picker) → back to the car view.
            val auto = AutoSettings(routingAnchorId = anchor.id, freeDrivingAnchorId = anchor.id)
            val carApp = AppSettings(
                autoRoutingAnchorId = auto.routingAnchorId,
                autoFreeDrivingAnchorId = auto.freeDrivingAnchorId
            )
            assertEquals(anchor.id, carApp.autoRoutingAnchorId)
            assertEquals(anchor.id, carApp.autoFreeDrivingAnchorId)
            assertEquals(anchor.id, carApp.toAutoSettings().routingAnchorId)
            assertEquals(anchor.id, carApp.toAutoSettings().freeDrivingAnchorId)
        }
    }
}
