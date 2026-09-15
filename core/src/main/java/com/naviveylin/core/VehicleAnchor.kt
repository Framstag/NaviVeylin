package com.naviveylin.core

/**
 * Vehicle anchor position preset: the screen position (fraction of the map
 * surface) at which follow-mode rendering keeps the vehicle marker, instead
 * of the surface center.
 *
 * Fixed 5×3 grid: horizontal 10/30/50/70/90% of the surface width, vertical
 * 10/50/90% of the surface height (15 presets). The grid is deliberate — free
 * form anchor values were rejected by design; the preset keeps the anchor
 * inside the overrun-buffer margins (0.1..0.9) and gives each preset a stable
 * persisted id and a user-facing label shared by the phone and Android Auto
 * pickers (label parity, guidelines/UI.md).
 *
 * Fractions are in the screen frame: [fx] from the left, [fy] from the top;
 * (0.5, 0.5) is the surface center and reproduces the pre-feature framing.
 */
enum class VehicleAnchorPosition(
    /** Stable persistence id (JSON settings; unknown ids fall back to [CENTER]). */
    val id: String,
    /** Horizontal screen fraction from the left edge. */
    val fx: Double,
    /** Vertical screen fraction from the top edge. */
    val fy: Double,
    /** User-facing label (shared by phone and Android Auto pickers). */
    val label: String
) {
    TOP_FAR_LEFT("top-far-left", 0.1, 0.1, "Top far left"),
    TOP_LEFT("top-left", 0.3, 0.1, "Top left"),
    TOP_CENTER("top-center", 0.5, 0.1, "Top center"),
    TOP_RIGHT("top-right", 0.7, 0.1, "Top right"),
    TOP_FAR_RIGHT("top-far-right", 0.9, 0.1, "Top far right"),

    MIDDLE_FAR_LEFT("middle-far-left", 0.1, 0.5, "Middle far left"),
    MIDDLE_LEFT("middle-left", 0.3, 0.5, "Middle left"),
    CENTER("center", 0.5, 0.5, "Center"),
    MIDDLE_RIGHT("middle-right", 0.7, 0.5, "Middle right"),
    MIDDLE_FAR_RIGHT("middle-far-right", 0.9, 0.5, "Middle far right"),

    BOTTOM_FAR_LEFT("bottom-far-left", 0.1, 0.9, "Bottom far left"),
    BOTTOM_LEFT("bottom-left", 0.3, 0.9, "Bottom left"),
    BOTTOM_CENTER("bottom-center", 0.5, 0.9, "Bottom center"),
    BOTTOM_RIGHT("bottom-right", 0.7, 0.9, "Bottom right"),
    BOTTOM_FAR_RIGHT("bottom-far-right", 0.9, 0.9, "Bottom far right");

    companion object {
        /** Default preset: surface center — reproduces the pre-feature framing. */
        val DEFAULT: VehicleAnchorPosition = CENTER

        /**
         * Lowest/highest anchor screen fraction the framing can use. Equal to the
         * overrun margin of the 1.2x frame (`(1.2 - 1) / 2`): an anchor outside it
         * would push the anchor-centered frame past the buffer and uncover a strip
         * of surface color, so the visible-area mapping clamps into this band.
         */
        const val MIN_FRACTION: Double = 0.1
        const val MAX_FRACTION: Double = 0.9

        private val byId = entries.associateBy { it.id }

        /**
         * Resolve a persisted id. Unknown or blank ids (older installs, or a
         * settings file edited by hand) fall back to [DEFAULT] so the feature
         * is additive and cannot crash on stored values.
         */
        fun fromId(id: String?): VehicleAnchorPosition = byId[id] ?: DEFAULT
    }
}

/**
 * Resolved anchor screen fraction (0..1 from the top-left), after visible-area
 * mapping: the position the vehicle is kept at inside the part of the surface
 * the driver can actually see.
 */
data class ResolvedAnchor(
    /** Horizontal screen fraction from the left edge. */
    val fx: Double,
    /** Vertical screen fraction from the top edge. */
    val fy: Double
)

/**
 * Resolve a preset into a screen fraction inside the **visible map area**: the
 * surface minus the regions covered by the surface's own overlays
 * ([leftPx]/[topPx]/[rightPx]/[bottomPx], 0 = nothing measured).
 *
 * With no overlay measured the result is the preset fraction itself, so a
 * surface without measured overlays (and the pre-feature framing) is unchanged.
 * The result is clamped to [VehicleAnchorPosition.MIN_FRACTION]..
 * [VehicleAnchorPosition.MAX_FRACTION] (the overrun-margin band) so the
 * anchor-centered framing can never uncover a strip — with the shipped phone
 * overlays the resolved values sit well inside the band (a 0.9 row resolves to
 * roughly 0.76 with a 0.19/0.18 top/bottom coverage).
 */
