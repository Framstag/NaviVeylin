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

    private fun launchOverlay(currentStepIndex: Int) {
        composeRule.setContent {
            NavigationDetailsOverlay(
                instructions = instructions(),
                currentStepIndex = currentStepIndex,
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 30 * 60 * 1000,
                currentSpeedKmH = 50.0,
                maxSpeedKmH = 50.0,
                onStopNavigation = {},
                onDismiss = {}
            )
        }
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
                currentSpeedKmH = 50.0,
                maxSpeedKmH = 50.0,
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
    fun closeButtonDismisses() {
        var dismissed = false
        composeRule.setContent {
            NavigationDetailsOverlay(
                instructions = instructions(),
                currentStepIndex = 0,
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 30 * 60 * 1000,
                currentSpeedKmH = 50.0,
                maxSpeedKmH = 50.0,
                onStopNavigation = {},
                onDismiss = { dismissed = true }
            )
        }
        composeRule.onNodeWithContentDescription("Close").performClick()
        assertTrue(dismissed)
    }
}
