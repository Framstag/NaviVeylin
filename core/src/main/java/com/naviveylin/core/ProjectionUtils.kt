package com.naviveylin.core

import kotlin.math.*

/**
 * Shared Mercator projection utilities used by MapRenderer and gesture handlers.
 *
 * All methods use the WGS84 ellipsoid (Earth radius 6378137m) and match the
 * projection formula used by libosmscout's MercatorProjection.
 */
object ProjectionUtils {

    /** Earth radius in meters (WGS84). */
    const val EARTH_RADIUS = 6378137.0

    /** Reference DPI for tile resolution calculation. */
    const val REFERENCE_DPI = 96.0

    /**
     * Projection scale factors for a given magnification and viewport width.
     */
    data class ProjectionScale(val scale: Double, val scaleGradtorad: Double)

    /**
     * Compute projection scale factors. [mag] is a fractional magnification
     * scale factor (2^z); integer levels are valid doubles.
     */
    fun computeScale(mag: Double, viewWidth: Double, dpi: Double): ProjectionScale {
        val extentMeter = 2.0 * PI * EARTH_RADIUS
        val magnif = 2.0.pow(mag)
        val equatorTileWidth = extentMeter / magnif
        val equatorTileResolution = equatorTileWidth / 256.0
        val equatorCorrectedResolution = equatorTileResolution * REFERENCE_DPI / dpi
        val groundWidthEquatorMeter = viewWidth * equatorCorrectedResolution
        val scale = viewWidth / (2.0 * PI * groundWidthEquatorMeter / extentMeter)
        val scaleGradtorad = scale * PI / 180.0
        return ProjectionScale(scale, scaleGradtorad)
    }

    /**
     * Stateless helper: create a [ProjectedViewport] for the given parameters.
     */
    fun viewport(
        centerLat: Double, centerLon: Double,
        mag: Double,
        screenW: Int, screenH: Int,
        dpi: Double,
        angle: Double = 0.0
    ): ProjectedViewport = ProjectedViewport(centerLat, centerLon, mag, angle, screenW, screenH, dpi)

    /**
     * Convert geographic coordinates to screen pixel coordinates.
     */
    fun geoToScreen(
        lat: Double, lon: Double,
        screenW: Int, screenH: Int,
        mag: Double,
        centerLat: Double, centerLon: Double,
        dpi: Double
    ): Pair<Double, Double> = viewport(centerLat, centerLon, mag, screenW, screenH, dpi)
        .geoToScreen(lat, lon)

    /**
     * Convert screen pixel coordinates to geographic coordinates.
     */
    fun screenToGeo(
        screenX: Double, screenY: Double,
        screenW: Int, screenH: Int,
        mag: Double,
        centerLat: Double, centerLon: Double,
        dpi: Double
    ): Pair<Double, Double> = viewport(centerLat, centerLon, mag, screenW, screenH, dpi)
        .screenToGeo(screenX, screenY)

    /**
     * Compute new map center after a drag delta.
     */
    fun dragDeltaToNewCenter(
        dx: Double, dy: Double,
        mag: Double,
        viewWidth: Double, viewHeight: Double,
        centerLat: Double, centerLon: Double,
        dpi: Double
    ): Pair<Double, Double> {
        val ps = computeScale(mag, viewWidth, dpi)
        val latOffset = atanh(sin(Math.toRadians(centerLat)))
        val newLon = centerLon - dx / ps.scaleGradtorad
        val newLat = Math.toDegrees(asin(tanh(dy / ps.scale + latOffset)))
        return Pair(newLat, newLon)
    }

    /**
     * Compute new map center after a drag delta in a rotated viewport.
     *
     * Matches [dragDeltaToNewCenter] at angle = 0. The screen delta is
     * rotated by the viewport angle (same convention as the native
     * MercatorProjection: geo = R(angle) * screen in the east-north frame)
     * before being converted to a geo offset.
     */
    fun dragDeltaToNewCenterRotated(
        dx: Double, dy: Double,
        angle: Double,
        mag: Double,
        viewWidth: Double, viewHeight: Double,
        centerLat: Double, centerLon: Double,
        dpi: Double
    ): Pair<Double, Double> {
        val ps = computeScale(mag, viewWidth, dpi)
        val latOffset = atanh(sin(Math.toRadians(centerLat)))
        val cosA = cos(angle)
        val sinA = sin(angle)
        // New center = geo point opposite the drag direction, rotated by the
        // viewport angle. For angle 0 this reduces to (-dx, +dy) which matches
        // dragDeltaToNewCenter.
        val geoEast = -dx * cosA - dy * sinA
        val geoNorth = -dx * sinA + dy * cosA
        val newLon = centerLon + geoEast / ps.scaleGradtorad
        val newLat = Math.toDegrees(asin(tanh(geoNorth / ps.scale + latOffset)))
        return Pair(newLat, newLon)
    }

