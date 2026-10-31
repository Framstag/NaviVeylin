package com.naviveylin.ui.route

import com.naviveylin.core.routeRows
import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row derivation from the route's structured steps (spec: `route-analysis` — Rows are derived from
 * the route's steps).
 *
 * Plain JUnit: the derivation is a pure function over [RouteStep] values and no client is
 * constructed, so no native library is loaded.
 */
class RouteRowDerivationTest {

    // --- What earns a row -------------------------------------------------------

    @Test
    fun `roundabout exit and motorway entry become rows`() {
        val steps = listOf(
            startStep(),
            step(
                RouteStep.Kind.ROUNDABOUT_LEAVE,
                lat = 52.5220, lon = 13.4060,
                legDistance = 300.0, legTime = 40.0,
                roundaboutExit = 3
            ),
            step(
                RouteStep.Kind.MOTORWAY_ENTER,
                lat = 52.5260, lon = 13.4090,
                legDistance = 900.0, legTime = 70.0,
                motorwayTo = "A42"
            ),
            targetStep(legDistance = 0.0)
        )

        val rows = routeRows(steps)

        assertEquals(4, rows.size)
        val roundabout = rows.first { it.manoeuvre.kind == RouteStep.Kind.ROUNDABOUT_LEAVE }
        assertEquals("the exit ordinal is the row's own", 3, roundabout.manoeuvre.roundaboutExit)
        val motorway = rows.first { it.manoeuvre.kind == RouteStep.Kind.MOTORWAY_ENTER }
        assertEquals("A42", motorway.manoeuvre.motorwayTo)
    }

    @Test
    fun `consecutive straight-on turns collapse into one row`() {
        val steps = listOf(
            startStep(),
            turnStep(TurnType.STRAIGHT_ON, legDistance = 100.0, legTime = 10.0),
            turnStep(TurnType.STRAIGHT_ON, legDistance = 200.0, legTime = 20.0),
            turnStep(TurnType.STRAIGHT_ON, legDistance = 300.0, legTime = 30.0),
            targetStep(legDistance = 400.0, legTime = 40.0)
        )

        val rows = routeRows(steps)

        assertEquals(3, rows.size)
        assertEquals("start, one straight-on row, arrival", 3, rows.size)
        assertEquals(600.0, rows[1].distanceMeters, 1e-6)
        assertEquals(60.0, rows[1].durationSeconds, 1e-6)
        assertEquals("the row carries the three legs", 2, rows[1].absorbedSteps)
    }

    // --- Street-name changes ---------------------------------------------------

    @Test
    fun `a street-name change earns its own row`() {
        // The next manoeuvre is 500 m away: it is not "close", so the change is its own row.
        val steps = listOf(
            startStep(),
            step(
                RouteStep.Kind.NAME_CHANGE,
                lat = 52.5210, lon = 13.4055,
                legDistance = 120.0, legTime = 15.0,
                name = "Neue Straße"
            ),
            turnStep(TurnType.LEFT, legDistance = 500.0, legTime = 60.0, name = "Markt")
        )

        val rows = routeRows(steps)

        assertEquals(3, rows.size)
        val change = rows[1]
        assertEquals(RouteStep.Kind.NAME_CHANGE, change.manoeuvre.kind)
        assertEquals("Neue Straße", change.manoeuvre.displayName)
        assertEquals(120.0, change.distanceMeters, 1e-6)
        assertEquals("Markt", rows[2].manoeuvre.displayName)
    }

    @Test
    fun `a street-name change restated by the next manoeuvre is merged into it`() {
        // The change is 60 m before the turn, and the turn names the same street: one row.
        val steps = listOf(
            startStep(),
            step(
                RouteStep.Kind.NAME_CHANGE,
                lat = 52.5210, lon = 13.4055,
                legDistance = 100.0, legTime = 12.0,
                name = "Hauptstraße"
            ),
            turnStep(TurnType.LEFT, legDistance = 60.0, legTime = 8.0, name = "Hauptstraße")
        )

        val rows = routeRows(steps)

        assertEquals("start and one merged row", 2, rows.size)
        assertEquals(RouteStep.Kind.TURN, rows[1].manoeuvre.kind)
        assertEquals("the change's leg joins the manoeuvre's", 160.0, rows[1].distanceMeters, 1e-6)
        assertEquals(1, rows[1].absorbedSteps)
    }

    @Test
    fun `an empty or repeated name change is merged`() {
        val empty = listOf(
            startStep(),
            turnStep(TurnType.RIGHT, legDistance = 200.0, legTime = 20.0, name = "Hauptstraße"),
            step(
                RouteStep.Kind.NAME_CHANGE,
                lat = 52.5240, lon = 13.4085,
                legDistance = 50.0, legTime = 6.0,
                name = "  "
            ),
            turnStep(TurnType.LEFT, lat = 52.5250, lon = 13.4090, legDistance = 500.0, legTime = 50.0)
        )

        val emptyRows = routeRows(empty)

        assertEquals("no row for the blank name change", 3, emptyRows.size)
        assertTrue(emptyRows.none { it.manoeuvre.kind == RouteStep.Kind.NAME_CHANGE })
        assertEquals("the leg joins the row above", 250.0, emptyRows[1].distanceMeters, 1e-6)

        // The same name restated with different case and spacing is no change either.
        val repeated = listOf(
            startStep(),
            turnStep(TurnType.RIGHT, legDistance = 200.0, legTime = 20.0, name = "Hauptstrasse"),
            step(
                RouteStep.Kind.NAME_CHANGE,
                lat = 52.5240, lon = 13.4085,
                legDistance = 50.0, legTime = 6.0,
                name = "  HAUPTSTRASSE "
            )
        )

        val repeatedRows = routeRows(repeated)

        assertEquals(2, repeatedRows.size)
        assertEquals(250.0, repeatedRows[1].distanceMeters, 1e-6)
    }

