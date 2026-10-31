package com.naviveylin.ui.route

import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RouteStep
import com.naviveylin.core.MIN_ROW_LEG_METERS
import com.naviveylin.core.Manoeuvre
import com.naviveylin.core.ROW_MERGE_WINDOW_METERS
import com.naviveylin.core.RouteRow
import com.naviveylin.core.foldName
import com.naviveylin.core.isDataOnly
import com.naviveylin.core.manoeuvreFromStep
import com.naviveylin.core.namesMatch
import kotlin.math.abs

/**
 * Leg values of one route row (spec: `osmscout-jni` — A calculated route publishes structured route
 * steps; spec: `route-analysis` — Step values describe the step's own leg).
 *
 * [distanceMeters] is the run from the previous row's manoeuvre to this row's own and
 * [durationSeconds] the travel time of that same run, folded over every step the row absorbed. The
 * values of a route's rows add up to the route's overall distance and duration, and they are never a
 * distance measured between two route nodes that carry no instruction (owner finding, 2026-10-04: a
 * step of a 17,3 km route reported "14 m" and "2 s").
 */
data class RouteStepValues(
    val distanceMeters: Double,
    val durationSeconds: Double
)

/**
 * The route's length in metres (spec: `osmscout-jni` — One route length for a calculated route).
 *
 * This is the one place a surface reads a route's total length from, so the card's statistic, the
 * step list and the route summary cannot state two lengths for one route again (design D3 of
 * `fix-route-length-disagreement`: the bridge published the router's own distance while the list
 * summed the description's legs, and the two disagreed by up to 45 %, 2026-10-05).
 *
 * The legs of the route's structured steps are the source — they are what the rows fold, so the
 * displayed total is literally the number the list below it adds up to. The native total
 * ([RouteEntry.distance]) is the fallback for a route that carries no steps or none with a leg.
 */
internal fun routeLengthMeters(route: RouteEntry): Double {
    val steps = route.steps
    if (steps != null && steps.isNotEmpty()) {
        val sum = steps.sumOf { it.legDistance }
        if (sum > 0.0) return sum
    }
    return route.distance
}

/**
 * What a route's rows add up to against the route's own totals, as one coordinate-free diagnostics
 * line (spec: `route-analysis` — Step values describe the step's own leg; spec: `auto-diagnostics` —
 * Diagnostics carry no coordinates). Numbers only: a row count, the summed and the route's own
 * distance and duration, and the distance error.
 */
internal fun stepValuesSummary(
    rows: List<RouteStepDisplay>,
    totalMeters: Double,
    totalSeconds: Double
): String {
    val sumMeters = rows.sumOf { it.distanceMeters }
    val sumSeconds = rows.sumOf { it.durationSeconds }
    return "route analysis: steps=${rows.size} withValues=${rows.count { it.hasLegValues }} " +
        "sumM=${sumMeters.toInt()} totalM=${totalMeters.toInt()} " +
        "sumS=${sumSeconds.toInt()} totalS=${totalSeconds.toInt()} " +
        "maxErrM=${abs(sumMeters - totalMeters).toInt()}"
}

/**
 * Whether the rows' distances diverge from the route's total distance (spec: `route-analysis` — Step
 * values describe the step's own leg).
 *
 * The route's total and the rows come from the same step model since `fix-route-length-disagreement`
 * (`TODO.md` §129/§139): the bridge publishes the description's own total, so the sum of the rows and
 * the route's total are equal up to the rounding of the per-row values, and this bound is the
 * regression guard for that — it fires when a second source for the route's length comes back.
 *
 * The bound separates the measured defect from rounding with room to spare: before the fix the gap was
 * 25 % on a ~70 km route and 45 % on a 1,5 km one (`TODO.md` §139), while a bridge that measures between
 * route nodes instead of between instructions misses by two orders of magnitude (a few hundred metres
 * against 17,3 km, measured 2026-10-04). A two-percent band is therefore ~12x tighter than the defect it
 * must catch and still well above the rounding of metre-valued legs (a 100 m route's 5 m rounding is the
 * tightest case the suite pins).
 */
internal fun stepValuesDiverge(rows: List<RouteStepDisplay>, totalMeters: Double): Boolean =
    abs(rows.sumOf { it.distanceMeters } - totalMeters) > maxOf(10.0, totalMeters * 0.02)

/**
 * The census of one calculated route's steps and rows, as one coordinate-free diagnostics line
 * (design D6 of `route-panel-from-steps`; spec: `route-analysis` — Rows are derived from the route's
 * steps; spec: `auto-diagnostics` — Diagnostics carry no coordinates).
 *
 * Counts and metres only: kinds, legs, straight-on run lengths, how many name changes the next
 * manoeuvre already restates, and how many informative steps carry nothing but a name. The numbers
 * are what settle the three thresholds the change cannot settle from the code — whether
 * `informative`/`POIAtRoute`/`Via` steps earn rows, and the [MIN_ROW_LEG_METERS] floor — and they
 * prove that roundabout and motorway manoeuvres exist at all on the routes the old list dropped.
 */
