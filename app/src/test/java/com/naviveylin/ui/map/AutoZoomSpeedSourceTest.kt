package com.naviveylin.ui.map

import com.naviveylin.core.SpeedZoomTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the speed the phone's auto-zoom is computed from (spec: `auto-speed-zoom` — Auto-zoom
 * speed source per surface).
 *
 * The defect this closes, measured 2026-10-12: the phone read
 * `NavigationViewModel.state.currentSpeedKmH` unconditionally, which is not fed outside navigation,
 * and its `filterSpeed` fell back to a remembered `20.0`; a free drive at 90+ km/h therefore kept the
 * magnification target of 20 km/h (16.0) instead of the highway one (13.0).
 *
 * Plain JUnit: no Robolectric, no device.
 */
class AutoZoomSpeedSourceTest {

    @Test
    fun freeDriveUsesOwnSpeed() {
        // Free driving: the phone's own fix speed decides, whatever the engine's last value was.
        val speed = autoZoomSpeedKmH(isNavigating = false, engineSpeedKmH = 20.0, ownSpeedKmH = 95.0)
        assertEquals(95.0, speed, 1e-9)
        val target = SpeedZoomTable.compute(speed)
        assertTrue("a 95 km/h free drive targets the highway magnification ($target)", target < 14.0)
        assertTrue(
            "and not the 20 km/h target the seed produced (${SpeedZoomTable.compute(20.0)})",
            target < SpeedZoomTable.compute(20.0)
        )
    }

    @Test
    fun navigationUsesEngineSpeed() {
        // Route guidance: the engine's filtered speed decides, not the raw fix speed.
        val speed = autoZoomSpeedKmH(isNavigating = true, engineSpeedKmH = 48.0, ownSpeedKmH = 95.0)
        assertEquals(48.0, speed, 1e-9)
    }

    @Test
    fun unknownSpeedIsNotRemembered() {
        // No speed on either surface: the source stays unknown instead of substituting a value.
        val speed = autoZoomSpeedKmH(isNavigating = false, engineSpeedKmH = Double.NaN, ownSpeedKmH = Double.NaN)
        assertTrue("an unknown speed stays unknown", speed.isNaN())
        // The table — not a remembered seed — owns what an unknown input means.
        val target = SpeedZoomTable.compute(speed)
        assertEquals(
            "an unknown speed follows the table's stationary level",
            SpeedZoomTable.compute(0.0), target, 1e-9
        )
    }
}