    // --- Informative steps ------------------------------------------------------

    @Test
    fun `an informative step is not a row and its leg moves to the row above`() {
        val steps = listOf(
            startStep(),
            turnStep(TurnType.LEFT, legDistance = 500.0, legTime = 50.0, name = "Hauptstraße"),
            step(RouteStep.Kind.INFORMATIVE, lat = 52.5240, legDistance = 100.0, legTime = 10.0),
            turnStep(TurnType.RIGHT, lat = 52.5250, lon = 13.4090, legDistance = 400.0, legTime = 40.0)
        )

        val rows = routeRows(steps)

        assertEquals("no row for the informative step", 3, rows.size)
        assertTrue(rows.none { it.manoeuvre.kind == RouteStep.Kind.INFORMATIVE })
        assertEquals("its leg joins the row above", 600.0, rows[1].distanceMeters, 1e-6)
        assertEquals(400.0, rows[2].distanceMeters, 1e-6)
    }

    // --- The invariants ---------------------------------------------------------

    @Test
    fun `rows tile the polyline without a gap or an overlap`() {
        val steps = arrayOf(
            startStep(lat = 52.5200, lon = 13.4050),
            turnStep(TurnType.LEFT, lat = 52.5230, lon = 13.4080, legDistance = 500.0),
            turnStep(TurnType.RIGHT, lat = 52.5260, lon = 13.4090, legDistance = 600.0),
            targetStep(lat = 52.5300, lon = 13.4100, legDistance = 700.0)
        )
        val route = routeWithSteps(*steps)
        val rows = routeRows(steps.toList())

        val ranges = stepSegments(
            polylineLats = route.latitudes,
            polylineLons = route.longitudes,
            stepCount = rows.size,
            anchors = rowAnchors(rows)
        )

        // The start line sits on the route's first vertex and owns no leg; the row after it begins
        // there, so the first leg starts at the route's first vertex.
        assertEquals(IntRange.EMPTY, ranges.first())
        assertEquals("the first row's boundary is the route's first vertex", 0, ranges[1].first)
        assertEquals("the last row ends at the route's last vertex", route.latitudes.lastIndex, ranges.last().last)

        val drawn = ranges.filter { it != IntRange.EMPTY }
        assertEquals(listOf(0..1, 1..2, 2..3), drawn)
        for ((previous, next) in drawn.zipWithNext()) {
            assertEquals("no gap and no overlap", previous.last, next.first)
        }
    }

    @Test
    fun `row distances sum to the route length after merging`() {
        val steps = listOf(
            startStep(),
            turnStep(TurnType.STRAIGHT_ON, legDistance = 100.0),
            step(RouteStep.Kind.INFORMATIVE, lat = 52.5240, legDistance = 150.0),
            step(
                RouteStep.Kind.NAME_CHANGE, lat = 52.5245,
                legDistance = 80.0, name = "Neue Straße"
            ),
            turnStep(TurnType.LEFT, lat = 52.5250, lon = 13.4090, legDistance = 500.0, name = "Markt"),
            targetStep(legDistance = 200.0)
        )

        val rows = routeRows(steps)

        val stepSum = steps.sumOf { it.legDistance }
        assertEquals(
            "every leg is counted exactly once, whichever row absorbed it",
            stepSum,
            rows.sumOf { it.distanceMeters },
            1e-6
        )
        assertEquals(1030.0, stepSum, 1e-6)
    }

    @Test
    fun `merged legs are attributed to the surviving row`() {
        val steps = listOf(
            startStep(),
            turnStep(TurnType.STRAIGHT_ON, legDistance = 200.0, legTime = 20.0),
            turnStep(TurnType.STRAIGHT_ON, legDistance = 350.0, legTime = 30.0),
            step(RouteStep.Kind.INFORMATIVE, lat = 52.5240, legDistance = 50.0, legTime = 5.0),
            targetStep(legDistance = 400.0, legTime = 40.0)
        )

        val rows = routeRows(steps)

        // The straight-on row carries its own leg, the collapsed step's leg and the informative one's.
        assertEquals(600.0, rows[1].distanceMeters, 1e-6)
        assertEquals(55.0, rows[1].durationSeconds, 1e-6)
        assertEquals(400.0, rows[2].distanceMeters, 1e-6)
    }

    @Test
    fun `row derivation leaves the step list intact`() {
        val steps = mutableListOf(
            startStep(),
            turnStep(TurnType.LEFT, legDistance = 500.0, name = "Hauptstraße"),
            step(RouteStep.Kind.INFORMATIVE, lat = 52.5240, legDistance = 50.0),
            targetStep(legDistance = 400.0)
        )
        val before = steps.toList()

        val rows = routeRows(steps)

        assertEquals("the route's complete step list stays available", before, steps)
        assertEquals(4, steps.size)
        assertEquals("the rows are a view, not the list", 3, rows.size)
        assertNotEquals(steps.size, rows.size)
    }

    @Test
    fun `a route without steps has no rows`() {
        assertTrue(routeRows(emptyList()).isEmpty())
    }
}
