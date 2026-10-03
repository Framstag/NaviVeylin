package com.naviveylin.ui.map

import com.naviveylin.core.ProjectionUtils

/**
 * Whether [bbox] stays inside the visible map area at [mag], with the camera
 * centered on ([cameraLat], [cameraLon]) — the geo point that will land on the
 * visible-area center.
 *
 * The camera center is deliberately **not** assumed to be the bbox midpoint: the
 * area-favorite fit parks the camera on the favorite coordinate and the POI fit
 * parks it on the POI, while `computeAreaZoom` solves "the bbox fits in 80 % when
 * the bbox midpoint is the camera center". A fit therefore has to be verified
 * against the camera it will actually use (found while landing this seam: both
 * non-route callers were off-centre, which clips at an exact fit, not only at a
 * rounded one).
 *
 * `ProjectionUtils.viewport` is the renderer's own projection, so the check is
 * exact for any viewport rotation — the rotated screen hull of a Mercator bbox
 * has its extremes at the bbox corners, which is what is projected here. The
 * visible band is the canvas minus the [coveredPx] a sheet covers (the route
 * panel reports its height; the other fits pass 0): with `coveredPx == 0` the
 * band is the whole canvas.
 *
 * Pure arithmetic on the main thread — no suspension, no IO, no renderer call,
 * and no allocation of its own beyond what `ProjectionUtils.viewport` already
 * makes. Four corner projections per call, so callers may loop over it.
 *
 * @param bbox double[4] = [minLat, maxLat, minLon, maxLon]
 * @param mag magnification to test (fractional values allowed)
 * @param angleRad viewport rotation in radians; 0 for a north-locked map
 * @param coveredPx height in pixels a sheet covers at the bottom of the canvas (0 = none)
 */
internal fun fitsVisibleArea(
    bbox: DoubleArray,
    cameraLat: Double,
    cameraLon: Double,
    mag: Double,
    width: Int,
    height: Int,
    dpi: Double,
    angleRad: Double = 0.0,
    coveredPx: Int = 0
): Boolean {
    if (bbox.size < 4) return false
    if (width <= 0 || height <= 0) return false
    if (cameraLat.isNaN() || cameraLon.isNaN()) return false

    val bandTop = coveredPx / 2.0
    val bandBottom = bandTop + (height - coveredPx)

    val vp = ProjectionUtils.viewport(cameraLat, cameraLon, mag, width, height, dpi, angleRad)
    for (latIndex in 0..1) {
        val lat = bbox[latIndex]
        for (lonIndex in 2..3) {
            val (x, y) = vp.geoToScreenRotated(lat, bbox[lonIndex])
            if (x < 0.0 || x > width.toDouble()) return false
            if (y < bandTop || y > bandBottom) return false
        }
    }
    return true
}

/**
 * The magnification a fit should apply: `computeAreaZoom`'s rounded result,
 * stepped one whole level out while [fitsVisibleArea] does not hold for it.
 *
 * Whole-level rounding can move an exact fit up by up to half a level (2^0.5 ≈
 * 1.41x more content than the 80 % target — past the viewport), and a rotated
 * viewport or an off-centre camera can need more room still. The loop is the
 * price of never clipping: a fit may land a level or two further out, a strictly
 * wider view, instead of hiding part of what it was asked to show.
 *
 * The fit itself is computed against the **visible** height (`height -
 * coveredPx`), as the route overview does; the verification then tests that same
 * band. The camera center does not enter the magnification formula — it is only
 * what the verification projects around.
 *
 * Stepping stops at [minZoom], the caller's floor: the area-favorite fit keeps
 * the documented area-favorites floor, the route overview and the POI fits pass
 * the render-stability minimum (`MapCanvasViewModel.MIN_MAG`) so a distant POI
 * can still be shown together with the current location. A floor-reached fit is
 * the accepted end state; callers must not read a returned floor as "verified
 * to fit".
 *
 * Pure arithmetic; callers run it where they already mutate the viewport (main
 * dispatcher) or, for the embedded result map, inside its composition. No new
 * state, coroutine or job is involved.
 *
 * @return magnification clamped to `[minZoom, MAX_MAG]`
 */
internal fun verifiedAreaFit(
    bbox: DoubleArray,
    cameraLat: Double,
    cameraLon: Double,
    width: Int,
    height: Int,
    dpi: Double,
    minZoom: Double,
    angleRad: Double = 0.0,
    coveredPx: Int = 0
): Double {
    val visibleHeight = height - coveredPx
    // Unknown canvas or a fully covered one: nothing to verify against, so keep
    // computeAreaZoom's own degenerate answer (its NODE_ZOOM / clamp paths) and
    // leave the decision to the caller, which guards these cases itself.
    if (width <= 0 || height <= 0 || visibleHeight <= 0) {
        return MapCanvasViewModel.computeAreaZoom(bbox, width, height, minZoom, dpi)
    }

    var mag = MapCanvasViewModel.computeAreaZoom(bbox, width, visibleHeight, minZoom, dpi)
    while (mag > minZoom &&
        !fitsVisibleArea(bbox, cameraLat, cameraLon, mag, width, height, dpi, angleRad, coveredPx)
    ) {
        mag -= 1.0
    }
    return mag
}
