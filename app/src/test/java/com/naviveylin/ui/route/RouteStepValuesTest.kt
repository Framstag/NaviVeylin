package com.naviveylin.ui.route

import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's reading of the route's row values (spec: `route-analysis` — Step values describe the
 * step's own leg) and the diagnostics line the analysis records for them.
 *
 * Pure unit tests: [RouteEntry] and [RouteStep] are plain value holders and no client is
 * constructed, so no native library is loaded.
 */
class RouteStepValuesTest {

    private fun displays(steps: List<RouteStep>): List<RouteStepDisplay> =
        routeRows(steps).map { row ->
            RouteStepDisplay(
                instruction = "",
                distanceMeters = row.distanceMeters,
                durationSeconds = row.durationSeconds
            )
        }

    @Test
    fun `a row's values are its own plus the merged legs`() {
        val rows = routeRows(
            listOf(
                startStep(),
                turnStep(com.framstag.libosmscout.client.TurnType.STRAIGHT_ON, legDistance = 120.0, legTime = 12.0),
                step(RouteStep.Kind.INFORMATIVE, lat = 52.5240, legDistance = 80.0, legTime = 8.0),
                targetStep(legDistance = 900.0, legTime = 90.0)
            )
        )

        assertEquals(200.0, rows[1].distanceMeters, 1e-6)
        assertEquals(20.0, rows[1].durationSeconds, 1e-6)
        assertEquals(200.0, rows[1].values.distanceMeters, 1e-6)
    }

    @Test
    fun `a row without a leg shows no numbers`() {
        val rows = routeRows(listOf(startStep(), targetStep(legDistance = 500.0)))

        assertFalse("the start line owns no leg", rows.first().hasLegValues)
        assertEquals(0.0, rows.first().distanceMeters, 1e-6)
        assertTrue(rows.last().hasLegValues)
    }

    @Test
    fun `rows sum to the route length`() {
        val steps = listOf(
            startStep(),
            turnStep(com.framstag.libosmscout.client.TurnType.LEFT, legDistance = 500.0, legTime = 60.0),
            turnStep(
                com.framstag.libosmscout.client.TurnType.RIGHT,
                lat = 52.5250, lon = 13.4090, legDistance = 700.0, legTime = 70.0
            ),
            targetStep(legDistance = 400.0, legTime = 40.0)
        )
        val route = routeWithSteps(*steps.toTypedArray())
        val rows = routeRows(steps)

        assertEquals(routeLengthMeters(route), rows.sumOf { it.distanceMeters }, 1e-6)
        assertEquals(1600.0, rows.sumOf { it.distanceMeters }, 1e-6)
    }

    @Test
    fun `the summary names counts, sums and the error`() {
        val steps = listOf(
            RouteStepDisplay("Start", 0.0, 0.0),
            RouteStepDisplay("Left", 900.0, 60.0),
            RouteStepDisplay("Arrive", 600.0, 45.0)
        )

        val summary = stepValuesSummary(steps, totalMeters = 1520.0, totalSeconds = 108.0)

        assertTrue(summary, summary.startsWith("route analysis: steps=3 withValues=2"))
        assertTrue(summary, summary.contains("sumM=1500 totalM=1520"))
        assertTrue(summary, summary.contains("sumS=105 totalS=108"))
        assertTrue(summary, summary.contains("maxErrM=20"))
        // Coordinate-free: the entry carries numbers only (spec: auto-diagnostics).
        assertFalse(summary, Regex("""\d{1,3}\.\d{5,}""").containsMatchIn(summary))
    }

    @Test
    fun `a fragment-sized sum diverges`() {
        // The pre-fix signature, measured on device: 19 steps between route nodes summed to a few
        // hundred metres on a 17,3 km route.
        val fragments = List(19) { RouteStepDisplay("step $it", 32.0, 5.0) }
        assertTrue(stepValuesDiverge(fragments, totalMeters = 17_300.0))

        val legs = List(19) { RouteStepDisplay("step $it", 17_300.0 / 19.0, 60.0) }
        assertFalse(stepValuesDiverge(legs, totalMeters = 17_300.0))
    }

    @Test
    fun `rounding of a short route does not count as a divergence`() {
        val rows = listOf(
            RouteStepDisplay("Start", 0.0, 0.0),
            RouteStepDisplay("Left", 95.0, 5.0)
        )
        assertFalse(stepValuesDiverge(rows, totalMeters = 100.0))
    }

    @Test
    fun `the measured defect's own pair counts as a divergence`() {
        // The numbers the change removed, measured on device 2026-10-05 (Dortmund Hbf -> Cologne Hbf):
        // the route total reported 72 771 m while the legs summed to 97 416 m. The tightened bound must
        // catch exactly this, which the 50 % band it replaces did not have to (TODO.md §129/§139).
        val legs = listOf(
            RouteStepDisplay("Start", 0.0, 0.0),
            RouteStepDisplay("Leg", 97_416.0, 4_100.0)
        )
        assertTrue(
            "72 771 m reported against 97 416 m of legs must count as a divergence",
            stepValuesDiverge(legs, totalMeters = 72_771.0)
        )

        // And the short route's pre-fix pair: 824 m reported against legs of 1 487 m (45 % short).
        val shortLegs = listOf(
            RouteStepDisplay("Start", 0.0, 0.0),
            RouteStepDisplay("Leg", 1_487.0, 200.0)
        )
        assertTrue(
            "824 m reported against 1 487 m of legs must count as a divergence",
            stepValuesDiverge(shortLegs, totalMeters = 824.0)
        )
    }

    @Test
    fun `an agreeing pair does not count as a divergence`() {
        // What the bridge publishes now: the total IS the legs' sum.
        val legs = listOf(
            RouteStepDisplay("Start", 0.0, 0.0),
            RouteStepDisplay("Leg", 97_416.0, 4_100.0)
        )
        assertFalse(stepValuesDiverge(legs, totalMeters = 97_416.0))
        // A metre of rounding per leg stays inside the band on a long route ...
        assertFalse(stepValuesDiverge(legs, totalMeters = 97_418.0))
    }

    // --- The route's own length (spec: osmscout-jni — One route length for a calculated route) ---

    @Test
    fun `the route's length is what its rows add up to`() {
        // 500 m + 1000 m of legs against a native total of 21011: the sum wins, so the statistic the
        // card shows is the number the row list below it adds up to.
        val route = routeWithSteps(
            startStep(),
            turnStep(com.framstag.libosmscout.client.TurnType.LEFT, legDistance = 500.0, legTime = 30.0),
            targetStep(legDistance = 1000.0, legTime = 60.0),
            distance = 21_011.0
        )

        assertEquals(1500.0, routeLengthMeters(route), 1e-6)
    }

    @Test
    fun `a route's length follows the legs, not the native total`() {
        // The defect this replaces, measured on device 2026-10-05 (Dortmund Hbf -> Cologne Hbf): the
        // native total reported 72 771 m while the legs summed to 97 416 m. The displayed length must
        // not be the figure that disagrees with the rows (TODO.md §129/§139).
        val route = routeWithSteps(
            startStep(),
            turnStep(com.framstag.libosmscout.client.TurnType.LEFT, legDistance = 48_000.0, legTime = 2_000.0),
            targetStep(legDistance = 49_416.0, legTime = 2_100.0),
            distance = 72_771.0
        )

        assertEquals(97_416.0, routeLengthMeters(route), 1e-6)
    }

    @Test
    fun `a route without steps falls back to the native total`() {
        val legacy = RouteEntry().apply {
            distance = 21_011.0
            steps = null
        }
        assertEquals(21_011.0, routeLengthMeters(legacy), 1e-6)

        // A step list whose legs are all zero carries no length either: the native total is the
        // better answer than a sum of zeros.
        val zeroLegs = routeWithSteps(startStep(), targetStep(), distance = 21_011.0)
        assertEquals(21_011.0, routeLengthMeters(zeroLegs), 1e-6)

        assertEquals(21_011.0, routeLengthMeters(RouteEntry().apply { distance = 21_011.0 }), 1e-6)
    }
}
