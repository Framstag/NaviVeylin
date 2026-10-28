package com.naviveylin.service

import com.naviveylin.test.stepOf
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.NavigationState
import com.naviveylin.core.R
import com.naviveylin.core.StringResolver
import com.naviveylin.core.ManoeuvreLabels
import com.naviveylin.core.manoeuvreFromStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Tests for the car-screen hint content of the ongoing notification
 * (spec: auto-navigation-hints — "Car hint content", "No car surface for free
 * driving"). Plain JUnit: the formatter stays Android-free, the string
 * resolver is faked.
 */
class CarHintContentTest {

    private val resolver = StringResolver { resId, args ->
        when (resId) {
            R.string.nav_turn_left -> "Turn left"
            R.string.nav_turn_right -> "Turn right"
            R.string.nav_straight_on -> "Straight on"
            R.string.nav_start -> "Start"
            R.string.nav_hint_neutral -> "Navigation active"
            R.string.nav_hint_distance_eta -> "${args[0]} · ${args[1]}"
            else -> "res:$resId"
        }
    }

    private fun instruction(
        turnType: TurnType = TurnType.LEFT,
        streetName: String = "Hauptstrasse",
        description: String = "Turn left into Hauptstrasse",
        shortDescription: String = "Turn left",
        distanceTo: Double = 250.0
    ) = stepOf(distanceTo, turnType, streetName, description, shortDescription)

    @Test
    fun instructionIsThePrimaryCarText() {
        val state = NavigationState(
            isNavigating = true,
            nextStep = instruction(),
            destinationName = "Home",
            remainingDistance = 12_400.0,
            etaMillis = 1_700_000_000_000L
        )

        val hint = NavigationNotificationContentFormatter.carHint(
            state, resolver, etaClock = { "14:32" }, locale = Locale.US
        )

        assertEquals("Turn left", hint?.title)
        assertEquals("250 m · 14:32", hint?.text)
        assertEquals(TurnType.LEFT, hint?.turnType)
    }

    @Test
    fun maneuverWithoutStreetNameKeepsInstructionWording() {
        val state = NavigationState(
            isNavigating = true,
            nextStep = instruction(
                streetName = "",
                description = "Turn left",
                shortDescription = "Turn left"
            ),
            etaMillis = 1_700_000_000_000L
        )

        val hint = NavigationNotificationContentFormatter.carHint(
            state, resolver, etaClock = { "14:32" }, locale = Locale.US
        )

        assertEquals("Turn left", hint?.title)
        assertTrue("no street composition expected", hint?.title?.contains("into") == false)
    }

    @Test
    fun neutralHintWhileNoManeuverIsKnown() {
        val state = NavigationState(
            isNavigating = true,
            isRerouting = true,
            remainingDistance = 500.0,
            etaMillis = 0L
        )

        val hint = NavigationNotificationContentFormatter.carHint(
            state, resolver, etaClock = { "14:32" }, locale = Locale.US
        )

        assertEquals("Navigation active", hint?.title)
        assertEquals("500 m · --:--", hint?.text)
        assertNull("no maneuver, no icon", hint?.turnType)
    }

    @Test
    fun neutralHintWhenInstructionsListHasNoCurrentEntry() {
        val state = NavigationState(
            isNavigating = true,
            currentStepIndex = 4,
            steps = emptyList(),
            remainingDistance = 1_200.0
        )

        val hint = NavigationNotificationContentFormatter.carHint(
            state, resolver, etaClock = { "14:32" }, locale = Locale.US
        )

        assertEquals("Navigation active", hint?.title)
        assertNull(hint?.turnType)
    }

    @Test
    fun noCarHintWhileFreeDriving() {
        val state = NavigationState(isNavigating = false, currentSpeedKmH = 54.0)

        val hint = NavigationNotificationContentFormatter.carHint(
            state, resolver, etaClock = { "14:32" }, locale = Locale.US
        )

        assertNull("free driving has no car surface", hint)
    }

    @Test
    fun carTitleMatchesOnScreenNextTurnText() {
        // The car hint and the phone overlay word a manoeuvre from the same builder, so the two
        // cannot disagree — that is what the retired text comparison used to check by hand
        // (spec: `turn-instruction-localization` — one wording for both variants).
        val cases = listOf(
            instruction(),
            instruction(turnType = TurnType.RIGHT, streetName = "Ringstrasse"),
            instruction(turnType = TurnType.START, streetName = "")
        )

        for (case in cases) {
            val state = NavigationState(isNavigating = true, nextStep = case)
            val carTitle = NavigationNotificationContentFormatter.carHint(
                state, resolver, etaClock = { "14:32" }, locale = Locale.US
            )?.title
            val onScreen = ManoeuvreLabels.short(resolver, manoeuvreFromStep(case))

            assertEquals("car hint title must equal the on-screen next-turn text", onScreen, carTitle)
        }
    }
}