    /**
     * Compute new map center after a zoom centered on a cursor position.
     */
    fun zoomAtCursor(
        cursorX: Double, cursorY: Double,
        oldMag: Double, newMag: Double,
        viewW: Double, viewH: Double,
        centerLat: Double, centerLon: Double,
        dpi: Double
    ): Pair<Double, Double> {
        val (cursorLat, cursorLon) = screenToGeo(
            cursorX, cursorY,
            viewW.toInt(), viewH.toInt(),
            oldMag, centerLat, centerLon, dpi
        )
        val newPs = computeScale(newMag, viewW, dpi)
        val newLatOffset = atanh(sin(Math.toRadians(cursorLat)))
        val dx = cursorX - viewW / 2.0
        val dy = viewH / 2.0 - cursorY
        val newCenterLon = cursorLon - dx / newPs.scaleGradtorad
        val newCenterLat = Math.toDegrees(asin(tanh(newLatOffset - dy / newPs.scale)))
        return Pair(newCenterLat, newCenterLon)
    }

    /**
     * Compute the new map center after a combined rotate+zoom gesture anchored
     * at a focal point (the finger midpoint — spec map-rotation-gesture:
     * Rotation anchored at the finger midpoint).
     *
     * The committed viewport must equal the pre-gesture viewport transformed by
     * the gesture: rotate by [rotationDelta] and scale by `2^(newMag − oldMag)`
     * around the focal point. The geographic point that ends up at the screen
     * center is the one that was at screen position
     * `p = M + (1/s)·R(−Δ)·(C − M)` before the gesture, so the new center is
     * `screenToGeoRotated(p)` with the pre-gesture viewport. Reduces exactly to
     * [zoomAtCursor] when [rotationDelta] = 0.
     */
    fun rotateZoomAtFocalPoint(
        focalX: Double, focalY: Double,
        oldMag: Double, newMag: Double,
        rotationDelta: Double,
        viewW: Double, viewH: Double,
        centerLat: Double, centerLon: Double,
        angle: Double, dpi: Double
    ): Pair<Double, Double> {
        val s = 2.0.pow(newMag - oldMag)
        val cx = viewW / 2.0
        val cy = viewH / 2.0
        val cosD = cos(rotationDelta)
        val sinD = sin(rotationDelta)
        // Inverse gesture transform: the screen point that lands on the screen
        // center after rotating by Δ and scaling by s around the focal point.
        // R(−Δ) in screen coords (y down): [cosΔ, sinΔ; −sinΔ, cosΔ].
        val dx = cx - focalX
        val dy = cy - focalY
        val px = focalX + (cosD * dx + sinD * dy) / s
        val py = focalY + (-sinD * dx + cosD * dy) / s
        return viewport(centerLat, centerLon, oldMag, viewW.toInt(), viewH.toInt(), dpi, angle)
            .screenToGeoRotated(px, py)
    }

    /**
     * Compute the on-screen bearing of a direction arrow on a rotated map.
     */
    fun screenBearing(rawBearingDegrees: Double, mapAngleRadians: Double): Double {
        var result = rawBearingDegrees + Math.toDegrees(mapAngleRadians)
        result = result.mod(360.0)
        return when {
            result < 0 -> result + 360.0
            result >= 360.0 -> result - 360.0
            else -> result
        }
    }

    /**
     * Screen direction of north, in degrees clockwise from screen-up (0° = up,
     * 90° = right), for a map rendered at [mapAngleRadians].
     *
     * Convention: the native MercatorProjection angle is counter-clockwise in
     * math coordinates, but on a screen (y-down) the rendered map's north glyph
     * sits at `+angle` clockwise from up — the same convention [screenBearing]
     * applies (verified by the GPS marker arrow and its tests). Heading-up
     * follow mode stores `angle = -bearing`, so a westbound heading (270°,
     * angle ≡ +90°) puts north 90° clockwise from up — the driver's right.
     * Compass needles and roses SHALL use this function, never an inline
     * negation, so the sign convention cannot drift again.
     */
    fun compassRotationDegrees(mapAngleRadians: Double): Double {
        var result = Math.toDegrees(mapAngleRadians).mod(360.0)
        return when {
            result < 0 -> result + 360.0
            result >= 360.0 -> result - 360.0
            else -> result
        }
    }

    /**
     * Inverse hyperbolic tangent.
     */
    fun atanh(x: Double): Double = 0.5 * ln((1.0 + x) / (1.0 - x))
}

/**
 * Shared Mercator projection state used by gesture handlers and the renderer.
 */
