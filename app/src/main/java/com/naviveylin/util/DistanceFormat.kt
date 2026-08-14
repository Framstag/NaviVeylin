package com.naviveylin.util

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Earth radius in meters, matching the haversine implementations in MapRenderer and MapCanvasViewModel. */
private const val EARTH_RADIUS_METERS = 6371000.0

/**
 * Great-circle (haversine) distance between two coordinates in meters.
 * Returns [Double.POSITIVE_INFINITY] when any coordinate is NaN.
 */
fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    if (lat1.isNaN() || lon1.isNaN() || lat2.isNaN() || lon2.isNaN()) return Double.POSITIVE_INFINITY
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1 - a))
}

/**
 * Format a distance in meters as a kilometer string for display, mirroring
 * upstream `LocationSearchRanker.formatDistanceKm`: sub-10 km keeps one
 * decimal place so nearby results are distinguishable, larger distances round
 * to whole kilometers.
 *
 * @param meters distance in meters
 * @return formatted value with "km" unit suffix, e.g. "0.5 km" or "12 km"
 */
fun formatDistanceKm(meters: Double): String {
    val km = meters / 1000.0
    return if (km < 10.0) {
        String.format(Locale.ROOT, "%.1f km", km)
    } else {
        String.format(Locale.ROOT, "%.0f km", km)
    }
}
