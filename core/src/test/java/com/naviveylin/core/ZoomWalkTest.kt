package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

/**
 * Verifies [ZoomWalk]: cold-start landing, window-bounded steps, render-synchronous
 * stepping, exact landing on the requested magnification, in-window single steps,
 * newest-request-wins re-targeting, re-commit tolerance, cancellation, the
 * persisted-value rule and the coverage bound of the display scale.
 *
 * Plain JUnit - no Robolectric, no JNI stub involved.
 */
class ZoomWalkTest {

    private companion object {
        /** The reported entry case: browse viewport 13.0, speed target 17.0. */
        const val ENTRY_FROM_MAG = 13.0
        const val ENTRY_TO_MAG = 17.0
    }

    @Test
    fun `cold start lands directly with no walk`() {
        val walk = ZoomWalk()
        assertEquals(ENTRY_TO_MAG, walk.request(ENTRY_TO_MAG, frontMag = 0.0), 1e-9)
        assertFalse("nothing to transition from", walk.active)
        assertNull(walk.onFrameLanded(ENTRY_TO_MAG))
    }

    @Test
    fun `walks a four level entry in window sized steps and lands exactly`() {
        val walk = ZoomWalk()
        var frontMag = ENTRY_FROM_MAG
        var current = walk.request(ENTRY_TO_MAG, frontMag)
        var stepCount = 1
        while (walk.active) {
            assertTrue(
                "step of ${current - frontMag} exceeds the window",
                abs(current - frontMag) <= ZoomWalk.ZOOM_BLIT_WINDOW + 1e-9
            )
            frontMag = current
            current = walk.onFrameLanded(frontMag)
                ?: error("walk still active but no next step after $frontMag")
            stepCount++
            assertTrue("must never overshoot the request", current <= ENTRY_TO_MAG + 1e-9)
        }
        assertEquals("the last step must land exactly on the request", ENTRY_TO_MAG, current, 1e-9)
        assertEquals("13.0 -> 17.0 needs 16 window-sized steps", 16, stepCount)
    }

    @Test
    fun `no step is committed before the previous frame landed`() {
        val walk = ZoomWalk()
        val first = walk.request(ENTRY_TO_MAG, ENTRY_FROM_MAG)
        // The frame at the committed step has not landed yet.
        assertNull(walk.onFrameLanded(ENTRY_FROM_MAG))
        assertEquals(first, walk.step, 1e-9)
        // Once it landed, the next step is committed.
        val second = walk.onFrameLanded(first)
        assertEquals(first + ZoomWalk.ZOOM_BLIT_WINDOW, second!!, 1e-9)
    }

    @Test
    fun `change inside the window is a single step`() {
        val walk = ZoomWalk()
        val requested = ENTRY_FROM_MAG + 0.2
        assertEquals(requested, walk.request(requested, ENTRY_FROM_MAG), 1e-9)
        assertFalse("an in-window change needs no walk", walk.active)
        assertEquals(requested, walk.step, 1e-9)
    }

    @Test
    fun `a change exactly at the window is a single step`() {
        val walk = ZoomWalk()
        val requested = ENTRY_FROM_MAG + ZoomWalk.ZOOM_BLIT_WINDOW
        assertEquals(requested, walk.request(requested, ENTRY_FROM_MAG), 1e-9)
        assertFalse(walk.active)
    }

    @Test
    fun `a descending walk steps by the window too`() {
        val walk = ZoomWalk()
        var frontMag = ENTRY_TO_MAG
        var current = walk.request(ENTRY_FROM_MAG, frontMag)
        assertEquals(ENTRY_TO_MAG - ZoomWalk.ZOOM_BLIT_WINDOW, current, 1e-9)
        while (walk.active) {
            assertTrue(
                "descending step of ${frontMag - current} exceeds the window",
                abs(frontMag - current) <= ZoomWalk.ZOOM_BLIT_WINDOW + 1e-9
            )
            assertTrue("must never undershoot the request", current >= ENTRY_FROM_MAG - 1e-9)
            frontMag = current
            current = walk.onFrameLanded(frontMag)
                ?: error("walk still active but no next step after $frontMag")
        }
        assertEquals(ENTRY_FROM_MAG, current, 1e-9)
    }

    @Test
    fun `retarget while the previous step is in flight keeps the committed step`() {
        val walk = ZoomWalk()
        val first = walk.request(ENTRY_TO_MAG, ENTRY_FROM_MAG)
        // The fix re-commits while the committed step's frame has not landed yet
        // (the display still shows ENTRY_FROM_MAG).
        val second = walk.request(14.5, ENTRY_FROM_MAG)
        assertEquals("the step must not lead the frame in flight", first, second, 1e-9)
        assertEquals("only the target changes", 14.5, walk.target, 1e-9)
    }

