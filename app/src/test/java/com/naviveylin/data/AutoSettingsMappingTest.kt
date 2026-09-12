package com.naviveylin.data

import com.naviveylin.core.AutoSettings
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
}
