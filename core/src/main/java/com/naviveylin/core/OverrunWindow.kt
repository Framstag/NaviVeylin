package com.naviveylin.core

import kotlin.math.cos
import kotlin.math.sin

/**
 * The overrun window's shift: how far the frame in hand must be drawn from the surface center so that a
 * requested displayed point lands where it belongs, clamped into the frame's own overrun margin.
 *
 * This is the single home of the rule three paths share — the follow display's drift, the pan window's
 * offset and the renderer's coverage predicate (`MapRenderer.overrunWindowCovers`), so the applied clamp and
 * the coverage decision can never disagree (spec: `canvas-overrun` — Overrun window shift for pan; spec:
 * `smooth-follow` — The follow blit offset is computed by one seam that owns the anchor; spec:
 * `auto-smooth-follow` — Single resolved anchor in the AA follow blit).
 *
 * It lives here, and not on [FollowPrediction], because most of its callers are not predictions: the pan
 * path and the coverage predicate pass the default (center) anchor and mean the absolute overrun-window
 * shift, which a prediction class misdescribed (issue #73).
 */
object OverrunWindow {

    /**
     * Result of [shiftPx]: the raw (unclamped) and clamped offsets in the viewport (rotated) frame.
     */
    data class DisplayOffset(
        val rawX: Double, val rawY: Double,
        val clampedX: Double, val clampedY: Double
    ) {
        val clamped: Boolean get() = clampedX != rawX || clampedY != rawY
    }

    /**
     * Compute the display drift (bitmap px) for a displayed position against the current frame's viewport,
     * clamped to the overrun margin.
     *
     * The bitmap has the viewport rotation baked in, so the north-up geo delta is rotated by the viewport
     * angle first (same rotation the renderer's blit applies) — otherwise the map scrolls in the wrong
     * direction in a rotated (heading-up) viewport.
     *
     * [anchorX]/[anchorY] are the follow anchor's screen fractions (0..1, defaults 0.5/0.5 = surface
     * center). The returned [DisplayOffset.rawX]/[DisplayOffset.rawY] and [DisplayOffset.clampedX]/
     * [clampedY] are the DRIFT of the displayed position from its anchor, not the absolute offset from the
     * buffer center: the frame is rendered anchor-centered (the renderer applies the anchor via
     * `anchorCenter`, see spec `smooth-follow`), so it already contains the vehicle at the anchor pixel. The
     * returned value is therefore the COMPLETE blit offset — no caller may apply the anchor a second time; a
     * full-offset shift would cancel the anchor and land the vehicle content at the surface center while the
     * marker draws at the anchor. With the default center anchor the drift equals the absolute offset and
     * the behavior is identical to the pre-anchor implementation.
     *
     * The overrun margin is never negative: a frame that offers no overrun margin on an axis (for example a
     * frame that still carries the previous orientation after a rotation while the canvas already has the
     * new one) yields a ZERO offset on that axis, reported as [DisplayOffset.clamped], so the caller
     * requests a frame in the current orientation instead of constructing an empty coercion range.
     */
    fun shiftPx(
        displayLat: Double, displayLon: Double,
        frameLat: Double, frameLon: Double,
        mag: Double, angle: Double,
        bitmapW: Int, bitmapH: Int,
        canvasW: Int, canvasH: Int, dpi: Double,
        anchorX: Double = 0.5, anchorY: Double = 0.5
    ): DisplayOffset {
        val (sx, sy) = ProjectionUtils.geoToScreen(
            displayLat, displayLon, bitmapW, bitmapH, mag, frameLat, frameLon, dpi
        )
        val ox = sx - bitmapW / 2.0
        val oy = sy - bitmapH / 2.0
        val cosA = cos(angle)
        val sinA = sin(angle)
        val oxR = ox * cosA - oy * sinA
        val oyR = ox * sinA + oy * cosA
        // Coerce to zero: during an orientation change the frame in hand can
        // offer no overrun margin on the swapped axis, and a negative margin
        // would make the clamp below an empty (throwing) range.
        val marginX = ((bitmapW - canvasW) / 2.0).coerceAtLeast(0.0)
        val marginY = ((bitmapH - canvasH) / 2.0).coerceAtLeast(0.0)
        // Anchor offset in the viewport frame: the anchor is defined in the
        // surface frame, so its rotated-frame offset is (fx-0.5)*W, (fy-0.5)*H.
        val anchorOX = (anchorX - 0.5) * canvasW
        val anchorOY = (anchorY - 0.5) * canvasH
        val driftX = oxR - anchorOX
        val driftY = oyR - anchorOY
        return DisplayOffset(
            driftX, driftY,
            driftX.coerceIn(-marginX, marginX), driftY.coerceIn(-marginY, marginY)
        )
    }
}
