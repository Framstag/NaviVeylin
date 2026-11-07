package com.naviveylin.ui.map

import com.naviveylin.core.OverrunWindow

/**
 * The phone follow display's blit offset, extracted from the map screen's frame loop so the anchor it
 * consumes is provable (spec: `smooth-follow` — The follow blit offset is computed by one seam that owns the
 * anchor; `smooth-follow` — Single resolved anchor across render, blit and marker).
 *
 * The seam takes the **whole** map state and reads [MapCanvasUiState.resolvedAnchor] itself, on purpose: an
 * `anchor` parameter would let a caller pass the raw preset ([MapCanvasUiState.activeFollowAnchor]), which
 * is the defect this seam exists to make unexpressible — the frame is rendered anchor-centered on the
 * resolved fraction (the ViewModel's follow render target) and the marker projects against it, so a blit
 * computed against the preset places the displayed content at a different fraction than the marker and
 * keeps the offset outside the overrun margin (500 ms re-render churn). Geometry cases cannot see that: they
 * hand the helper their own fractions, so two fixes to this anchor landed in one day with the suite green
 * (issue #18).
 *
 * [frame] is the frame in hand — its own viewport and bitmap dimensions, the emitted overrun frame — kept as
 * [PanWindowRules.Frame] so this seam carries no Compose or Android type and its geometry case needs no
 * bitmap. The caller's guard decides whether a usable frame exists; a frame that is here is assumed usable.
 */
internal object FollowBlitRules {

    /**
     * The offset the displayed frame must be drawn with to show [displayedLat]/[displayedLon] at the
     * resolved anchor, clamped into the frame's overrun margin. Carries the prediction drift only: the
     * anchor was applied to the render target, never a second time here.
     */
    internal fun offsetFor(
        ui: MapCanvasUiState,
        frame: PanWindowRules.Frame,
        displayedLat: Double,
        displayedLon: Double,
        canvasW: Int,
        canvasH: Int,
        dpi: Double
    ): OverrunWindow.DisplayOffset {
        val anchor = ui.resolvedAnchor
        return OverrunWindow.shiftPx(
            displayedLat, displayedLon,
            frame.lat, frame.lon, frame.mag, frame.angle,
            frame.width, frame.height,
            canvasW, canvasH, dpi,
            anchor.fx, anchor.fy
        )
    }
}
