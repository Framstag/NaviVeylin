package com.naviveylin.ui.route

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.test.ComposeSandboxRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The route summary dialog lists the route's **rows** in route order, each with its own run's leg
 * distance and its localized text (spec: `route-summary-dialog` — Scrollable step list).
 */
@RunWith(RobolectricTestRunner::class)
class RouteSummaryRowsTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val steps = listOf(
        startStep(lat = 52.5200, lon = 13.4050),
        turnStep(
            TurnType.LEFT,
            lat = 52.5230, lon = 13.4080,
            legDistance = 1200.0, legTime = 300.0,
            name = "Main Street"
        ),
        targetStep(lat = 52.5300, lon = 13.4100, legDistance = 900.0, legTime = 120.0)
    )

    private fun displays() = routeRows(steps).map { row ->
        RouteStepDisplay(
            instruction = if (row.manoeuvre.kind == com.framstag.libosmscout.client.RouteStep.Kind.START) {
                "Start"
            } else if (row.manoeuvre.kind == com.framstag.libosmscout.client.RouteStep.Kind.TARGET) {
                "Arrive"
            } else {
                "Turn left into ${row.manoeuvre.displayName}"
            },
            distanceMeters = row.distanceMeters,
            durationSeconds = row.durationSeconds,
            turnType = rowTurnType(row.manoeuvre)
        )
    }

    @Test
    fun `rows are listed in route order with their own leg distance`() {
        val rows = displays()
        val route = routeWithSteps(*steps.toTypedArray())

        composeRule.setContent {
            RouteSummary(routeEntry = route, steps = rows)
        }

        // Route order: start, the turn, the arrival.
        composeRule.onNodeWithTag("routeStep0").assertIsDisplayed()
        composeRule.onNodeWithTag("routeStep1").assertIsDisplayed()
        composeRule.onNodeWithTag("routeStep2").assertIsDisplayed()
        composeRule.onNodeWithText("Turn left into Main Street").assertIsDisplayed()

        // Each row carries its own run: the start line nothing, the turn 1.2 km, the arrival 900 m.
        assertEquals(0.0, rows[0].distanceMeters, 1e-6)
        assertEquals(1200.0, rows[1].distanceMeters, 1e-6)
        assertEquals(900.0, rows[2].distanceMeters, 1e-6)
    }

    @Test
    fun `a condensed row shows the sum of the legs it absorbed`() {
        val straightRuns = listOf(
            startStep(),
            turnStep(TurnType.STRAIGHT_ON, lat = 52.5210, lon = 13.4060, legDistance = 100.0),
            turnStep(TurnType.STRAIGHT_ON, lat = 52.5220, lon = 13.4070, legDistance = 200.0),
            targetStep(lat = 52.5300, lon = 13.4100, legDistance = 700.0)
        )
        val rows = routeRows(straightRuns)

        composeRule.setContent {
            RouteSummary(
                routeEntry = routeWithSteps(*straightRuns.toTypedArray()),
                steps = rows.map { row ->
                    RouteStepDisplay(
                        instruction = "Straight on",
                        distanceMeters = row.distanceMeters,
                        durationSeconds = row.durationSeconds,
                        turnType = rowTurnType(row.manoeuvre)
                    )
                }
            )
        }

        assertEquals("one row for the two straight-on turns", 3, rows.size)
        assertEquals(300.0, rows[1].distanceMeters, 1e-6)
        composeRule.onNodeWithTag("routeStep1").assertIsDisplayed()
        composeRule.onNodeWithTag("routeStep3").assertDoesNotExist()
    }
}
