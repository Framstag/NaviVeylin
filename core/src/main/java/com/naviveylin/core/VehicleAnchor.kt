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
 * Map center that projects the vehicle geo position to the [anchor] screen
 * fraction under the current viewport rotation.
 *
 * Mirror trick (same as `PaneOffset.paneOffsetCenter`): with a viewport
 * centered on [vehicleLat]/[vehicleLon], the geo point occupying the screen
 * pixel (1−fx)·W, (1−fy)·H is the center that moves the vehicle to (fx·W,
 * fx·H) — a screen-space mirror because re-centering on that geo projects the
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
    anchor: VehicleAnchorPosition,
    mag: Double,
    screenW: Int, screenH: Int,
    dpi: Double,
    angle: Double = 0.0
): Pair<Double, Double> {
    val vp = ProjectionUtils.viewport(vehicleLat, vehicleLon, mag, screenW, screenH, dpi, angle)
    return vp.screenToGeoRotated((1.0 - anchor.fx) * screenW, (1.0 - anchor.fy) * screenH)
}

/**
 * Screen-space offset of the anchor from the surface center in pixels,
 * in the viewport (rotated) frame. The anchor is defined in the surface
 * frame, so its rotated-frame offset is the plain (fx−0.5)·W, (fy−0.5)·H.
 */
fun anchorOffsetPx(anchor: VehicleAnchorPosition, screenW: Int, screenH: Int): Pair<Double, Double> =
    (anchor.fx - 0.5) * screenW to (anchor.fy - 0.5) * screenH
