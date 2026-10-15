package com.naviveylin.navigation

import com.naviveylin.core.FailureReason
import com.naviveylin.core.NavigationFailure
import kotlin.math.roundToInt

/**
 * The `ROUTE` diagnostics line for a finished route calculation (spec: `route-calculation-feedback` —
 * Route calculation is measured without coordinates, its "The failing reason is identifiable from the
 * entry alone" scenario).
 *
 * Built here, out of the call that writes it, so the property — coordinate-free, and naming the cause of a
 * failure — is asserted deterministically. Reading the diagnostics file instead would mean polling a
 * background writer, whose flush seam is `internal` to `:core`, and `checkNoWallClockWaits` refuses a
 * wall-clock wait in a unit test (`ki_processing_failures.log`, `add-map-update-check` task 3.5).
 *
 * Numbers and identity only, never a position:
 *
 * - `source` is what asked for the calculation (a coordinate-free identity, e.g. `acquisition`).
 * - `reachedPct` is the routing layer's own progress percentage — how far the search got as a fraction of
 *   the start-to-target air-line distance, its own progress denominator. `-1` means it reported none, and
 *   that is what separates a search that was cut short from one that covered the whole distance and still
 *   found no connection.
 * - `reason` is the failure's cause, `-` when the calculation did not fail. It is what makes the entry
 *   alone enough to tell the two failures that used to read the same.
 * - `nearestRoadM` is the distance from a failing end to the nearest road the router could use, `-1` when
 *   unknown.
 */
internal fun routeCalculationDoneLine(
    source: String,
    durationMillis: Long,
    progressCount: Int,
    outcome: String,
    reachedPercent: Int?,
    failure: NavigationFailure? = null
): String =
    "calc done: source=$source duration=${durationMillis}ms percents=$progressCount" +
        " outcome=$outcome reachedPct=${reachedPercent ?: -1}" +
        " reason=${failure?.reason?.name ?: "-"}" +
        " nearestRoadM=${failure?.nearestRoadDistanceMeters?.roundToInt() ?: -1}"
