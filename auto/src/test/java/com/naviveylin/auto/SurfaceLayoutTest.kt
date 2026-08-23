package com.naviveylin.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure geometry tests for the strip layout constants (spec: auto-map-layout —
 * actions left, visualisations right, overlay content between them).
 */
class SurfaceLayoutTest {

    private val density = 2.0f // 320 dpi

    @Test
    fun insetAndReserveAreDisjointOnWideSurface() {
        val range = SurfaceLayout.overlayRangePx(1920, density)
        assertFalse(range.isEmpty())
        assertTrue(range.first >= SurfaceLayout.insetLeftPx(density))
        assertTrue(range.last < 1920 - SurfaceLayout.reserveRightPx(density))
    }

    @Test
    fun overlayRangeEmptyWhenSurfaceTooNarrow() {
        // Surface narrower than the combined strips → no overlay region.
        assertTrue(SurfaceLayout.overlayRangePx(10, density).isEmpty())
    }

    @Test
    fun pixelsScaleWithDensity() {
        assertEquals((SurfaceLayout.ACTION_STRIP_INSET_DP * density).toInt(), SurfaceLayout.insetLeftPx(density))
        assertEquals((SurfaceLayout.VIEW_STRIP_RESERVE_DP * density).toInt(), SurfaceLayout.reserveRightPx(density))
    }
}
