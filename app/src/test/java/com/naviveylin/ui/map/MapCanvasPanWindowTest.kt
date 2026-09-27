package com.naviveylin.ui.map

import com.naviveylin.core.ProjectionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/**
 * The screen-level seam of the pan window: a drag against the frame in hand must move
 * the displayed content — and no state may exist in which the renderer drops the
 * request as covered while the display applies a zero offset (the dead pan reported on
 * device; spec: `map-pan-zoom` — "A pan SHALL never be invisible", spec:
 * `canvas-overrun` — "Coverage and display never disagree into a freeze").
 *
 * This drives the same production rules the screen's frame loop uses
 * ([PanWindowRules]) through a pan: the displayed center moves by the finger travel
 * while the committed center and the frame stay where they are (no render has landed),
 * which is the state the device froze in.
 *
 * Plain JUnit: the rules are pure (no JNI stub, no Robolectric sandbox).
 */
class MapCanvasPanWindowTest {

    private val dpi = 320.0
    private val canvasW = 1080
    private val canvasH = 1920
    private val frameW = (canvasW * 1.2).toInt()
    private val frameH = (canvasH * 1.2).toInt()
    private val mag = 15.0
    private val frameLat = 51.5142273
    private val frameLon = 7.4652789

    /** The frame in hand: rendered at the committed center, no margin-consumed render yet. */
    private fun frame(angle: Double = 0.0) =
        PanWindowRules.Frame(frameLat, frameLon, mag, angle, frameW, frameH)

    /** The displayed center after a drag of [dx]/[dy] px (the gesture's conversion). */
    private fun panned(dx: Double, dy: Double, fromLat: Double, fromLon: Double, angle: Double = 0.0) =
        ProjectionUtils.dragDeltaToNewCenterRotated(
            dx, dy, angle, mag, canvasW.toDouble(), canvasH.toDouble(), fromLat, fromLon, dpi
        )

    @Test
    fun dragMovesTheContentWhileTheCommittedCenterAndFrameStayBehind() {
        // The device reproduction: the pan commits nothing and no render lands while the
        // window stays inside the margin, so the committed center and the frame in hand
        // are still at the start position.
        val (lat, lon) = panned(60.0, 25.0, frameLat, frameLon)

        val released = PanWindowRules.holdReleased(lat, lon, frame(), gestureActive = true)
        val offset = PanWindowRules.offsetFor(lat, lon, frame(), canvasW, canvasH, dpi)

        assertFalse("an uncommitted pan must not release the display window", released)
        assertNotNull("the frame in hand must serve the pan window", offset)
        assertEquals("the frame must be drawn against the drag (x)", -60.0, offset!!.clampedX, 0.5)
        assertEquals("the frame must be drawn against the drag (y)", -25.0, offset.clampedY, 0.5)
        assertFalse("an in-margin drag must not saturate", offset.clamped)
    }

    @Test
    fun dragOnARotatedViewportMovesTheContentAlongTheDrag() {
        val angle = PI / 3
        val (lat, lon) = panned(45.0, -20.0, frameLat, frameLon, angle)

        val released = PanWindowRules.holdReleased(lat, lon, frame(angle), gestureActive = true)
        val offset = PanWindowRules.offsetFor(lat, lon, frame(angle), canvasW, canvasH, dpi)

        assertFalse(released)
        assertNotNull(offset)
        assertEquals("rotated drag x", -45.0, offset!!.clampedX, 0.5)
        assertEquals("rotated drag y", 20.0, offset.clampedY, 0.5)
    }

    @Test
    fun holdEndsOnlyWhenTheFrameCarriesTheDisplayedCenter() {
        // The re-centred frame landed at the displayed (committed) center: the window is
        // where the viewport says it is, so the hold can go without moving the content.
        val (lat, lon) = panned(60.0, 25.0, frameLat, frameLon)
        val landed = PanWindowRules.Frame(lat, lon, mag, 0.0, frameW, frameH)

        assertTrue(
            "a frame at the displayed center must release the hold",
            PanWindowRules.holdReleased(lat, lon, landed, gestureActive = false)
        )
        val offset = PanWindowRules.offsetFor(lat, lon, landed, canvasW, canvasH, dpi)
        assertNotNull(offset)
        assertEquals("the released offset is zero by definition", 0.0, offset!!.clampedX, 1e-9)
    }

    @Test
    fun saturatedDragSaturatesAtTheMarginAndAsksForARender() {
        val (lat, lon) = panned(4_000.0, 0.0, frameLat, frameLon)

        val offset = PanWindowRules.offsetFor(lat, lon, frame(), canvasW, canvasH, dpi)

        assertNotNull(offset)
        assertTrue("a drag beyond the buffer must report clamped", offset!!.clamped)
        assertEquals(
            "the window saturates exactly at the overrun margin",
            (frameW - canvasW) / 2.0, abs(offset.clampedX), 1e-6
        )
    }

    @Test
    fun unusableFrameIsReportedUnavailableInsteadOfAZeroOffset() {
        // No frame yet, and a frame without an overrun margin: the display cannot shift,
        // so the rules must say "not available" (the caller then renders) rather than
        // silently applying a zero offset — that is the freeze state.
        val (lat, lon) = panned(60.0, 0.0, frameLat, frameLon)

        assertEquals(
            "no frame in hand means no window",
            null,
            PanWindowRules.offsetFor(lat, lon, null, canvasW, canvasH, dpi)
        )
        val cropped = PanWindowRules.Frame(frameLat, frameLon, mag, 0.0, canvasW, canvasH)
        assertEquals(
            "a frame without an overrun margin means no window",
            null,
            PanWindowRules.offsetFor(lat, lon, cropped, canvasW, canvasH, dpi)
        )
        assertFalse(
            "an unusable frame must never release the hold with a zero offset",
            PanWindowRules.holdReleased(lat, lon, null, gestureActive = false)
        )
    }

    @Test
    fun coverageAndDisplayNeverDisagreeForTheSameRequest() {
        // The seam contract: whenever the rules derive a shift for the frame in hand, a
        // render request for the displayed center is a request the renderer may drop as
        // covered — and the offset it drops it for is the non-zero one derived here.
        // (The renderer side of this contract is pinned by MapRendererBlitTest's
        // unservable-window cases; this asserts the display half cannot be zero while
        // the render path considers the window covered.)
        for (drag in listOf(10.0 to 0.0, 60.0 to 25.0, -40.0 to 15.0)) {
            val (lat, lon) = panned(drag.first, drag.second, frameLat, frameLon)
            val offset = PanWindowRules.offsetFor(lat, lon, frame(), canvasW, canvasH, dpi)

            assertNotNull("drag=$drag must be servable inside the margin", offset)
            assertFalse("drag=$drag must stay inside the margin", offset!!.clamped)
            assertTrue(
                "drag=$drag: a window the renderer may drop as covered must be a " +
                    "non-zero shift on the display (was ${offset.clampedX}," +
                    "${offset.clampedY})",
                abs(offset.clampedX) > 0.5 || abs(offset.clampedY) > 0.5
            )
        }
    }
}