    @Test
    fun `a lower retarget descends one window per landed frame`() {
        val walk = ZoomWalk()
        // Walk up, land one step, then ask for a value below the committed step.
        var displayed = walk.request(ENTRY_TO_MAG, ENTRY_FROM_MAG)
        displayed = walk.onFrameLanded(displayed) ?: error("expected a second ascending step")
        var current = walk.request(12.5, displayed)
        assertEquals("the descending leg starts one window down", displayed - ZoomWalk.ZOOM_BLIT_WINDOW, current, 1e-9)

        displayed = current
        var stepCount = 1
        while (walk.active) {
            current = walk.onFrameLanded(displayed) ?: error("no next step after $displayed")
            assertTrue(
                "descending step of ${displayed - current} exceeds the window",
                abs(displayed - current) <= ZoomWalk.ZOOM_BLIT_WINDOW + 1e-9
            )
            assertTrue("must never pass the requested value", current >= 12.5 - 1e-9)
            displayed = current
            stepCount++
        }
        assertEquals("the walk ends on the newest request", 12.5, current, 1e-9)
        assertEquals("13.5 -> 12.5 needs four window-sized steps", 4, stepCount)
    }

    @Test
    fun `re-committing the value already stepped does not cancel the walk`() {
        val walk = ZoomWalk()
        val first = walk.request(ENTRY_TO_MAG, ENTRY_FROM_MAG)
        // The fix re-commits the same speed target after the step landed.
        val second = walk.request(ENTRY_TO_MAG, first)
        assertTrue("a fix re-committing the same target keeps the walk", walk.active)
        assertEquals("advances one window, not the whole gap", first + ZoomWalk.ZOOM_BLIT_WINDOW, second, 1e-9)
    }

    @Test
    fun `cancel abandons the walk and clears the pinned step`() {
        val walk = ZoomWalk()
        walk.request(ENTRY_TO_MAG, ENTRY_FROM_MAG)
        walk.cancel()
        assertFalse(walk.active)
        assertTrue("a cancelled walk must not keep pinning the render step", walk.step.isNaN())
        assertNull(walk.onFrameLanded(ENTRY_FROM_MAG))
        assertEquals(
            "renders fall back to the committed magnification",
            13.0,
            walk.renderMag(13.0),
            1e-9
        )
    }

    @Test
    fun `target is what gets persisted while a walk is pending`() {
        val walk = ZoomWalk()
        walk.request(ENTRY_TO_MAG, ENTRY_FROM_MAG)
        assertEquals("the displayed step is never persisted", ENTRY_TO_MAG, walk.persistMag(0.0), 1e-9)
        walk.cancel()
        assertEquals("no walk pending -> the committed value is persisted", 16.0, walk.persistMag(16.0), 1e-9)
    }

    @Test
    fun `renderMag falls back to the committed magnification with no walk`() {
        val walk = ZoomWalk()
        assertEquals(15.5, walk.renderMag(15.5), 1e-9)
        val step = walk.request(ENTRY_TO_MAG, ENTRY_FROM_MAG)
        assertEquals(step, walk.renderMag(15.5), 1e-9)
    }

    @Test
    fun `display scale is clamped into the window`() {
        // A far commit is clamped to the window: 2^-0.25 still covers the surface with
        // the overrun frame, and 2^+0.25 is the largest zoom-in step.
        assertEquals(
            2.0.pow(-0.25).toFloat(),
            ZoomWalk.displayScale(ENTRY_FROM_MAG - 4.0, ENTRY_FROM_MAG),
            1e-6f
        )
        assertEquals(
            2.0.pow(0.25).toFloat(),
            ZoomWalk.displayScale(ENTRY_FROM_MAG + 4.0, ENTRY_FROM_MAG),
            1e-6f
        )
        // Inside the window the scale is exact.
        assertEquals(
            2.0.pow(0.25).toFloat(),
            ZoomWalk.displayScale(ENTRY_FROM_MAG + 0.25, ENTRY_FROM_MAG),
            1e-6f
        )
        // No frame -> no scale.
        assertEquals(1f, ZoomWalk.displayScale(14.0, 0.0), 1e-6f)
    }

    @Test
    fun `window keeps margin below what the overrun canvas allows`() {
        assertEquals(log2(ZoomWalk.OVERRUN_FACTOR), ZoomWalk.blitWindowLimit, 1e-9)
        assertTrue(
            "window ${ZoomWalk.ZOOM_BLIT_WINDOW} must keep margin below ${ZoomWalk.blitWindowLimit}",
            ZoomWalk.ZOOM_BLIT_WINDOW <= ZoomWalk.blitWindowLimit - ZoomWalk.ZOOM_BLIT_MARGIN + 1e-9
        )
        assertTrue("the margin must be positive", ZoomWalk.ZOOM_BLIT_WINDOW < ZoomWalk.blitWindowLimit)
    }
}
