package com.naviveylin.auto

import com.naviveylin.core.ProjectionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for gesture → viewport state mapping via [ProjectionUtils].
 */
@RunWith(RobolectricTestRunner::class)
class GestureMappingTest {

    private val dpi = 160.0
    private val screenW = 1920
    private val screenH = 1080
    private val centerLat = 48.8566
    private val centerLon = 2.3522
    private val mag = 5

    @Test
    fun screenToGeoCenter() {
        val (lat, lon) = ProjectionUtils.screenToGeo(
            screenW / 2.0, screenH / 2.0,
            screenW, screenH, mag, centerLat, centerLon, dpi
        )
        assertEquals(centerLat, lat, 1e-5)
        assertEquals(centerLon, lon, 1e-5)
    }

    @Test
    fun screenToGeoRoundTrip() {
        val testLat = 48.86
        val testLon = 2.35
        val (sx, sy) = ProjectionUtils.geoToScreen(
            testLat, testLon,
            screenW, screenH, mag, centerLat, centerLon, dpi
        )
        val (lat, lon) = ProjectionUtils.screenToGeo(
            sx, sy,
            screenW, screenH, mag, centerLat, centerLon, dpi
        )
        assertEquals(testLat, lat, 1e-5)
        assertEquals(testLon, lon, 1e-5)
    }

    @Test
    fun dragDeltaMovesCenter() {
        val dx = 100.0
        val dy = 50.0
        val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenter(
            dx, dy, mag,
            screenW.toDouble(), screenH.toDouble(),
            centerLat, centerLon, dpi
        )
        // Dragging right (positive dx) should move center west (decrease lon)
        assertTrue("Lon should decrease when dragging right", newLon < centerLon)
    }

    @Test
    fun zoomAtCursorKeepsCursorFixed() {
        val cursorX = screenW / 2.0 + 200.0
        val cursorY = screenH / 2.0 + 100.0
        val newMag = mag + 1

        // Get geo coord under cursor before zoom
        val (cursorLat, cursorLon) = ProjectionUtils.screenToGeo(
            cursorX, cursorY,
            screenW, screenH, mag, centerLat, centerLon, dpi
        )

        // Zoom at cursor
        val (newCenterLat, newCenterLon) = ProjectionUtils.zoomAtCursor(
            cursorX, cursorY,
            mag, newMag,
            screenW.toDouble(), screenH.toDouble(),
            centerLat, centerLon, dpi
        )

        // Get screen position of cursor geo coord after zoom
        val (newSx, newSy) = ProjectionUtils.geoToScreen(
            cursorLat, cursorLon,
            screenW, screenH, newMag, newCenterLat, newCenterLon, dpi
        )

        // Cursor geo coord should map to same screen position
        assertEquals(cursorX, newSx, 1.0)
        assertEquals(cursorY, newSy, 1.0)
    }

    @Test
    fun geoToScreenRoundTrip() {
        val testLat = 48.85
        val testLon = 2.36
        val (sx, sy) = ProjectionUtils.geoToScreen(
            testLat, testLon,
            screenW, screenH, mag, centerLat, centerLon, dpi
        )
        assertTrue("Screen X should be within bounds", sx in 0.0..screenW.toDouble())
        assertTrue("Screen Y should be within bounds", sy in 0.0..screenH.toDouble())
    }

    @Test
    fun dragDeltaNoOpForZeroDelta() {
        val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenter(
            0.0, 0.0, mag,
            screenW.toDouble(), screenH.toDouble(),
            centerLat, centerLon, dpi
        )
        assertEquals(centerLat, newLat, 1e-10)
        assertEquals(centerLon, newLon, 1e-10)
    }
}
