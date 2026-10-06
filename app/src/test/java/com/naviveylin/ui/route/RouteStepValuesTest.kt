package com.naviveylin.ui.route

import com.framstag.libosmscout.client.RouteEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's reading of the route's per-step values (spec: `osmscout-jni` — Per-step leg values on a
 * calculated route) and the diagnostics line the analysis records for them (spec: `route-analysis` —
 * Step values describe the step's own leg).
 *
 * Pure unit tests: [RouteEntry] is a plain value holder and no client is constructed, so no native
 * library is loaded.
 */
class RouteStepValuesTest {

    private fun route(
        instructionLines: Int,
        distances: DoubleArray? = null,
        times: DoubleArray? = null,
        nativeDistance: Double = 0.0
    ) = RouteEntry().apply {
        descriptions = Array(instructionLines + 1) { index ->
            if (index == 0) "--- Route ---" else "Turn $index  []"
        }
        instructionDistances = distances
        instructionTimes = times
        distance = nativeDistance
    }

    @Test
    fun `values are index-aligned with the instruction lines`() {
        val values = instructionValues(
            route(
                3,
                doubleArrayOf(0.0, 900.0, 1800.0),
                doubleArrayOf(0.0, 60.0, 120.0)
            )
        )

        assertEquals(3, values.size)
        assertEquals(0.0, values[0].distanceMeters, 1e-6)
        assertEquals(900.0, values[1].distanceMeters, 1e-6)
        assertEquals(120.0, values[2].durationSeconds, 1e-6)
    }

    @Test
    fun `a route without values yields none`() {
        assertEquals(0, instructionValues(route(3)).size)
        assertEquals(0, instructionValues(route(3, doubleArrayOf(0.0, 1.0, 2.0))).size)
        assertEquals(0, instructionValues(route(3, null, doubleArrayOf(0.0, 1.0, 2.0))).size)
    }

    @Test
    fun `a mismatched array yields none, never a shifted list`() {
        // Two values for three instruction lines: using them would attribute the first two steps'
        // numbers to the wrong rows.
        assertEquals(
            0,
            instructionValues(
                route(
                    3,
                    doubleArrayOf(0.0, 900.0),
                    doubleArrayOf(0.0, 60.0)
                )
            ).size
        )
    }

    @Test
    fun `the header does not count as an instruction`() {
        val values = instructionValues(
            route(2, doubleArrayOf(0.0, 500.0), doubleArrayOf(0.0, 30.0))
        )
        assertEquals(2, values.size)
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
        val steps = listOf(
            RouteStepDisplay("Start", 0.0, 0.0),
            RouteStepDisplay("Left", 95.0, 5.0)
        )
        assertFalse(stepValuesDiverge(steps, totalMeters = 100.0))
    }

    @Test
    fun `the measured defect's own pair counts as a divergence`() {
        // The numbers the change removed, measured on device 2026-10-05 (Dortmund Hbf -> Cologne Hbf):
        // the route total reported 72 771 m while the legs summed to 97 416 m. The tightened bound must
        // catch exactly this, which the 50 % band it replaces did not have to (TODO.md 129/139).
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
    fun `the route's length is what its steps add up to`() {
        // 500 m + 1000 m of legs against a native total of 21011: the sum wins, so the statistic the
        // card shows is the number the step list below it adds up to.
        val route = route(2, doubleArrayOf(500.0, 1000.0), doubleArrayOf(30.0, 60.0), nativeDistance = 21_011.0)

        assertEquals(1500.0, routeLengthMeters(route), 1e-6)
    }

    @Test
    fun `a route's length follows the legs, not the native total`() {
        // The defect this replaces, measured on device 2026-10-05 (Dortmund Hbf -> Cologne Hbf): the
        // native total reported 72 771 m while the steps summed to 97 416 m. The displayed length must
        // not be the figure that disagrees with the steps (TODO.md §129/§139).
        val route = route(2, doubleArrayOf(48_000.0, 49_416.0), doubleArrayOf(2_000.0, 2_100.0), nativeDistance = 72_771.0)

        assertEquals(97_416.0, routeLengthMeters(route), 1e-6)
    }

    @Test
    fun `a route without per-step values falls back to the native total`() {
        assertEquals(21_011.0, routeLengthMeters(route(3, null, null, nativeDistance = 21_011.0)), 1e-6)
        // Values that cannot be aligned are dropped by `instructionValues`, so the fallback applies
        // instead of a shifted sum.
        assertEquals(
            21_011.0,
            routeLengthMeters(
                route(3, doubleArrayOf(1.0, 2.0), doubleArrayOf(1.0, 2.0), nativeDistance = 21_011.0)
            ),
            1e-6
        )
        // An all-zero leg list carries no length either: the native total is the better answer.
        assertEquals(
            21_011.0,
            routeLengthMeters(
                route(2, doubleArrayOf(0.0, 0.0), doubleArrayOf(0.0, 0.0), nativeDistance = 21_011.0)
            ),
            1e-6
        )
    }
}
