package com.naviveylin.ui.map

import androidx.compose.ui.geometry.Offset
import com.naviveylin.core.ResolvedAnchor
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.ZoomAnimation
import com.naviveylin.core.ZoomWalk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * Verifies the screen-side zoom-animation rules (spec: smooth-zoom):
 *
 * - "Geographic anchor stays fixed during zoom animation" - the animation pivots on the
 *   vehicle's resolved follow anchor pixel while a follow mode is active, on every
 *   preset, and on the screen center otherwise ([zoomAnimationAnchor]);
 * - "Zoom animation never exposes uncovered map area" - the applied scale is bounded to
 *   the window the frame in hand can serve ([ZoomWalk.displayScale]);
 * - "Stepped magnification change retracks per step" - a stepped change retracks the
 *   running animation from its current displayed scale and keeps the cadence the
 *   animation was started with ([ZoomAnimation.activeDurationMs]).
 *
 * Pure JVM tests - no Robolectric, no composition.
 */
class MapCanvasZoomAnimationRulesTest {

    private companion object {
        const val CANVAS_W = 1080
        const val CANVAS_H = 2400
        const val FRONT_MAG = 14.0
    }

    @Test
    fun `every follow anchor preset pivots on the vehicle pixel`() {
        for (preset in VehicleAnchorPosition.entries) {
            val pivot = zoomAnimationAnchor(
                followMode = true,
                anchor = ResolvedAnchor(preset.fx, preset.fy),
                canvasWidth = CANVAS_W,
                canvasHeight = CANVAS_H
            )
            assertEquals("${preset.id} fx", preset.fx * CANVAS_W, pivot.x.toDouble(), 1e-3)
            assertEquals("${preset.id} fy", preset.fy * CANVAS_H, pivot.y.toDouble(), 1e-3)
        }
    }

    @Test
    fun `an off center preset pivots away from the surface center`() {
        val pivot = zoomAnimationAnchor(
            followMode = true,
            anchor = ResolvedAnchor(0.5, 0.9),
            canvasWidth = CANVAS_W,
            canvasHeight = CANVAS_H
        )
        // Bottom-center: the old screen-center pivot was 0.4 * height away from the
        // vehicle, which the animation would have swung the vehicle through.
        assertEquals(0.5 * CANVAS_W, pivot.x.toDouble(), 1e-3)
        assertEquals(0.9 * CANVAS_H, pivot.y.toDouble(), 1e-3)
    }

    @Test
    fun `without follow mode the pivot is the surface center`() {
        val pivot = zoomAnimationAnchor(
            followMode = false,
            anchor = ResolvedAnchor(0.1, 0.9),
            canvasWidth = CANVAS_W,
            canvasHeight = CANVAS_H
        )
        assertEquals(CANVAS_W / 2f, pivot.x, 1e-3f)
        assertEquals(CANVAS_H / 2f, pivot.y, 1e-3f)
    }

    @Test
    fun `an unmeasured canvas falls back to the center`() {
        val pivot = zoomAnimationAnchor(
            followMode = true,
            anchor = ResolvedAnchor(0.5, 0.9),
            canvasWidth = 0,
            canvasHeight = 0
        )
        assertEquals(0f, pivot.x, 1e-3f)
        assertEquals(0f, pivot.y, 1e-3f)
        assertEquals(Offset.Zero, pivot)
    }

    @Test
    fun `a walked step scales by at most the window`() {
        // The screen derives the applied scale with ZoomWalk.displayScale: a walked step
        // can never ask for more than the frame in hand covers.
        val step = FRONT_MAG + ZoomWalk.ZOOM_BLIT_WINDOW
        assertEquals(
            2.0.pow(ZoomWalk.ZOOM_BLIT_WINDOW).toFloat(),
            ZoomWalk.displayScale(step, FRONT_MAG),
            1e-6f
        )
    }

    @Test
    fun `an un-walked far change is clamped instead of exposing the surface`() {
        // Guard: if a magnification change ever lands without a walk (e.g. a camera fit
        // while the animation still runs), the scale stays inside the covered region.
        val down = ZoomWalk.displayScale(FRONT_MAG - 4.0, FRONT_MAG)
        val up = ZoomWalk.displayScale(FRONT_MAG + 4.0, FRONT_MAG)
        assertEquals(2.0.pow(-ZoomWalk.ZOOM_BLIT_WINDOW).toFloat(), down, 1e-6f)
        assertEquals(2.0.pow(ZoomWalk.ZOOM_BLIT_WINDOW).toFloat(), up, 1e-6f)
        assertTrue(
            "a 1.2x overrun frame scaled by the down-clamp must still cover the surface",
            (down * ZoomWalk.OVERRUN_FACTOR) >= 1f
        )
    }

    @Test
    fun `a stepped change retracks from the displayed scale and keeps the cadence`() {
        val anim = ZoomAnimation(durationMs = 250L)
        anim.start(1f, 2f, 0f, 0f, nowMs = 1_000L, durationMs = 650L)
        assertEquals(650L, anim.activeDurationMs)
        val displayed = anim.tick(1_200L)

        // The next step retracks the running animation with the cadence it started with.
        anim.retrack(1.1f, 0f, 0f, 1_200L, anim.activeDurationMs)

        assertTrue("retracking must not end the animation", anim.active)
        assertEquals(650L, anim.activeDurationMs)
        val retracked = anim.tick(1_200L)
        assertTrue(
            "the retrack resumes from the displayed scale, not the previous start",
            abs(retracked - displayed) < 0.05f
        )
        assertFalse("must not snap back to the previous start scale", retracked >= 2f)
    }
}
