package com.naviveylin.ui.route

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the route summary dialog.
 * Robolectric with the default sandbox — no @Config(sdk=...) here, per the
 * JNI stub classloader rule.
 */
@RunWith(RobolectricTestRunner::class)
class RouteSummaryDialogComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun routeEntry(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
        longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
        distance = 5000.0
        duration = 600.0
    }

    private fun steps(): List<RouteStepDisplay> = listOf(
        RouteStepDisplay(
            instruction = "Left onto Main Street",
            distanceText = "1.2 km",
            timeText = "5 min",
            turnType = TurnType.LEFT
        )
    )

    private fun launchDialog() {
        composeRule.setContent {
            RouteSummaryDialog(
                routeEntry = routeEntry(),
                steps = steps(),
                onStartNavigation = {},
                onDismiss = {}
            )
        }
    }

    @Test
    fun dialogShowsRouteStatsAndSteps() {
        launchDialog()

        composeRule.onNodeWithText("1.2 km").assertIsDisplayed()
        composeRule.onNodeWithText("Left onto Main Street").assertIsDisplayed()
    }

    @Test
    fun activeStepHighlightedWhenIndexProvided() {
        composeRule.setContent {
            RouteSummaryDialog(
                routeEntry = routeEntry(),
                steps = steps(),
                activeStepIndex = 0,
                onStartNavigation = {},
                onDismiss = {}
            )
        }
        composeRule.onNodeWithTag("activeStep").assertIsDisplayed()
        composeRule.onNodeWithTag("activeStep").assertTextEquals("Left onto Main Street")
    }

    @Test
    fun noHighlightWithoutActiveIndex() {
        launchDialog()
        composeRule.onNodeWithTag("activeStep").assertDoesNotExist()
    }

    @Test
    fun stopNavigationButtonInvokesCallback() {
        var stopped = false
        composeRule.setContent {
            RouteSummaryDialog(
                routeEntry = routeEntry(),
                steps = steps(),
                isNavigating = true,
                onStartNavigation = {},
                onStopNavigation = { stopped = true },
                onDismiss = {}
            )
        }

        composeRule.onNodeWithText("Stop Navigation").performClick()
        assertTrue("tapping Stop Navigation must invoke onStopNavigation", stopped)
    }

    @Test
    fun startNavigationButtonShownWhenNotNavigating() {
        composeRule.setContent {
            RouteSummaryDialog(
                routeEntry = routeEntry(),
                steps = steps(),
                isNavigating = false,
                onStartNavigation = {},
                onDismiss = {}
            )
        }

        composeRule.onNodeWithText("Start Navigation").assertIsDisplayed()
        composeRule.onNodeWithText("Stop Navigation").assertDoesNotExist()
    }
}
