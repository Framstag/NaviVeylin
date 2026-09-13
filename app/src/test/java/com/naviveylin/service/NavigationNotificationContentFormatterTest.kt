package com.naviveylin.service

import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.NavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Pure content tests for the ongoing navigation/free-driving notification
 * (task 2.4; specs R3 navigation guidance content, R5 free-driving content).
 */
class NavigationNotificationContentFormatterTest {

    private val instruction = RouteInstruction(
        250.0,
        TurnType.LEFT,
        "Hauptstrasse",
        "Turn left into Hauptstrasse",
        "Turn left"
    )

    @Test
    fun navigationShowsDestinationAndInstruction() {
        val state = NavigationState(
            isNavigating = true,
            nextInstruction = instruction,
            destinationName = "Home",
            remainingDistance = 12_400.0,
            etaMillis = fakeNow + 30 * 60_000
        )
        val content = NavigationNotificationContentFormatter.format(
            state, freeDrivingActive = false, etaClock = { "14:32" }, locale = Locale.US
        )
        assertEquals("Home", content.title)
        assertEquals("Turn left into Hauptstrasse", content.contentText)
        assertTrue(content.bigTextLines.any { it == "Turn left into Hauptstrasse" })
        // stats line: distance to turn (250 m), arrival (14:32), remaining (12.4 km)
        assertTrue(content.bigTextLines.any { it.contains("250 m") && it.contains("14:32") && it.contains("12.4 km") })
        assertTrue(content.showStopAction)
    }

    @Test
    fun navigationWithoutDestinationUsesNeutralTitle() {
        val state = NavigationState(isNavigating = true, nextInstruction = instruction)
        val content = NavigationNotificationContentFormatter.format(
            state, freeDrivingActive = false, etaClock = { "--:--" }
        )
        assertEquals("Navigation active", content.title)
    }

    @Test
    fun navigationWithoutInstructionFallsBackToStats() {
        val state = NavigationState(
            isNavigating = true,
            remainingDistance = 500.0,
            etaMillis = fakeNow + 5 * 60_000
        )
        val content = NavigationNotificationContentFormatter.format(
            state, freeDrivingActive = false, etaClock = { "13:00" }
        )
        // contentText falls back to the stats line
        assertTrue(content.contentText.contains("13:00"))
    }

    @Test
    fun freeDrivingShowsRoadAndSpeedWithoutStopAction() {
        val road = com.framstag.libosmscout.client.CurrentRoadInfo(
            "A5",
            "motorway",
            "Autobahn"
        )
        val state = NavigationState(
            isNavigating = false,
            currentRoadInfo = road,
            currentSpeedKmH = 112.0
        )
        val content = NavigationNotificationContentFormatter.format(
            state, freeDrivingActive = true, etaClock = { "13:00" }
        )
        assertEquals("Free driving", content.title)
        assertTrue(content.contentText.contains("A5 Autobahn"))
        assertTrue(content.contentText.contains("112 km/h"))
        assertFalse("no stop action in free driving", content.showStopAction)
    }

    @Test
    fun freeDrivingOffRoadShowsFallback() {
        val state = NavigationState(
            isNavigating = false,
            currentRoadInfo = null,
            currentSpeedKmH = Double.NaN
        )
        val content = NavigationNotificationContentFormatter.format(
            state, freeDrivingActive = true, etaClock = { "13:00" }
        )
        assertTrue(content.contentText.contains("Offroad"))
    }

    @Test
    fun noDrivingModeProducesEmptyContent() {
        val state = NavigationState(isNavigating = false)
        val content = NavigationNotificationContentFormatter.format(
            state, freeDrivingActive = false, etaClock = { "13:00" }
        )
        assertTrue(content.contentText.isEmpty())
        assertFalse(content.showStopAction)
    }

    private companion object {
        /** Fixed "now" so remaining-time assertions are stable. */
        val fakeNow: Long = 1_700_000_000_000L
    }
}
