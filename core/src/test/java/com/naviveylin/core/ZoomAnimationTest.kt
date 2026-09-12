package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Verifies [ZoomAnimation]: ease-out cubic curve at t=0/0.5/1, duration
 * expiry, anchor passthrough, and retrack compounding from the current
 * scale. Plain JUnit — no Robolectric, no JNI stub involved.
 */
class ZoomAnimationTest {

    @Test
    fun `returns start scale at animation start`() {
        val anim = ZoomAnimation()
        anim.start(1f, 4f, 100f, 200f, nowMs = 1_000L)
        assertEquals(1f, anim.tick(1_000L), 1e-6f)
        assertTrue(anim.active)
    }

    @Test
    fun `eases with ease-out cubic at midpoint`() {
        val anim = ZoomAnimation()
        anim.start(1f, 5f, 0f, 0f, nowMs = 1_000L)
        // Ease-out cubic: 1-(1-0.5)^3 = 0.875 → 1 + 4*0.875 = 4.5
        assertEquals(4.5f, anim.tick(1_125L), 1e-4f)
    }

    @Test
    fun `returns target scale after duration expires`() {
        val anim = ZoomAnimation()
        anim.start(1f, 4f, 0f, 0f, nowMs = 1_000L)
        assertEquals(4f, anim.tick(1_250L), 1e-6f)
        assertFalse(anim.active)
        // Stays at target after expiry.
        assertEquals(4f, anim.tick(2_000L), 1e-6f)
    }

    @Test
    fun `currentScale does not deactivate the animation`() {
        val anim = ZoomAnimation()
        anim.start(1f, 4f, 0f, 0f, nowMs = 1_000L)
        assertEquals(4f, anim.currentScale(1_250L), 1e-6f)
        assertTrue(anim.active)
        // Peak query then a climb from zero progress: currentScale is
        // stateless, tick from the original start still returns the start.
        assertEquals(1f, anim.tick(1_000L), 1e-6f)
    }

    @Test
    fun `anchor passthrough`() {
        val anim = ZoomAnimation()
        anim.start(1f, 2f, 123.5f, -45.25f, nowMs = 1_000L)
        assertEquals(123.5f, anim.anchorX, 1e-6f)
        assertEquals(-45.25f, anim.anchorY, 1e-6f)
    }

    @Test
    fun `retrack compounds from current scale, not previous start`() {
        val anim = ZoomAnimation(durationMs = 400L)
        anim.start(1f, 5f, 0f, 0f, nowMs = 1_000L)
        // At t=0.25 (100 ms in): eased = 1-(0.75)^3 = 0.578125 → 1 + 4*0.578125
        val midElapsed = anim.currentScale(1_100L)
        assertEquals(1f + 4f * 0.578125f, midElapsed, 1e-4f)
        // Retrack toward 2 from the CURRENT scale, new anchor.
        anim.retrack(2f, 50f, 60f, nowMs = 1_100L)
        assertEquals(midElapsed, anim.tick(1_100L), 1e-6f)
        assertEquals(50f, anim.anchorX, 1e-6f)
        assertEquals(60f, anim.anchorY, 1e-6f)
        // The retracked run eases toward 2 and expires exactly there.
        assertEquals(2f, anim.tick(1_500L), 1e-6f)
    }

    @Test
    fun `retrack on idle animation starts from rest value`() {
        val anim = ZoomAnimation()
        anim.start(1f, 4f, 0f, 0f, nowMs = 1_000L)
        anim.tick(1_250L) // expiry
        assertFalse(anim.active)
        anim.retrack(2f, 0f, 0f, nowMs = 2_000L)
        assertEquals(4f, anim.tick(2_000L), 1e-6f) // starts from expired target
    }

    @Test
    fun `negative elapsed clamps to start scale`() {
        val anim = ZoomAnimation()
        anim.start(1f, 2f, 0f, 0f, nowMs = 1_000L)
        assertEquals(1f, anim.tick(500L), 1e-6f)
    }

    @Test
    fun `idle animation returns rest value and is inactive`() {
        val anim = ZoomAnimation()
        assertEquals(1f, anim.tick(0L), 1e-6f)
        assertFalse(anim.active)
        anim.start(2f, 2f, 0f, 0f, nowMs = 1_000L) // no-op start: from == to
        assertFalse(anim.active)
    }

    @Test
    fun `finish parks at the current displayed scale`() {
        val anim = ZoomAnimation()
        anim.start(1f, 5f, 0f, 0f, nowMs = 1_000L)
        val mid = anim.currentScale(1_125L)
        anim.finish(1_125L)
        assertFalse(anim.active)
        assertEquals(mid, anim.tick(2_000L), 1e-6f)
    }

    @Test
    fun `start accepts a per-animation duration longer than the default`() {
        // Auto-zoom display animation (~500 ms, spec: smooth-zoom) vs the
        // 250 ms constructor default: a 500 ms run must still be running at
        // 300 ms and complete only by 500 ms.
        val anim = ZoomAnimation()
        anim.start(1f, 4f, 0f, 0f, nowMs = 1_000L, durationMs = 500L)
        assertEquals(1f, anim.tick(1_000L), 1e-6f)
        assertTrue("500 ms animation not yet finished at t=300 ms", anim.active)
        assertEquals(4f, anim.tick(1_500L), 1e-6f)
        assertFalse(anim.active)
    }

    @Test
    fun `the same instance animates default or custom durations per call`() {
        val anim = ZoomAnimation()
        // Discrete input: 250 ms default.
        anim.start(1f, 2f, 0f, 0f, nowMs = 1_000L)
        assertTrue(anim.active)
        assertEquals(2f, anim.tick(1_250L), 1e-6f)
        assertFalse(anim.active)
        // Auto-zoom commit right after: 500 ms custom duration.
        anim.retrack(3f, 0f, 0f, nowMs = 2_000L, durationMs = 500L)
        assertEquals(2f, anim.tick(2_000L), 1e-6f)
        assertTrue("retracked with 500 ms must still be active at t=200", anim.active)
        assertFalse(anim.tick(2_500L) != 3f || anim.active)
    }

    @Test
    fun `retrack without a duration inherits the instance duration`() {
        // The retrack API's default defers to the constructor duration, so a
        // 400 ms instance keeps 400 ms until the caller passes one explicitly.
        val anim = ZoomAnimation(durationMs = 400L)
        anim.start(1f, 5f, 0f, 0f, nowMs = 1_000L)
        assertTrue(anim.active)
        anim.retrack(2f, 0f, 0f, nowMs = 1_100L)
        // The retrack starts from the scale currently displayed (eased toward
        // 5 at t=100/400): 3.3125, then eases down to 2 over 400 ms.
        val retrackStart = anim.currentScale(1_100L)
        assertEquals(3.3125f, retrackStart, 1e-4f)
        // 150 ms into the retracked run (150/400): eased 1-(0.625)^3 = 0.7559.
        assertEquals(
            retrackStart + (2f - retrackStart) * (1f - (1f - 150f / 400f).pow(3f)),
            anim.tick(1_250L), 1e-3f
        )
        assertTrue(anim.active)
        assertEquals(2f, anim.tick(1_500L), 1e-6f) // 400 ms elapsed → done
        assertFalse(anim.active)
    }
}