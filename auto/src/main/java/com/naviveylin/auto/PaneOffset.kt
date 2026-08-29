package com.naviveylin.auto

import com.naviveylin.core.ProjectionUtils

/**
 * Fraction of the surface width covered by the host's pane panel
 * (host-dependent; the car-app API exposes no panel geometry). The host on
 * this class of unit draws the panel on the left, covering ~40% of the
 * surface. Tune per host class if the panel size differs.
 */
internal const val PANE_FRACTION = 0.4

/**
 * Viewport center that projects the given geo point to the center of the
 * visible map area — the part not covered by the host's pane panel.
 *
 * To place the point at screen fraction `d` of the width, the viewport
 * center must be the geo point at the mirrored fraction `1 - d`
 * (screenToGeo returns the geo position at a screen offset from the center;
 * setting that as the new center moves the point the other way). RTL hosts
 * are assumed to mirror the panel to the right.
 */
internal fun paneOffsetCenter(
    centerLat: Double,
    centerLon: Double,
    zoom: Double,
    surfaceWidth: Int,
    surfaceHeight: Int,
    dpi: Double,
    rtl: Boolean
): Pair<Double, Double> {
    val destX = if (rtl) (1.0 - PANE_FRACTION) / 2.0 else (1.0 + PANE_FRACTION) / 2.0
    val centerX = 1.0 - destX
    return ProjectionUtils.screenToGeo(
        surfaceWidth * centerX, surfaceHeight / 2.0,
        surfaceWidth, surfaceHeight,
        zoom, centerLat, centerLon,
        dpi
    )
}
