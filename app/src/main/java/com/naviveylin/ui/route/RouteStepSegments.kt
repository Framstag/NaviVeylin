package com.naviveylin.ui.route

import com.naviveylin.core.haversineDistanceMeters

/**
 * Polyline vertex range belonging to one route step (spec: `route-analysis`).
 *
 * The route polyline is a densified point list and the manoeuvres are separate
 * description entries, so nothing links them by index. [stepSegments] closes that
 * gap: each manoeuvre is matched to the polyline vertex it happens at, and a step owns the leg
 * that **leads to** its manoeuvre — from the previous manoeuvre's vertex up to its own
 * (owner finding, 2026-10-03: the highlighted leg used to be the one *after* the manoeuvre
 * named in the card). That orientation matches the step's values: a step's distance and duration are
 * the leg that ends at its own manoeuvre (spec: `osmscout-jni` — Per-step leg values on a calculated
 * route), so the row and the highlight describe the same piece of route. Before that change the
 * native side measured them between two consecutive route **nodes**, which is the last geometry edge
 * before the manoeuvre — a step of a 17,3 km route reported "14 m" and "2 s".
 *
 * Matching is **monotonic** — a step's vertex is never earlier than the previous
 * step's — because instructions arrive in route order. Without the constraint a
 * manoeuvre near a leg the route visits twice (a spur, a U-turn) could match the
 * earlier visit and highlight the wrong part of the route.
 *
 * Returns one range per step, in step order. A step without a position (fewer
 * anchors than steps, no polyline at all) yields [IntRange.EMPTY] rather than a
 * range borrowed from its neighbour, and so does a step whose leg has no length
 * (the start line sits on the route's first vertex).
 *
 * @param polylineLats route polyline latitudes, ordered from start to destination
 * @param polylineLons route polyline longitudes, same order
 * @param stepCount number of steps in the route's step list
 * @param anchors per-step manoeuvre positions, index-aligned with the step list
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
        // The previous manoeuvre's vertex starts this step's leg; the start line is the
        // route's first vertex, so the leg before step 0 is empty.
        val from = if (step > 0) startIndices[step - 1] else 0
        if (own < 0 || from < 0) {
            // No position for this manoeuvre (or for the one before it): draw nothing rather
            // than highlight a leg whose ends are unknown.
            IntRange.EMPTY
        } else if (own <= from) {
            // Both manoeuvres sit on the same vertex, or the step is the start line on the
            // route's first vertex: no leg to draw.
            IntRange.EMPTY
        } else {
            from..own
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
