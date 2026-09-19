package com.naviveylin.core

/**
 * Shared geometry and palette for the unified vehicle position marker.
 *
 * Single source of truth consumed by BOTH renderers:
 * - phone: `app/.../ui/map/LocationMarkerOverlay.kt` (Compose canvas)
 * - Android Auto: `auto/.../AutoMapRenderer.kt` (android.graphics)
 *
 * The arrow is described in a normalized local frame where the arrow points
 * up (negative y in canvas coordinates) and `H` is half the marker size. All
 * shape constants are fractions of H; renderers multiply by their own
 * dp -> px density (`SIZE_DP * density` / `SIZE_DP.dp.toPx()`), which makes
 * the marker the same visual size on every screen ("density-aware" in the
 * specs `gps-location-marker` / `auto-map-renderer`).
 *
 * Layering (bottom to top): blurred shadow (screen-space offset below) ->
 * casing ring (core scaled — white in day, deep blue-black in dark
 * presentation so no stencil-white halo remains) -> dark rim stroke ->
 * vertical blue gradient core (the standard blue in day, a lighter blue in
 * dark presentation so the marker reads on dark land).
 *
 * Only the PALETTE branches on presentation (casing and gradient stops); the
 * geometry never does — vertices, casing scale, rim width and shadow are
 * identical in both presentations (spec scenarios "Arrow legible on
 * daylight/dark map", "Marker geometry identical in both presentations").
 */
object VehicleMarkerGeometry {

    /** Marker footprint in dp — same on phone and Android Auto. */
    const val SIZE_DP = 38f

    /** Half-width of the arrow body at the center line (x = +/-this*H, y = 0). */
    const val BODY_HALF_W = 0.5f

    /** Tail tip offset along the arrow axis (y = +this*H in canvas coords). */
    const val TAIL_Y = 0.35f

    /** Half-width of the tail (narrows back toward the tail tip). */
    const val TAIL_HALF_W = 0.25f

    /**
     * Tip-cap rounding: the two edges that meet at the tip end at
     * `(x = +/-TIP_ROUND_T*BODY_HALF_W, y = (TIP_ROUND_T-1)*H)` and join via a
     * quadratic curve with the tip point `(0, -H)` as control — a rounded,
     * parabolic cap on both renderers.
     */
    const val TIP_ROUND_T = 0.08f

    /** White casing ring: core outline scaled about the center by this factor. */
    const val CASING_SCALE = 1.16f

    /**
     * Dark-mode casing: a fixed white ring reads as a bright stencil outline on
     * dark map land (user feedback on `unified-vehicle-marker`). In dark
     * presentation the casing switches to a deep blue-black that blends with
     * the road, so the silhouette stays clean-cut while the white ring keeps
     * the daylight contrast. See `COLOR_CASING` / `COLOR_CASING_DARK`.
     */
    const val COLOR_CASING_DARK = 0xFF0E1622

    /**
     * Dark-presentation core gradient stops. User feedback: on dark land the
     * near-black casing plus the dark end of the day gradient made the whole
     * marker read as a dark blob. The dark core is therefore markedly lighter
     * than the day core while deliberately NOT white — a white core would read
     * as the same stencil halo the dark casing exists to avoid.
     */
    const val COLOR_GRADIENT_TOP_DARK = 0xFFBBDEFB
    const val COLOR_GRADIENT_BOTTOM_DARK = 0xFF1E88E5

    /** Dark rim stroke width, as a fraction of H. */
    const val RIM_WIDTH_H = 0.10f

    /** Soft shadow: offset (dp) and blur radius (dp). */
    const val SHADOW_OFFSET_DP = 3f
    const val SHADOW_BLUR_DP = 3f

    // Palette (ARGB). Core is a vertical gradient, light from above.
    const val COLOR_GRADIENT_TOP = 0xFF42A5F5
    const val COLOR_GRADIENT_BOTTOM = 0xFF0D47A1
    const val COLOR_CASING = 0xFFFFFFFF
    const val COLOR_RIM = 0xFF0D47A1
    const val COLOR_SHADOW = 0x66000000 // 40% black

    /**
     * Rounded-cap outline vertices in H units, local frame (arrow up).
     * Renderer contract: moveTo(v[0]); quadTo(0, -H, v[1]); then lineTo
     * through v[2]..v[6]; close. Both renderers build their platform `Path`
     * from exactly this list, so the rendered shape is identical.
     */
    fun outlineVertices(): List<Pair<Float, Float>> = listOf(
        (-BODY_HALF_W * TIP_ROUND_T) to (TIP_ROUND_T - 1f),
        (BODY_HALF_W * TIP_ROUND_T) to (TIP_ROUND_T - 1f),
        BODY_HALF_W to 0f,
        TAIL_HALF_W to TAIL_Y,
        0f to TAIL_Y,
        (-TAIL_HALF_W) to TAIL_Y,
        (-BODY_HALF_W) to 0f
    )

    /**
     * Presentation palette for the marker core gradient: the light-from-above
     * vertical stops, standard blue in day and the lighter dark core in dark
     * presentation. Renderers pick the pair with their resolved dark flag; the
     * geometry they draw with it is the same in both cases.
     */
    fun gradientColors(dark: Boolean): Pair<Long, Long> = if (dark) {
        COLOR_GRADIENT_TOP_DARK to COLOR_GRADIENT_BOTTOM_DARK
    } else {
        COLOR_GRADIENT_TOP to COLOR_GRADIENT_BOTTOM
    }

    /** Convenience: vertices scaled by H (screen px) — used by tests and debug. */
    fun outlineVerticesPx(h: Float): List<Pair<Float, Float>> =
        outlineVertices().map { (x, y) -> x * h to y * h }
}
