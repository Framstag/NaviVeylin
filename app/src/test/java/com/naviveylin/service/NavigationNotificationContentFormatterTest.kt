package com.naviveylin.service

import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.NavigationState
import com.naviveylin.core.StringResolver
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

    // ── Host-visible content dedup (spec: car-host-fault-isolation — Bounded
    // host-facing traffic while not visible; design D5). The navigation state stream
    // emits at the position/speed rate, so a re-post must be justified by content the
    // host or the shade can actually see. ──

    private fun post(
        state: NavigationState,
        freeDriving: Boolean = false,
        etaClock: (Long) -> String = { "13:00" }
    ): NotificationPost = NotificationPost(
        content = NavigationNotificationContentFormatter.format(
            state, freeDriving, etaClock = etaClock, locale = Locale.US
        ),
        hint = null
    )

    @Test
    fun aPositionOrSpeedOnlyTickDoesNotRepost() {
        // Navigation content: the next manoeuvre, the distance bucket, the arrival minute
        // and the remaining distance — speed is host-visible only in free driving.
        val first = NavigationState(
            isNavigating = true,
            nextInstruction = instruction,
            remainingDistance = 12_400.0,
            etaMillis = fakeNow + 30 * 60_000,
            currentSpeedKmH = 50.0
        )
        val tick = first.copy(currentSpeedKmH = 87.0)

        assertFalse(
            NavigationNotificationContentFormatter.hostVisibleContentChanged(
                post(first), post(tick)
            )
        )
    }

    @Test
    fun aNewManoeuvreReposts() {
        val first = NavigationState(isNavigating = true, nextInstruction = instruction)
        val next = first.copy(
            nextInstruction = RouteInstruction(
                250.0, TurnType.RIGHT, "Hauptstrasse", "Turn right into Hauptstrasse", "Turn right"
            )
        )

        assertTrue(
            NavigationNotificationContentFormatter.hostVisibleContentChanged(
                post(first), post(next)
            )
        )
    }

    @Test
    fun aDistanceBucketChangeRepostsButASubBucketChangeDoesNot() {
        val first = NavigationState(isNavigating = true, nextInstruction = instruction)

        // Same rounded bucket (250 m -> the display rounds to whole metres there).
        val subBucket = first.copy(
            nextInstruction = RouteInstruction(
                250.4, TurnType.LEFT, "Hauptstrasse", "Turn left into Hauptstrasse", "Turn left"
            )
        )
        assertFalse(
            NavigationNotificationContentFormatter.hostVisibleContentChanged(
                post(first), post(subBucket)
            )
        )

        val nextBucket = first.copy(
            nextInstruction = RouteInstruction(
                900.0, TurnType.LEFT, "Hauptstrasse", "Turn left into Hauptstrasse", "Turn left"
            )
        )
        assertTrue(
            NavigationNotificationContentFormatter.hostVisibleContentChanged(
                post(first), post(nextBucket)
            )
        )
    }

    @Test
    fun anArrivalMinuteChangeRepostsButASubMinuteChangeDoesNot() {
        // The arrival time is compared as the host sees it: the car hint's text carries
        // the formatted clock (the injected clock makes it deterministic here), so a
        // change within the same rendered minute is not host-visible.
        val state = NavigationState(
            isNavigating = true,
            nextInstruction = instruction,
            // A known arrival time: with etaMillis = 0 the hint falls back to the
            // unknown placeholder, which would make the two clocks indistinguishable.
            etaMillis = fakeNow + 30 * 60_000
        )
        val content = NavigationNotificationContentFormatter.format(
            state, freeDrivingActive = false, etaClock = { "13:00" }, locale = Locale.US
        )
        fun hintPost(clock: String) = NotificationPost(
            content = content,
            hint = NavigationNotificationContentFormatter.carHint(
                state, TestResolver(), etaClock = { clock }, locale = Locale.US
            )
        )

        assertFalse(
            NavigationNotificationContentFormatter.hostVisibleContentChanged(
                hintPost("13:00"), hintPost("13:00")
            )
        )
        assertTrue(
            NavigationNotificationContentFormatter.hostVisibleContentChanged(
                hintPost("13:00"), hintPost("13:01")
            )
        )
    }

    @Test
    fun aRemainingDistanceChangeReposts() {
        val first = NavigationState(isNavigating = true, remainingDistance = 12_400.0)
        val shorter = first.copy(remainingDistance = 11_900.0)

        assertTrue(
            NavigationNotificationContentFormatter.hostVisibleContentChanged(
                post(first), post(shorter)
            )
        )
    }

    @Test
    fun theCurrentRoadChangeRepostsInFreeDriving() {
        val first = NavigationState(
            isNavigating = false,
            currentRoadInfo = com.framstag.libosmscout.client.CurrentRoadInfo("A5", "motorway", "Autobahn"),
            currentSpeedKmH = 100.0
        )
        val otherRoad = first.copy(
            currentRoadInfo = com.framstag.libosmscout.client.CurrentRoadInfo("B7", "trunk", "Bundesstrasse")
        )

        assertTrue(NavigationNotificationContentFormatter.hostVisibleContentChanged(post(first, true), post(otherRoad, true)))
    }

    @Test
    fun theFreeDrivingSpeedChangeReposts() {
        val first = NavigationState(isNavigating = false, currentSpeedKmH = 50.0)
        val faster = first.copy(currentSpeedKmH = 51.0)

        assertTrue(NavigationNotificationContentFormatter.hostVisibleContentChanged(post(first, true), post(faster, true)))
    }

    @Test
    fun theFirstPostIsAlwaysAChange() {
        assertTrue(
            NavigationNotificationContentFormatter.hostVisibleContentChanged(null, post(NavigationState(isNavigating = true)))
        )
    }

    @Test
    fun anIdenticalStateDoesNotRepost() {
        val state = NavigationState(isNavigating = true, nextInstruction = instruction)

        assertFalse(
            NavigationNotificationContentFormatter.hostVisibleContentChanged(post(state), post(state))
        )
    }

    private companion object {
        /** Fixed "now" so remaining-time assertions are stable. */
        val fakeNow: Long = 1_700_000_000_000L
    }
}

/** Minimal resolver for the hint's instruction text (the hint only needs a string back). */
private class TestResolver : StringResolver {
    override fun get(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) "text" else args.joinToString(" ")
}
