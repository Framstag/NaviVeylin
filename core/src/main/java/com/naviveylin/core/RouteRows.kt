package com.naviveylin.core

import com.framstag.libosmscout.client.RouteStep

/**
 * One row of a route list (spec: `route-analysis` — Rows are derived from the route's steps).
 *
 * A row is not a step: the route's step list is filtered, collapsed and merged into rows, and a row
 * carries the legs of every step it absorbed ([absorbedSteps] counts them). [distanceMeters] and
 * [durationSeconds] are therefore the run from the previous row's manoeuvre up to this row's own —
 * the same run the route panel's segment highlight covers for the row.
 *
 * Shared by both variants: the phone route list and the car route description list the same rows
 * (change `share-route-rows-in-core`), so a policy change lands once.
 */
data class RouteRow(
    val manoeuvre: Manoeuvre,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val absorbedSteps: Int = 0
) {
    /** Whether this row carries leg values at all (the start line owns none). */
    val hasLegValues: Boolean
        get() = distanceMeters > 0.0 || durationSeconds > 0.0
}

/**
 * The row filter's single predicate for the kinds whose policy the census settles (design D5).
 *
 * `informative`, `POIAtRoute` and `via` steps describe facts (a max speed, lanes, a way type, a
 * crossing, a destination) rather than a driving decision, so they never earn a row; their legs are
 * folded into the row above, so the rows still add up. **The census settled this on device
 * (2026-10-28, `RouteRowCensusDeviceTest`)**: 302 of 330 steps on an Andorran city route and 1631 of
 * 1663 on Dortmund → Cologne are `informative`, and of the same routes' steps 18 and 61 carry a POI
 * record (`info_poi`) and none carries a `via` record (`info_via`) — a row per such step would turn a
 * 24-row list into a 330-row one, for facts the row above already carries. Measured by the census
 * line `ROUTE route census:` (`guidelines/Build.md` §10k).
 *
 * The same multiplication governs what a car route description sends to its host: one row per step is
 * one maneuver bitmap per step.
 */
internal fun informativeEarnsRow(): Boolean = false

/**
 * Minimum leg a row must carry to earn its own row, in metres; a street-name change below it is a
 * node artifact rather than a driving decision.
 *
 * The census's floor reading (2026-10-28): `leg_m median` is 7–43 m and `under20` covers 234–353 of
 * each measured route's steps, so most legs are node-sized; the name changes' own legs sit at a
 * median of 2–43 m (`nc_leg_median`) and every one measured was restated by the following manoeuvre
 * (`nc_restated = nc_total`), so the floor removed no decision on the routes measured. 20 m stays —
 * every name change it drops would have been a row whose leg is under 20 m.
 */
const val MIN_ROW_LEG_METERS = 20.0

/**
 * The row-merge window in metres: a street-name change restated by the manoeuvre that follows within
 * this distance is folded into that manoeuvre's row instead of earning its own.
 *
 * The number is the bound live guidance announces with ([HINT_WINDOW_METERS], the native
 * `osmscout::client::RouteStepBuilder::NextNextWindowMeters`), so "close" means one thing app-wide.
 * It stays a second named constant rather than a reused one: the announcement bound and the row-layout
 * rule answer different questions, and a divergence between them needs a measured reason.
 */
const val ROW_MERGE_WINDOW_METERS = HINT_WINDOW_METERS

/**
 * Derive a route list's rows from the route's structured steps
 * (spec: `route-analysis` — Rows are derived from the route's steps).
 *
 * The rules, in the order they are applied:
 *
 * - the start, the arrival, every turn, roundabout and motorway manoeuvre earns a row;
 * - consecutive straight-on turns with no street name between them collapse into one row that
 *   carries all their legs;
 * - a street-name change earns its own row unless the manoeuvre that follows it is closer than
 *   [ROW_MERGE_WINDOW_METERS] **and** names the same street, in which case its leg joins that
 *   manoeuvre's row; a change whose new name is empty or repeats the previous name (folded case and
 *   spacing) never earns a row;
 * - an `informative` step never earns a row, and its leg joins the previous row
 *   ([informativeEarnsRow] is the one predicate to change when the census says otherwise).
 *
 * Every leg is counted exactly once, so the rows' distances still add up to the route's length and
 * the rows still cover it once. The input list is never mutated or shortened: the full step list
 * stays in the navigation state, and the rows are a view of it.
 *
 * Both variants call this: the phone route panel over the whole route, the car route description over
 * the remaining steps, so the two lists cannot drift apart.
 */
