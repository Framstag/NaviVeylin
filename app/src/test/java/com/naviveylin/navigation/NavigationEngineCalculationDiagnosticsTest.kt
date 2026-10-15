package com.naviveylin.navigation

import com.naviveylin.core.FailureReason
import com.naviveylin.core.NavigationFailure
import com.naviveylin.core.RouteEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the route-calculation measurement entry (spec: `route-calculation-feedback` — *Route
 * calculation is measured without coordinates*, every scenario of it).
 *
 * The line is a pure function of what the engine knows ([routeCalculationDoneLine]), so the entry's shape
 * is asserted here instead of by reading the diagnostics file: its reader is `internal` to `:core` and a
 * polling test would be a wall-clock wait, which `checkNoWallClockWaits` refuses.
 */
class NavigationEngineCalculationDiagnosticsTest {

    private fun line(outcome: String,
                     reachedPercent: Int? = null,
                     failure: NavigationFailure? = null,
                     progressCount: Int = 3,
                     durationMillis: Long = 1_234L) =
        routeCalculationDoneLine(
            source = "acquisition",
            durationMillis = durationMillis,
            progressCount = progressCount,
            outcome = outcome,
            reachedPercent = reachedPercent,
            failure = failure
        )

    @Test
    fun entryOnASuccessfulCalculation() {
        val entry = line(outcome = "ok", reachedPercent = 99)

        assertTrue(entry, entry.contains("outcome=ok"))
        assertTrue("the trigger is recorded", entry.contains("source=acquisition"))
        assertTrue("the duration is recorded", entry.contains("duration=1234ms"))
        assertTrue("the progress count is recorded", entry.contains("percents=3"))
        assertTrue("nothing failed, so no reason", entry.contains("reason=-"))
    }

    @Test
    fun entryOnAFailedCalculation() {
        val entry = line(
            outcome = "error",
            reachedPercent = 41,
            failure = NavigationFailure(reason = FailureReason.NO_ROUTE_FOUND)
        )

        assertTrue(entry, entry.contains("outcome=error"))
        assertTrue("the cause is recorded", entry.contains("reason=NO_ROUTE_FOUND"))
    }

    @Test
    fun entryOnACancelledCalculation() {
        val entry = line(outcome = "cancelled", reachedPercent = 12)

        assertTrue(entry, entry.contains("outcome=cancelled"))
        assertTrue("a cancellation is not a failure, so it carries no reason", entry.contains("reason=-"))
    }

    @Test
    fun theReasonIsIdentifiableFromTheEntryAlone() {
        // The two failures that used to be one sentence: the entry alone tells them apart — the reason
        // names the cause, reachedPct says how far the search got, and the distance names how far the
        // failing end is from a usable road.
        val unattachable = line(
            outcome = "error",
            reachedPercent = 0,
            failure = NavigationFailure(
                reason = FailureReason.TARGET_UNROUTABLE,
                endpoint = RouteEndpoint.TARGET,
                nearestRoadDistanceMeters = 7_000.0
            )
        )
        val noRoute = line(
            outcome = "error",
            reachedPercent = 41,
            failure = NavigationFailure(reason = FailureReason.NO_ROUTE_FOUND)
        )

        assertTrue(unattachable, unattachable.contains("reason=TARGET_UNROUTABLE"))
        assertTrue(unattachable, unattachable.contains("nearestRoadM=7000"))
        assertTrue("no progress at all: the walk never started", unattachable.contains("reachedPct=0"))

        assertTrue(noRoute, noRoute.contains("reason=NO_ROUTE_FOUND"))
        assertTrue("the search covered part of the distance", noRoute.contains("reachedPct=41"))
        assertTrue("no failing end, so no distance", noRoute.contains("nearestRoadM=-1"))

        assertFalse(
            "one entry cannot describe both failures",
            unattachable.substringAfter("reason=").substringBefore(" nearest") ==
                noRoute.substringAfter("reason=").substringBefore(" nearest")
        )
    }

    @Test
    fun noCoordinatesInTheEntry() {
        // Every field is an identity or a number. The entry is built from the engine's own values and this
        // case pins the shape: a decimal latitude/longitude pair would look like "52.52, 13.40".
        val entry = line(
            outcome = "error",
            reachedPercent = 7,
            failure = NavigationFailure(reason = FailureReason.START_UNROUTABLE, nearestRoadDistanceMeters = 350.0)
        )

        assertFalse("no coordinate-shaped pair in the entry: $entry",
            Regex("""-?\d+\.\d+\s*,\s*-?\d+\.\d+""").containsMatchIn(entry))
        assertFalse("no coordinate field name in the entry: $entry",
            listOf("lat=", "lon=", "latitude", "longitude").any { entry.contains(it) })
    }

    @Test
    fun aCalculationThatReportedNoProgressSaysSo() {
        val entry = line(outcome = "error", reachedPercent = null)

        assertTrue(
            "an unreported progress is -1, not a fabricated 0: $entry",
            entry.contains("reachedPct=-1")
        )
    }

    @Test
    fun anUnknownFailureStillNamesSomething() {
        val entry = line(outcome = "error", failure = NavigationFailure(reason = FailureReason.UNKNOWN))

        assertTrue("the entry never leaves the cause blank: $entry", entry.contains("reason=UNKNOWN"))
    }
}
