package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [OverrunWindow]: the overrun-window shift the follow display, the pan window and the renderer's
 * coverage predicate share — the rotated geo delta against the frame's own viewport, clamped into the
 * frame's overrun margin, with the follow anchor as an optional drift reference.
 *
 * These cases came from `FollowPredictionTest` with the helper they drive (issue #73): the rule is not a
 * prediction, and the pan/coverage callers pass the default center anchor.
 *
 * Plain JUnit — the math is pure, no Robolectric, no JNI stub involved.
 */
class OverrunWindowTest {

    @Test
    fun `shift is zero when displayed position equals frame center`() {
        // 1080x1920 canvas, 1296x2304 overrun bitmap (1.2x), mag 14, dpi 320.
        val off = OverrunWindow.shiftPx(
            48.0, 2.0, 48.0, 2.0, 14.0, 0.0, 1296, 2304, 1080, 1920, 320.0
        )
        assertEquals(0.0, off.clampedX, 1e-6)
        assertEquals(0.0, off.clampedY, 1e-6)
        assertFalse(off.clamped)
    }

    @Test
    fun `shift equals the geo delta within the margin`() {
        // Move ~50 m east of the frame center at mag 14 (≈1.2 m/px at 320 dpi).
        val dLon = 50.0 / FollowPrediction.METERS_PER_DEG_LON
        val off = OverrunWindow.shiftPx(
            48.0, 2.0 + dLon, 48.0, 2.0, 14.0, 0.0, 1296, 2304, 1080, 1920, 320.0
        )
        assertTrue(off.clampedX > 0.0)
        assertTrue(off.clampedX < 108.0) // within the 0.1 * 1080 margin
        assertEquals(0.0, off.clampedY, 1e-6)
        assertFalse(off.clamped)
    }

    @Test
    fun `shift clamps to the overrun margin`() {
        // Move far east — the offset must clamp to the margin (108 px at 1080 wide).
        val dLon = 500.0 / FollowPrediction.METERS_PER_DEG_LON
        val off = OverrunWindow.shiftPx(
            48.0, 2.0 + dLon, 48.0, 2.0, 14.0, 0.0, 1296, 2304, 1080, 1920, 320.0
        )
        assertEquals(108.0, off.clampedX, 1e-6)
        assertEquals(0.0, off.clampedY, 1e-6)
        assertTrue(off.clamped)
    }

    @Test
    fun `shift rotates with the viewport angle`() {
        // Eastward delta at a -90° (heading-up) viewport must become an upward
        // (negative-y) offset — the bitmap has the rotation baked in.
        val dLon = 50.0 / FollowPrediction.METERS_PER_DEG_LON
        val off = OverrunWindow.shiftPx(
            48.0, 2.0 + dLon, 48.0, 2.0, 14.0, -Math.PI / 2.0, 1296, 2304, 1080, 1920, 320.0
        )
        assertEquals(0.0, off.clampedX, 1e-6)
        assertTrue(off.clampedY < 0.0) // east on the map points up on screen
        assertFalse(off.clamped)
    }

    @Test
    fun `off center anchor is zero drift and unclamped when aligned`() {
        // Follow framing: the frame center IS the anchor center of the vehicle
        // (the vehicle sits at the anchor inside the frame), so the vehicle's
        // DRIFT from the anchor is zero — the frame must not look permanently
        // clamped (which would force a full render every frame). The absolute
        // offset is 0.4*canvas but is not what the clamp bounds.
        val (fcLat, fcLon) = anchorCenter(48.0, 2.0, VehicleAnchorPosition.MIDDLE_FAR_RIGHT, 14.0, 1080, 1920, 320.0)
        val off = OverrunWindow.shiftPx(
            48.0, 2.0, fcLat, fcLon, 14.0, 0.0, 1296, 2304, 1080, 1920, 320.0,
            anchorX = 0.9, anchorY = 0.5
        )
        assertEquals(0.0, off.clampedX, 1e-3)
        assertEquals(0.0, off.clampedY, 1e-3)
        assertFalse(off.clamped)
    }

    @Test
    fun `off center anchor clamps only after drifting the margin`() {
        // Anchor-aligned frame, then move ~50 m east of the vehicle:
        // within the margin → unclamped, drift equals the geo delta.
        val (fcLat, fcLon) = anchorCenter(48.0, 2.0, VehicleAnchorPosition.MIDDLE_FAR_RIGHT, 14.0, 1080, 1920, 320.0)
        val dLon = 50.0 / FollowPrediction.METERS_PER_DEG_LON
        val off = OverrunWindow.shiftPx(
            48.0, 2.0 + dLon, fcLat, fcLon, 14.0, 0.0, 1296, 2304, 1080, 1920, 320.0,
            anchorX = 0.9, anchorY = 0.5
        )
        assertTrue(off.clampedX > 0.0)
        assertTrue(off.clampedX < 108.0) // within the 0.1 * 1080 margin
        assertFalse(off.clamped)
        // ~500 m east: drift clamps at the margin and is flagged.
        val dLon2 = 500.0 / FollowPrediction.METERS_PER_DEG_LON
        val off2 = OverrunWindow.shiftPx(
            48.0, 2.0 + dLon2, fcLat, fcLon, 14.0, 0.0, 1296, 2304, 1080, 1920, 320.0,
            anchorX = 0.9, anchorY = 0.5
        )
        assertEquals(108.0, off2.clampedX, 1e-6)
        assertTrue(off2.clamped)
    }

    @Test
    fun `explicit center anchor matches the default behavior`() {
        val dLon = 50.0 / FollowPrediction.METERS_PER_DEG_LON
        val withDefault = OverrunWindow.shiftPx(
            48.0, 2.0 + dLon, 48.0, 2.0, 14.0, 0.0, 1296, 2304, 1080, 1920, 320.0
        )
        val explicit = OverrunWindow.shiftPx(
            48.0, 2.0 + dLon, 48.0, 2.0, 14.0, 0.0, 1296, 2304, 1080, 1920, 320.0,
            anchorX = 0.5, anchorY = 0.5
        )
        assertEquals(withDefault.clampedX, explicit.clampedX, 1e-9)
        assertEquals(withDefault.clampedY, explicit.clampedY, 1e-9)
        assertEquals(withDefault.rawX, explicit.rawX, 1e-9)
        assertEquals(withDefault.clamped, explicit.clamped)
    }

    @Test
    fun `portrait frame against a landscape canvas yields a zero offset on the margin-less axis`() {
        // Orientation change still in flight: the displayed frame is portrait
        // (1296x2880) while the canvas already is landscape (2400x1080), so the
        // x margin (1296-2400)/2 is negative and must coerce to zero instead of
        // throwing "Cannot coerce value to an empty range".
        val dLon = 500.0 / FollowPrediction.METERS_PER_DEG_LON
        val off = OverrunWindow.shiftPx(
            48.0, 2.0 + dLon, 48.0, 2.0, 14.0, 0.0, 1296, 2880, 2400, 1080, 320.0
        )
        assertTrue(off.clampedX.isFinite())
        assertEquals(0.0, off.clampedX, 1e-9) // no overrun margin on x
        assertTrue("the clamp must have acted on a non-zero drift", off.clamped)
    }

    @Test
    fun `landscape frame against a portrait canvas yields a zero offset on the margin-less axis`() {
        // The reverse transition: the displayed frame is landscape (2880x1296)
        // while the canvas is portrait (1080x1920), so the y margin is negative.
        val dLat = 500.0 / FollowPrediction.METERS_PER_DEG_LAT
        val off = OverrunWindow.shiftPx(
            48.0 + dLat, 2.0, 48.0, 2.0, 14.0, 0.0, 2880, 1296, 1080, 1920, 320.0
        )
        assertTrue(off.clampedY.isFinite())
        assertEquals(0.0, off.clampedY, 1e-9) // no overrun margin on y
        assertTrue("the clamp must have acted on a non-zero drift", off.clamped)
    }

    @Test
    fun `a frame with an overrun margin keeps the existing clamp`() {
        // Same orientation as before the guard: the clamp still bounds the drift
        // to the frame's own margin, and an in-margin drift stays unclamped.
        val inMargin = OverrunWindow.shiftPx(
            48.0, 2.0 + 50.0 / FollowPrediction.METERS_PER_DEG_LON,
            48.0, 2.0, 14.0, 0.0, 1296, 2304, 1080, 1920, 320.0
        )
        assertTrue(inMargin.clampedX > 0.0)
        assertTrue(inMargin.clampedX < 108.0)
        assertFalse(inMargin.clamped)
        val clamped = OverrunWindow.shiftPx(
            48.0, 2.0 + 500.0 / FollowPrediction.METERS_PER_DEG_LON,
            48.0, 2.0, 14.0, 0.0, 1296, 2304, 1080, 1920, 320.0
        )
        assertEquals(108.0, clamped.clampedX, 1e-6)
        assertTrue(clamped.clamped)
    }
}