fun routeRows(steps: List<RouteStep>): List<RouteRow> {
    val rows = mutableListOf<RouteRow>()
    // Legs of steps that precede the first row (a route whose first step is not its start).
    var leadDistance = 0.0
    var leadTime = 0.0
    // A readable street-name change waiting for the manoeuvre that follows it.
    var pending: Manoeuvre? = null
    var pendingDistance = 0.0
    var pendingTime = 0.0
    // Legs accumulated between that name change and the next manoeuvre, for the window test.
    var gapSincePending = 0.0
    // Whether the row on top was built across a street name, so a straight-on run cannot extend it.
    var nameBoundaryOnTop = false
    var previousName: String? = null

    fun append(row: RouteRow, spansNameChange: Boolean = false) {
        rows += RouteRow(
            manoeuvre = row.manoeuvre,
            distanceMeters = row.distanceMeters + leadDistance,
            durationSeconds = row.durationSeconds + leadTime,
            absorbedSteps = row.absorbedSteps
        )
        leadDistance = 0.0
        leadTime = 0.0
        nameBoundaryOnTop = spansNameChange
    }

    /** Fold a leg into the row above — the previous row's values, as the informative rule states. */
    fun foldIntoRowAbove(distance: Double, time: Double) {
        val last = rows.lastOrNull()
        if (last == null) {
            leadDistance += distance
            leadTime += time
        } else {
            rows[rows.lastIndex] = last.copy(
                distanceMeters = last.distanceMeters + distance,
                durationSeconds = last.durationSeconds + time,
                absorbedSteps = last.absorbedSteps + 1
            )
        }
    }

    for (step in steps) {
        val manoeuvre = manoeuvreFromStep(step)
        val nameBefore = manoeuvre.displayName ?: previousName

        when {
            manoeuvre.kind == RouteStep.Kind.NAME_CHANGE -> {
                if (!readableNameChange(manoeuvre, previousName)) {
                    // An empty or repeated name change carries no decision: its leg joins the row above.
                    foldIntoRowAbove(manoeuvre.legDistanceMeters, manoeuvre.legTimeSeconds)
                } else {
                    // A second readable change before any manoeuvre intervened: the first earns its row.
                    pending?.let { waiting ->
                        append(
                            RouteRow(
                                manoeuvre = waiting,
                                distanceMeters = pendingDistance,
                                durationSeconds = pendingTime
                            )
                        )
                    }
                    pending = manoeuvre
                    pendingDistance = manoeuvre.legDistanceMeters
                    pendingTime = manoeuvre.legTimeSeconds
                    gapSincePending = 0.0
                }
            }

            isDataOnly(manoeuvre) && !informativeEarnsRow() -> {
                if (pending != null) gapSincePending += manoeuvre.legDistanceMeters
                foldIntoRowAbove(manoeuvre.legDistanceMeters, manoeuvre.legTimeSeconds)
            }

            pending != null -> {
                val waiting = pending!!
                pending = null
                val close =
                    gapSincePending + manoeuvre.legDistanceMeters < ROW_MERGE_WINDOW_METERS
                gapSincePending = 0.0
                if (close && namesMatch(waiting, manoeuvre)) {
                    // Restated by the manoeuvre that follows: its leg joins that manoeuvre's row.
                    append(
                        RouteRow(
                            manoeuvre = manoeuvre,
                            distanceMeters = pendingDistance + manoeuvre.legDistanceMeters,
                            durationSeconds = pendingTime + manoeuvre.legTimeSeconds,
                            absorbedSteps = 1
                        ),
                        spansNameChange = true
                    )
                } else {
                    append(
                        RouteRow(
                            manoeuvre = waiting,
                            distanceMeters = pendingDistance,
                            durationSeconds = pendingTime
                        ),
                        spansNameChange = true
                    )
                    append(
                        RouteRow(
                            manoeuvre = manoeuvre,
                            distanceMeters = manoeuvre.legDistanceMeters,
                            durationSeconds = manoeuvre.legTimeSeconds
                        )
                    )
                }
            }

            manoeuvre.isStraightOn &&
                rows.lastOrNull()?.manoeuvre?.isStraightOn == true &&
                !nameBoundaryOnTop -> {
                // A run of straight-on turns is one decision: collapse it into the row already there.
                val last = rows[rows.lastIndex]
                rows[rows.lastIndex] = last.copy(
                    manoeuvre = manoeuvre,
                    distanceMeters = last.distanceMeters + manoeuvre.legDistanceMeters,
                    durationSeconds = last.durationSeconds + manoeuvre.legTimeSeconds,
                    absorbedSteps = last.absorbedSteps + 1
                )
            }

            else -> append(
                RouteRow(
                    manoeuvre = manoeuvre,
                    distanceMeters = manoeuvre.legDistanceMeters,
                    durationSeconds = manoeuvre.legTimeSeconds
                )
            )
        }

        previousName = nameBefore
    }

    // A name change at the very end of the route has no manoeuvre to merge into: it earns its row.
    pending?.let { waiting ->
        append(
            RouteRow(
                manoeuvre = waiting,
                distanceMeters = pendingDistance,
                durationSeconds = pendingTime
            )
        )
    }

    return rows
}

/**
 * Whether a street-name change carries a decision of its own: it names something, that name is not
 * the name the route already had (compared with case and spacing folded), and its leg is long enough
 * to be a decision.
 */
internal fun readableNameChange(manoeuvre: Manoeuvre, previousName: String?): Boolean {
    val new = manoeuvre.displayName?.let(::foldName) ?: return false
    if (new.isEmpty()) return false
    val previous = previousName?.let(::foldName)
    if (previous != null && new == previous) return false
    // A leg too short to be a decision is a node artifact, not a street change.
    return manoeuvre.legDistanceMeters >= MIN_ROW_LEG_METERS
}

/** Whether [next] names the street the [change] introduced. */
fun namesMatch(change: Manoeuvre, next: Manoeuvre): Boolean {
    val introduced = change.displayName?.let(::foldName) ?: return false
    val named = next.displayName?.let(::foldName) ?: return false
    return introduced.isNotEmpty() && introduced == named
}

/** Case- and spacing-insensitive form of a street name — the comparison the row policy folds with. */
fun foldName(name: String): String =
    name.trim().lowercase().replace(Regex("\\s+"), " ")
