package com.naviveylin.navigation

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Shortest distance in meters from a point to a polyline given as parallel
 * latitude/longitude arrays (spec: reroute-trigger — Distance-based fast path).
 *
 * Uses an equirectangular projection centered on the query point, which is
 * accurate for the short distances involved in off-route detection (< a few km).
 * Mirrors the haversine math in [NavigationViewModel.computeRouteDistance].
 *
 * @return distance in meters, or [Double.POSITIVE_INFINITY] when the polyline
 *   has fewer than 2 points or the arrays differ in size.
 */
fun distanceToPolyline(
    lat: Double,
    lon: Double,
    lats: DoubleArray,
    lons: DoubleArray
): Double {
    if (lats.size < 2 || lats.size != lons.size) {
        return Double.POSITIVE_INFINITY
    }
    var min = Double.POSITIVE_INFINITY
    for (i in 0 until lats.size - 1) {
        val d = distanceToSegment(lat, lon, lats[i], lons[i], lats[i + 1], lons[i + 1])
        if (d < min) min = d
    }
    return min
}

/**
 * Shortest distance in meters from a point to a segment (lat1/lon1 → lat2/lon2),
 * computed in an equirectangular plane centered on the query point.
 */
private fun distanceToSegment(
    lat: Double,
    lon: Double,
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val r = 6371000.0
    val degToRad = Math.PI / 180.0
    val cosLat = cos(Math.toRadians(lat))

    // Project segment endpoints into meters relative to the query point (0,0).
    val x1 = (lon1 - lon) * cosLat * degToRad * r
    val y1 = (lat1 - lat) * degToRad * r
    val x2 = (lon2 - lon) * cosLat * degToRad * r
    val y2 = (lat2 - lat) * degToRad * r

    val dx = x2 - x1
    val dy = y2 - y1
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0.0) 0.0 else (-(x1 * dx + y1 * dy)) / len2
    val tc = t.coerceIn(0.0, 1.0)
    val cx = x1 + tc * dx
    val cy = y1 + tc * dy
    return sqrt(cx * cx + cy * cy)
}
