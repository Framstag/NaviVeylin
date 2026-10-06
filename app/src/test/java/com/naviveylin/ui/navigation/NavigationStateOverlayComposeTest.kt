package com.naviveylin.ui.navigation

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun launchOverlay(
        distanceProgress: Int? = null,
        timeProgress: Int? = null,
        onStop: () -> Unit = {},
        onClick: () -> Unit = {}
    ) {
        composeRule.setContent {
            NavigationStateOverlay(
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 600_000,
                distanceProgressPercent = distanceProgress,
                timeProgressPercent = timeProgress,
                onStopNavigation = onStop,
                onClick = onClick
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

    // --- Stop control as a tap target of its own --------------------------------
    // Spec: navigation-status-details — "Routing status card is clickable": the control's
    // hit area is its own, and it never overlaps the card's details tap area.

    @Test
    fun stopControlHitAreaIsAtLeast48Dp() {
        launchOverlay()

        val control = composeRule.onNodeWithTag("stopNavigation", useUnmergedTree = true)
        control.assertWidthIsAtLeast(48.dp)
        control.assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun stopControlBoundsLieOutsideDetailsRegionBounds() {
        launchOverlay()

        val control = composeRule.onNodeWithTag("stopNavigation", useUnmergedTree = true)
            .getBoundsInRoot()
        val statsRegion = composeRule.onNodeWithTag("navStatusDetailsStatsRegion", useUnmergedTree = true)
            .getBoundsInRoot()
        val blockRegion = composeRule.onNodeWithTag("navStatusDetailsRegion", useUnmergedTree = true)
            .getBoundsInRoot()

        // The stats strip ends at the control's leading edge and the road-name/progress
        // block sits above it, so neither region covers the control.
        assertTrue(
            "stats region $statsRegion overlaps control $control",
            statsRegion.right <= control.left
        )
        assertTrue(
            "details block $blockRegion overlaps control $control",
            blockRegion.bottom <= control.top
        )
    }

    @Test
    fun detailsRegionClickOpensDetailsAndDoesNotStop() {
        var detailsClicks = 0
        var stopClicks = 0
        launchOverlay(onStop = { stopClicks++ }, onClick = { detailsClicks++ })

        composeRule.onNodeWithTag("navStatusDetailsRegion", useUnmergedTree = true).performClick()

        assertEquals(1, detailsClicks)
        assertEquals(0, stopClicks)
    }

    @Test
    fun detailsRegionClickLeavesStopCallbackUntouched() {
        var detailsClicks = 0
        var stopClicks = 0
        launchOverlay(onStop = { stopClicks++ }, onClick = { detailsClicks++ })

        composeRule.onNodeWithTag("navStatusDetailsStatsRegion", useUnmergedTree = true).performClick()

        assertEquals(1, detailsClicks)
        assertEquals(0, stopClicks)
    }

    @Test
    fun stopControlCentreEndsNavigationWithoutOpeningDetails() {
        var detailsClicks = 0
        var stopClicks = 0
        launchOverlay(onStop = { stopClicks++ }, onClick = { detailsClicks++ })

        composeRule.onNodeWithTag("stopNavigation", useUnmergedTree = true).performClick()

        assertEquals(1, stopClicks)
        assertEquals(0, detailsClicks)
    }

    @Test
    fun noStopControlWhenCallbackIsNull() {
        // Host substitute for the car surfaces, which render the same shared row with no
        // stop action (spec scenario: The car card carries no stop control).
        composeRule.setContent {
            NavigationStatsRow(
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 600_000,
                onStopNavigation = null
            )
        }

        composeRule.onNodeWithTag("stopNavigation", useUnmergedTree = true).assertDoesNotExist()
    }
}
