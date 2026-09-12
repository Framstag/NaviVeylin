package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import com.naviveylin.core.ProjectionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [SurfaceIndicators] (right-edge visualisation indicators: rotating
 * compass rose + speed-limit badge, anchored to the host stable area).
 */
@RunWith(RobolectricTestRunner::class)
class SurfaceIndicatorsTest {

    private val density = 2.0f

    @Test
    fun roseCenteredAboveSpeedBadge() {
        // The compass rose is horizontally centered above the speed badge
        // (spec: auto/free-driving — compass centered above the speed view).
        val stable = Rect(320, 240, 1600, 900)
        val g = SurfaceIndicators.geometry(stable, 1920, density, showSpeed = true)
        val badge = g.badgeRect!!
        assertEquals(badge.exactCenterX(), g.compassCenterX, 0.01f)
        // Badge sits at the right edge with the 8 dp margin.
        assertEquals(1920f - 8f * density, badge.right.toFloat(), 0.01f)
        // Rose above the badge, inside the stable area top.
        assertTrue(g.compassCenterY + g.compassRadius <= badge.top)
        assertTrue(g.compassCenterY - g.compassRadius >= stable.top)
    }

    @Test
    fun speedBadgeShowsCurrentSpeedWhenNoLimit() {
        // Free driving: no speed-limit data — the badge shows the current
        // speed (spec: auto/free-driving — "Current driving speed shown").
        val g = SurfaceIndicators.geometry(Rect(), 1920, density, showSpeed = true)
        assertTrue(g.badgeRect != null)
        // Below the rose.
        assertTrue(g.badgeRect!!.top >= g.compassCenterY + g.compassRadius)
    }

    @Test
    fun noBadgeWhenNoSpeed() {
        val g = SurfaceIndicators.geometry(Rect(), 1920, density, showSpeed = false)
        assertNull(g.badgeRect)
    }

    @Test
    fun speedLimitSignBelowBadge() {
        // The speed-limit sign sits below the speed badge, centered on the
        // same axis (spec: auto/free-driving — "Speed limit sign").
        val g = SurfaceIndicators.geometry(Rect(), 1920, density, showSpeed = true, showSpeedLimit = true)
        val badge = g.badgeRect!!
        val limit = g.speedLimitRect!!
        assertTrue(limit.top >= badge.bottom)
        assertEquals(badge.exactCenterX(), limit.exactCenterX(), 0.01f)
    }

    @Test
    fun noSpeedLimitSignWhenHidden() {
        val g = SurfaceIndicators.geometry(Rect(), 1920, density, showSpeed = true, showSpeedLimit = false)
        assertNull(g.speedLimitRect)
    }

    @Test
    fun roseAtLeast56dp() {
        // Spec: auto-map-layout — the compass rose is 56 dp during navigation.
        val g = SurfaceIndicators.geometry(Rect(), 1920, density, showSpeed = true)
        val expected = 56f * density / 2f
        assertTrue(
            "rose radius must be >= 56dp/2 (was ${g.compassRadius})",
            g.compassRadius >= expected - 0.01f
        )
    }

    @Test
    fun speedLimitSignAtLeast56dp() {
        // Spec: auto-map-layout — the speed-limit sign is at least 56 dp.
        val g = SurfaceIndicators.geometry(Rect(), 1920, density, showSpeed = true, showSpeedLimit = true)
        val limit = g.speedLimitRect!!
        val expected = 56f * density
        assertTrue(
            "limit sign must be >= 56dp (was ${limit.width()}px)",
            limit.width() >= expected - 1f && limit.height() >= expected - 1f
        )
    }

