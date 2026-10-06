package com.naviveylin.ui.route

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose tests for the two independent step markings in [RouteSummary] (specs:
 * `route-analysis` — the analysed step SHALL be distinguishable from the navigation
 * step; `routing-summary` — the navigation step stays highlighted): analysed only,
 * navigation only, both on one row, neither, and the tap reporting the step index.
 */
@RunWith(RobolectricTestRunner::class)
class RouteSummaryStepSelectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val steps = listOf(
        RouteStepDisplay("Start: Hauptbahnhof", 0.0, 0.0, TurnType.START),
        RouteStepDisplay("Left onto Main Street", 1200.0, 300.0, TurnType.LEFT),
        RouteStepDisplay("Destination reached", 0.0, 0.0, TurnType.TARGET_REACHED)
    )

    private fun entry() = RouteEntry().apply {
        distance = 12_400.0
        duration = 1500.0
    }

    private fun launch(
        activeStepIndex: Int? = null,
        analysedStepIndex: Int? = null,
        onStepSelected: ((Int) -> Unit)? = null
    ) {
        composeRule.setContent {
            RouteSummary(
                routeEntry = entry(),
                steps = steps,
                activeStepIndex = activeStepIndex,
                analysedStepIndex = analysedStepIndex,
                onStepSelected = onStepSelected
            )
        }
    }

    @Test
    fun `step values are formatted with the device locale`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            launch()

            // The rows format the numbers themselves (spec: routing-summary — the summary formats its
            // own values): German comma decimals, the unit from the resource.
            composeRule.onNodeWithTag("routeStep1", useUnmergedTree = true)
                .onChildren().filterToOne(hasText("1,2", substring = true))
                .assertExists()
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `a step duration below a minute reads in seconds`() {
        val shortSteps = listOf(
            RouteStepDisplay("Start: Hauptbahnhof", 0.0, 0.0, TurnType.START),
            RouteStepDisplay("Left onto Main Street", 1200.0, 45.0, TurnType.LEFT)
        )
        composeRule.setContent {
            RouteSummary(routeEntry = entry(), steps = shortSteps)
        }

        // 45 s must not read "0 min" (owner finding, 2026-10-03: every step of a city route did).
        composeRule.onNodeWithTag("routeStep1", useUnmergedTree = true)
            .onChildren().filterToOne(hasText("45 s", substring = true))
            .assertExists()
        composeRule.onNodeWithText("0 min", substring = true).assertDoesNotExist()
    }

    @Test
    fun `analysed step is marked and no navigation step is`() {
        launch(analysedStepIndex = 1)

        composeRule.onNodeWithTag("routeStep1").assertIsSelected()
        composeRule.onNodeWithTag("routeStep0").assertIsNotSelected()
        composeRule.onNodeWithTag("routeStep2").assertIsNotSelected()
        composeRule.onNodeWithTag("activeStep", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `navigation step is marked and nothing is analysed`() {
        launch(activeStepIndex = 2)

        composeRule.onNodeWithTag("activeStep", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("routeStep2").assertIsNotSelected()
        composeRule.onNodeWithTag("routeStep0").assertIsNotSelected()
    }

    @Test
    fun `analysed and navigation step on one row carry both markings`() {
        launch(activeStepIndex = 1, analysedStepIndex = 1)

        composeRule.onNodeWithTag("routeStep1")
            .assertIsSelected()
            .assert(hasAnyDescendant(hasTestTag("activeStep")))
    }

    @Test
    fun `no index marks no row`() {
        launch()

        composeRule.onNodeWithTag("routeStep0").assertIsNotSelected()
        composeRule.onNodeWithTag("routeStep1").assertIsNotSelected()
        composeRule.onNodeWithTag("routeStep2").assertIsNotSelected()
        composeRule.onNodeWithTag("activeStep", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `tapping a row reports its index`() {
        val tapped = mutableListOf<Int>()
        launch(onStepSelected = { tapped.add(it) })

        composeRule.onNodeWithTag("routeStep2").assertHasClickAction().performClick()

        assertEquals(listOf(2), tapped)
    }

    @Test
    fun `rows are not tappable without a callback`() {
        launch()

        composeRule.onNodeWithTag("routeStep1").assert(
            hasNoClickAction()
        )
    }
}
