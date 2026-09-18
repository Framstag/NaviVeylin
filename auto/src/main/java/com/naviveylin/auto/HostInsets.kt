package com.naviveylin.auto

import android.graphics.Rect

/**
 * Host/system chrome insets derived from the host's stable area (design D8,
 * street-name-host-views): the bands the host chrome covers at the surface
 * top and bottom (e.g. the AAOS status bar at the top and the AAOS task bar
 * at the bottom). Both are 0 when the host delivers no stable area (or one
 * spanning the full surface) — the guaranteed-visible band is then the whole
 * surface and callers fall back to real-edge anchoring.
 *
 * Top inset = the stable-area top edge (chrome above it); bottom inset = the
 * surface height minus the stable-area bottom edge (chrome below it). Same
 * input the follow-anchor bottom clamp already uses (hostBottomInset, change
 * anchor-per-surface-visible-area); this makes it a shared pair for the
 * follow anchor AND the street-name pill so both resolve the same band.
 */
internal object HostInsets {

    /**
     * [Pair] of (topInset, bottomInset) in surface pixels for [stableArea]
     * against a surface of [surfaceHeight] rows; (0, 0) when the stable area
     * is empty or spans the full surface.
     */
    fun fromStableArea(surfaceHeight: Int, stableArea: Rect): Pair<Int, Int> {
        if (stableArea.isEmpty()) return 0 to 0
        val top = stableArea.top.coerceAtLeast(0)
        val bottom = (surfaceHeight - stableArea.bottom).coerceAtLeast(0)
        return top to bottom
    }

    /**
     * Top inset for the street pill: the currently-visible top (the REAL
     * coverage — the stable area can over-reserve a top band the host never
     * draws, which would push the label too far down), falling back to the
     * stable-area top, then 0 when neither rect is known.
     */
    fun topInset(visibleArea: Rect, stableArea: Rect): Int = when {
        !visibleArea.isEmpty() -> visibleArea.top.coerceAtLeast(0)
        !stableArea.isEmpty() -> stableArea.top.coerceAtLeast(0)
        else -> 0
    }
}
