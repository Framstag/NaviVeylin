package com.naviveylin.ui.route

import com.naviveylin.core.RouteRow

/**
 * The map position of one route row's manoeuvre (spec: `route-analysis` — Analysed step is
 * locatable on the map).
 *
 * Produced by [rowAnchors] from the derived rows; the position belongs to the row with the same index
 * in the row list. It is the row's **boundary**: the row owns the polyline run between the previous
 * row's boundary and this one, so no row's segment overlaps its neighbour's and none of the polyline
 * is left over.
 */
data class StepAnchor(
    val lat: Double,
    val lon: Double
)

/**
 * One boundary position per row, index-aligned with [rows] (spec: `route-analysis` — Analysed step is
 * locatable on the map).
 *
 * A row's position is its manoeuvre's own node coordinate, which is what the bridge publishes as the
 * step's position — no nearest-vertex guess and no prose. The first row is the route's start, so its
 * boundary is the route's first vertex and it owns no leg; the last row is the arrival, so its
 * boundary is the route's last vertex and the last leg ends there.
 *
 * A row whose manoeuvre carries no usable coordinate yields no anchor at all: an empty list is
 * better than a list whose entries address the wrong rows, because a shifted anchor would move every
 * later row to the wrong manoeuvre.
 */
fun rowAnchors(rows: List<RouteRow>): List<StepAnchor> {
    if (rows.isEmpty()) return emptyList()
    val anchors = rows.map { row ->
        val manoeuvre = row.manoeuvre
        StepAnchor(manoeuvre.lat, manoeuvre.lon)
    }
    // A partial list would shift every later row onto the wrong manoeuvre: no anchors are better
    // than anchors that address the wrong rows.
    return if (anchors.all { it.lat.isFinite() && it.lon.isFinite() }) anchors else emptyList()
}
