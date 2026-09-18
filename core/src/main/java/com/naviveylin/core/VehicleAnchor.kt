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
         * of surface color, so the collision mapping clamps into this band.
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
 * Resolved anchor screen fraction (0..1 from the top-left): the position the
 * vehicle is kept at after collision resolution against the surface's own
 * overlays (see [resolveAnchorFraction]).
 */
data class ResolvedAnchor(
    /** Horizontal screen fraction from the left edge. */
    val fx: Double,
    /** Vertical screen fraction from the top edge. */
    val fy: Double
)

/**
 * Half of the marker footprint (marker half-size plus clearance padding) used by
 * the collision test in [resolveAnchorFraction], as a fraction of the SHORTER
 * surface side. A fraction tracks the real marker across densities (both the
 * 32dp marker and the screen size scale with density: 24dp ≈ 0.06 of a 1080px
 * wide surface at ~2.6x), and stays meaningful on small test surfaces. It is a
 * threshold only: it decides WHICH presets count as covered; variation moves the
 * boundary a few px either way and the `0.1..0.9` clamp keeps every outcome inside
 * the render-safe band.
 */
private const val ANCHOR_FOOTPRINT_HALF_FRACTION: Double = 0.06

private fun anchorFootprintHalfPx(screenW: Int, screenH: Int): Double =
    (ANCHOR_FOOTPRINT_HALF_FRACTION *
        minOf(screenW.coerceAtLeast(0), screenH.coerceAtLeast(0))).coerceAtLeast(1.0)

/**
 * Resolve a preset into a screen fraction under the **collision rule**: the preset
 * keeps its exact fraction unless the marker — its footprint plus padding
 * ([anchorFootprintHalfPx]) — would fall inside a region one of the surface's own
 * overlays covers ([leftPx]/[topPx]/[rightPx]/[bottomPx], 0 = nothing measured).
 * A covered preset moves, per axis independently, to the nearest free position on
 * that axis (covered-region edge plus the grid's 10% margin), so e.g. bottom-center
 * moves up above the routing-status card while its horizontal fraction stays exactly
 * 50%, and bottom-right moves on BOTH axes. Non-covered presets — including the
 * default center/center with navigation overlays measured — resolve to their exact
 * fraction, so the default framing is the pre-feature framing in every mode and
 * orientation.
 *
 * The result is clamped to [VehicleAnchorPosition.MIN_FRACTION]..
 * [VehicleAnchorPosition.MAX_FRACTION] (the overrun-margin band), so the
 * anchor-centered framing can never uncover a strip.
 *
 * A 2026-09-16 device report (vehicle left of canvas center in portrait navigation
 * with default anchors) showed the earlier visible-area *rescale* moving every preset
 * into the reduced visible rect; collision-remap replaced it.
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
    val footprintHalf = anchorFootprintHalfPx(screenW, screenH)
    val fx = resolveAnchorAxis(anchor.fx, screenW, leftPx, rightPx, footprintHalf)
    val fy = resolveAnchorAxis(anchor.fy, screenH, topPx, bottomPx, footprintHalf)
    return ResolvedAnchor(
        fx.coerceIn(VehicleAnchorPosition.MIN_FRACTION, VehicleAnchorPosition.MAX_FRACTION),
        fy.coerceIn(VehicleAnchorPosition.MIN_FRACTION, VehicleAnchorPosition.MAX_FRACTION)
    )
}

/**
 * One-axis half of [resolveAnchorFraction]: keep the fraction exact unless the
 * marker footprint overlaps a covered band on this axis, otherwise move to the
 * nearest free position (covered edge + the grid's 10% margin measured against the
 * visible extent of this axis).
 */
private fun resolveAnchorAxis(
    fraction: Double,
    size: Int,
    leadingPx: Int,
    trailingPx: Int,
    footprintHalfPx: Double
): Double {
    val leading = leadingPx.coerceAtLeast(0)
    val trailing = trailingPx.coerceAtLeast(0)
    if (leading == 0 && trailing == 0) return fraction
    val px = fraction * size
    val half = footprintHalfPx
    val overlapsLeading = px - half < leading
    val overlapsTrailing = px + half > size - trailing
    if (!overlapsLeading && !overlapsTrailing) return fraction
    val visible = (size - leading - trailing).coerceAtLeast(1).toDouble()
    val freeLeading = (leading + VehicleAnchorPosition.MIN_FRACTION * visible) / size
    val freeTrailing = (size - trailing - VehicleAnchorPosition.MIN_FRACTION * visible) / size
    return when {
        overlapsLeading && !overlapsTrailing -> freeLeading
        overlapsTrailing && !overlapsLeading -> freeTrailing
        // Both bands overlap the footprint at once (absurd insets): nearest free edge.
        else -> {
            val distLeading = (px - half) - leading
            val distTrailing = (size - trailing) - (px + half)
            if (distLeading <= distTrailing) freeLeading else freeTrailing
        }
    }
}

/**
 * Move a preset out of a region the **host** covers (its route-status/turn panel) —
 * the Android Auto case.
 *
 * The host panel is wide and asymmetric (40% of the surface width on the leading
 * edge, left in LTR and right in RTL). It is the same collision rule the phone uses
 * for its own overlays ([resolveAnchorFraction]), specialized to the panel: the panel
 * spans the surface height, so only the left/right axes are passed. A preset that
 * would land inside the panel band moves to the nearest free position (the band edge
 * plus the same 10% margin the grid uses at the surface edge), while presets outside
 * the band — including the default center/center — keep their exact fraction, so the
 * AA default framing is unchanged. Semantics: a side preset means "as far to that
 * side as the visible surface allows".
 */
fun clampAnchorOutOfPane(
    anchor: VehicleAnchorPosition,
    paneLeftPx: Int = 0,
    paneRightPx: Int = 0,
    screenW: Int,
    screenH: Int
): ResolvedAnchor = resolveAnchorFraction(
    anchor,
    leftPx = paneLeftPx.coerceIn(0, screenW),
    rightPx = paneRightPx.coerceIn(0, screenW),
    screenW = screenW,
    screenH = screenH
)

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
 * [anchorCenter] for a preset that resolves without visible-area mapping (the
 * identity case: Android Auto uses [clampAnchorOutOfPane] for the host panel,
 * browse mode, and any surface that measures no overlay).
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
