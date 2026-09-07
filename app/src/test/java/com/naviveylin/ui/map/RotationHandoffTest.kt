package com.naviveylin.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

/**
 * Unit tests for the rotation display-layer hold (design D1, spec
 * map-rotation-gesture — "No temporary angle jump on gesture end"): after a
 * rotation gesture ends, the display keeps the final angle — by rotating the
 * current front buffer by the angle gap (committed − front-buffer angle) — until
 * the re-render at the committed angle lands. The gap is derived from the same
 * state the draw reads, so it zeroes atomically with the committed bitmap swap
 * (no one-frame double-rotation twist). The pure function is
 * [rotationDisplayTheta]; the hold flag arming (gesture end) and disarming
 * (frame loop on render land, next-gesture start) are UI wiring in
 * MapCanvasScreen.
 */
class RotationHandoffTest {

    @Test
    fun holdKeepsFinalAngleWhileRenderInFlight() {
        // Gesture ended at 0.5 rad; the front buffer is still at the pre-gesture
        // angle 0.1 rad and the re-render is in flight — the display must rotate
        // the old buffer by the gap (0.4 rad) to keep the final angle.
        assertEquals(0.4f, rotationDisplayTheta(0f, true, 0.1, 0.5), 1e-4f)
    }

    @Test
    fun holdZeroesWhenFrontBufferReachesCommittedAngle() {
        // The re-render at the committed angle landed — the gap is zero, so no
        // extra rotation is applied on top of the new bitmap.
        assertEquals(0f, rotationDisplayTheta(0f, true, 0.5, 0.5), 1e-6f)
    }

    @Test
    fun holdIsWrappingSafe() {
        // Committed angle wraps to [-π, π]: rotating the old buffer by the gap
        // (π) is visually identical to the wrapped accumulated rotation.
        assertEquals(PI.toFloat(), rotationDisplayTheta(0f, true, -PI / 2, PI / 2), 1e-4f)
        // Large single-gesture rotation: front 0.2, accumulated +3.0 rad → the
        // committed angle wraps to 3.2 − 2π; the derived gap (committed − front)
        // equals (3.0 − 2π) — visually identical to rotating by the
        // accumulated +3.0 rad.
        val committed = normalizeAngleForTest(3.2)
        assertEquals(3.0f - 2.0f * PI.toFloat(), rotationDisplayTheta(0f, true, 0.2, committed), 1e-4f)
    }

    @Test
    fun noHoldUsesGestureRotationOnly() {
        // Hold not armed (during a gesture or normal state): only the live
        // gesture accumulation applies.
        assertEquals(0.3f, rotationDisplayTheta(0.3f, false, 0.1, 0.5), 1e-6f)
        assertEquals(0f, rotationDisplayTheta(0f, false, 0.1, 0.5), 1e-6f)
    }

    @Test
    fun holdWithUnknownFrontBufferFallsBackToGestureRotation() {
        // No front buffer yet: no gap can be derived, so the display follows the
        // gesture value only (the missing render will land with the gap zeroed).
        assertEquals(0f, rotationDisplayTheta(0f, true, null, 0.5), 1e-6f)
        assertEquals(0.2f, rotationDisplayTheta(0.2f, true, null, 0.5), 1e-6f)
    }

    @Test
    fun holdIsIndependentOfZoomState() {
        // Combined rotate+zoom gesture: the rotation gap depends only on the
        // angle state — the zoom handoff (zoomAnimScale, cleared on the mag
        // match in the frame loop) composes independently. The zoom side is
        // covered by the existing smooth-zoom handoff tests.
        assertEquals(0.4f, rotationDisplayTheta(0f, true, 0.1, 0.5), 1e-4f)
        assertEquals(0f, rotationDisplayTheta(0f, true, 0.5, 0.5), 1e-6f)
    }

    private fun normalizeAngleForTest(rad: Double): Double {
        var r = rad % (2 * PI)
        if (r > PI) r -= 2 * PI
        if (r < -PI) r += 2 * PI
        return r
    }
}
