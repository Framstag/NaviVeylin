package com.naviveylin.ui.map

import com.naviveylin.core.FollowRefreshPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the numbers the follow display loop hands to [FollowRefreshPolicy] and the decision it
 * takes with them (spec: `smooth-follow` — Follow frame refresh before the overrun margin is
 * exhausted; spec: `gps-render-coalescing` — Follow-mode render throttling).
 *
 * The emulator reproduction of 2026-10-12 supplies the constants: a 1080x2400 canvas inside the 1.2x
 * overrun frame offers **108 px** of margin on X and **240 px** on Y (the drift — the vehicle's
 * travel, up-screen — saturates at the Y one), and it grows at 45 px/s at 50 km/h. The old loop,
 * which requested a frame only once `offset.clamped` was already true, left the content pinned at
 * that margin for a whole render: 1.5-2.7 s of byte-identical frames, 29 % of the drive.
 *
 * Plain JUnit: no Robolectric. The loop's own call site is device-verified (task 3.4); verified here
 * is the arithmetic it feeds and the request decision.
 */
class MapFollowRefreshTest {

    private val canvasW = 1080
    private val canvasH = 2400

    /** The overrun frame the renderer allocates: 1.2x the canvas on both axes. */
    private val bitmapW = (canvasW * 1.2).toInt()
    private val bitmapH = (canvasH * 1.2).toInt()

    @Test
    fun thresholdRequestsFrame() {
        // A drift along the travel direction uses the Y margin (240 px), not the X one (108 px).
        val (driftY, marginY) = followRefreshWindow(0.0, -200.0, bitmapW, bitmapH, canvasW, canvasH)
        assertEquals(200.0, driftY, 1e-9)
        assertTrue("Y margin $marginY px", marginY > 200.0 && marginY < 280.0)
        // A drift that uses MORE of its own axis is the one that decides.
        val (driftX, marginX) = followRefreshWindow(100.0, -200.0, bitmapW, bitmapH, canvasW, canvasH)
        assertEquals(100.0, driftX, 1e-9)
        assertTrue("X margin $marginX px", marginX > 90.0 && marginX < 130.0)
        // A frame with no overrun, or one smaller than the canvas (previous orientation), offers no
        // margin at all: the request is unconditional there (the frame must be replaced).
        assertEquals(0.0, followRefreshWindow(0.0, -50.0, canvasW, canvasH, canvasW, canvasH).second, 1e-9)
        assertEquals(0.0, followRefreshWindow(0.0, -50.0, 1200, 1080, canvasW, canvasH).second, 1e-9)
        assertTrue(
            "no margin requests the frame",
            FollowRefreshPolicy().shouldRequest(50.0, 0.0)
        )

        val policy = FollowRefreshPolicy()
        val threshold = policy.thresholdPx(marginY)
        assertFalse(
            "a drift below the threshold must not request a frame",
            policy.shouldRequest(threshold - 1.0, marginY)
        )
        assertTrue("a drift at the threshold requests a frame", policy.shouldRequest(threshold, marginY))
        assertTrue("one request is outstanding", policy.outstanding)
        assertFalse("and no second one until it lands", policy.shouldRequest(marginY, marginY))
    }

    @Test
    fun belowThresholdRendersNothing() {
        val (_, margin) = followRefreshWindow(0.0, -160.0, bitmapW, bitmapH, canvasW, canvasH)
        val policy = FollowRefreshPolicy()
        val threshold = policy.thresholdPx(margin)
        // The drift one landed frame in (the render latency's worth of travel, ~45 px/s at 50 km/h).
        var drift = 45.0 * 0.5
        assertFalse("an in-margin drift renders nothing", policy.shouldRequest(drift, margin))
        while (drift < threshold - 45.0 * 0.1) {
            drift += 45.0 * (1.0 / 60.0)
            assertFalse(
                "drift $drift px below the threshold must render nothing",
                policy.shouldRequest(drift, margin)
            )
        }
        assertTrue("the threshold is reached", policy.shouldRequest(threshold, margin))
        assertFalse("the next frame is only requested once this one lands", policy.shouldRequest(threshold * 2, margin))
    }
}
