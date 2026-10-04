package com.naviveylin.ui.route

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.framstag.libosmscout.client.RouteEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose tests for the hidden anchor's route-ready affordance (spec:
 * `route-planning-session` — anchors): it states the route, and tapping it brings the
 * overlay back.
 */
@RunWith(RobolectricTestRunner::class)
class RouteReadyPillTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun entry() = RouteEntry().apply {
        distance = 12_400.0
        duration = 1_500.0
    }

    @Test
    fun pillStatesTheRouteAndExpandsOnTap() {
        var expanded = 0
        composeRule.setContent {
            RouteReadyPill(routeEntry = entry(), onExpand = { expanded++ })
        }

        composeRule.onNodeWithTag("RouteReadyPill").assertIsDisplayed()
        composeRule.onNodeWithText("12.4 km").assertExists()
        composeRule.onNodeWithText("25 min").assertExists()

        composeRule.onNodeWithTag("RouteReadyPill").performClick()

        assertEquals(1, expanded)
    }

    @Test
    fun pillWithoutACalculatedRouteStillOffersTheSession() {
        var expanded = 0
        composeRule.setContent {
            RouteReadyPill(routeEntry = null, onExpand = { expanded++ })
        }

        composeRule.onNodeWithTag("RouteReadyPill").performClick()

        assertEquals(1, expanded)
    }
}
