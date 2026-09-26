package com.naviveylin.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The map-anchored overlays (GPS marker, accuracy circle, compass arrow) are shifted
 * by the same offset the map content is drawn with, so they cannot drift off the
 * content they ride while a pan window is applied (spec: gps-location-marker — Marker
 * projects against displayed bitmap viewport).
 *
 * In follow mode the projection itself uses the anchor center of the displayed
 * position, which already carries the drift — translating there too would apply it
 * twice. A saturated pan window passes the CLAMPED offset, so the overlays stay on the
 * content that is actually on screen instead of running ahead of the finger.
 *
 * Plain JUnit — pure function, no JNI (AGENTS.md classloader rules).
 */
class MarkerDisplayShiftTest {

    @Test
    fun followModeLeavesTheOverlayProjectionAlone() {
        assertShift(0f, 0f, markerDisplayShiftPx(followActive = true, panOffsetX = 42f, panOffsetY = -17f))
    }

    @Test
    fun panWindowShiftsTheOverlaysAgainstTheDrawOffset() {
        // drawFrontFrame draws the frame at center − offset, so the overlays are
        // translated by the negated offset.
        assertShift(-42f, 17f, markerDisplayShiftPx(followActive = false, panOffsetX = 42f, panOffsetY = -17f))
    }

    @Test
    fun noPanWindowMeansNoOverlayShift() {
        assertShift(0f, 0f, markerDisplayShiftPx(followActive = false, panOffsetX = 0f, panOffsetY = 0f))
    }

    /** Primitive float comparison: a negated zero (−0.0f) is the same shift as 0.0f. */
    private fun assertShift(expectedX: Float, expectedY: Float, actual: Pair<Float, Float>) {
        assertEquals("x", expectedX, actual.first, 0f)
        assertEquals("y", expectedY, actual.second, 0f)
    }
}
