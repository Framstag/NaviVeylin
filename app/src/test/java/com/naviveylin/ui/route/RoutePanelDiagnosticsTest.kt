package com.naviveylin.ui.route

import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The route census: the one diagnostics line that settles the row policy from measurement instead of
 * a guess (design D6 of `route-panel-from-steps`; spec: `route-analysis` — Rows are derived from the
 * route's steps; spec: `auto-diagnostics` — Diagnostics carry no coordinates).
 *
 * Plain JUnit — the census is a pure function over the step list.
 */
class RoutePanelDiagnosticsTest {

    private fun censusRoute(): List<RouteStep> = listOf(
        startStep(),
        // A straight-on run of two, the first one close enough to the next name change to show a run.
        turnStep(TurnType.STRAIGHT_ON, lat = 52.5210, legDistance = 210.0, legTime = 20.0),
        turnStep(TurnType.STRAIGHT_ON, lat = 52.5220, legDistance = 210.0, legTime = 20.0),
        turnStep(TurnType.LEFT, lat = 52.5230, legDistance = 500.0, legTime = 50.0, name = "Hauptstraße"),
        // A name change restated by the manoeuvre that follows it.
        step(RouteStep.Kind.NAME_CHANGE, lat = 52.5240, legDistance = 90.0, name = "Hauptstraße"),
        turnStep(TurnType.RIGHT, lat = 52.5250, legDistance = 60.0, legTime = 8.0, name = "Hauptstraße"),
        // An informative step whose only content is a name.
        step(RouteStep.Kind.INFORMATIVE, lat = 52.5260, legDistance = 30.0, name = "Kirchweg"),
        step(RouteStep.Kind.ROUNDABOUT_LEAVE, lat = 52.5270, legDistance = 400.0, roundaboutExit = 2),
        step(RouteStep.Kind.MOTORWAY_ENTER, lat = 52.5280, legDistance = 900.0, motorwayTo = "A42"),
        targetStep(lat = 52.5300, legDistance = 300.0, legTime = 30.0)
    )

    @Test
    fun `the census line carries counts and metres and no coordinates`() {
        val steps = censusRoute()
        val line = routeCensus(steps, routeRows(steps))

        // Counts ...
        assertTrue(line, line.contains("steps=${steps.size}"))
        assertTrue(line, line.contains("turn=2"))
        assertTrue(line, line.contains("straightOn=2"))
        assertTrue(line, line.contains("nameChange=1"))
        assertTrue(line, line.contains("roundabout=1"))
        assertTrue(line, line.contains("motorway=1"))
        assertTrue(line, line.contains("info=1"))
        // ... metres ...
        assertTrue(line, line.contains("leg_m median="))
        assertTrue(line, line.contains("under20="))
        assertTrue(line, line.contains("so_runs=1"))
        assertTrue(line, line.contains("so_len_max=420"))
        assertTrue(line, line.contains("nc_restated=1"))
        assertTrue(line, line.contains("nc_blank="))
        assertTrue(line, line.contains("nc_short="))
        assertTrue(line, line.contains("info_name_only=1"))
        assertTrue(line, line.contains("info_poi="))
        assertTrue(line, line.contains("info_via="))
        // ... and no coordinate: nothing shaped like a position may reach the entry.
        assertFalse(line, Regex("""-?\d+\.\d{3,}""").containsMatchIn(line))
        assertFalse(line, line.contains("lat"))
        assertFalse(line, line.contains("lon"))
    }

    @Test
    fun `the census reports the rows it produced next to the steps it read`() {
        val steps = censusRoute()
        val rows = routeRows(steps)

        val line = routeCensus(steps, rows)

        assertTrue(line, line.contains("rows=${rows.size}"))
        assertTrue("the rows are fewer than the steps: $line", rows.size < steps.size)
    }

    @Test
    fun `the census of a route without steps is still a line of numbers`() {
        val line = routeCensus(emptyList(), emptyList())

        assertTrue(line, line.startsWith("route census:"))
        assertTrue(line, line.contains("steps=0"))
        assertTrue(line, line.contains("leg_m median=0"))
    }

    @Test
    fun `the census sums the legs' kilometres per kind`() {
        val steps = censusRoute()
        val straightOnKm = steps
            .filter { it.kind == RouteStep.Kind.TURN && it.turn == TurnType.STRAIGHT_ON }
            .sumOf { it.legDistance } / 1000.0

        val line = routeCensus(steps, routeRows(steps))

        assertTrue(
            line,
            line.contains("straightOn=${String.format(java.util.Locale.ROOT, "%.1f", straightOnKm)}")
        )
        // The metres the route carries are the legs' sum, not a geometry measurement.
        val legSum = steps.sumOf { it.legDistance }
        assertTrue(abs(legSum - 2_700.0) < 1e-6)
    }
}