    @Test
    fun drawSpeedLimitSignDoesNotFail() {
        val canvas = Canvas(Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888))
        SurfaceIndicators.draw(
            canvas, 1920, 1080, Rect(0, 0, 1920, 1080), density, angleRadians = 0.0,
            currentKmH = 50.0, maxKmH = 30.0, drawSpeedLimitSign = true
        )
    }

    @Test
    fun drawDoesNotFail() {
        val canvas = Canvas(Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888))
        SurfaceIndicators.draw(canvas, 1920, 1080, Rect(0, 0, 1920, 1080), density, angleRadians = 1.2)
        SurfaceIndicators.draw(
            canvas, 1920, 1080, Rect(0, 0, 1920, 1080), density, angleRadians = 0.0,
            currentKmH = 95.0, maxKmH = 80.0
        )
        SurfaceIndicators.draw(
            canvas, 1920, 1080, Rect(0, 0, 1920, 1080), density, angleRadians = 0.0,
            currentKmH = 50.0, maxKmH = 80.0
        )
    }

    @Test
    fun drawIgnoresUnknownNegativeSpeeds() {
        // The native engine reports -1 when speed/limit are unknown — the
        // badge and sign must render nothing, never a "-1 km/h" (regression
        // guard for the unknown-speed convention).
        val canvas = Canvas(Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888))
        SurfaceIndicators.draw(
            canvas, 1920, 1080, Rect(0, 0, 1920, 1080), density, angleRadians = 0.0,
            currentKmH = -1.0, maxKmH = -1.0, drawSpeedLimitSign = true
        )
        // Unknown limit must not render the limit sign either.
        SurfaceIndicators.draw(
            canvas, 1920, 1080, Rect(0, 0, 1920, 1080), density, angleRadians = 0.0,
            currentKmH = 50.0, maxKmH = -1.0, drawSpeedLimitSign = true
        )
    }

    @Test
    fun roseRotationPointsAtRenderedNorth() {
        // Spec: auto-map-layout / auto/free-driving — the rose north pointer
        // faces true north on the head-unit screen. drawRose rotates by the
        // shared core convention (ProjectionUtils.compassRotationDegrees); the
        // same function feeds the phone compass (spec: compass-button), so a
        // sign drift is structurally impossible. Pin the heading-up cases:
        // westbound (bearing 270°, θ = −270° ≡ +90°) → north pointer at 90°
        // (driver's right); eastbound (θ = −90° ≡ +270°) → 270° (driver's left).
        assertEquals(90.0, ProjectionUtils.compassRotationDegrees(Math.toRadians(-270.0)), 1e-10)
        assertEquals(270.0, ProjectionUtils.compassRotationDegrees(Math.toRadians(-90.0)), 1e-10)

        // Smoke: the rose draws with a westbound heading-up angle (the rotation
        // path through canvas.rotate must not throw with the same input that
        // FreeDrivingScreen feeds it — headingRadians = -bearing).
        val canvas = Canvas(Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888))
        SurfaceIndicators.draw(
            canvas, 1920, 1080, Rect(0, 0, 1920, 1080), density,
            angleRadians = Math.toRadians(-270.0)
        )
    }

    @Test
    fun stationaryZeroShowsBadgeWithZeroKmh() {
        // Standstill zeroing (spec: gps-speed-priority — stationary reads 0)
        // must keep the badge visible and render "0 km/h" — the road's limit
        // must NOT appear in the badge at halt (regression: the old
        // "currentKmH > 0.0" guard fell through to the maxKmH fallback and
        // showed the speed limit as the current speed at standstill).
        assertTrue(SurfaceIndicators.shouldShowSpeedBadge(0.0, 50.0))
        assertTrue(SurfaceIndicators.shouldShowSpeedBadge(0.0, Double.NaN))
        assertEquals("0 km/h", SurfaceIndicators.speedBadgeLabel(0.0, 50.0))
        assertEquals("0 km/h", SurfaceIndicators.speedBadgeLabel(0.0, Double.NaN))
        // Unknown current (negative/NaN) still falls back to the limit.
        assertTrue(SurfaceIndicators.shouldShowSpeedBadge(Double.NaN, 50.0))
        assertEquals("50 km/h", SurfaceIndicators.speedBadgeLabel(Double.NaN, 50.0))
        assertTrue(SurfaceIndicators.shouldShowSpeedBadge(55.0, 50.0))
        assertEquals("55 km/h", SurfaceIndicators.speedBadgeLabel(55.0, 50.0))
        // Nothing known renders nothing (never a fake "-1 km/h").
        assertTrue(!SurfaceIndicators.shouldShowSpeedBadge(-1.0, -1.0))
        assertEquals("", SurfaceIndicators.speedBadgeLabel(-1.0, -1.0))
        assertEquals("", SurfaceIndicators.speedBadgeLabel(Double.NaN, Double.NaN))
    }
}
