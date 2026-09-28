package com.naviveylin.location

import com.naviveylin.core.FixFreshness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [FixFreshness] (spec: `gps-fix-quality` — Fix availability and quality tiers).
 *
 * The provider goes silent at standstill (min-distance throttling), so an aged timestamp alone must
 * not be read as "no fix"; beyond the age limit the quality falls to NONE, which is the fallback for
 * a live-but-silent source (tunnel, garage) and for a source that was disabled without an event.
 *
 * Pure JUnit — no Android/OSMScoutClient dependencies.
 */
class FixFreshnessTest {

    @Test
    fun ageLimitLiesInsideTheSpecifiedWindow() {
        // Spec: at least 30 s (survives standstill silence), at most 60 s (a lost signal is not
        // presented as a good fix indefinitely).
        assertTrue(FixFreshness.FIX_AGE_LIMIT_MS >= 30_000L)
        assertTrue(FixFreshness.FIX_AGE_LIMIT_MS <= 60_000L)
    }

    @Test
    fun neverHadFixIsNotAgedOut() {
        assertFalse(FixFreshness.isAgedOut(0L, System.currentTimeMillis()))
    }

    @Test
    fun freshFixIsNotAgedOut() {
        val now = System.currentTimeMillis()
        assertFalse(FixFreshness.isAgedOut(now, now))
        assertFalse(FixFreshness.isAgedOut(now - 1_000L, now))
    }

    @Test
    fun exactlyAtTheLimitIsNotAgedOut() {
        val now = System.currentTimeMillis()
        assertFalse(FixFreshness.isAgedOut(now - FixFreshness.FIX_AGE_LIMIT_MS, now))
    }

    @Test
    fun beyondTheLimitIsAgedOut() {
        val now = System.currentTimeMillis()
        assertTrue(FixFreshness.isAgedOut(now - FixFreshness.FIX_AGE_LIMIT_MS - 1L, now))
    }

    @Test
    fun customLimitIsHonoured() {
        val now = System.currentTimeMillis()
        assertFalse(FixFreshness.isAgedOut(now - 999L, now, limitMs = 1_000L))
        assertTrue(FixFreshness.isAgedOut(now - 1_001L, now, limitMs = 1_000L))
    }

    @Test
    fun limitIsAnExclusiveBoundary() {
        // The boundary case is the revert-check for 1.1: `>=` would report an aged-out fix here.
        val now = 1_000_000L
        assertFalse(FixFreshness.isAgedOut(now - 60_000L, now))
        assertEquals(60_000L, FixFreshness.FIX_AGE_LIMIT_MS)
    }
}
