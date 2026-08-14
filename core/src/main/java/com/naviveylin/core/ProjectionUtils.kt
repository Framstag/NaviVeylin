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
     * Compute projection scale factors.
     */
    fun computeScale(mag: Int, viewWidth: Double, dpi: Double): ProjectionScale {
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
        mag: Int,
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
        mag: Int,
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
        mag: Int,
        centerLat: Double, centerLon: Double,
        dpi: Double
    ): Pair<Double, Double> = viewport(centerLat, centerLon, mag, screenW, screenH, dpi)
        .screenToGeo(screenX, screenY)

    /**
     * Compute new map center after a drag delta.
     */
    fun dragDeltaToNewCenter(
        dx: Double, dy: Double,
        mag: Int,
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
        mag: Int,
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
        oldMag: Int, newMag: Int,
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
    val mag: Int,
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
    fun zoomScale(newMag: Int): Double = 2.0.pow(newMag - mag)
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
    frontBufferMag: Int, newMag: Int,
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