fun resolveAnchorFraction(
    anchor: VehicleAnchorPosition,
    leftPx: Int = 0,
    topPx: Int = 0,
    rightPx: Int = 0,
    bottomPx: Int = 0,
    screenW: Int,
    screenH: Int
): ResolvedAnchor {
    if (screenW <= 0 || screenH <= 0) return ResolvedAnchor(anchor.fx, anchor.fy)
    val visibleW = (screenW - leftPx.coerceAtLeast(0) - rightPx.coerceAtLeast(0)).coerceAtLeast(1)
    val visibleH = (screenH - topPx.coerceAtLeast(0) - bottomPx.coerceAtLeast(0)).coerceAtLeast(1)
    val fx = (leftPx.coerceAtLeast(0) + anchor.fx * visibleW) / screenW
    val fy = (topPx.coerceAtLeast(0) + anchor.fy * visibleH) / screenH
    return ResolvedAnchor(
        fx.coerceIn(VehicleAnchorPosition.MIN_FRACTION, VehicleAnchorPosition.MAX_FRACTION),
        fy.coerceIn(VehicleAnchorPosition.MIN_FRACTION, VehicleAnchorPosition.MAX_FRACTION)
    )
}

/**
 * Move a preset out of a region the **host** covers (its route-status/turn panel) —
 * the Android Auto case.
 *
 * The host panel is wide and asymmetric (40% of the surface width on the leading
 * edge, left in LTR), so *remapping* every preset into the remaining strip — what
 * [resolveAnchorFraction] does for the phone's balanced cards — would move the
 * DEFAULT preset off the surface center and break the specified "default anchors
 * reproduce today's framing" contract of both AA capabilities. Instead, a preset
 * that would land inside the covered band moves to the nearest free position (the
 * band edge plus the same 10% margin the grid uses at the surface edge), while
 * presets outside the band keep their exact fraction. Semantics: a side preset means
 * "as far to that side as the visible surface allows".
 */
fun clampAnchorOutOfPane(
    anchor: VehicleAnchorPosition,
    paneLeftPx: Int = 0,
    paneRightPx: Int = 0,
    screenW: Int,
    screenH: Int
): ResolvedAnchor {
    if (screenW <= 0 || screenH <= 0) return ResolvedAnchor(anchor.fx, anchor.fy)
    val left = paneLeftPx.coerceIn(0, screenW)
    val right = paneRightPx.coerceIn(0, screenW)
    val visibleW = (screenW - left - right).coerceAtLeast(1)
    // Position just inside the visible strip with the grid's own edge margin, so a
    // clamped preset is not glued to the panel edge.
    val freeLeft = (left + VehicleAnchorPosition.MIN_FRACTION * visibleW) / screenW
    val freeRight = (left + (1.0 - VehicleAnchorPosition.MIN_FRACTION) * visibleW) / screenW
    val fx = when {
        anchor.fx < freeLeft -> freeLeft
        anchor.fx > freeRight -> freeRight
        else -> anchor.fx
    }
    return ResolvedAnchor(
        fx.coerceIn(VehicleAnchorPosition.MIN_FRACTION, VehicleAnchorPosition.MAX_FRACTION),
        anchor.fy.coerceIn(VehicleAnchorPosition.MIN_FRACTION, VehicleAnchorPosition.MAX_FRACTION)
    )
}

/**
 * Map center that projects the vehicle geo position to the given screen
 * fraction [fx]/[fy] under the current viewport rotation — the follow-mode
 * render target.
 *
 * The follow renderer renders each frame with this center, so the vehicle sits
 * at the anchor *inside the frame* and the display offset between frames is the
 * projection drift only (never a fraction of the surface). Nothing else may
 * apply the anchor again: a second shift of the drawn frame or of the marker
 * would push the frame outside the overrun margin (uncovered strip).
 *
 * Mirror trick (same as `PaneOffset.paneOffsetCenter`): with a viewport
 * centered on [vehicleLat]/[vehicleLon], the geo point occupying the screen
 * pixel (1−fx)·W, (1−fy)·H is the center that moves the vehicle to (fx·W,
 * fy·H) — a screen-space mirror because re-centering on that geo projects the
 * old center to the mirrored pixel. [screenToGeoRotated] carries the rotation:
 * the offset is rotated back by [angle] first, so the vehicle stays pinned at
 * the anchor in heading-up views too.
 *
 * @param angle viewport rotation in radians (native MercatorProjection
 *   convention, same as everywhere in ProjectionUtils)
 * @return the new viewport center (lat, lon)
 */
fun anchorCenter(
    vehicleLat: Double, vehicleLon: Double,
    fx: Double, fy: Double,
    mag: Double,
    screenW: Int, screenH: Int,
    dpi: Double,
    angle: Double = 0.0
): Pair<Double, Double> {
    val vp = ProjectionUtils.viewport(vehicleLat, vehicleLon, mag, screenW, screenH, dpi, angle)
    return vp.screenToGeoRotated((1.0 - fx) * screenW, (1.0 - fy) * screenH)
}

/**
 * [anchorCenter] for a preset without visible-area mapping (the identity case:
 * Android Auto today, browse mode, and any surface that measures no overlay).
 */
fun anchorCenter(
    vehicleLat: Double, vehicleLon: Double,
    anchor: VehicleAnchorPosition,
    mag: Double,
    screenW: Int, screenH: Int,
    dpi: Double,
    angle: Double = 0.0
): Pair<Double, Double> = anchorCenter(
    vehicleLat, vehicleLon, anchor.fx, anchor.fy, mag, screenW, screenH, dpi, angle
)
