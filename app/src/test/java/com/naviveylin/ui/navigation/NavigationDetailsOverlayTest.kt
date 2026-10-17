package com.naviveylin.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.test.ComposeSandboxRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the expanded routing status details view:
 * current step highlighted, shown at the top of the list, and dismissible.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationDetailsOverlayTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()

    private fun instructions(): List<RouteInstruction> {
        // Long enough that the list overflows the viewport, so scrolling
        // to the current step actually moves earlier steps off-screen.
        return (0 until 20).map { i ->
            RouteInstruction(
                500.0 + i * 100,
                if (i % 2 == 0) TurnType.LEFT else TurnType.RIGHT,
                "Street $i",
                "Turn into Street $i",
                "Turn"
            )
        }
    }

    // --- the numbered roundabout exit (spec: navigation-status-details - Route description list) ---

    private fun roundaboutSteps(): List<RouteInstruction> = listOf(
        RouteInstruction(
            500.0, 300.0, 500.0, TurnType.ROUNDABOUT_LEAVE, "Bahnhofstraße",
            "Take exit 3 onto Bahnhofstraße", "Exit 3",
            0.0, TurnType.STRAIGHT_ON, "", "", 3, true, 0
        ),
        RouteInstruction(
            900.0, 300.0, 400.0, TurnType.ROUNDABOUT_LEAVE, "Marktplatz",
            "Take exit 1 onto Marktplatz", "Exit 1",
            0.0, TurnType.STRAIGHT_ON, "", "", 1, true, 0
        ),
        RouteInstruction(
            1300.0, 300.0, 400.0, TurnType.ROUNDABOUT_LEAVE, "Ringstraße",
            "Leave roundabout", "Exit ?",
            0.0, TurnType.STRAIGHT_ON, "", "", 0, true, 0
        )
    )

    @Test
    fun `a roundabout exit step icon carries that step's number`() {
        val steps = roundaboutSteps()

        assertEquals(NavSymbol.RoundaboutExit(3), navSymbolForInstruction(steps[0]))
        assertEquals(NavSymbol.RoundaboutExit(1), navSymbolForInstruction(steps[1]))

        // and the list renders those steps, so the icon decision reaches the row rather than only
        // the helper: the expanded view is the surface the delta's scenario names.
        composeRule.setContent {
            NavigationDetailsOverlay(
                instructions = steps,
                currentStepIndex = 0,
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 30 * 60 * 1000,
                onStopNavigation = {},
                onDismiss = {}
            )
        }
        composeRule.onNodeWithText("Take exit 3 onto Bahnhofstraße").assertIsDisplayed()
    }

    @Test
    fun `a roundabout exit step without an ordinal keeps the plain icon`() {
        assertEquals(
            NavSymbol.TurnArrow(TurnType.ROUNDABOUT_LEAVE),
            navSymbolForInstruction(roundaboutSteps()[2])
        )
    }

    private fun launchOverlay(currentStepIndex: Int) {
        composeRule.setContent {
            NavigationDetailsOverlay(
                instructions = instructions(),
                currentStepIndex = currentStepIndex,
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 30 * 60 * 1000,
                onStopNavigation = {},
                onDismiss = {}
            )
        }
    }

    /**
     * The expanded view carries the same two progress rings as the routing status card: it is the
     * second host of the shared stats row (spec: `navigation-status-details` — The same rings on
     * every host of the row).
     */
    @Test
    fun ringsShownInExpandedView() {
        composeRule.setContent {
            NavigationDetailsOverlay(
                instructions = instructions(),
                currentStepIndex = 0,
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 30 * 60 * 1000,
                distanceProgressPercent = 42,
                timeProgressPercent = 50,
                onStopNavigation = {},
                onDismiss = {}
            )
        }

        composeRule.onNodeWithTag("distanceProgressRing", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("timeProgressRing", useUnmergedTree = true).assertExists()
    }

    @Test
    fun currentStepIsHighlighted() {
        launchOverlay(currentStepIndex = 2)
        composeRule.onNodeWithTag("activeStep").assertIsDisplayed()
        composeRule.onNodeWithTag("activeStep").assertTextEquals("Turn into Street 2")
    }

    @Test
    fun currentStepShownAtTopOfList() {
        launchOverlay(currentStepIndex = 2)
        // Current step is the first visible item: earlier steps are not composed.
        composeRule.onNodeWithTag("activeStep").assertIsDisplayed()
        composeRule.onNodeWithText("Turn into Street 0").assertDoesNotExist()
        composeRule.onNodeWithText("Turn into Street 1").assertDoesNotExist()
    }

    @Test
    fun highlightFollowsProgress() {
        var stepIndex by mutableStateOf(0)
        composeRule.setContent {
            NavigationDetailsOverlay(
                instructions = instructions(),
                currentStepIndex = stepIndex,
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 30 * 60 * 1000,
                onStopNavigation = {},
                onDismiss = {}
            )
        }
        composeRule.onNodeWithTag("activeStep").assertTextEquals("Turn into Street 0")

        stepIndex = 3
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("activeStep").assertTextEquals("Turn into Street 3")
        composeRule.onNodeWithText("Turn into Street 0").assertDoesNotExist()
    }

    @Test
    fun perStepTimeShown() {
        composeRule.setContent {
            NavigationDetailsOverlay(
                instructions = listOf(
                    RouteInstruction(
                        500.0, 300.0, 900.0, TurnType.LEFT, "Street 0", "Turn into Street 0", "Turn",
                        0.0, TurnType.STRAIGHT_ON, "", ""
                    )
                ),
                currentStepIndex = 0,
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 30 * 60 * 1000,
                onStopNavigation = {},
                onDismiss = {}
            )
        }
        // 300 s = 5 min, shown under the distance (spec: move-routing-summary).
        composeRule.onNodeWithText("5 min").assertIsDisplayed()
    }

    @Test
    fun perStepDistanceIsTheStepsOwnSegment() {
        composeRule.setContent {
            NavigationDetailsOverlay(
                instructions = listOf(
                    RouteInstruction(
                        // `distanceTo` is the distance from the route start in this list; the row
                        // shows the step's own leg (spec: navigation-status-details — Route
                        // description list), so a row must not show the 8.4 km.
                        8_400.0, 60.0, 900.0, TurnType.LEFT, "Street 0", "Turn into Street 0", "Turn",
                        0.0, TurnType.STRAIGHT_ON, "", ""
                    )
                ),
                currentStepIndex = 0,
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 30 * 60 * 1000,
                onStopNavigation = {},
                onDismiss = {}
            )
        }
        composeRule.onNodeWithText("900 m").assertIsDisplayed()
        composeRule.onNodeWithText("8.4 km").assertDoesNotExist()
    }

    @Test
    fun aRowWithoutALegShowsNoNumbers() {
        composeRule.setContent {
            NavigationDetailsOverlay(
                instructions = listOf(
                    RouteInstruction(
                        0.0, 0.0, 0.0, TurnType.LEFT, "Street 0", "Turn into Street 0", "Turn",
                        0.0, TurnType.STRAIGHT_ON, "", ""
                    )
                ),
                currentStepIndex = 0,
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 30 * 60 * 1000,
                onStopNavigation = {},
                onDismiss = {}
            )
        }
        composeRule.onNodeWithText("Turn into Street 0").assertIsDisplayed()
        composeRule.onNodeWithText("0 m").assertDoesNotExist()
        composeRule.onNodeWithText("0 min").assertDoesNotExist()
    }

    @Test
    fun perStepTimeHiddenWhenUnknown() {
        composeRule.setContent {
            NavigationDetailsOverlay(
                instructions = instructions(), // 5-arg ctor → timeTo = 0
                currentStepIndex = 0,
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 30 * 60 * 1000,
                onStopNavigation = {},
                onDismiss = {}
            )
        }
        composeRule.onNodeWithText("5 min").assertDoesNotExist()
    }

    @Test
    fun closeButtonDismisses() {
        var dismissed = false
        composeRule.setContent {
            NavigationDetailsOverlay(
                instructions = instructions(),
                currentStepIndex = 0,
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 30 * 60 * 1000,
                onStopNavigation = {},
                onDismiss = { dismissed = true }
            )
        }
        composeRule.onNodeWithContentDescription("Close").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun expandedViewStopControlEndsNavigation() {
        var stops = 0
        composeRule.setContent {
            NavigationDetailsOverlay(
                instructions = instructions(),
                currentStepIndex = 0,
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 30 * 60 * 1000,
                onStopNavigation = { stops++ },
                onDismiss = {}
            )
        }

        // The expanded view carries no tap area of its own (the card's details regions exist
        // only on the map surface), so the control's hit area is the only target here
        // (spec: navigation-status-details — Routing status card is clickable, scenario
        // "The expanded view's stop control is its own target too").
        composeRule.onNodeWithTag("navStatusDetailsRegion").assertDoesNotExist()
        composeRule.onNodeWithTag("navStatusDetailsStatsRegion").assertDoesNotExist()

        composeRule.onNodeWithTag("stopNavigation", useUnmergedTree = true).performClick()

        assertEquals(1, stops)
    }
}
