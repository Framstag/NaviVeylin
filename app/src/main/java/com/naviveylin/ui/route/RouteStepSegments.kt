package com.naviveylin.ui.route

import com.naviveylin.core.haversineDistanceMeters

/**
 * Polyline vertex range belonging to one route row (spec: `route-analysis` — Analysed step is
 * locatable on the map).
 *
 * Rows are the route analysis's **boundaries**, not points (design D2 of
 * `route-panel-from-steps`): row *i* owns the vertices from row *i-1*'s boundary up to its own, the
 * first row starts at the route's first vertex and the last row ends at the route's last vertex. A
 * row's values are the legs of exactly that run, so the numbers a row shows and the segment the map
 * highlights describe the same piece of route.
 *
 * Matching is **monotonic** — a row's vertex is never earlier than the previous row's — because the
 * rows arrive in route order. Without the constraint a manoeuvre near a leg the route visits twice
 * (a spur, a U-turn) could match the earlier visit and highlight the wrong part of the route.
 *
 * Returns one range per row, in row order. A row without a position (fewer anchors than rows, no
 * polyline at all) yields [IntRange.EMPTY] rather than a range borrowed from its neighbour, and so
 * does a row whose leg has no length (the start line sits on the route's first vertex).
 *
 * @param polylineLats route polyline latitudes, ordered from start to destination
 * @param polylineLons route polyline longitudes, same order
 * @param stepCount number of rows in the route's row list
 * @param anchors per-row boundary positions, index-aligned with the row list
 *   (may be shorter than `stepCount` when positions are unavailable)
 */
fun stepSegments(
    polylineLats: DoubleArray,
    polylineLons: DoubleArray,
    stepCount: Int,
    anchors: List<StepAnchor>
): List<IntRange> {
    if (stepCount <= 0) return emptyList()

    val pointCount = minOf(polylineLats.size, polylineLons.size)
    if (pointCount == 0) return List(stepCount) { IntRange.EMPTY }

    val startIndices = IntArray(stepCount) { -1 }
    var searchFrom = 0
    for (step in 0 until minOf(stepCount, anchors.size)) {
        val index = nearestVertexFrom(polylineLats, polylineLons, pointCount, anchors[step], searchFrom)
        startIndices[step] = index
        searchFrom = index
    }

    return List(stepCount) { step ->
        val own = startIndices[step]
        // The previous row's boundary starts this row's leg; the first row begins at the route's
        // first vertex, so the leg before it is empty.
        val from = if (step > 0) startIndices[step - 1] else 0
        // The last row ends at the route's last vertex, so the arrival owns the tail of the polyline
        // and the rows tile it without a gap at the far end either.
        val to = if (step == stepCount - 1) pointCount - 1 else own
        if (own < 0 || from < 0) {
            // No position for this row (or for the one before it): draw nothing rather
            // than highlight a leg whose ends are unknown.
            IntRange.EMPTY
        } else if (to <= from) {
            // Both boundaries sit on the same vertex, or the row is the start line on the
            // route's first vertex: no leg to draw.
            IntRange.EMPTY
        } else {
            from..to
        }
    }
}

/**
 * Nearest polyline vertex at or after [from], using the shared haversine distance
 * so the match agrees with every other distance the app displays.
 */
private fun nearestVertexFrom(
    polylineLats: DoubleArray,
    polylineLons: DoubleArray,
    pointCount: Int,
    anchor: StepAnchor,
    from: Int
): Int {
    var bestIndex = from.coerceIn(0, pointCount - 1)
    var bestDistance = Double.POSITIVE_INFINITY
    for (index in bestIndex until pointCount) {
        val distance = haversineDistanceMeters(anchor.lat, anchor.lon, polylineLats[index], polylineLons[index])
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = index
        }
    }
    return bestIndex
}
