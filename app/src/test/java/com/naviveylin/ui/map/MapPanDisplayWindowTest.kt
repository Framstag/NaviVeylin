package com.naviveylin.ui.map

import com.naviveylin.core.FollowPrediction
import com.naviveylin.core.ProjectionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/**
 * The pan display window: the screen positions the overrun frame by the delta between
 * the displayed (panned) center and the frame's own viewport, rotated with the
 * viewport and clamped to the overrun margin (spec: canvas-overrun — Overrun window
 * shift for pan; spec: map-pan-zoom — Touch-based pan).
 *
 * This is the contract the phone pan path relies on for a render-free pan: inside the
 * margin the offset equals the finger travel (the content follows the finger), at the
 * margin it saturates and the screen asks for a re-centered frame.
 *
 * Plain JUnit — the math is pure (`FollowPrediction.displayOffsetPx` with the default
 * center anchor, which is the documented pan/overrun-window case); no JNI, no
 * Robolectric sandbox involved (AGENTS.md classloader rules).
 */
class MapPanDisplayWindowTest {

    private val dpi = 320.0
    private val canvasW = 1080
    private val canvasH = 1920
    private val bitmapW = (canvasW * 1.2).toInt()
    private val bitmapH = (canvasH * 1.2).toInt()
    private val mag = 15.0
    private val frameLat = 51.5142273
    private val frameLon = 7.4652789

    private fun offset(displayLat: Double, displayLon: Double, angle: Double = 0.0) =
        FollowPrediction.displayOffsetPx(
            displayLat, displayLon, frameLat, frameLon, mag, angle,
            bitmapW, bitmapH, canvasW, canvasH, dpi
        )

    /** The same delta through the independent rotated-viewport projection. */
    private fun expectedOffset(displayLat: Double, displayLon: Double, angle: Double): Pair<Double, Double> {
        val viewport = ProjectionUtils.viewport(frameLat, frameLon, mag, canvasW, canvasH, dpi, angle)
        val (sx, sy) = viewport.geoToScreenRotated(displayLat, displayLon)
        return (sx - canvasW / 2.0) to (sy - canvasH / 2.0)
    }

    /** The center a pan by [dx]/[dy] screen px produces (the gesture's conversion). */
    private fun pannedCenter(dx: Double, dy: Double, angle: Double): Pair<Double, Double> =
        ProjectionUtils.dragDeltaToNewCenterRotated(
            dx, dy, angle, mag, canvasW.toDouble(), canvasH.toDouble(), frameLat, frameLon, dpi
        )

    @Test
    fun frameThatMatchesTheDisplayedCenterNeedsNoOffset() {
        val window = offset(frameLat, frameLon)

        assertEquals(0.0, window.clampedX, 1e-9)
        assertEquals(0.0, window.clampedY, 1e-9)
        assertFalse("a frame centered on the displayed center must stay unclamped", window.clamped)
    }

    @Test
    fun offsetEqualsTheRotatedProjectionOfTheDisplayedCenter() {
        for (angle in listOf(0.0, PI / 4, -PI / 2, PI)) {
            val (lat, lon) = pannedCenter(40.0, -25.0, angle)
            val window = offset(lat, lon, angle)
            val (expectedX, expectedY) = expectedOffset(lat, lon, angle)

            assertEquals("angle=$angle x", expectedX, window.clampedX, 1e-6)
            assertEquals("angle=$angle y", expectedY, window.clampedY, 1e-6)
            assertFalse("angle=$angle must stay inside the margin", window.clamped)
        }
    }

    @Test
    fun offsetIsTheNegatedFingerTravelSoTheContentFollowsTheFinger() {
        // The frame is drawn at basePosition − displayOffset, so an offset equal to the
        // negated drag delta moves the map content exactly with the finger.
        for (angle in listOf(0.0, PI / 4, -PI / 2)) {
            for (drag in listOf(50.0 to 0.0, 0.0 to -30.0, -18.0 to 22.0)) {
                val (lat, lon) = pannedCenter(drag.first, drag.second, angle)
                val window = offset(lat, lon, angle)

                assertEquals(
                    "angle=$angle drag=$drag x", -drag.first, window.clampedX, 0.5
                )
                assertEquals(
                    "angle=$angle drag=$drag y", -drag.second, window.clampedY, 0.5
                )
            }
        }
    }

    @Test
    fun offsetSaturatesExactlyAtTheOverrunMargin() {
        // A pan far beyond the overrun buffer: the window stops at the margin.
        val (lat, lon) = pannedCenter(5_000.0, 5_000.0, 0.0)
        val window = offset(lat, lon)

        assertTrue("a pan beyond the buffer must report clamped", window.clamped)
        assertEquals((bitmapW - canvasW) / 2.0, abs(window.clampedX), 1e-6)
        assertEquals((bitmapH - canvasH) / 2.0, abs(window.clampedY), 1e-6)
    }

    @Test
    fun clampedOffsetNeverLeavesTheMargin() {
        val marginX = (bitmapW - canvasW) / 2.0
        val marginY = (bitmapH - canvasH) / 2.0
        for (dx in listOf(-4_000.0, -300.0, -20.0, 0.0, 20.0, 300.0, 4_000.0)) {
            for (dy in listOf(-4_000.0, -300.0, -20.0, 0.0, 20.0, 300.0, 4_000.0)) {
                for (angle in listOf(0.0, PI / 3, -PI / 6)) {
                    val (lat, lon) = pannedCenter(dx, dy, angle)
                    val window = offset(lat, lon, angle)

                    assertTrue(
                        "dx=$dx dy=$dy angle=$angle exceeds the margin",
                        abs(window.clampedX) <= marginX + 1e-6 && abs(window.clampedY) <= marginY + 1e-6
                    )
                }
            }
        }
    }

    @Test
    fun saturatedWindowStopsFollowingTheFingerInsteadOfRunningAhead() {
        // The marker/overlay shift uses the CLAMPED offset, so a saturated window must
        // report the margin, not the (larger) finger travel.
        val (lat, lon) = pannedCenter(900.0, 0.0, 0.0)
        val window = offset(lat, lon)
        val marginX = (bitmapW - canvasW) / 2.0

        assertTrue(window.clamped)
        assertEquals(marginX, abs(window.clampedX), 1e-6)
        assertTrue("the clamped offset must stay below the raw travel", abs(window.clampedX) < 900.0)
    }
}
