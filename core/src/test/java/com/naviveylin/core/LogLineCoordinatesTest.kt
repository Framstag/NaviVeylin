package com.naviveylin.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures for [LogLineCoordinates] (spec: auto-diagnostics — Coordinate-carrying entries do not
 * survive the retention pass; `TODO.md` §88).
 *
 * The catch-cases are the shapes the pre-redaction builds actually wrote — the §88 entry verbatim and
 * the car render entry of `TODO.md` §98 — while the keep-cases pin the precision-free identity lines
 * the app writes today, so a later rule change cannot quietly start deleting them.
 */
class LogLineCoordinatesTest {

    // ── catch cases: a line that carries a position ──────────────────────────────────────────────

    @Test
    fun flagsThePhoneLongPressEntryVerbatim() {
        // The §88 evidence line, quoted from TODO.md.
        val line = "[2026-09-25 21:34:34.085] LONGPRESS lat=51.513298135108705 lon=7.474341597216892 mag=16.0"

        assertTrue("the stale entry the recipe found must be flagged", LogLineCoordinates.carriesPosition(line))
    }

    @Test
    fun flagsTheCarRenderEntryVerbatim() {
        // The §98 shape: an unnamed pair, no `lat`/`lon` token anywhere.
        val line = "[2026-09-27 21:00:54.000] MAP render center=51.60987926464756,7.621644390462239 mag=17.0 " +
            "dpi=420 -> bitmap 1296x720"

        assertTrue("the unnamed car render pair must be flagged", LogLineCoordinates.carriesPosition(line))
    }

    @Test
    fun flagsALatitudeLabelledEntry() {
        assertTrue(LogLineCoordinates.carriesPosition("SHARE latitude: 51.513298135108705 longitude: 7.474341597216892"))
    }

    @Test
    fun flagsAPairWithNegativeLongitude() {
        assertTrue(LogLineCoordinates.carriesPosition("VIEWPORT center=-122.41941550000001,37.77492950000001 mag=12.0"))
    }

    @Test
    fun flagsAPairWithASpaceAfterTheComma() {
        assertTrue(LogLineCoordinates.carriesPosition("VIEWPORT center=51.60987926464756, 7.621644390462239"))
    }

    @Test
    fun flagsATokenWithASinglePreciseValue() {
        assertTrue(LogLineCoordinates.carriesPosition("FIX lon=7.474341597216892"))
    }

    // ── keep cases: precision-free identity the app writes today ─────────────────────────────────

    @Test
    fun keepsTheLongPressIdentityEntry() {
        assertFalse(
            "screen pixel, magnification and map name are identity, not a position",
            LogLineCoordinates.carriesPosition("[2026-09-27 22:00:00.000] LONGPRESS x=540 y=1500 mag=18.0 map=iceland")
        )
    }

    @Test
    fun keepsTheCarRenderIdentityEntry() {
        assertFalse(
            "the post-redaction MAP entry carries magnification, DPI and bitmap size",
            LogLineCoordinates.carriesPosition("[2026-09-27 22:00:00.000] MAP render mag=17.0 dpi=420 -> bitmap 1296x720")
        )
    }

    @Test
    fun keepsAccuracyBearingAndTileCounts() {
        assertFalse(LogLineCoordinates.carriesPosition("FIX acc=12.5 bearing=180.0"))
        assertFalse(LogLineCoordinates.carriesPosition("MEMORY retention released: trigger=poll-low (avail=215MB threshold=216MB) 512 -> 256 tiles/db"))
    }

    @Test
    fun keepsTheSharedLocationShapeEntry() {
        assertFalse(LogLineCoordinates.carriesPosition("MapCanvasVM Shared location: shape=coordinates mag=14.0 map=iceland"))
    }

    @Test
    fun keepsAWirelessEntryWithAnObjectLabel() {
        assertFalse(LogLineCoordinates.carriesPosition("SEARCH select label='Heinz-Hilpert-Theater Lünen' mag=16.0 map=nordrhein-westfalen"))
    }

    @Test
    fun keepsTwoRawDoublesWithoutACommaBetweenThem() {
        // A raw Double can print at coordinate precision without being a position; the pair half
        // requires the comma, so this stays (design D3, accepted limitation).
        assertFalse(
            LogLineCoordinates.carriesPosition("MapRenderer requestRender mag=16.666666666666668 (was 17.333333333333332)")
        )
    }

    @Test
    fun keepsProseAndIdentifierLookalikes() {
        assertFalse(LogLineCoordinates.carriesPosition("WARN Unknown type 'latitude_marker' on map=iceland"))
        assertFalse(LogLineCoordinates.carriesPosition("Diag/WARMUP stylesheet sync took 1.2345s"))
        assertFalse(LogLineCoordinates.carriesPosition(""))
        assertFalse(LogLineCoordinates.carriesPosition("   "))
    }
}
