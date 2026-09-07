package com.naviveylin.ui.map

import com.naviveylin.core.ProjectionUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the rotation gesture pivot (design D1/D2, spec
 * map-rotation-gesture — "Rotation anchored at the finger midpoint"): the
 * gesture rotates/zooms around the finger midpoint, and the gesture-end commit
 * ([ProjectionUtils.rotateZoomAtFocalPoint]) adjusts the viewport center so the
 * geographic point under the midpoint stays under it in the rendered frame.
 */
class RotationPivotTest {

    private val viewW = 1080.0
    private val viewH = 2400.0
    private val dpi = 440.0
    private val centerLat = 48.0
    private val centerLon = 8.0
    private val angle = 0.3
    private val mag = 14.0
    // Off-center finger midpoint.
    private val focalX = 200.0
    private val focalY = 400.0

    private fun geoAt(lat: Double, lon: Double, mag: Double, angle: Double, x: Double, y: Double) =
        ProjectionUtils.viewport(lat, lon, mag, viewW.toInt(), viewH.toInt(), dpi, angle)
            .screenToGeoRotated(x, y)

    @Test
    fun pureRotationKeepsMidpointAnchor() {
        // Pure rotation around an off-center midpoint: the geo point under the
        // midpoint before the gesture must be under the midpoint after the
        // commit (new center + rotated angle).
        val delta = 0.7
        val (oldLat, oldLon) = geoAt(centerLat, centerLon, mag, angle, focalX, focalY)
        val (newLat, newLon) = ProjectionUtils.rotateZoomAtFocalPoint(
            focalX, focalY, mag, mag, delta, viewW, viewH, centerLat, centerLon, angle, dpi
        )
        val (newGeoLat, newGeoLon) = geoAt(newLat, newLon, mag, angle + delta, focalX, focalY)
        assertEquals(oldLat, newGeoLat, 1e-9)
        assertEquals(oldLon, newGeoLon, 1e-9)
    }

    @Test
    fun zeroRotationReducesToZoomAtCursor() {
        // Δ=0 must reduce exactly to zoomAtCursor (pure zoom around the focal
        // point keeps the geo under the cursor fixed). zoomAtCursor is
        // north-up (ignores the viewport angle), so compare at angle 0.
        val newMag = 15.0
        val (clat, clon) = ProjectionUtils.rotateZoomAtFocalPoint(
            focalX, focalY, mag, newMag, 0.0, viewW, viewH, centerLat, centerLon, 0.0, dpi
        )
        val (zlat, zlon) = ProjectionUtils.zoomAtCursor(
            focalX, focalY, mag, newMag, viewW, viewH, centerLat, centerLon, dpi
        )
        assertEquals(zlat, clat, 1e-9)
        assertEquals(zlon, clon, 1e-9)
    }

    @Test
    fun combinedRotateZoomKeepsMidpointAnchor() {
        // Combined rotate+zoom: the geo under the midpoint stays under it after
        // the commit.
        val delta = 0.7
        val newMag = 15.0
        val (oldLat, oldLon) = geoAt(centerLat, centerLon, mag, angle, focalX, focalY)
        val (newLat, newLon) = ProjectionUtils.rotateZoomAtFocalPoint(
            focalX, focalY, mag, newMag, delta, viewW, viewH, centerLat, centerLon, angle, dpi
        )
        val (newGeoLat, newGeoLon) = geoAt(newLat, newLon, newMag, angle + delta, focalX, focalY)
        assertEquals(oldLat, newGeoLat, 1e-9)
        assertEquals(oldLon, newGeoLon, 1e-9)
    }

    @Test
    fun counterClockwiseRotationKeepsMidpointAnchor() {
        // Negative rotation delta (counter-clockwise) keeps the anchor too.
        val delta = -0.9
        val (oldLat, oldLon) = geoAt(centerLat, centerLon, mag, angle, focalX, focalY)
        val (newLat, newLon) = ProjectionUtils.rotateZoomAtFocalPoint(
            focalX, focalY, mag, mag, delta, viewW, viewH, centerLat, centerLon, angle, dpi
        )
        val (newGeoLat, newGeoLon) = geoAt(newLat, newLon, mag, angle + delta, focalX, focalY)
        assertEquals(oldLat, newGeoLat, 1e-9)
        assertEquals(oldLon, newGeoLon, 1e-9)
    }

    @Test
    fun midpointAtScreenCenterLeavesCenterUnchanged() {
        // Midpoint at the screen center: a pure rotation must not move the
        // viewport center (the anchor is the center itself).
        val cx = viewW / 2.0
        val cy = viewH / 2.0
        val (newLat, newLon) = ProjectionUtils.rotateZoomAtFocalPoint(
            cx, cy, mag, mag, 0.5, viewW, viewH, centerLat, centerLon, angle, dpi
        )
        assertEquals(centerLat, newLat, 1e-9)
        assertEquals(centerLon, newLon, 1e-9)
    }
}
