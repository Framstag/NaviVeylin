package com.naviveylin.location

import com.naviveylin.core.SpeedStaleness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SpeedStaleness] (spec: gps-speed-priority / speed-spike-filtering).
 *
 * The provider goes silent at standstill (min-distance throttling), so the
 * consumers' last-fix timestamp ages; beyond the staleness window the
 * displayed speed SHALL decay to 0 instead of pinning the last value.
 *
 * Pure JUnit — no Android/OSMScoutClient dependencies.
 */
class SpeedStalenessTest {

    @Test
    fun stalenessWindowIsThreeSeconds() {
        assertEquals(3_000L, SpeedStaleness.STALE_SPEED_MS)
    }

    @Test
    fun neverHadFixIsNotStale() {
        assertFalse(SpeedStaleness.isStale(0L, System.currentTimeMillis()))
    }

    @Test
    fun freshFixIsNotStale() {
        val now = System.currentTimeMillis()
        assertFalse(SpeedStaleness.isStale(now, now))
        assertFalse(SpeedStaleness.isStale(now - 1_000L, now))
    }

    @Test
    fun exactlyAtWindowIsNotStale() {
        val now = System.currentTimeMillis()
        assertFalse(SpeedStaleness.isStale(now - SpeedStaleness.STALE_SPEED_MS, now))
    }

    @Test
    fun beyondWindowIsStale() {
        val now = System.currentTimeMillis()
        assertTrue(SpeedStaleness.isStale(now - SpeedStaleness.STALE_SPEED_MS - 1L, now))
    }
}
