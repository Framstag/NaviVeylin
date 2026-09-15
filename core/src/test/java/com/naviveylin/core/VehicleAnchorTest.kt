package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the vehicle anchor preset model and the rotation-aware anchor
 * math (spec: auto/navigation-view — Vehicle anchor during navigation;
 * auto/free-driving — Follow mode activated; smooth-follow — Vehicle
 * position anchor in follow mode).
 */
class VehicleAnchorTest {

    @Test
    fun `fifteen presets with stable ids and labels`() {
        assertEquals(15, VehicleAnchorPosition.entries.size)
        val ids = VehicleAnchorPosition.entries.map { it.id }.toSet()
        assertEquals(15, ids.size)
        val labels = VehicleAnchorPosition.entries.map { it.label }.toSet()
        assertEquals(15, labels.size)
        // Every row/column of the 5x3 grid is present.
        val fxSet = VehicleAnchorPosition.entries.map { it.fx }.toSet()
        val fySet = VehicleAnchorPosition.entries.map { it.fy }.toSet()
        assertEquals(setOf(0.1, 0.3, 0.5, 0.7, 0.9), fxSet)
        assertEquals(setOf(0.1, 0.5, 0.9), fySet)
    }

    @Test
    fun `fractions stay within the overrun-safe range`() {
        for (anchor in VehicleAnchorPosition.entries) {
            assertTrue(anchor.fx in 0.1..0.9)
            assertTrue(anchor.fy in 0.1..0.9)
        }
    }

    @Test
    fun `default is the surface center`() {
        assertEquals(VehicleAnchorPosition.CENTER, VehicleAnchorPosition.DEFAULT)
        assertEquals(0.5, VehicleAnchorPosition.DEFAULT.fx, 1e-9)
        assertEquals(0.5, VehicleAnchorPosition.DEFAULT.fy, 1e-9)
    }

    @Test
    fun `from id resolves every preset and falls back for unknown`() {
        for (anchor in VehicleAnchorPosition.entries) {
            assertEquals(anchor, VehicleAnchorPosition.fromId(anchor.id))
        }
        assertEquals(VehicleAnchorPosition.DEFAULT, VehicleAnchorPosition.fromId("unknown"))
        assertEquals(VehicleAnchorPosition.DEFAULT, VehicleAnchorPosition.fromId(null))
        assertEquals(VehicleAnchorPosition.DEFAULT, VehicleAnchorPosition.fromId(""))
    }

    @Test
    fun `center anchor with zero angle returns the vehicle position`() {
        // Mirror fraction of center is center: the anchor center is the
        // vehicle's own geo position.
        val (lat, lon) = anchorCenter(48.0, 2.0, VehicleAnchorPosition.CENTER, 14.0, 1080, 1920, 320.0)
        assertEquals(48.0, lat, 1e-9)
        assertEquals(2.0, lon, 1e-9)
    }

    @Test
    fun `vehicle projects to the anchor fraction at zero angle`() {
        for (anchor in VehicleAnchorPosition.entries) {
            val (clat, clon) = anchorCenter(48.0, 2.0, anchor, 14.0, 1080, 1920, 320.0)
            val vp = ProjectionUtils.viewport(clat, clon, 14.0, 1080, 1920, 320.0)
            val (x, y) = vp.geoToScreen(48.0, 2.0)
            assertEquals(anchor.fx * 1080, x, 0.5)
            assertEquals(anchor.fy * 1920, y, 0.5)
        }
    }

    @Test
    fun `vehicle stays pinned at the anchor under rotation`() {
        val angles = listOf(0.5, -0.9, Math.PI / 2.0, 2.1)
        val anchors = listOf(
            VehicleAnchorPosition.CENTER,
            VehicleAnchorPosition.BOTTOM_RIGHT,
            VehicleAnchorPosition.TOP_FAR_LEFT
        )
        for (angle in angles) {
            for (anchor in anchors) {
                val (clat, clon) = anchorCenter(48.0, 2.0, anchor, 14.0, 1080, 1920, 320.0, angle)
                val vp = ProjectionUtils.viewport(clat, clon, 14.0, 1080, 1920, 320.0, angle)
                val (x, y) = vp.geoToScreenRotated(48.0, 2.0)
                assertEquals(anchor.fx * 1080, x, 0.5)
                assertEquals(anchor.fy * 1920, y, 0.5)
            }
        }
    }

