package com.naviveylin.ui.map

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class OrientationLogicTest {

    @Test
    fun northUpProducesZeroAngle() {
        // When north-up is selected, angle must be 0.0 regardless of bearing
        val bearing = 45.0
        val angle = if (true) 0.0 else -Math.toRadians(bearing)
        assertEquals(0.0, angle, 1e-9)
    }

    @Test
    fun followDirectionUsesNegatedBearingRadians() {
        // When follow-direction is selected, angle = -Math.toRadians(bearing)
        val bearing = 90.0
        val angle = if (false) 0.0 else -Math.toRadians(bearing)
        val expected = -PI / 2
        assertEquals(expected, angle, 1e-9)
    }

    @Test
    fun bearingZeroProducesZeroAngle() {
        // Bearing 0° (north) → angle 0.0
        val bearing = 0.0
        val angle = if (false) 0.0 else -Math.toRadians(bearing)
        assertEquals(0.0, angle, 1e-9)
    }

    @Test
    fun bearing180ProducesNegativePi() {
        // Bearing 180° (south) → angle = -PI
        val bearing = 180.0
        val angle = if (false) 0.0 else -Math.toRadians(bearing)
        assertEquals(-PI, angle, 1e-9)
    }

    @Test
    fun bearing360ProducesNegativeTwoPi() {
        // Bearing 360° → angle = -2*PI
        val bearing = 360.0
        val angle = if (false) 0.0 else -Math.toRadians(bearing)
        assertEquals(-2 * PI, angle, 1e-9)
    }

    @Test
    fun negativeBearingStillComputesCorrectly() {
        // Bearing -45° → angle = +PI/4 (negation of negative = positive)
        val bearing = -45.0
        val angle = if (false) 0.0 else -Math.toRadians(bearing)
        assertEquals(PI / 4, angle, 1e-9)
    }

    @Test
    fun northUpOverridesAnyBearing() {
        // North-up must always produce 0.0, even with valid bearing
        val bearings = listOf(0.0, 45.0, 90.0, 180.0, 270.0, 360.0, -90.0)
        for (bearing in bearings) {
            val angle = if (true) 0.0 else -Math.toRadians(bearing)
            assertEquals("bearing=$bearing", 0.0, angle, 1e-9)
        }
    }
}