internal fun routeCensus(steps: List<RouteStep>, rows: List<RouteRow>): String {
    val manoeuvres = steps.map(::manoeuvreFromStep)
    fun count(predicate: (Manoeuvre) -> Boolean): Int = manoeuvres.count(predicate)
    fun kindCount(kind: RouteStep.Kind): Int = count { it.kind == kind }

    val turn = count { it.kind == RouteStep.Kind.TURN && !it.isStraightOn }
    val straightOn = count { it.isStraightOn }
    val nameChanges = count { it.kind == RouteStep.Kind.NAME_CHANGE }
    val roundabout = count {
        it.kind == RouteStep.Kind.ROUNDABOUT_ENTER || it.kind == RouteStep.Kind.ROUNDABOUT_LEAVE
    }
    val motorway = count {
        it.kind == RouteStep.Kind.MOTORWAY_ENTER ||
            it.kind == RouteStep.Kind.MOTORWAY_CHANGE ||
            it.kind == RouteStep.Kind.MOTORWAY_LEAVE
    }
    val informational = count { isDataOnly(it) }

    fun kilometres(predicate: (Manoeuvre) -> Boolean): String =
        manoeuvres.filter(predicate).sumOf { it.legDistanceMeters }
            .div(1000.0).let { String.format(java.util.Locale.ROOT, "%.1f", it) }

    val legs = steps.map { it.legDistance }
    val straightOnRuns = straightOnRuns(manoeuvres)
    val nameChangeLegs = manoeuvres
        .filter { it.kind == RouteStep.Kind.NAME_CHANGE }
        .map { it.legDistanceMeters }

    val restated = manoeuvres.indices.count { index ->
        val change = manoeuvres[index]
        val next = manoeuvres.getOrNull(index + 1) ?: return@count false
        change.kind == RouteStep.Kind.NAME_CHANGE &&
            next.legDistanceMeters < ROW_MERGE_WINDOW_METERS &&
            namesMatch(change, next)
    }
    // A name change whose name is blank or repeats the previous one, and one that is only too short to
    // be a decision: two different rules, so the census keeps them apart (the floor's effect is what
    // task 5.3 has to read).
    val blankNames = manoeuvres.indices.count { index ->
        val change = manoeuvres[index]
        if (change.kind != RouteStep.Kind.NAME_CHANGE) return@count false
        val new = change.displayName?.let(::foldName)
        val previous = manoeuvres.getOrNull(index - 1)?.displayName?.let(::foldName)
        new.isNullOrEmpty() || (previous != null && new == previous)
    }
    val shortNameChanges = manoeuvres.indices.count { index ->
        val change = manoeuvres[index]
        if (change.kind != RouteStep.Kind.NAME_CHANGE) return@count false
        val new = change.displayName?.let(::foldName)
        val previous = manoeuvres.getOrNull(index - 1)?.displayName?.let(::foldName)
        val blank = new.isNullOrEmpty() || (previous != null && new == previous)
        !blank && change.legDistanceMeters < MIN_ROW_LEG_METERS
    }
    val repeatedRuns = (1 until manoeuvres.size).count { index ->
        manoeuvres[index].kind == RouteStep.Kind.NAME_CHANGE &&
            manoeuvres[index - 1].kind == RouteStep.Kind.NAME_CHANGE
    }
    val nameOnlyInformation = manoeuvres.count {
        isDataOnly(it) && it.displayName != null && it.typeName == null
    }
    // The two records whose row policy the census has to settle next to `informative`.
    val poiSteps = steps.count { it.poi != null }
    val viaSteps = steps.count { it.via != null }

    return buildString {
        append("route census: steps=${steps.size} rows=${rows.size} ")
        append("turn=$turn straightOn=$straightOn nameChange=$nameChanges ")
        append("roundabout=$roundabout motorway=$motorway info=$informational ")
        append("start=${kindCount(RouteStep.Kind.START)} target=${kindCount(RouteStep.Kind.TARGET)} ")
        append("dist_km turn=${kilometres { it.kind == RouteStep.Kind.TURN && !it.isStraightOn }} ")
        append("straightOn=${kilometres { it.isStraightOn }} ")
        append("nameChange=${kilometres { it.kind == RouteStep.Kind.NAME_CHANGE }} ")
        append("info=${kilometres { isDataOnly(it) }} ")
        append("leg_m median=${percentile(legs, 0.5)} p90=${percentile(legs, 0.9)} ")
        append("under20=${legs.count { it < MIN_ROW_LEG_METERS }} ")
        append("so_runs=${straightOnRuns.size} ")
        append("so_len_median=${percentile(straightOnRuns, 0.5)} ")
        append("so_len_max=${straightOnRuns.maxOrNull()?.toInt() ?: 0} ")
        append("nc_total=$nameChanges nc_restated=$restated nc_runs2plus=$repeatedRuns ")
        append("nc_blank=$blankNames nc_short=$shortNameChanges ")
        append("nc_leg_median=${percentile(nameChangeLegs, 0.5)} ")
        append("info_name_only=$nameOnlyInformation info_poi=$poiSteps info_via=$viaSteps")
    }
}

/** The lengths, in metres, of the maximal straight-on runs of a route's manoeuvres. */
private fun straightOnRuns(
    manoeuvres: List<Manoeuvre>
): List<Double> {
    val runs = mutableListOf<Double>()
    var current = 0.0
    var inRun = false
    for (manoeuvre in manoeuvres) {
        if (manoeuvre.isStraightOn) {
            current += manoeuvre.legDistanceMeters
            inRun = true
        } else if (inRun) {
            runs += current
            current = 0.0
            inRun = false
        }
    }
    if (inRun) runs += current
    return runs
}

/** The [p] quantile of [values], rounded to whole units; 0 for an empty list. */
private fun percentile(values: List<Double>, p: Double): Int {
    if (values.isEmpty()) return 0
    val sorted = values.sorted()
    val index = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)
    return sorted[index].toInt()
}
