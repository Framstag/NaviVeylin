package com.naviveylin.ui.route

import com.framstag.libosmscout.client.RouteEntry
import kotlin.math.abs

/**
 * Leg values of one route step (spec: `osmscout-jni` — Per-step leg values on a calculated route).
 *
 * [distanceMeters] is the route distance from the previous instruction's manoeuvre to this one and
 * [durationSeconds] the travel time of that same leg, so the values of a route's steps add up to
 * the route's overall distance and duration. They are never a distance or time measured between two
 * route nodes that carry no instruction (owner finding, 2026-10-04: a step of a 17,3 km route
 * reported "14 m" and "2 s", the last geometry edge before its manoeuvre).
 */
data class RouteStepValues(
    val distanceMeters: Double,
    val durationSeconds: Double
)

/**
 * Per-step leg values of a route result, index-aligned with the step list built from
 * [RouteEntry.descriptions] (spec: `osmscout-jni`).
 *
 * Returns an empty list — never a shifted or partial one — when the route carries no per-step
 * values, or when the arrays do not cover exactly the instruction lines. A mismatch means the native
 * alignment guard dropped them; a partial list would attribute one step's numbers to another, so no
 * values are better than wrong ones. A caller that gets an empty list omits the values (or falls back
 * to the native description's bracket), instead of showing a number that belongs elsewhere.
 */
fun instructionValues(route: RouteEntry): List<RouteStepValues> {
    val descriptions = route.descriptions ?: return emptyList()
    val distances = route.instructionDistances ?: return emptyList()
    val times = route.instructionTimes ?: return emptyList()

    val instructionCount = descriptions.count { isInstructionLine(it) }
    if (instructionCount == 0) return emptyList()
    if (distances.size != instructionCount || times.size != instructionCount) return emptyList()

    return (0 until instructionCount).map { index ->
        RouteStepValues(distances[index], times[index])
    }
}

/**
 * The route's length in metres (spec: `osmscout-jni` — One route length for a calculated route).
 *
 * This is the one place a surface reads a route's total length from, so the card's statistic, the
 * step list and the route summary cannot state two lengths for one route again (design D3 of
 * `fix-route-length-disagreement`: the bridge published the router's own distance while the step
 * list summed the description's legs, and the two disagreed by up to 45 %, 2026-10-05).
 *
 * The sum of the per-step legs is preferred whenever the route carries aligned ones — that makes
 * the displayed total literally the number the step list below it adds up to, which is the
 * invariant this spec states — and the native total ([RouteEntry.distance]) is the fallback for a
 * route whose description produced no per-step values.
 */
internal fun routeLengthMeters(route: RouteEntry): Double {
    val legs = instructionValues(route)
    if (legs.isNotEmpty()) {
        val sum = legs.sumOf { it.distanceMeters }
        if (sum > 0.0) return sum
    }
    return route.distance
}

/**
 * What a route's steps add up to against the route's own totals, as one coordinate-free diagnostics line
 * (spec: `route-analysis` — Step values describe the step's own leg; spec: `auto-diagnostics` —
 * Diagnostics carry no coordinates). Numbers only: a step count, the summed and the route's own distance
 * and duration, and the distance error.
 */
internal fun stepValuesSummary(
    steps: List<RouteStepDisplay>,
    totalMeters: Double,
    totalSeconds: Double
): String {
    val sumMeters = steps.sumOf { it.distanceMeters }
    val sumSeconds = steps.sumOf { it.durationSeconds }
    return "route analysis: steps=${steps.size} withValues=${steps.count { it.hasLegValues }} " +
        "sumM=${sumMeters.toInt()} totalM=${totalMeters.toInt()} " +
        "sumS=${sumSeconds.toInt()} totalS=${totalSeconds.toInt()} " +
        "maxErrM=${abs(sumMeters - totalMeters).toInt()}"
}

/**
 * Whether the steps' distances diverge from the route's total distance (spec: `route-analysis` — Step
 * values describe the step's own leg).
 *
 * The route's total and the steps come from the same description since `fix-route-length-disagreement`
 * (`TODO.md` §129/§139): the bridge publishes the description's own total, so the sum of the legs and
 * the route's total are equal up to the rounding of the per-step values, and this bound is the
 * regression guard for that — it fires when a second source for the route's length comes back.
 *
 * The bound separates the measured defect from rounding with room to spare: before the fix the gap was
 * 25 % on a ~70 km route and 45 % on a 1,5 km one (`TODO.md` §139), while a bridge that measures between
 * route nodes instead of between instructions misses by two orders of magnitude (a few hundred metres
 * against 17,3 km, measured 2026-10-04). A two-percent band is therefore ~12x tighter than the defect it
 * must catch and still well above the rounding of metre-valued legs (a 100 m route's 5 m rounding is the
 * tightest case the suite pins).
 */
internal fun stepValuesDiverge(steps: List<RouteStepDisplay>, totalMeters: Double): Boolean =
    abs(steps.sumOf { it.distanceMeters } - totalMeters) > maxOf(10.0, totalMeters * 0.02)
