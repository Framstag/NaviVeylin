package com.naviveylin.ui.route

import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the row boundaries the map is given (spec: `route-analysis` — Analysed step is
 * locatable on the map).
 *
 * `RouteStep` is a plain value holder, so these run as plain JUnit tests — no native library is
 * loaded.
 */
class RouteStepAnchorsTest {

    @Test
    fun `there is one boundary per row, in row order`() {
        val rows = routeRows(
            listOf(
                startStep(lat = 52.50, lon = 13.30),
                turnStep(TurnType.LEFT, lat = 52.51, lon = 13.31, legDistance = 400.0),
                targetStep(lat = 52.52, lon = 13.32, legDistance = 400.0)
            )
        )

        assertEquals(
            listOf(StepAnchor(52.50, 13.30), StepAnchor(52.51, 13.31), StepAnchor(52.52, 13.32)),
            rowAnchors(rows)
        )
    }

    @Test
    fun `the first row anchors at the route start`() {
        val rows = routeRows(
            listOf(
                startStep(lat = 52.50, lon = 13.30),
                turnStep(TurnType.LEFT, lat = 52.51, lon = 13.31, legDistance = 400.0)
            )
        )

        // The start line sits on the route's first vertex: its boundary is where the route begins, and
        // it owns no leg of its own.
        assertEquals(
            StepAnchor(52.50, 13.30),
            rowAnchors(rows).first()
        )
    }

    @Test
    fun `a condensed row anchors at its own manoeuvre`() {
        val rows = routeRows(
            listOf(
                startStep(lat = 52.50, lon = 13.30),
                turnStep(TurnType.STRAIGHT_ON, lat = 52.51, lon = 13.31, legDistance = 100.0),
                turnStep(TurnType.STRAIGHT_ON, lat = 52.52, lon = 13.32, legDistance = 200.0),
                turnStep(TurnType.STRAIGHT_ON, lat = 52.53, lon = 13.33, legDistance = 300.0),
                targetStep(lat = 52.54, lon = 13.34, legDistance = 400.0)
            )
        )

        // One row for the three straight-on turns, anchored where the run ends — the manoeuvre the
        // collapsed row names.
        assertEquals(3, rows.size)
        assertEquals(
            listOf(StepAnchor(52.50, 13.30), StepAnchor(52.53, 13.33), StepAnchor(52.54, 13.34)),
            rowAnchors(rows)
        )
    }

    @Test
    fun `a row without a usable position yields no anchors at all`() {
        // A partial list would move every later row onto the wrong manoeuvre: no anchors are better.
        val rows = routeRows(
            listOf(
                startStep(lat = 52.50, lon = 13.30),
                turnStep(TurnType.LEFT, lat = Double.NaN, lon = 13.31, legDistance = 400.0)
            )
        )

        assertTrue(rowAnchors(rows).isEmpty())
    }

    @Test
    fun `a route without steps yields no anchors`() {
        assertTrue(rowAnchors(routeRows(emptyList())).isEmpty())
    }

    @Test
    fun `an informative step adds no boundary of its own`() {
        val rows = routeRows(
            listOf(
                startStep(lat = 52.50, lon = 13.30),
                turnStep(TurnType.LEFT, lat = 52.51, lon = 13.31, legDistance = 400.0),
                step(RouteStep.Kind.INFORMATIVE, lat = 52.515, lon = 13.315, legDistance = 50.0),
                targetStep(lat = 52.52, lon = 13.32, legDistance = 400.0)
            )
        )

        assertEquals(
            listOf(StepAnchor(52.50, 13.30), StepAnchor(52.51, 13.31), StepAnchor(52.52, 13.32)),
            rowAnchors(rows)
        )
    }
}
