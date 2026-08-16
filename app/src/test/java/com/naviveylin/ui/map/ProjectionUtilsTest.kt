package com.naviveylin.ui.map

import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.computeZoomPlaceholderRects
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
class ProjectionUtilsTest {

    private val dpi = 480.0
    private val screenW = 1080
    private val screenH = 1920

    @Test
    fun `computeScale produces consistent scale factors`() {
        val ps = ProjectionUtils.computeScale(8, screenW.toDouble(), dpi)
        assertTrue("scale should be positive", ps.scale > 0)
        assertTrue("scaleGradtorad should be positive", ps.scaleGradtorad > 0)
        // scaleGradtorad = scale * PI / 180
        assertEquals(ps.scale * Math.PI / 180.0, ps.scaleGradtorad, 1e-12)
    }

    @Test
    fun `higher magnification gives larger scale`() {
        val ps8 = ProjectionUtils.computeScale(8, screenW.toDouble(), dpi)
        val ps12 = ProjectionUtils.computeScale(12, screenW.toDouble(), dpi)
        assertTrue("mag 12 scale should be > mag 8 scale", ps12.scale > ps8.scale)
    }

    @Test
    fun `higher DPI gives larger scale`() {
        val psLowDpi = ProjectionUtils.computeScale(8, screenW.toDouble(), 240.0)
        val psHighDpi = ProjectionUtils.computeScale(8, screenW.toDouble(), 480.0)
        assertTrue("higher DPI should have larger scale", psHighDpi.scale > psLowDpi.scale)
    }

    @Test
    fun `geoToScreen center maps to screen center`() {
        val centerLat = 51.5
        val centerLon = 7.5
        val (sx, sy) = ProjectionUtils.geoToScreen(
            centerLat, centerLon,
            screenW, screenH, 8,
            centerLat, centerLon, dpi
        )
        assertEquals(screenW / 2.0, sx, 1.0)
        assertEquals(screenH / 2.0, sy, 1.0)
    }

    @Test
    fun `geoToScreen point east of center maps right of center`() {
        val centerLat = 51.5
        val centerLon = 7.5
        val (sx, _) = ProjectionUtils.geoToScreen(
            centerLat, centerLon + 0.1,
            screenW, screenH, 8,
            centerLat, centerLon, dpi
        )
        assertTrue("east point should be right of center", sx > screenW / 2.0)
    }

    @Test
    fun `geoToScreen point north of center maps above center`() {
        val centerLat = 51.5
        val centerLon = 7.5
        val (_, sy) = ProjectionUtils.geoToScreen(
            centerLat + 0.1, centerLon,
            screenW, screenH, 8,
            centerLat, centerLon, dpi
        )
        assertTrue("north point should be above center (lower y)", sy < screenH / 2.0)
    }

    @Test
    fun `screenToGeo inverts geoToScreen`() {
        val centerLat = 48.2
        val centerLon = 16.4
        val testLat = 48.5
        val testLon = 16.8

        val (sx, sy) = ProjectionUtils.geoToScreen(
            testLat, testLon,
            screenW, screenH, 10,
            centerLat, centerLon, dpi
        )
        val (latBack, lonBack) = ProjectionUtils.screenToGeo(
            sx, sy,
            screenW, screenH, 10,
            centerLat, centerLon, dpi
        )
        assertEquals(testLat, latBack, 1e-8)
        assertEquals(testLon, lonBack, 1e-8)
    }

    @Test
    fun `screenToGeoRotated round-trips with geoToScreenRotated at non-zero angle`() {
        val centerLat = 48.2
        val centerLon = 16.4
        val angle = Math.PI / 3
        val testLat = 48.5
        val testLon = 16.8

        val vp = ProjectionUtils.viewport(centerLat, centerLon, 10, screenW, screenH, dpi, angle)
        val (sx, sy) = vp.geoToScreenRotated(testLat, testLon)
        val (latBack, lonBack) = vp.screenToGeoRotated(sx, sy)
        assertEquals(testLat, latBack, 1e-8)
        assertEquals(testLon, lonBack, 1e-8)
    }

    @Test
    fun `screenToGeoRotated at angle zero equals screenToGeo`() {
        val centerLat = 48.2
        val centerLon = 16.4
        val sx = 300.0
        val sy = 700.0

        val (northLat, northLon) = ProjectionUtils.screenToGeo(
            sx, sy,
            screenW, screenH, 10,
            centerLat, centerLon, dpi
        )
        val vp = ProjectionUtils.viewport(centerLat, centerLon, 10, screenW, screenH, dpi, 0.0)
        val (rotLat, rotLon) = vp.screenToGeoRotated(sx, sy)
        assertEquals(northLat, rotLat, 1e-9)
        assertEquals(northLon, rotLon, 1e-9)
    }

