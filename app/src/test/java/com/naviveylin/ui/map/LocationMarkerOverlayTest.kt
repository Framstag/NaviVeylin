package com.naviveylin.ui.map

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the marker overlay projection ([projectMarker]): the GPS coordinate
 * is projected with the front-buffer viewport, culled off-screen, and rotated
 * with the map — mirroring what the native renderer used to do, but as a pure
 * overlay target that never touches tiles or buffers.
 */
class LocationMarkerOverlayTest {

    private val viewport = MapRenderer.RenderViewport(
        lat = 48.8566,
        lon = 2.3522,
        mag = 14,
        angle = 0.0
    )
    private val screenW = 1080.0
    private val screenH = 2400.0
    private val dpi = 320.0

    @Test
    fun markerAtCenterProjectsToScreenCenter() {
        val pos = projectMarker(48.8566, 2.3522, viewport, screenW, screenH, dpi)
        assertNotNull(pos)
        assertEquals(screenW / 2.0, pos!!.x.toDouble(), 1.0)
        assertEquals(screenH / 2.0, pos.y.toDouble(), 1.0)
    }

    @Test
    fun markerCulledWhenOffScreen() {
        // ~10° east of center → far outside the visible viewport
        val pos = projectMarker(48.8566, 2.3522 + 10.0, viewport, screenW, screenH, dpi)
        assertNull("off-screen marker must be culled", pos)
    }

    @Test
    fun markerFollowsViewportCenterPan() {
        // Same geo position, viewport center moved: marker must re-project
        val panned = viewport.copy(lat = 48.8566 + 0.01, lon = 2.3522 + 0.01)
        val pos = projectMarker(48.8566, 2.3522, panned, screenW, screenH, dpi)
        assertNotNull(pos)
        // Marker sits 0.01° south-west of the new center → left and below center
        assertTrue("x must move left of center", pos!!.x < screenW / 2.0)
        assertTrue("y must move below center", pos.y > screenH / 2.0)
    }

    @Test
    fun markerRotatesWithMapRotation() {
        val northUp = projectMarker(48.8566, 2.3532, viewport, screenW, screenH, dpi)
        assertNotNull(northUp)

        val rotated = viewport.copy(angle = 0.5)
        val pos = projectMarker(48.8566, 2.3532, rotated, screenW, screenH, dpi)
        assertNotNull(pos)
        // East-of-center point rotated 0.5 rad about the center → y shifts.
        assertTrue("rotation must move the marker on screen", pos!!.y != northUp!!.y)
    }

    @Test
    fun markerHiddenAtNaNPosition() {
        val pos = projectMarker(Double.NaN, Double.NaN, viewport, screenW, screenH, dpi)
        assertNull(pos)
    }

    @Test
    fun bearingSignConventionMatchesNative() {
        // Native: screenBearing = markerBearing + mapRotation. Kotlin helper must match.
        val bearingDeg = com.naviveylin.core.ProjectionUtils.screenBearing(45.0, Math.toRadians(30.0))
        assertEquals(75.0, bearingDeg, 1e-9)
        val northUp = com.naviveylin.core.ProjectionUtils.screenBearing(0.0, Math.toRadians(30.0))
        assertEquals("no bearing → arrow points north on map", 30.0, northUp, 1e-9)
    }
}
