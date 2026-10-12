package com.naviveylin.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [RerouteConfirmationGate] (spec: `reroute-trigger` — Fast first
 * trigger, Post-reroute cooldown, Distance fast path).
 *
 * The gate under test is built from the engine's own policy constants
 * ([NavigationEngine.MIN_REROUTE_CONFIRM_COUNT], [NavigationEngine.MIN_OFF_ROUTE_DURATION_MS],
 * [NavigationEngine.REROUTE_CONFIRM_WINDOW_MS], [NavigationEngine.REROUTE_COOLDOWN_MS],
 * [NavigationEngine.FAST_PATH_DISTANCE_M]), not from the class defaults, so these
 * cases pin the policy the engine actually applies: floor 4 s, window 60 s,
 * cooldown 25 s, fast path 30 m. The native engine reports every ~5 s while
 * off-route, so requests in tests are spaced to mirror production cadence.
 */
class RerouteConfirmationGateTest {

    private class FakeClock {
        var nowMs = 0L
        fun advance(ms: Long) {
            nowMs += ms
        }
    }

    private fun gate(clock: FakeClock) = RerouteConfirmationGate(
        now = { clock.nowMs },
        minConfirmCount = NavigationEngine.MIN_REROUTE_CONFIRM_COUNT,
        minOffRouteDurationMs = NavigationEngine.MIN_OFF_ROUTE_DURATION_MS,
        confirmWindowMs = NavigationEngine.REROUTE_CONFIRM_WINDOW_MS,
        cooldownMs = NavigationEngine.REROUTE_COOLDOWN_MS,
        fastPathDistanceM = NavigationEngine.FAST_PATH_DISTANCE_M
    )

    // --- Fast first trigger (normal path) ---

    @Test
    fun transientDeviationDoesNotTrigger() {
        val clock = FakeClock()
        val g = gate(clock)
        // Two reports spanning 2 s, then the vehicle returns to the route (no more
        // reports). The 4 s floor is never met.
        assertFalse(g.onRequest(null)) // t=0
        clock.advance(2_000)
        assertFalse(g.onRequest(null)) // t=2s — count reached but 4s floor not met
        // No further reports — never triggers
    }

    @Test
    fun consistentDeviationTriggersAfterFourSeconds() {
        val clock = FakeClock()
        val g = gate(clock)
        assertFalse(g.onRequest(null)) // t=0
        clock.advance(2_000)
        assertFalse(g.onRequest(null)) // t=2s
        clock.advance(2_000)
        assertTrue(g.onRequest(null)) // t=4s — count=3 and duration met
    }

    @Test
    fun windowExpiryRestartsEpisode() {
        val clock = FakeClock()
        val g = gate(clock)
        assertFalse(g.onRequest(null)) // t=0
        clock.advance(70_000) // window (60s) expired
        assertFalse(g.onRequest(null)) // t=70s — fresh episode, count=1
        clock.advance(2_000)
        assertFalse(g.onRequest(null)) // t=72s — count=2, duration 2s < 4s
        clock.advance(2_000)
        assertTrue(g.onRequest(null)) // t=74s — duration met
    }

    // --- Post-reroute cooldown ---

    @Test
    fun cooldownBlocksReconfirmation() {
        val clock = FakeClock()
        val g = gate(clock)
        assertFalse(g.onRequest(null)) // t=0
        clock.advance(2_000)
        assertFalse(g.onRequest(null)) // t=2s
        clock.advance(2_000)
        assertTrue(g.onRequest(null)) // t=4s — confirmed, cooldown starts
        clock.advance(5_000)
        assertFalse(g.onRequest(null)) // t=9s — inside 25s cooldown
    }

    @Test
    fun cooldownExpiresAndNormalPathApplies() {
        val clock = FakeClock()
        val g = gate(clock)
        assertFalse(g.onRequest(null)) // t=0
        clock.advance(2_000)
        assertFalse(g.onRequest(null)) // t=2s
        clock.advance(2_000)
        assertTrue(g.onRequest(null)) // t=4s — confirmed
        clock.advance(30_000) // cooldown (25s) expired
        assertFalse(g.onRequest(null)) // t=34s — fresh episode, count=1
        clock.advance(2_000)
        assertFalse(g.onRequest(null)) // t=36s — count=2, duration 2s < 4s
        clock.advance(2_000)
        assertTrue(g.onRequest(null)) // t=38s — confirmed again
    }