    @Test
    fun `dragDeltaToNewCenter pan right moves center west`() {
        val centerLat = 51.5
        val centerLon = 7.5
        val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenter(
            100.0, 0.0, 8,
            screenW.toDouble(), screenH.toDouble(),
            centerLat, centerLon, dpi
        )
        // Dragging right (positive dx) should move center west (lower lon)
        assertTrue("pan right should decrease lon", newLon < centerLon)
        assertEquals("pan right should not change lat much", centerLat, newLat, 0.1)
    }

    @Test
    fun `dragDeltaToNewCenter pan down moves center north`() {
        val centerLat = 51.5
        val centerLon = 7.5
        val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenter(
            0.0, 100.0, 8,
            screenW.toDouble(), screenH.toDouble(),
            centerLat, centerLon, dpi
        )
        // Dragging down (positive dy) should move center north (higher lat)
        assertTrue("pan down should increase lat", newLat > centerLat)
        assertEquals("pan down should not change lon", centerLon, newLon, 1e-8)
    }

    @Test
    fun `dragDeltaToNewCenterRotated at angle zero matches north-up`() {
        val centerLat = 51.5
        val centerLon = 7.5
        val (northLat, northLon) = ProjectionUtils.dragDeltaToNewCenter(
            100.0, 50.0, 8,
            screenW.toDouble(), screenH.toDouble(),
            centerLat, centerLon, dpi
        )
        val (rotLat, rotLon) = ProjectionUtils.dragDeltaToNewCenterRotated(
            100.0, 50.0, 0.0, 8,
            screenW.toDouble(), screenH.toDouble(),
            centerLat, centerLon, dpi
        )
        assertEquals(northLat, rotLat, 1e-9)
        assertEquals(northLon, rotLon, 1e-9)
    }

    @Test
    fun `dragDeltaToNewCenterRotated pan right at 90 degrees moves center south`() {
        val centerLat = 51.5
        val centerLon = 7.5
        val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenterRotated(
            100.0, 0.0, Math.PI / 2, 8,
            screenW.toDouble(), screenH.toDouble(),
            centerLat, centerLon, dpi
        )
        // At 90° rotation north is on the right of the screen, so dragging
        // right must move the center south (lower lat).
        assertTrue("pan right at 90° should decrease lat", newLat < centerLat)
        assertEquals("pan right at 90° should not change lon", centerLon, newLon, 1e-8)
    }

    @Test
    fun `dragDeltaToNewCenterRotated pan down at 90 degrees moves center west`() {
        val centerLat = 51.5
        val centerLon = 7.5
        val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenterRotated(
            0.0, 100.0, Math.PI / 2, 8,
            screenW.toDouble(), screenH.toDouble(),
            centerLat, centerLon, dpi
        )
        // At 90° rotation east is at the bottom of the screen, so dragging
        // down must move the center west (lower lon).
        assertTrue("pan down at 90° should decrease lon", newLon < centerLon)
        assertEquals("pan down at 90° should not change lat", centerLat, newLat, 0.1)
    }

    @Test
    fun `dragDeltaToNewCenterRotated pan right at 45 degrees moves center southwest`() {
        val centerLat = 51.5
        val centerLon = 7.5
        val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenterRotated(
            100.0, 0.0, Math.PI / 4, 8,
            screenW.toDouble(), screenH.toDouble(),
            centerLat, centerLon, dpi
        )
        // At 45° rotation the drag vector splits evenly between the geo
        // axes: dragging right moves the center south-west.
        assertTrue("pan right at 45° should decrease lat", newLat < centerLat)
        assertTrue("pan right at 45° should decrease lon", newLon < centerLon)
    }

    @Test
    fun `dragDeltaToNewCenterRotated pan right at 135 degrees moves center southeast`() {
        val centerLat = 51.5
        val centerLon = 7.5
        val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenterRotated(
            100.0, 0.0, 3 * Math.PI / 4, 8,
            screenW.toDouble(), screenH.toDouble(),
            centerLat, centerLon, dpi
        )
        // At 135° rotation north-east points right on screen, so dragging
        // right moves the center south-east.
        assertTrue("pan right at 135° should decrease lat", newLat < centerLat)
        assertTrue("pan right at 135° should increase lon", newLon > centerLon)
    }

