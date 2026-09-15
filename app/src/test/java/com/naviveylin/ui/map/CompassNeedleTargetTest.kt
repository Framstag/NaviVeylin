package com.naviveylin.ui.map

import com.naviveylin.core.ProjectionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JUnit tests for [compassNeedleTarget] — the pure needle-rotation
 * decision (spec: compass-button — Compass shows north direction, North pointer
 * points at rendered north; change compass-always-north-phone).
 *
 * The needle indicates NORTH in every orientation mode. Bearing independence is
 * enforced by the signature: the needle is a function of the map rotation alone,
 * so an unstable or absent GPS bearing cannot move it (the standstill "swirl").
 * No Robolectric needed: the function has no Android dependencies.
 */
class CompassNeedleTargetTest {

    @Test
    fun `needle points at north for the current map rotation`() {
        // North-up map: north is straight up.
        assertEquals(0.0, compassNeedleTarget(0.0), 1e-10)
        // θ = +90°: north renders a quarter turn clockwise — to the driver's right.
        assertEquals(90.0, compassNeedleTarget(Math.toRadians(90.0)), 1e-10)
        // θ = −90° ≡ +270°: north to the driver's left.
        assertEquals(270.0, compassNeedleTarget(Math.toRadians(-90.0)), 1e-10)
    }

    @Test
    fun `heading-up southbound puts north behind the vehicle`() {
        // Driving south (bearing 180) in heading-up follow stores θ = −180 ≡ 180°,
        // so north points down — behind the vehicle. This is the reported case.
        assertEquals(180.0, compassNeedleTarget(Math.toRadians(-180.0)), 1e-10)
        // Driving north: θ = 0 → north straight up (in front).
        assertEquals(0.0, compassNeedleTarget(0.0), 1e-10)
    }

    @Test
    fun `heading-up eastbound puts north on the driver's left`() {
        // Driving east (bearing 90): θ = −90 ≡ 270 → needle left.
        assertEquals(270.0, compassNeedleTarget(Math.toRadians(-90.0)), 1e-10)
    }

    @Test
    fun `manually rotated map keeps the north needle on the map north`() {
        // Follow direction with a manual rotation of +30° beyond heading-up
        // (westbound): θ = −270 + 30 = −240 ≡ 120° on screen. The vehicle bearing is
        // not an input, so the needle cannot follow the travel direction instead.
        val mapAngle = Math.toRadians(-270.0 + 30.0)
        assertEquals(120.0, compassNeedleTarget(mapAngle), 1e-10)
    }

    @Test
    fun `normalization table matches compassRotationDegrees`() {
        listOf(0.0, 45.0, -45.0, 90.0, 180.0, -90.0, 270.0, 450.0, -450.0).forEach { deg ->
            val angle = Math.toRadians(deg)
            val target = compassNeedleTarget(angle)
            assertEquals("angle $deg", ProjectionUtils.compassRotationDegrees(angle), target, 1e-10)
            assertTrue("angle $deg must be in [0,360)", target >= 0.0 && target < 360.0)
            assertTrue("angle $deg must not be NaN", !target.isNaN())
        }
    }

    @Test
    fun `needle is independent of the vehicle bearing`() {
        // Compile-time guarantee: compassNeedleTarget takes no bearing argument, so
        // two situations that differ only in the (noisy, standstill or absent)
        // vehicle bearing produce the same needle for the same map rotation. This
        // test pins the API shape the behavior depends on: one parameter, and the
        // value equals the map-north direction.
        val mapAngle = Math.toRadians(-270.0)
        assertEquals(90.0, compassNeedleTarget(mapAngle), 1e-10)
        assertEquals(
            "needle must be the map-north direction regardless of driving direction",
            ProjectionUtils.compassRotationDegrees(mapAngle),
            compassNeedleTarget(mapAngle),
            0.0
        )
    }
}
