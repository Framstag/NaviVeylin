package com.naviveylin.location

import com.naviveylin.core.SpeedStaleness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SpeedSpikeFilter] (spec: speed-spike-filtering — stale-value
 * decay). Valid speeds (0..cap) refresh the last known good; a spike or an
 * unknown speed returns the last good while it is fresh, but decays to 0 once
 * the last valid value ages beyond the staleness window — the frozen pre-stop
 * value is never pinned forever.
 *
 * Pure JUnit with an injectable clock.
 */
class SpeedSpikeFilterTest {

    private var nowMs = 1_000_000L

    private fun filter(cap: Double = 150.0): SpeedSpikeFilter =
        SpeedSpikeFilter(maxPlausibleSpeedKmH = cap) { nowMs }

    @Test
    fun validSpeedRefreshesLastGood() {
        val f = filter()
        assertEquals(50.0, f.filter(50.0), 1e-9)
        assertEquals(80.0, f.filter(80.0), 1e-9)
    }

    @Test
    fun zeroIsValid() {
        val f = filter()
        assertEquals(0.0, f.filter(0.0), 1e-9)
    }

    @Test
    fun spikeRejectedWithinWindowKeepsLastGood() {
        val f = filter()
        f.filter(50.0)
        nowMs += 500
        assertEquals(50.0, f.filter(392.0), 1e-9)
    }

    @Test
    fun unknownWithinWindowKeepsLastGood() {
        val f = filter()
        f.filter(50.0)
        nowMs += 500
        assertEquals(50.0, f.filter(Double.NaN), 1e-9)
        assertEquals(50.0, f.filter(-1.0), 1e-9)
    }

    @Test
    fun unknownBeyondWindowDecaysToZero() {
        val f = filter()
        f.filter(7.0)
        nowMs += SpeedStaleness.STALE_SPEED_MS + 1
        assertEquals(0.0, f.filter(Double.NaN), 1e-9)
    }

    @Test
    fun decayedZeroIsStickyUntilFreshValid() {
        val f = filter()
        f.filter(7.0)
        nowMs += SpeedStaleness.STALE_SPEED_MS + 1
        assertEquals(0.0, f.filter(Double.NaN), 1e-9)
        // Still no valid input — keeps reading 0, no NaN flicker.
        nowMs += 1_000
        assertEquals(0.0, f.filter(-1.0), 1e-9)
    }

    @Test
    fun freshValidRestoresImmediately() {
        val f = filter()
        f.filter(7.0)
        nowMs += SpeedStaleness.STALE_SPEED_MS + 1
        assertEquals(0.0, f.filter(Double.NaN), 1e-9)
        f.filter(60.0)
        assertEquals(60.0, f.filter(60.0), 1e-9)
    }

    @Test
    fun neverValidStaysNaN() {
        val f = filter()
        nowMs += 60_000
        assertTrue(f.filter(392.0).isNaN())
        assertTrue(f.filter(Double.NaN).isNaN())
    }

    @Test
    fun resetClearsState() {
        val f = filter()
        f.filter(50.0)
        f.reset()
        assertTrue(f.filter(Double.NaN).isNaN())
    }

    @Test
    fun aaCapDoesNotClipAutobahnSpeeds() {
        val f = filter(cap = 250.0)
        assertEquals(220.0, f.filter(220.0), 1e-9)
        // 280 beyond the cap is a spike: while the last good is fresh the
        // filter keeps it (220), it never echoes the absurd 280.
        assertEquals(220.0, f.filter(280.0), 1e-9)
    }
}
