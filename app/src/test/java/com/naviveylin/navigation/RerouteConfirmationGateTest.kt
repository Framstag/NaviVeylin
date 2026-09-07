package com.naviveylin.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [RerouteConfirmationGate] (spec: reroute-trigger — Fast first
 * trigger, Post-reroute cooldown, Distance fast path).
 *
 * Defaults under test: minConfirmCount=2, minOffRouteDurationMs=10s,
 * confirmWindowMs=60s, cooldownMs=25s, fastPathDistanceM=50m.
 * The native engine reports every ~5s while off-route, so requests in tests
 * are spaced 5s apart to mirror production cadence.
 */
class RerouteConfirmationGateTest {

    private class FakeClock {
        var nowMs = 0L
        fun advance(ms: Long) {
            nowMs += ms
        }
    }

    private fun gate(clock: FakeClock) = RerouteConfirmationGate(now = { clock.nowMs })

    // --- Fast first trigger (normal path) ---

    @Test
    fun transientDeviationDoesNotTrigger() {
        val clock = FakeClock()
        val g = gate(clock)
        // Two reports, then the vehicle returns to the route (no more reports)
        assertFalse(g.onRequest(null)) // t=0
        clock.advance(5_000)
        assertFalse(g.onRequest(null)) // t=5s — count reached but 10s floor not met
        // No further reports — never triggers
    }

    @Test
    fun consistentDeviationTriggersAfterTenSeconds() {
        val clock = FakeClock()
        val g = gate(clock)
        assertFalse(g.onRequest(null)) // t=0
        clock.advance(5_000)
        assertFalse(g.onRequest(null)) // t=5s
        clock.advance(5_000)
        assertTrue(g.onRequest(null)) // t=10s — count=2 and duration met
    }

    @Test
    fun windowExpiryRestartsEpisode() {
        val clock = FakeClock()
        val g = gate(clock)
        assertFalse(g.onRequest(null)) // t=0
        clock.advance(70_000) // window (60s) expired
        assertFalse(g.onRequest(null)) // t=70s — fresh episode, count=1
        clock.advance(5_000)
        assertFalse(g.onRequest(null)) // t=75s — count=2, duration 5s < 10s
        clock.advance(5_000)
        assertTrue(g.onRequest(null)) // t=80s — duration met
    }

    // --- Post-reroute cooldown ---

    @Test
    fun cooldownBlocksReconfirmation() {
        val clock = FakeClock()
        val g = gate(clock)
        assertFalse(g.onRequest(null)) // t=0
        clock.advance(5_000)
        assertFalse(g.onRequest(null)) // t=5s
        clock.advance(5_000)
        assertTrue(g.onRequest(null)) // t=10s — confirmed, cooldown starts
        clock.advance(5_000)
        assertFalse(g.onRequest(null)) // t=15s — inside 25s cooldown
    }

    @Test
    fun cooldownExpiresAndNormalPathApplies() {
        val clock = FakeClock()
        val g = gate(clock)
        assertFalse(g.onRequest(null)) // t=0
        clock.advance(5_000)
        assertFalse(g.onRequest(null)) // t=5s
        clock.advance(5_000)
        assertTrue(g.onRequest(null)) // t=10s — confirmed
        clock.advance(30_000) // cooldown (25s) expired
        assertFalse(g.onRequest(null)) // t=40s — fresh episode, count=1
        clock.advance(5_000)
        assertFalse(g.onRequest(null)) // t=45s — count=2, duration 5s < 10s
        clock.advance(5_000)
        assertTrue(g.onRequest(null)) // t=50s — confirmed again
    }

    // --- Distance fast path ---

    @Test
    fun largeDeviationConfirmsOnFirstReport() {
        val clock = FakeClock()
        val g = gate(clock)
        assertTrue(g.onRequest(60.0)) // > 50m on first report → immediate
    }

    @Test
    fun fastPathOnlyAppliesToFirstReport() {
        val clock = FakeClock()
        val g = gate(clock)
        assertFalse(g.onRequest(30.0)) // t=0 — marginal, normal path starts
        clock.advance(5_000)
        assertFalse(g.onRequest(60.0)) // t=5s — deviation now large but not first report
        clock.advance(5_000)
        assertTrue(g.onRequest(60.0)) // t=10s — normal path completes
    }

    @Test
    fun marginalDeviationUsesNormalPath() {
        val clock = FakeClock()
        val g = gate(clock)
        assertFalse(g.onRequest(30.0)) // t=0
        clock.advance(5_000)
        assertFalse(g.onRequest(30.0)) // t=5s
        clock.advance(5_000)
        assertTrue(g.onRequest(30.0)) // t=10s
    }

    @Test
    fun cooldownExpiryRestoresFastPathEligibility() {
        val clock = FakeClock()
        val g = gate(clock)
        assertTrue(g.onRequest(60.0)) // t=0 — fast path confirms, cooldown starts
        clock.advance(30_000) // cooldown expired
        assertTrue(g.onRequest(60.0)) // t=30s — fast path again
    }

    @Test
    fun nanDeviationFallsThroughToNormalPath() {
        val clock = FakeClock()
        val g = gate(clock)
        assertFalse(g.onRequest(Double.NaN)) // no polyline available
        clock.advance(5_000)
        assertFalse(g.onRequest(Double.NaN))
        clock.advance(5_000)
        assertTrue(g.onRequest(Double.NaN))
    }

    // --- Reset ---

    @Test
    fun resetClearsCooldownAndCounters() {
        val clock = FakeClock()
        val g = gate(clock)
        assertTrue(g.onRequest(60.0)) // confirmed
        g.reset() // fresh route
        assertTrue(g.onRequest(60.0)) // fast path immediately, no cooldown
    }

    // --- needsDeviation ---

    @Test
    fun needsDeviationOnlyBeforeEpisodeStarts() {
        val clock = FakeClock()
        val g = gate(clock)
        assertTrue(g.needsDeviation()) // fresh
        g.onRequest(30.0) // episode starts
        assertFalse(g.needsDeviation())
    }

    @Test
    fun needsDeviationFalseDuringCooldown() {
        val clock = FakeClock()
        val g = gate(clock)
        assertTrue(g.onRequest(60.0)) // confirmed, cooldown starts
        assertFalse(g.needsDeviation()) // inside cooldown
        clock.advance(30_000)
        assertTrue(g.needsDeviation()) // cooldown expired
    }
}
