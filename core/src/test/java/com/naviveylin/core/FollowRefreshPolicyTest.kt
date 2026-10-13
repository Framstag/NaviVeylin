package com.naviveylin.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [FollowRefreshPolicy]: the follow frame is requested while margin remains, not once the
 * offset is already clamped (spec: `smooth-follow` — Follow frame refresh before the overrun margin
 * is exhausted; spec: `gps-render-coalescing` — Follow-mode render throttling).
 *
 * The drift/landing model below uses the numbers the emulator reproduction measured on
 * 2026-10-12: the drift grows at 45 px/s at 50 km/h (≈ 3.1 px/m at mag 16) and the overrun margin is
 * 240 px, so the margin is consumed in 5.3 s and the reproduction's freeze lasted a whole render
 * (0.4-2.7 s on that AVD).
 */
class FollowRefreshPolicyTest {

    private val margin = 240.0
    private val driftRatePxPerSec = 45.0

    @Test
    fun thresholdRequestsBeforeMargin() {
        val policy = FollowRefreshPolicy()
        // Unknown latency: the threshold sits at MAX_SHARE of the margin, inside it.
        val limit = policy.thresholdPx(margin)
        assertTrue("threshold $limit must be inside the margin", limit < margin)
        assertTrue("threshold $limit must leave room", limit <= FollowRefreshPolicy.MAX_SHARE * margin + 1e-9)

        assertFalse("below the threshold nothing is requested", policy.shouldRequest(limit - 1.0, margin))
        assertTrue("at the threshold the frame is requested", policy.shouldRequest(limit, margin))

        // A known latency inside the reserve keeps the cap; a latency that needs more reserve lowers
        // the threshold; one that cannot fit floors at MIN_SHARE.
        val known = FollowRefreshPolicy().thresholdPx(margin, latencyPx = 100.0)
        assertTrue("a latency inside the reserve keeps the cap ($known)", known <= limit + 1e-9)
        val slow = FollowRefreshPolicy().thresholdPx(margin, latencyPx = 200.0)
        assertTrue(
            "a slow render floors at MIN_SHARE ($slow)",
            kotlin.math.abs(slow - FollowRefreshPolicy.MIN_SHARE * margin) < 1e-9
        )
        val fast = FollowRefreshPolicy().thresholdPx(margin, latencyPx = 1.0)
        assertTrue("a fast render never waits past the cap ($fast)", fast <= FollowRefreshPolicy.MAX_SHARE * margin + 1e-9)
    }

    @Test
    fun steadyDriveNeverClamps() {
        // A steady drive, a render latency that fits the margin the threshold leaves: the drift is
        // reset by each landed frame and never reaches the margin.
        val latencySec = 0.5
        val resetPx = driftRatePxPerSec * latencySec
        val policy = FollowRefreshPolicy()
        var drift = 0.0
        var requests = 0
        var frames = 0
        val dt = 1.0 / 60.0
        var t = 0.0
        var landing = Double.NaN
        while (t < 30.0) {
            drift -= driftRatePxPerSec * dt
            if (!landing.isNaN() && t >= landing) {
                policy.onFrameLanded()
                frames++
                landing = Double.NaN
                drift = -resetPx
            }
            if (policy.shouldRequest(drift, margin, latencyPx = resetPx)) {
                requests++
                landing = t + latencySec
            }
            assertTrue("drift ${-drift} px reached the margin", -drift < margin)
            t += dt
        }
        assertTrue("the drive must have requested frames ($requests)", requests > 5)
        assertTrue("every landed request must have cleared the flag ($frames of $requests)", frames >= requests - 1)
    }

    @Test
    fun singleRequestOutstanding() {
        val policy = FollowRefreshPolicy()
        assertTrue(policy.shouldRequest(margin, margin))
        assertTrue(policy.outstanding)
        assertFalse("a second request while one is outstanding", policy.shouldRequest(margin * 2, margin))
        assertTrue(
            "an unlanded request stops blocking after the timeout",
            policy.shouldRequest(margin, margin, nowMs = FollowRefreshPolicy.REQUEST_TIMEOUT_MS + 1)
        )
        policy.onFrameLanded()
        assertFalse(policy.outstanding)
        assertTrue("the next frame can be requested once the last one landed", policy.shouldRequest(margin, margin))
        policy.reset()
        assertFalse("a reset forgets the outstanding request", policy.outstanding)
    }

    @Test
    fun slowRenderClampsAsLastResort() {
        // A render slower than the margin the threshold leaves: the threshold floors at MIN_SHARE and
        // the policy keeps asking after every landing — the display's clamp is the last resort (the
        // caller's rule, not this policy's).
        val latencyPx = margin * 1.5
        val policy = FollowRefreshPolicy()
        val limit = policy.thresholdPx(margin, latencyPx)
        assertTrue(
            "a hopeless latency floors the threshold at MIN_SHARE ($limit)",
            kotlin.math.abs(limit - FollowRefreshPolicy.MIN_SHARE * margin) < 1e-9
        )
        assertTrue("it still requests", policy.shouldRequest(limit, margin, latencyPx = latencyPx))
        assertFalse("once per landing", policy.shouldRequest(limit * 2, margin, latencyPx = latencyPx))
        policy.onFrameLanded()
        assertTrue("and again after the landing", policy.shouldRequest(limit, margin, latencyPx = latencyPx))
    }
}
