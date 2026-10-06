package com.naviveylin.ui.route

import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.core.DiagnosticsLog

/**
 * The drawable geometry of a route **adopted** from the engine, or `null` when the route carries no
 * usable polyline: the arrays are absent (the bridge hands over platform types, so absent is legal),
 * there are fewer than two points, or the two arrays have different lengths
 * (spec: `route-map-overview` — Degenerate route geometry degrades safely: absent counts as an empty
 * polyline).
 *
 * Start and destination come from the polyline's own ends — an adopted route has no other endpoints.
 * `null` means *publish nothing*: the map keeps the geometry it already displays and the camera is
 * not moved, the rule a failed reroute follows (spec: `reroute-route-visibility` — Failed reroute
 * keeps the last route visible). One coordinate-free diagnostics line records the reason, so a device
 * run can attribute "no route drawn" to data instead of guessing (spec: `auto-diagnostics` —
 * Diagnostics carry no coordinates).
 */
internal fun adoptedRouteGeometry(route: RouteEntry): RouteResult? {
    val polyline = usablePolyline(route) ?: return null
    return RouteResult(
        routeLats = polyline.first,
        routeLons = polyline.second,
        startLat = polyline.first.first(), startLon = polyline.second.first(),
        destLat = polyline.first.last(), destLon = polyline.second.last()
    )
}

/**
 * The geometry result of a **completed calculation**, which is always published: the requested start
 * and target are the endpoints, and coordinates that are absent or too few become an *empty* polyline
 * so the camera falls back to those endpoints
 * (spec: `route-map-overview` — Empty polyline falls back to endpoints). Nothing is drawn for an
 * empty polyline, and no exception is raised for absent coordinates.
 */
internal fun calculatedRouteGeometry(
    route: RouteEntry,
    startLat: Double,
    startLon: Double,
    destLat: Double,
    destLon: Double
): RouteResult {
    val polyline = usablePolyline(route)
    return RouteResult(
        routeLats = polyline?.first ?: DoubleArray(0),
        routeLons = polyline?.second ?: DoubleArray(0),
        startLat = startLat, startLon = startLon,
        destLat = destLat, destLon = destLon
    )
}

/**
 * The route's polyline when it can be drawn — present, two or more points, equal length — otherwise
 * `null`, with the one coordinate-free diagnostics line naming the shape.
 */
private fun usablePolyline(route: RouteEntry): Pair<DoubleArray, DoubleArray>? {
    val lats = route.latitudes
    val lons = route.longitudes
    if (lats == null || lons == null || lats.size < 2 || lats.size != lons.size) {
        logAbsentGeometry(route, lats, lons)
        return null
    }
    return lats to lons
}

/**
 * Counts and shape only — a coordinate, and a coordinate *word*, never reach this line (the
 * `checkNoCoordinatesInLogs` gate scans the call for position identifiers, so `lat`/`lon` must not
 * appear even as a key).
 */
private fun logAbsentGeometry(route: RouteEntry, lats: DoubleArray?, lons: DoubleArray?) {
    val shape = when {
        lats == null && lons == null -> "absent"
        lats == null || lons == null -> "incomplete"
        lats.size != lons.size -> "mismatched ${lats.size}/${lons.size}"
        else -> "${lats.size} points"
    }
    DiagnosticsLog.log(
        DiagnosticsLog.ROUTE_TAG,
        "route geometry absent: descriptions=${route.descriptions?.size ?: 0} polyline=$shape"
    )
}
