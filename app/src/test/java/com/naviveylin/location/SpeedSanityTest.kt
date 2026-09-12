package com.naviveylin.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SpeedSanity] (spec: gps-speed-priority — stationary reads zero).
 *
 * The rule: a reported speed below the dead-band with negligible displacement
 * between consecutive fresh fixes snaps to 0 (stationary evidence beats a
 * residual/stale velocity estimate). Real movement — even slow crawling — is
 * passed through unchanged.
 *
 * These tests do not touch OSMScoutClient statics, so they are free of the
 * FakeOSMScoutClient classloader constraint (see AGENTS.md).
 */
class SpeedSanityTest {

    private val sanity = SpeedSanity()

    /** Approx. latitude delta for [meters] of north-south displacement. 1° ≈ 111.32 km. */
    private fun latDelta(meters: Double): Double = meters / 111_320.0

    val residualSpeedKmH = 7.0 // ≈ 2 m/s — the observed false reading at standstill
    val deadBandKmH = 8.0

    /** First fix: no evidence yet, reported value must pass through. */
    @Test
    fun firstFixPassesReportedSpeedThrough() {
        assertEquals(residualSpeedKmH, sanity.process(0.0, 0.0, residualSpeedKmH, 1_000L), 1e-9)
    }

    /** Stationary jitter + residual speed: snaps to 0. */
    @Test
    fun stationaryWithResidualSpeedSnapsToZero() {
        sanity.process(0.0, 0.0, 50.0, 1_000L)
        // ~1 m displacement between fixes (stationary jitter), still 7 km/h.
        assertEquals(0.0, sanity.process(latDelta(1.0), 0.0, residualSpeedKmH, 2_000L), 1e-9)
    }

    /** Stationary jitter + sub-dead-band speed: snaps to 0. */
    @Test
    fun stationaryWithTinySpeedSnapsToZero() {
        sanity.process(0.0, 0.0, 1.0, 1_000L)
        assertEquals(0.0, sanity.process(latDelta(0.5), 0.0, 1.0, 2_000L), 1e-9)
    }

    /** Exactly at the dead-band with stationary evidence: still snaps. */
    @Test
    fun stationaryAtDeadBandBoundarySnapsToZero() {
        sanity.process(0.0, 0.0, 50.0, 1_000L)
        assertEquals(0.0, sanity.process(latDelta(1.0), 0.0, deadBandKmH, 2_000L), 1e-9)
    }

    /** Real movement beats the dead-band: slow crawl stays reported. */
    @Test
    fun slowCrawlWithRealMovementStaysReported() {
        sanity.process(0.0, 0.0, 2.0, 1_000L)
        // 10 m of real movement between fixes — displacement evidence wins,
        // the low reported speed is NOT forced to 0.
        assertEquals(2.0, sanity.process(latDelta(10.0), 0.0, 2.0, 2_000L), 1e-9)
    }

    /** Above the dead-band but tiny displacement: NOT snapped (could be real velocity noise). */
    @Test
    fun aboveDeadBandWithTinyDisplacementStaysReported() {
        sanity.process(0.0, 0.0, 50.0, 1_000L)
        assertEquals(20.0, sanity.process(latDelta(1.0), 0.0, 20.0, 2_000L), 1e-9)
    }

    /** Fast movement: reported speed always passes through. */
    @Test
    fun movingFastStaysReported() {
        sanity.process(0.0, 0.0, 80.0, 1_000L)
        assertEquals(80.0, sanity.process(latDelta(10.0), 0.0, 80.0, 2_000L), 1e-9)
    }

    /** Fix gap beyond the evidence window: no snap (movement may have happened between). */
    @Test
    fun gapBeyondEvidenceWindowDoesNotSnap() {
        sanity.process(0.0, 0.0, 50.0, 1_000L)
        // 4 s gap > 3 s window; 1 m apart, residual speed — must stay reported.
        assertEquals(residualSpeedKmH, sanity.process(latDelta(1.0), 0.0, residualSpeedKmH, 5_000L), 1e-9)
    }

    /** Unknown speed (NaN) passes through — the -1.0 native contract downstream. */
    @Test
    fun nanSpeedPassesThrough() {
        sanity.process(0.0, 0.0, 50.0, 1_000L)
        assertTrue(sanity.process(latDelta(1.0), 0.0, Double.NaN, 2_000L).isNaN())
    }

    /** Sub-dead-band reported speed with NO displacement evidence (first fix) stays. */
    @Test
    fun firstFixWithTinySpeedStaysReported() {
        assertEquals(deadBandKmH, sanity.process(0.0, 0.0, deadBandKmH, 1_000L), 1e-9)
    }
}