    @Test
    fun `dragDeltaToNewCenterRotated finger follow invariant holds at arbitrary angle`() {
        // The geo point under the finger at drag start must appear under the
        // finger's new position after the drag — the map follows the finger.
        val centerLat = 51.5
        val centerLon = 7.5
        val angle = Math.toRadians(30.0)
        val fingerStart = Pair(260.0, 310.0)
        val delta = Pair(60.0, -40.0)
        val vp = ProjectionUtils.viewport(centerLat, centerLon, 8, screenW, screenH, dpi, angle)
        val (gLat, gLon) = vp.screenToGeoRotated(fingerStart.first, fingerStart.second)
        val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenterRotated(
            delta.first, delta.second, angle, 8,
            screenW.toDouble(), screenH.toDouble(),
            centerLat, centerLon, dpi
        )
        val vp2 = ProjectionUtils.viewport(newLat, newLon, 8, screenW, screenH, dpi, angle)
        val (sx, sy) = vp2.geoToScreenRotated(gLat, gLon)
        assertEquals("finger-follow X", fingerStart.first + delta.first, sx, 1e-6)
        assertEquals("finger-follow Y", fingerStart.second + delta.second, sy, 1e-6)
    }

    @Test
    fun `zoomAtCursor keeps cursor geo point fixed`() {
        val centerLat = 51.5
        val centerLon = 7.5
        val cursorX = 300.0
        val cursorY = 400.0
        val oldMag = 8
        val newMag = 9

        // Geo coord under cursor before zoom
        val (cursorLat, cursorLon) = ProjectionUtils.screenToGeo(
            cursorX, cursorY,
            screenW, screenH, oldMag,
            centerLat, centerLon, dpi
        )

        val (newCenterLat, newCenterLon) = ProjectionUtils.zoomAtCursor(
            cursorX, cursorY,
            oldMag, newMag,
            screenW.toDouble(), screenH.toDouble(),
            centerLat, centerLon, dpi
        )

        // After zoom, the cursor geo coord should map to the same screen position
        val (newSx, newSy) = ProjectionUtils.geoToScreen(
            cursorLat, cursorLon,
            screenW, screenH, newMag,
            newCenterLat, newCenterLon, dpi
        )
        assertEquals("cursor X should stay fixed after zoom", cursorX, newSx, 1.0)
        assertEquals("cursor Y should stay fixed after zoom", cursorY, newSy, 1.0)
    }

    @Test
    fun `zoomAtCursor zoom in moves center toward cursor`() {
        val centerLat = 51.5
        val centerLon = 7.5
        val cursorX = 200.0  // left of center
        val cursorY = 300.0  // above center

        val (newLat, newLon) = ProjectionUtils.zoomAtCursor(
            cursorX, cursorY,
            8, 9,
            screenW.toDouble(), screenH.toDouble(),
            centerLat, centerLon, dpi
        )

        // Zooming in on a point left+above center should move center left+up
        assertTrue("zoom in on left point should move center west", newLon < centerLon)
        assertTrue("zoom in on above point should move center north", newLat > centerLat)
    }

    @Test
    fun `atanh matches mathematical definition`() {
        for (x in listOf(-0.9, -0.5, 0.0, 0.3, 0.7)) {
            val expected = 0.5 * ln((1.0 + x) / (1.0 - x))
            assertEquals(expected, ProjectionUtils.atanh(x), 1e-15)
        }
    }

    @Test
    fun `atanh at zero`() {
        assertEquals(0.0, ProjectionUtils.atanh(0.0), 1e-15)
    }

    @Test
    fun `screenBearing adds map rotation`() {
        // Map rotated 30 degrees CCW; arrow with absolute bearing 90 (east) should
        // appear at 120 degrees on screen.
        val screen = ProjectionUtils.screenBearing(90.0, Math.toRadians(30.0))
        assertEquals(120.0, screen, 1e-10)
    }

    @Test
    fun `screenBearing normalizes negative results`() {
        // Map rotated 45 degrees CCW; arrow with absolute bearing 0 (north).
        val screen = ProjectionUtils.screenBearing(0.0, -Math.toRadians(45.0))
        assertEquals(315.0, screen, 1e-10)
    }

    @Test
    fun `screenBearing with follow-direction rotation keeps arrow upright`() {
        // Follow-direction mode stores angle = -bearing. Arrow bearing added to
        // map rotation should point straight up (0 degrees on screen).
        val bearing = 123.0
        val mapAngle = -Math.toRadians(bearing)
        val screen = ProjectionUtils.screenBearing(bearing, mapAngle)
        assertEquals(0.0, screen, 1e-7)
    }

    @Test
    fun `ProjectedViewport geoToScreen and screenToGeo round-trip`() {
        val vp = ProjectionUtils.viewport(51.5, 7.5, 10, screenW, screenH, dpi)
        val original = Pair(51.49, 7.51)
        val (sx, sy) = vp.geoToScreen(original.first, original.second)
        val (latBack, lonBack) = vp.screenToGeo(sx, sy)
        assertEquals(original.first, latBack, 1e-8)
        assertEquals(original.second, lonBack, 1e-8)
    }

