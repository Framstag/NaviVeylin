package com.naviveylin.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

/**
 * Tests for the orientation logic in [MapCanvasViewModel]: the marker arrow
 * bearing and the map rotation angle, per orientation mode.
 *
 * Regression guard for the north-up marker bug: the arrow SHALL point in the
 * direction of travel whenever a bearing is available, regardless of the map
 * orientation mode (spec: gps-location-marker). Orientation mode controls only
 * the map rotation (spec: compass-settings).
 */
class OrientationLogicTest {

    // --- computeMarkerBearing: orientation mode must NOT influence the arrow ---

    @Test
    fun northUpKeepsBearing() {
        // North-up + bearing 180° (driving south) → arrow must point south, not north
        assertEquals(180.0, computeMarkerBearing(isNorthUp = true, rawBearing = 180.0), 1e-9)
    }

    @Test
    fun followDirectionKeepsBearing() {
        assertEquals(180.0, computeMarkerBearing(isNorthUp = false, rawBearing = 180.0), 1e-9)
    }

    @Test
    fun northUpKeepsAnyValidBearing() {
        val bearings = listOf(0.0, 45.0, 90.0, 180.0, 270.0, 360.0, -90.0)
        for (bearing in bearings) {
            assertEquals("bearing=$bearing", bearing, computeMarkerBearing(true, bearing), 1e-9)
        }
    }

    @Test
    fun unavailableBearingFallsBackToSentinelInBothModes() {
        // NaN bearing → -1.0 sentinel (arrow points north) in both orientation modes
        assertEquals(-1.0, computeMarkerBearing(true, Double.NaN), 1e-9)
        assertEquals(-1.0, computeMarkerBearing(false, Double.NaN), 1e-9)
    }

    // --- computeMapAngle: north-up keeps 0°, follow-direction rotates to bearing ---

    @Test
    fun northUpProducesZeroAngle() {
        // When north-up is selected, angle must be 0.0 regardless of bearing
        val bearings = listOf(0.0, 45.0, 90.0, 180.0, 270.0, 360.0, -90.0)
        for (bearing in bearings) {
            assertEquals("bearing=$bearing", 0.0, computeMapAngle(true, bearing), 1e-9)
        }
    }

    @Test
    fun followDirectionUsesNegatedBearingRadians() {
        // When follow-direction is selected, angle = -Math.toRadians(bearing)
        val bearing = 90.0
        assertEquals(-PI / 2, computeMapAngle(false, bearing), 1e-9)
    }

    @Test
    fun bearingZeroProducesZeroAngle() {
        // Bearing 0° (north) → angle 0.0
        assertEquals(0.0, computeMapAngle(false, 0.0), 1e-9)
    }

    @Test
    fun bearing180ProducesPi() {
        // Bearing 180° (south) → angle = -PI, normalized to +PI (same 180° rotation;
        // normalizeAngle maps the -PI boundary to +PI)
        assertEquals(PI, computeMapAngle(false, 180.0), 1e-9)
    }

    @Test
    fun bearing360WrapsToZero() {
        // Bearing 360° → angle = -2*PI, normalized to 0
        assertEquals(0.0, computeMapAngle(false, 360.0), 1e-9)
    }

    @Test
    fun negativeBearingStillComputesCorrectly() {
        // Bearing -45° → angle = +PI/4 (negation of negative = positive)
        assertEquals(PI / 4, computeMapAngle(false, -45.0), 1e-9)
    }
}
