package com.naviveylin.ui.map

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.R
import com.naviveylin.core.NavigationState
import com.naviveylin.core.TurnInstructionLocalizer
import com.naviveylin.core.distanceUsesKilometers
import com.naviveylin.core.formatDistanceNumber
import com.naviveylin.core.stringResolver
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose tests for [CarSessionSurface] (spec: `map-canvas-screen` — The car-session surface
 * is informative and offers the map back): the surface states where the guidance is, repeats
 * the guidance summary with the labels the car surface uses for the same state, and its one
 * action returns the phone to the map.
 */
@RunWith(RobolectricTestRunner::class)
class CarSessionSurfaceComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val instruction = RouteInstruction(
        DISTANCE_TO_TURN,
        TurnType.LEFT,
        "Hauptstrasse",
        "Turn left into Hauptstrasse",
        ""
    )

    private val navigating = NavigationState(
        isNavigating = true,
        nextInstruction = instruction,
        remainingDistance = 4_200.0,
        etaMillis = System.currentTimeMillis() + 12 * 60_000L,
        destinationName = "Hauptbahnhof"
    )

    /** The label the car's routing cue and the phone's next-turn card share. */
    private fun sharedInstructionLabel(): String =
        TurnInstructionLocalizer.shortDescription(context.stringResolver(), instruction)

    private fun sharedDistanceLabel(): String = context.getString(
        if (distanceUsesKilometers(DISTANCE_TO_TURN)) R.string.distance_unit_km else R.string.distance_unit_m,
        formatDistanceNumber(DISTANCE_TO_TURN)
    )

    @Test
    fun theSurfaceStatesThatTheGuidanceIsOnTheCarDisplay() {
        composeRule.setContent {
            CarSessionSurface(navigationState = navigating, onShowMap = {})
        }

        composeRule.onNodeWithTag("carSessionSurface").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.car_session_active_indicator))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.car_session_surface_paused))
            .assertIsDisplayed()
    }

    @Test
    fun theSurfaceShowsTheGuidanceSummaryWithTheSharedLabels() {
        composeRule.setContent {
            CarSessionSurface(navigationState = navigating, onShowMap = {})
        }

        composeRule.onNodeWithTag("carSessionDestination").assertIsDisplayed()
        composeRule.onNodeWithText("Hauptbahnhof").assertIsDisplayed()
        composeRule.onNodeWithTag("carSessionNextDistance").assertIsDisplayed()
        composeRule.onNodeWithText(sharedDistanceLabel()).assertIsDisplayed()
        composeRule.onNodeWithTag("carSessionInstruction").assertIsDisplayed()
        composeRule.onNodeWithText(sharedInstructionLabel()).assertIsDisplayed()
        // The phone's own stats row: arrival time, remaining time and remaining distance,
        // each labelled with the phone's existing labels (the icons carry them).
        composeRule.onNodeWithTag("carSessionStats").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.eta)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.remaining_time))
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(
                if (distanceUsesKilometers(4_200.0)) R.string.distance_unit_km else R.string.distance_unit_m,
                formatDistanceNumber(4_200.0)
            )
        ).assertIsDisplayed()
    }

    @Test
    fun theSurfaceOffersTheMapBackAndDoesNotOfferStopNavigation() {
        var mapRequested = false
        composeRule.setContent {
            CarSessionSurface(navigationState = navigating, onShowMap = { mapRequested = true })
        }

        composeRule.onNodeWithText(context.getString(R.string.stop_navigation)).assertDoesNotExist()
        composeRule.onNodeWithTag("carSessionShowMap").performClick()

        assertTrue("the surface's action returns to the map", mapRequested)
    }

    @Test
    fun aFreeDriveShowsTheStatementWithoutAGuidanceSummary() {
        composeRule.setContent {
            CarSessionSurface(navigationState = NavigationState(), onShowMap = {})
        }

        // No navigation: no maneuver, no stats — but the surface still explains the phone and
        // still offers the map.
        composeRule.onNodeWithText(context.getString(R.string.car_session_active_indicator))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("carSessionStats").assertDoesNotExist()
        composeRule.onNodeWithTag("carSessionInstruction").assertDoesNotExist()
        composeRule.onNodeWithTag("carSessionShowMap").assertIsDisplayed()
    }

    private companion object {
        const val DISTANCE_TO_TURN = 320.0
    }
}