    @Test
    fun `anchor center shifts so the vehicle is not centered`() {
        // Bottom-right anchor: the render center must move up-left relative to
        // the vehicle so the vehicle appears at the bottom-right fraction.
        val (clat, clon) = anchorCenter(48.0, 2.0, VehicleAnchorPosition.BOTTOM_RIGHT, 14.0, 1080, 1920, 320.0)
        // The vehicle stays south and east of the center (bottom-right bias).
        val vp = ProjectionUtils.viewport(clat, clon, 14.0, 1080, 1920, 320.0)
        val (x, y) = vp.geoToScreen(48.0, 2.0)
        assertTrue(x > 1080 / 2.0)
        assertTrue(y > 1920 / 2.0)
        assertEquals(0.7 * 1080, x, 0.5)
        assertEquals(0.9 * 1920, y, 0.5)
    }

    @Test
    fun `anchor center is not the vehicle for off-center anchors`() {
        val (clat, clon) = anchorCenter(48.0, 2.0, VehicleAnchorPosition.BOTTOM_CENTER, 14.0, 1080, 1920, 320.0)
        assertFalse(clat == 48.0 && clon == 2.0)
    }

    @Test
    fun `marker sits at the anchor against the anchored frame center`() {
        // Phone draw layer: the marker projects the displayed position against
        // the ANCHOR center of the committed frame, so at commit time the
        // marker lands exactly on the anchor fraction (spec: smooth-follow —
        // Vehicle position anchor in follow mode, marker rides the content).
        val anchor = VehicleAnchorPosition.BOTTOM_RIGHT
        val (acLat, acLon) = anchorCenter(48.0, 2.0, anchor, 14.0, 1080, 1920, 320.0, 0.3)
        val vp = ProjectionUtils.viewport(acLat, acLon, 14.0, 1080, 1920, 320.0, 0.3)
        val (x, y) = vp.geoToScreenRotated(48.0, 2.0)
        assertEquals(anchor.fx * 1080, x, 0.5)
        assertEquals(anchor.fy * 1920, y, 0.5)
    }

    @Test
    fun `marker drift equals content drift under anchored framing`() {
        // During the glide between fixes the marker must move exactly with the
        // displayed map content: marking against the anchored committed frame
        // center shifts by the same px as the content against the raw frame
        // center (the draw layer applies the same Δ to bitmap and marker).
        val anchor = VehicleAnchorPosition.BOTTOM_RIGHT
        val commit = 48.0 to 2.0
        val dLon = 40.0 / FollowPrediction.METERS_PER_DEG_LON
        val display = 48.0 to (2.0 + dLon)
        val w = 1080; val h = 1920; val mag = 14.0; val angle = 0.3; val dpi = 320.0

        val (acLat, acLon) = anchorCenter(commit.first, commit.second, anchor, mag, w, h, dpi, angle)
        val vpA = ProjectionUtils.viewport(acLat, acLon, mag, w, h, dpi, angle)
        val (x0, y0) = vpA.geoToScreenRotated(commit.first, commit.second)
        val (x1, y1) = vpA.geoToScreenRotated(display.first, display.second)

        val vpC = ProjectionUtils.viewport(commit.first, commit.second, mag, w, h, dpi, angle)
        val (cx0, cy0) = vpC.geoToScreenRotated(commit.first, commit.second)
        val (cx1, cy1) = vpC.geoToScreenRotated(display.first, display.second)

        assertEquals(x1 - x0, cx1 - cx0, 1e-6)
        assertEquals(y1 - y0, cy1 - cy0, 1e-6)
    }
}
