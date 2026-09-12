package com.naviveylin.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plain-JUnit tests for [compassNeedleTarget] — the pure needle-rotation
 * decision (spec: compass-button — north needle points at rendered north;
 * follow triangle points at the travel direction). No Robolectric needed:
 * the function has no Android dependencies.
 */
class CompassNeedleTargetTest {

    @Test
    fun `north-up needle points at map north`() {
        // θ = +90° (map rotated a quarter turn): north renders to the right.
        assertEquals(90.0, compassNeedleTarget(true, null, Math.toRadians(90.0)), 1e-10)
        // θ = 0: north-up map, needle straight up.
        assertEquals(0.0, compassNeedleTarget(true, 123.0, 0.0), 1e-10)
    }

    @Test
    fun `follow needle points up while heading-up`() {
        // Follow mode stores θ = −bearing; bearing + θ = 0 → straight up.
        val bearing = 123.0
        assertEquals(0.0, compassNeedleTarget(false, bearing, -Math.toRadians(bearing)), 1e-10)
    }

    @Test
    fun `westbound follow points up while heading-up`() {
        // Heading 270° (west), θ = −270°: follow needle = travel direction on
        // screen = bearing + θ = 0 → straight up (the map is rotated heading-up).
        // (The north POSITION on the rotated map is compassRotationDegrees(θ)
        // = 90° — the driver's right — pinned in ProjectionUtilsTest.)
        val target = compassNeedleTarget(false, 270.0, Math.toRadians(-270.0))
        assertEquals(0.0, target, 1e-10)
    }

    @Test
    fun `eastbound follow points up while heading-up`() {
        // Heading 90° (east), θ = −90°: follow needle → bearing + θ = 0 → up.
        val target = compassNeedleTarget(false, 90.0, Math.toRadians(-90.0))
        assertEquals(0.0, target, 1e-10)
    }

    @Test
    fun `follow needle follows travel direction after manual rotation`() {
        // User rotated the map +30° beyond heading-up: travel direction on
        // screen = bearing + θ = 270 + (−270 + 30) = 30°.
        val bearing = 270.0
        val mapAngle = Math.toRadians(-270.0 + 30.0)
        assertEquals(30.0, compassNeedleTarget(false, bearing, mapAngle), 1e-10)
    }

    @Test
    fun `unknown bearing falls back to map north`() {
        val mapAngle = Math.toRadians(-270.0)
        assertEquals(
            "null bearing must fall back",
            90.0, compassNeedleTarget(false, null, mapAngle), 1e-10
        )
        assertEquals(
            "NaN bearing must fall back",
            90.0, compassNeedleTarget(false, Double.NaN, mapAngle), 1e-10
        )
        assertEquals(
            "negative bearing must fall back",
            90.0, compassNeedleTarget(false, -1.0, mapAngle), 1e-10
        )
    }
}