    // --- Distance fast path ---

    @Test
    fun largeDeviationConfirmsOnFirstReport() {
        val clock = FakeClock()
        val g = gate(clock)
        // 40 m: above the 30 m fast path, below the retired 50 m threshold.
        assertTrue(g.onRequest(40.0)) // > 30m on first report → immediate
    }

    @Test
    fun fastPathOnlyAppliesToFirstReport() {
        val clock = FakeClock()
        val g = gate(clock)
        assertFalse(g.onRequest(25.0)) // t=0 — marginal, normal path starts
        clock.advance(2_000)
        assertFalse(g.onRequest(40.0)) // t=2s — deviation now large but not first report
        clock.advance(2_000)
        assertTrue(g.onRequest(40.0)) // t=4s — normal path completes
    }

    @Test
    fun marginalDeviationUsesNormalPath() {
        val clock = FakeClock()
        val g = gate(clock)
        assertFalse(g.onRequest(25.0)) // t=0
        clock.advance(2_000)
        assertFalse(g.onRequest(25.0)) // t=2s
        clock.advance(2_000)
        assertTrue(g.onRequest(25.0)) // t=4s
    }

    @Test
    fun cooldownExpiryRestoresFastPathEligibility() {
        val clock = FakeClock()
        val g = gate(clock)
        assertTrue(g.onRequest(40.0)) // t=0 — fast path confirms, cooldown starts
        clock.advance(30_000) // cooldown expired
        assertTrue(g.onRequest(40.0)) // t=30s — fast path again
    }

    @Test
    fun nanDeviationFallsThroughToNormalPath() {
        val clock = FakeClock()
        val g = gate(clock)
        assertFalse(g.onRequest(Double.NaN)) // no polyline available
        clock.advance(2_000)
        assertFalse(g.onRequest(Double.NaN))
        clock.advance(2_000)
        assertTrue(g.onRequest(Double.NaN))
    }

    // --- Reset ---

    @Test
    fun resetClearsCooldownAndCounters() {
        val clock = FakeClock()
        val g = gate(clock)
        assertTrue(g.onRequest(40.0)) // confirmed
        g.reset() // fresh route
        assertTrue(g.onRequest(40.0)) // fast path immediately, no cooldown
    }

    // --- Confirmed-episode duration (diagnostics) ---

    @Test
    fun lastConfirmElapsedIsTheConfirmedEpisodeDuration() {
        val clock = FakeClock()
        val g = gate(clock)
        assertTrue(g.onRequest(40.0)) // fast path: confirmed on the first report
        assertEquals(0L, g.lastConfirmElapsedMs)

        clock.advance(30_000) // cooldown expired
        assertFalse(g.onRequest(25.0)) // t=30s — marginal episode starts
        clock.advance(2_000)
        assertFalse(g.onRequest(25.0)) // t=32s
        clock.advance(2_000)
        assertTrue(g.onRequest(25.0)) // t=34s — normal path, 4s of episode elapsed
        assertEquals(4_000L, g.lastConfirmElapsedMs)
    }

    // --- needsDeviation ---

    @Test
    fun needsDeviationOnlyBeforeEpisodeStarts() {
        val clock = FakeClock()
        val g = gate(clock)
        assertTrue(g.needsDeviation()) // fresh
        g.onRequest(25.0) // episode starts
        assertFalse(g.needsDeviation())
    }

    @Test
    fun needsDeviationFalseDuringCooldown() {
        val clock = FakeClock()
        val g = gate(clock)
        assertTrue(g.onRequest(40.0)) // confirmed, cooldown starts
        assertFalse(g.needsDeviation()) // inside cooldown
        clock.advance(30_000)
        assertTrue(g.needsDeviation()) // cooldown expired
    }
}