data class ProjectedViewport(
    val centerLat: Double,
    val centerLon: Double,
    val mag: Double,
    val angle: Double,
    val screenW: Int,
    val screenH: Int,
    val dpi: Double
) {
    private val ps: ProjectionUtils.ProjectionScale = ProjectionUtils.computeScale(mag, screenW.toDouble(), dpi)
    private val latOffset: Double = ProjectionUtils.atanh(sin(Math.toRadians(centerLat)))

    /** Convert geographic coordinates to screen pixel coordinates (north-up). */
    fun geoToScreen(lat: Double, lon: Double): Pair<Double, Double> {
        val cx = (lon - centerLon) * ps.scaleGradtorad
        val cy = -(ProjectionUtils.atanh(sin(Math.toRadians(lat))) - latOffset) * ps.scale
        return Pair(screenW / 2.0 + cx, screenH / 2.0 + cy)
    }

    /**
     * Convert geographic coordinates to screen pixel coordinates with map rotation.
     */
    fun geoToScreenRotated(lat: Double, lon: Double): Pair<Double, Double> {
        val x = (lon - centerLon) * ps.scaleGradtorad
        val yNative = (ProjectionUtils.atanh(sin(Math.toRadians(lat))) - latOffset) * ps.scale
        val c = cos(angle)
        val s = sin(angle)
        val rx = x * c + yNative * s
        val ry = -x * s + yNative * c
        return Pair(screenW / 2.0 + rx, screenH / 2.0 - ry)
    }

    /** Convert screen pixel coordinates to geographic coordinates (north-up). */
    fun screenToGeo(screenX: Double, screenY: Double): Pair<Double, Double> {
        val cx = screenX - screenW / 2.0
        val cy = screenH / 2.0 - screenY
        val lon = centerLon + cx / ps.scaleGradtorad
        val lat = Math.toDegrees(asin(tanh(cy / ps.scale + latOffset)))
        return Pair(lat, lon)
    }

    /**
     * Convert screen pixel coordinates to geographic coordinates with map rotation.
     */
    fun screenToGeoRotated(screenX: Double, screenY: Double): Pair<Double, Double> {
        var rx = screenX - screenW / 2.0
        var ryNative = screenH / 2.0 - screenY
        val c = cos(angle)
        val s = sin(angle)
        val x = rx * c - ryNative * s
        val yNative = rx * s + ryNative * c
        val lon = centerLon + x / ps.scaleGradtorad
        val lat = Math.toDegrees(asin(tanh(yNative / ps.scale + latOffset)))
        return Pair(lat, lon)
    }

    /** Scale factor from one magnification to another at the same viewport/DPI. */
    fun zoomScale(newMag: Double): Double = 2.0.pow(newMag - mag)
}

/**
 * Source and destination rectangles for drawing a zoom placeholder from a front buffer.
 */
data class PlaceholderRects(
    val srcX: Double, val srcY: Double,
    val srcW: Double, val srcH: Double,
    val dstX: Double, val dstY: Double,
    val dstW: Double, val dstH: Double
)

/**
 * Compute placeholder draw rectangles for a zoom transition.
 */
fun computeZoomPlaceholderRects(
    frontBufferW: Int, frontBufferH: Int,
    screenW: Int, screenH: Int,
    frontBufferMag: Double, newMag: Double,
    newCenterLat: Double, newCenterLon: Double,
    frontBufferLat: Double, frontBufferLon: Double,
    dpi: Double
): PlaceholderRects {
    val zoomScale = 2.0.pow(newMag - frontBufferMag)
    val vp = ProjectionUtils.viewport(frontBufferLat, frontBufferLon, frontBufferMag,
        frontBufferW, frontBufferH, dpi)
    val (ncix, nciy) = vp.geoToScreen(newCenterLat, newCenterLon)

    return if (zoomScale >= 1) {
        val srcW = screenW / zoomScale
        val srcH = screenH / zoomScale
        var srcX = ncix - srcW / 2.0
        var srcY = nciy - srcH / 2.0
        srcX = srcX.coerceIn(0.0, (frontBufferW - srcW).coerceAtLeast(0.0))
        srcY = srcY.coerceIn(0.0, (frontBufferH - srcH).coerceAtLeast(0.0))
        PlaceholderRects(
            srcX, srcY,
            srcW.coerceAtMost(frontBufferW.toDouble()), srcH.coerceAtMost(frontBufferH.toDouble()),
            0.0, 0.0,
            screenW.toDouble(), screenH.toDouble()
        )
    } else {
        val dstW = frontBufferW * zoomScale
        val dstH = frontBufferH * zoomScale
        val dstX = screenW / 2.0 - ncix * zoomScale
        val dstY = screenH / 2.0 - nciy * zoomScale
        PlaceholderRects(
            0.0, 0.0,
            frontBufferW.toDouble(), frontBufferH.toDouble(),
            dstX, dstY,
            dstW, dstH
        )
    }
}