    @Test
    fun `ProjectedViewport zoomScale doubles per zoom level`() {
        val vp = ProjectionUtils.viewport(51.5, 7.5, 10, screenW, screenH, dpi)
        assertEquals(2.0, vp.zoomScale(11), 1e-12)
        assertEquals(0.5, vp.zoomScale(9), 1e-12)
        assertEquals(1.0, vp.zoomScale(10), 1e-12)
    }

    @Test
    fun `rotated geoToScreen keeps center fixed`() {
        val angle = Math.toRadians(45.0)
        val vp = ProjectionUtils.viewport(51.5, 7.5, 10, screenW, screenH, dpi, angle)
        val (cx, cy) = vp.geoToScreenRotated(51.5, 7.5)
        assertEquals(screenW / 2.0, cx, 1e-9)
        assertEquals(screenH / 2.0, cy, 1e-9)
    }

    @Test
    fun `rotated geoToScreen and screenToGeo round-trip`() {
        val angle = Math.toRadians(30.0)
        val vp = ProjectionUtils.viewport(51.5, 7.5, 10, screenW, screenH, dpi, angle)
        val (sx, sy) = vp.geoToScreenRotated(51.49, 7.51)
        val (latBack, lonBack) = vp.screenToGeoRotated(sx, sy)
        assertEquals(51.49, latBack, 1e-8)
        assertEquals(7.51, lonBack, 1e-8)
    }

    @Test
    fun `zoom placeholder zoom-in source is screen-sized region around new center`() {
        val fbW = (screenW * 1.2).toInt()
        val fbH = (screenH * 1.2).toInt()
        val rects = computeZoomPlaceholderRects(
            fbW, fbH, screenW, screenH,
            10, 11,
            51.5, 7.5, // new center same as front-buffer center
            51.5, 7.5,
            dpi
        )
        assertEquals(screenW.toDouble(), rects.dstW, 1e-9)
        assertEquals(screenH.toDouble(), rects.dstH, 1e-9)
        assertEquals(0.0, rects.dstX, 1e-9)
        assertEquals(0.0, rects.dstY, 1e-9)
        // With a 1.2x overrun buffer, the front-buffer center is offset by 0.1x screen;
        // the source rectangle is centered on that.
        val expectedSrcW = screenW / 2.0
        val expectedSrcH = screenH / 2.0
        assertEquals(fbW / 2.0 - expectedSrcW / 2.0, rects.srcX, 1.0)
        assertEquals(fbH / 2.0 - expectedSrcH / 2.0, rects.srcY, 1.0)
        assertEquals(expectedSrcW, rects.srcW, 1e-9)
        assertEquals(expectedSrcH, rects.srcH, 1e-9)
    }

    @Test
    fun `zoom placeholder zoom-out destination centers new center on screen`() {
        val fbW = (screenW * 1.2).toInt()
        val fbH = (screenH * 1.2).toInt()
        val rects = computeZoomPlaceholderRects(
            fbW, fbH, screenW, screenH,
            11, 10,
            51.5, 7.5,
            51.5, 7.5,
            dpi
        )
        assertEquals(fbW.toDouble(), rects.srcW, 1e-9)
        assertEquals(fbH.toDouble(), rects.srcH, 1e-9)
        // Scaled buffer should be centered on screen
        val expectedDstW = fbW / 2.0
        val expectedDstH = fbH / 2.0
        assertEquals(expectedDstW, rects.dstW, 1e-9)
        assertEquals(expectedDstH, rects.dstH, 1e-9)
        assertEquals((screenW - expectedDstW) / 2.0, rects.dstX, 1e-9)
        assertEquals((screenH - expectedDstH) / 2.0, rects.dstY, 1e-9)
    }

    @Test
    fun `zoom placeholder clamps source rect to front-buffer bounds when zooming in near edge`() {
        val fbW = screenW
        val fbH = screenH
        val rects = computeZoomPlaceholderRects(
            fbW, fbH, screenW, screenH,
            10, 11,
            // New center north-east of front-buffer center; with no overrun buffer the
            // ideal source rect would partially fall outside the front buffer.
            51.52, 7.52,
            51.5, 7.5,
            dpi
        )
        assertTrue("source X should be clamped non-negative", rects.srcX >= 0.0)
        assertTrue("source Y should be clamped non-negative", rects.srcY >= 0.0)
        assertTrue("source right edge should not exceed buffer width",
            rects.srcX + rects.srcW <= fbW + 1e-9)
        assertTrue("source bottom edge should not exceed buffer height",
            rects.srcY + rects.srcH <= fbH + 1e-9)
    }
}
