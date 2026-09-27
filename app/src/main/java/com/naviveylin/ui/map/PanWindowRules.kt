package com.naviveylin.ui.map

import com.naviveylin.core.FollowPrediction

/**
 * The pan display window's decision rules, extracted from the map screen so the seam
 * is testable without a Compose harness (spec: `canvas-overrun` — Sub-region blit for
 * pan, Offset and frame stay consistent, Coverage and display never disagree into a
 * freeze; spec: `map-pan-zoom` — Touch-based pan, "A pan SHALL never be invisible").
 *
 * A drag is a sequence of *displayed* centers held against the frame in hand: the
 * frame can be drawn at an offset derived from it (no native work inside the overrun
 * margin), a frame whose margin cannot serve the offset is unavailable (the caller
 * commits and renders instead), and the hold ends when the frame on screen carries the
 * center the user panned to.
 */
internal object PanWindowRules {

    /**
     * The frame currently on screen: its own viewport and its bitmap dimensions
     * (the overrun-sized frame the UI holds, not a crop).
     */
    internal data class Frame(
        val lat: Double,
        val lon: Double,
        val mag: Double,
        val angle: Double,
        val width: Int,
        val height: Int
    )

    /** Whether [frame] can serve a window shift at all at this canvas size. */
    internal fun frameUsable(frame: Frame?, canvasW: Int, canvasH: Int): Boolean =
        frame != null && canvasW > 0 && canvasH > 0 &&
            frame.width > canvasW && frame.height > canvasH

    /**
     * True when the pan hold can be released: a frame carrying the center the user has
     * panned to is on screen, so the displayed content already sits where the pan put it
     * and the offset is zero by definition (dropping it cannot jump the map).
     *
     * A live gesture never releases the hold: mid-drag the frame on screen is normally
     * still the pre-pan one, and releasing there is exactly the dead pan this rule
     * caused (the displayed center kept moving while the display applied a zero offset).
     */
    internal fun holdReleased(
        displayedLat: Double,
        displayedLon: Double,
        frame: Frame?,
        gestureActive: Boolean
    ): Boolean {
        if (gestureActive) return false
        if (frame == null) return false
        return nearly(displayedLat, frame.lat) && nearly(displayedLon, frame.lon)
    }

    /**
     * The offset the frame in hand must be drawn with to show [displayedLat]/
     * [displayedLon], or null when the frame cannot serve that window (no usable frame,
     * no overrun margin) — the caller then commits the displayed center and renders.
     */
    internal fun offsetFor(
        displayedLat: Double,
        displayedLon: Double,
        frame: Frame?,
        canvasW: Int,
        canvasH: Int,
        dpi: Double
    ): FollowPrediction.Companion.DisplayOffset? {
        if (displayedLat.isNaN() || displayedLon.isNaN()) return null
        if (!frameUsable(frame, canvasW, canvasH)) return null
        return FollowPrediction.displayOffsetPx(
            displayedLat, displayedLon,
            frame!!.lat, frame.lon, frame.mag, frame.angle,
            frame.width, frame.height,
            canvasW, canvasH, dpi
        )
    }

    private fun nearly(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) < 1e-9
}
