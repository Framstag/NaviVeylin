package com.naviveylin.ui.map

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the analysed-segment layer's projection and palette (spec:
 * `route-analysis`). Pure Kotlin — no composition, no native library.
 */
class RouteSegmentHighlightOverlayTest {

    private val lats = doubleArrayOf(52.5200, 52.5230, 52.5260, 52.5300)
    private val lons = doubleArrayOf(13.4050, 13.4080, 13.4090, 13.4100)
    private val viewport = MapRenderer.RenderViewport(lat = 52.5250, lon = 13.4075, mag = 14.0, angle = 0.0)

    private fun points(segment: IntRange) = segmentScreenPoints(
        polylineLats = lats,
        polylineLons = lons,
        segment = segment,
        viewport = viewport,
        screenWidthPx = 1080.0,
        screenHeightPx = 1920.0,
        dpi = 480.0
    )

    @Test
    fun `one screen point per polyline vertex in the range`() {
        assertEquals(3, points(0..2).size)
        assertEquals(2, points(1..2).size)
        assertEquals(1, points(2..2).size)
    }

    @Test
    fun `the range selects the vertices it names`() {
        assertEquals(points(0..3)[1], points(1..2)[0])
        assertEquals(points(0..3)[2], points(1..2)[1])
    }

    @Test
    fun `projected vertices land at different screen positions`() {
        val projected = points(0..3)

        assertEquals(4, projected.distinct().size)
        assertNotEquals(Offset.Zero, projected.first())
    }

    @Test
    fun `a range outside the polyline yields no points`() {
        assertTrue(points(1..9).isEmpty())
        assertTrue(points(-1..2).isEmpty())
        assertTrue(points(3..1).isEmpty())
    }

    @Test
    fun `an empty polyline yields no points`() {
        val projected = segmentScreenPoints(
            polylineLats = doubleArrayOf(),
            polylineLons = doubleArrayOf(),
            segment = 0..1,
            viewport = viewport,
            screenWidthPx = 1080.0,
            screenHeightPx = 1920.0,
            dpi = 480.0
        )

        assertTrue(projected.isEmpty())
    }

    @Test
    fun `the casing differs per presentation while the fill is shared`() {
        // Deliberate: one cyan fill reads on both the violet daylight route and the
        // dark presentation, so only the casing (the contrast carrier) branches.
        assertNotEquals(
            segmentHighlightCasingColor(isDarkPresentation = false),
            segmentHighlightCasingColor(isDarkPresentation = true)
        )
        assertEquals(
            segmentHighlightFillColor(isDarkPresentation = false),
            segmentHighlightFillColor(isDarkPresentation = true)
        )
    }

    @Test
    fun `the fill is drawn solid enough to read on a map`() {
        assertTrue(segmentHighlightFillColor(isDarkPresentation = false).alpha > 0.5f)
        assertTrue(segmentHighlightFillColor(isDarkPresentation = true).alpha > 0.5f)
        assertTrue(segmentHighlightCasingColor(isDarkPresentation = false).alpha == 1f)
        assertTrue(segmentHighlightCasingColor(isDarkPresentation = true).alpha == 1f)
    }

    @Test
    fun `the casing is wider than the fill`() {
        // Guards the "bordered highlight" contract: a thinner casing would be
        // covered by the fill and disappear.
        assertTrue(SEGMENT_CASING_WIDTH_DP > SEGMENT_FILL_WIDTH_DP)
    }
}
