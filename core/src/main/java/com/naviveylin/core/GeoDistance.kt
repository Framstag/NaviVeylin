package com.naviveylin.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Earth radius in meters, matching the haversine implementations in the renderers and the navigation code. */
private const val EARTH_RADIUS_METERS = 6371000.0

/**
 * Great-circle (haversine) distance between two coordinates in meters.
 * Returns [Double.POSITIVE_INFINITY] when any coordinate is NaN.
 *
 * Single implementation for the whole app (`guidelines/Design.md` §4 — one
 * source of truth per signal): the phone search list, the route-panel picker,
 * the Android Auto search rows and the search result ranking all compare
 * distances, so a second copy would let the displayed number disagree with the
 * order it explains.
 */
fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    if (lat1.isNaN() || lon1.isNaN() || lat2.isNaN() || lon2.isNaN()) return Double.POSITIVE_INFINITY
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1 - a))
}
