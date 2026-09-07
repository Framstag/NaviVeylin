package com.naviveylin.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the routing status card progress lines
 * (spec: navigation-status-details — "Route progress lines in routing status
 * card"). Robolectric with the default sandbox — no @Config(sdk=...) here,
 * per the JNI stub classloader rule.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationStateOverlayComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun launchOverlay(distanceProgress: Int?, timeProgress: Int?) {
        composeRule.setContent {
            NavigationStateOverlay(
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 600_000,
                distanceProgressPercent = distanceProgress,
                timeProgressPercent = timeProgress
            )
        }
    }

    @Test
    fun progressLinesShownWhenValuesPassed() {
        launchOverlay(distanceProgress = 42, timeProgress = 50)

        composeRule.onNodeWithTag("distanceProgressLine", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("timeProgressLine", useUnmergedTree = true).assertExists()
    }

    @Test
    fun progressLinesHiddenWhenValuesNull() {
        launchOverlay(distanceProgress = null, timeProgress = null)

        composeRule.onNodeWithTag("distanceProgressLine", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("timeProgressLine", useUnmergedTree = true).assertDoesNotExist()
    }
}
