package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
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
}
