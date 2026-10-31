package com.naviveylin.core

/**
 * Palette for the two app-drawn overlays that are not the vehicle marker: the accuracy circle and
 * the mini map's current-position marker.
 *
 * Single source of truth consumed by BOTH surfaces:
 * - phone: `app/.../ui/map/LocationMarkerOverlay.kt` (accuracy circle, Compose canvas) and
 *   `app/.../ui/map/MiniMap.kt` (current-position marker, Compose canvas)
 * - Android Auto: `auto/.../AutoMapRendererMarkers.kt` (accuracy circle, android.graphics paints)
 *
 * It sits beside [VehicleMarkerGeometry] because that object already states the invariant this one
 * keeps: the shape/palette of an app-drawn overlay lives in `:core` and both renderers read it, so
 * phone and car cannot drift (`guidelines/UI.md` §8, spec `cross-variant-ui-parity`).
 *
 * Only the PALETTE branches on presentation; the geometry never does — the accuracy circle's radius
 * and stroke width, and the current-position marker's radii and draw order, are identical in both
 * presentations (spec: `gps-location-marker`, `mini-map`).
 *
 * The branch follows the rule of `dark-mode` "Which app-drawn elements stay visible in dark
 * presentation": dark presentation darkens the map and keeps few loud elements, so an element keeps
 * a clearly visible tone only when it carries navigation-essential information.
 * - the accuracy circle does not — it dims to a dark tone of its own hue family, so its two
 *   surfaces stay one shared value instead of the diverging `#4A90D9` / `#2196F3` pair;
 * - the current-position dot does — it is where the user is, so it stays visible in both
 *   presentations, and its hue leaves the neighbourhood of the object-marker roles (the theme's
 *   `primary`/`secondary`/`tertiary`, see `app/.../ui/theme/Color.kt` and `Theme.kt`) which it
 *   collided with before (`0xFF1A73E8` was literally `PrimaryLight`).
 *
 * Values are ARGB in the low 32 bits, like [VehicleMarkerGeometry]'s palette, so
 * `Color(value)` (Compose) and `paint.color = value.toInt()` (android.graphics) read the same
 * constant.
 */
object GpsMarkerPalette {

    // ---- Accuracy circle -------------------------------------------------------------------
    // Not navigation-essential: it dims in dark presentation, keeping the hue family of the
    // shipped light blue (~211°) while dropping its tone.

    /** Light presentation: the shipped fill, `#4A90D9` at ~10 %. */
    const val ACCURACY_FILL = 0x1A4A90D9L

    /** Light presentation: the shipped border, `#4A90D9` at ~40 %. */
    const val ACCURACY_BORDER = 0x664A90D9L

    /** Dark presentation: `#2E6DA4` (hue 208°, the same blue family) at ~10 %. */
    const val ACCURACY_FILL_DARK = 0x1A2E6DA4L

    /** Dark presentation: `#2E6DA4` at ~40 % — dimmer than [ACCURACY_BORDER] over the same land. */
    const val ACCURACY_BORDER_DARK = 0x662E6DA4L

    // ---- Mini map current-position marker ---------------------------------------------------
    // Navigation-essential (the user's own position): visible in both presentations, with the halo
    // and the casing dimmed so the dot stays the brightest part of the marker.

    /** Light presentation dot: `#0B57D0` — deliberately not the theme's `primary` (`#1A73E8`). */
    const val POSITION_DOT = 0xFF0B57D0L

    /** Dark presentation dot: `#4DA3FF`, a light tone of the same family, lighter than its halo. */
    const val POSITION_DOT_DARK = 0xFF4DA3FFL

    /** Light presentation inner highlight: the shipped white centre. */
    const val POSITION_DOT_CORE = 0xFFFFFFFFL

    /** Dark presentation inner highlight: a light tint instead of white (no bright centre blob). */
    const val POSITION_DOT_CORE_DARK = 0xFFE8F1FFL

    /** Light presentation halo: the dot at 20 %. */
    const val POSITION_DOT_HALO = 0x330B57D0L

    /** Dark presentation halo: the dot at 16 % — dimmed, so the dot reads as the marker's centre. */
    const val POSITION_DOT_HALO_DARK = 0x294DA3FFL

    /** Light presentation casing ring: the shipped white ring. */
    const val POSITION_DOT_CASING = 0xFFFFFFFFL

    /**
     * Dark presentation casing ring: the vehicle marker's deep blue-black
     * ([VehicleMarkerGeometry.COLOR_CASING_DARK], hue ~216°, i.e. the same family as the dot), so no
     * white ring glares on dark land.
     */
    const val POSITION_DOT_CASING_DARK = 0xFF0E1622L

    /** Accuracy-circle fill for the resolved presentation. */
    fun accuracyFill(dark: Boolean): Long = if (dark) ACCURACY_FILL_DARK else ACCURACY_FILL

    /** Accuracy-circle border for the resolved presentation. */
    fun accuracyBorder(dark: Boolean): Long = if (dark) ACCURACY_BORDER_DARK else ACCURACY_BORDER

    /** Current-position dot for the resolved presentation. */
    fun positionDot(dark: Boolean): Long = if (dark) POSITION_DOT_DARK else POSITION_DOT

    /** Current-position inner highlight for the resolved presentation. */
    fun positionDotCore(dark: Boolean): Long = if (dark) POSITION_DOT_CORE_DARK else POSITION_DOT_CORE

    /** Current-position halo for the resolved presentation. */
    fun positionDotHalo(dark: Boolean): Long = if (dark) POSITION_DOT_HALO_DARK else POSITION_DOT_HALO

    /** Current-position casing ring for the resolved presentation. */
    fun positionDotCasing(dark: Boolean): Long = if (dark) POSITION_DOT_CASING_DARK else POSITION_DOT_CASING

    /**
     * Every value this palette draws with, for one presentation — the surface a test can walk to
     * prove that no overlay colour is presentation-independent (spec `dark-mode`, "No overlay keeps
     * a light literal while dark presentation is active").
     */
    fun values(dark: Boolean): List<Long> = listOf(
        accuracyFill(dark),
        accuracyBorder(dark),
        positionDot(dark),
        positionDotCore(dark),
        positionDotHalo(dark),
        positionDotCasing(dark)
    )
}
