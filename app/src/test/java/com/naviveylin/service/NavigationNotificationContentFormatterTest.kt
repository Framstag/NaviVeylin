package com.naviveylin.service

import com.naviveylin.test.stepOf
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.R
import com.naviveylin.core.FreeDrivingStatus
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
 *
 * The notification's own text is resolved from resources (spec: i18n-l10n —
 * All user-facing text is translatable), so the tests supply the English forms
 * the way the shipped resources do.
 */
class NavigationNotificationContentFormatterTest {

    private val instruction = stepOf(
        250.0,
        TurnType.LEFT,
        "Hauptstrasse",
        "Turn left into Hauptstrasse",
        "Turn left"
    )

    /** English forms of the formatter's own text, as `values/` ships them. */
    private val resolver = StringResolver { resId, args ->
        when (resId) {
            com.naviveylin.core.R.string.nav_hint_neutral -> "Navigation active"
            com.naviveylin.core.R.string.nav_hint_distance_eta -> "${args[0]} · ${args[1]}"
            R.string.navigation_notification_title_free_driving -> "Free driving"
            R.string.road_offroad -> "Offroad"
            R.string.speed_unit_kmh -> "%1${'$'}s km/h".format(args.firstOrNull() ?: 0)
            // The manoeuvre wording comes from `:core`, so the notification now resolves the same
            // strings the phone overlay does (spec: `turn-instruction-localization`).
            com.naviveylin.core.R.string.nav_turn_left -> "Turn left"
            com.naviveylin.core.R.string.nav_turn_right -> "Turn right"
            com.naviveylin.core.R.string.nav_start -> "Start"
            com.naviveylin.core.R.string.nav_arrive -> "Arrive"
            com.naviveylin.core.R.string.nav_straight_on -> "Straight on"
            com.naviveylin.core.R.string.nav_turn_into -> "%1${'$'}s into %2${'$'}s".format(args[0], args[1])
            com.naviveylin.core.R.string.nav_keep_onto -> "%1${'$'}s onto %2${'$'}s".format(args[0], args[1])
            com.naviveylin.core.R.string.nav_exit_roundabout -> "Exit %1${'$'}d".format(args[0])
            else -> "?"
        }
    }

    @Test
    fun navigationShowsDestinationAndInstruction() {
        val state = NavigationState(
            isNavigating = true,
            nextStep = instruction,
            destinationName = "Home",
            remainingDistance = 12_400.0,
            etaMillis = fakeNow + 30 * 60_000
        )
        val content = NavigationNotificationContentFormatter.format(
            state,
            resolver = resolver, freeDrivingActive = false, etaClock = { "14:32" }, locale = Locale.US
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
        val state = NavigationState(isNavigating = true, nextStep = instruction)
        val content = NavigationNotificationContentFormatter.format(
            state,
            resolver = resolver, freeDrivingActive = false, etaClock = { "--:--" }
        )
        assertEquals("Navigation active", content.title)
    }

    @Test
    fun zeroDistanceReadsZeroWhileAnUnknownDistanceStaysUnknown() {
        // A vehicle standing on the announced node has a known distance of zero — the one moment
        // the wording matters most. A step whose progress the engine has not published, or a NaN
        // distance, is unknown and keeps "--" on both surfaces
        // (spec: navigation-ongoing-notification — A known zero distance is not an unknown distance).
        val zeroStep = stepOf(0.0, TurnType.LEFT, "Hauptstrasse")
        val zeroState = NavigationState(
            isNavigating = true,
            nextStep = zeroStep,
            remainingDistance = 12_400.0,
            etaMillis = fakeNow + 30 * 60_000
        )

        val content = NavigationNotificationContentFormatter.format(
            zeroState,
            resolver = resolver, freeDrivingActive = false, etaClock = { "14:32" }, locale = Locale.US
        )
        val statsLine = content.bigTextLines.firstOrNull { it.contains("arrive") }
            ?: error("no stats line in ${content.bigTextLines}")
        assertTrue(
            "zero distance must render 0 m in the notification: $statsLine",
            statsLine.contains("in 0 m")
        )
        assertFalse(statsLine.contains("in --"))

        val hint = NavigationNotificationContentFormatter.carHint(
            zeroState, resolver, etaClock = { "14:32" }, locale = Locale.US
        )
        assertEquals("0 m · 14:32", hint?.text)

        // A step whose progress the engine has not published keeps `remainingDistance` null: unknown,
        // never a known zero, on either surface.
        val absentState = NavigationState(
            isNavigating = true,
            nextStep = stepOf(0.0, TurnType.LEFT, "Hauptstrasse").apply { remainingDistance = null },
            remainingDistance = 12_400.0,
            etaMillis = fakeNow + 30 * 60_000
        )
        assertUnknownOnBothSurfaces("an absent distance", absentState)

        val unknownState = NavigationState(
            isNavigating = true,
            nextStep = stepOf(Double.NaN, TurnType.LEFT, "Hauptstrasse"),
            remainingDistance = 12_400.0,
            etaMillis = fakeNow + 30 * 60_000
        )
        assertUnknownOnBothSurfaces("a NaN distance", unknownState)

        // A passed manoeuvre's rounding artefact is a known zero, never a negative distance.
        val behindState = zeroState.copy(nextStep = stepOf(-0.4, TurnType.LEFT, "Hauptstrasse"))
        val behindContent = NavigationNotificationContentFormatter.format(
            behindState,
            resolver = resolver, freeDrivingActive = false, etaClock = { "14:32" }, locale = Locale.US
        )
        val behindStatsLine = behindContent.bigTextLines.firstOrNull { it.contains("arrive") }
            ?: error("no stats line in ${behindContent.bigTextLines}")
        assertTrue(
            "a passed manoeuvre clamps to a known zero, never a negative distance: $behindStatsLine",
            behindStatsLine.contains("in 0 m")
        )
    }

    /** The unknown half of the case, asserted where it is read: the phone stats line and the car hint. */
    private fun assertUnknownOnBothSurfaces(label: String, state: NavigationState) {
        val content = NavigationNotificationContentFormatter.format(
            state,
            resolver = resolver, freeDrivingActive = false, etaClock = { "14:32" }, locale = Locale.US
        )
        val statsLine = content.bigTextLines.firstOrNull { it.contains("arrive") }
            ?: error("no stats line in ${content.bigTextLines}")
        assertTrue(
            "$label keeps -- on the phone stats line: $statsLine",
            statsLine.contains("in --")
        )

        val hint = NavigationNotificationContentFormatter.carHint(
            state, resolver, etaClock = { "14:32" }, locale = Locale.US
        )
        assertEquals(
            "$label keeps -- in the car hint",
            "-- · 14:32",
            hint?.text
        )
    }

    @Test
    fun navigationWithoutInstructionFallsBackToStats() {
        val state = NavigationState(
            isNavigating = true,
            remainingDistance = 500.0,
            etaMillis = fakeNow + 5 * 60_000
        )
        val content = NavigationNotificationContentFormatter.format(
            state,
            resolver = resolver, freeDrivingActive = false, etaClock = { "13:00" }
        )
        // contentText falls back to the stats line
        assertTrue(content.contentText.contains("13:00"))
        assertTrue(
            "no next step is unknown, not a known zero: ${content.bigTextLines}",
            content.bigTextLines.any { it.startsWith("in -- ·") }
        )
    }

    @Test
    fun freeDrivingShowsStatusRoadAndSpeed() {
        val status = FreeDrivingStatus(roadRef = "A5", roadName = "Autobahn", speedKmH = 112.0)
        val content = NavigationNotificationContentFormatter.format(
            NavigationState(isNavigating = false),
            resolver = resolver,
            freeDrivingActive = true,
            freeDrivingStatus = status
        )
        assertEquals("Free driving", content.title)
        assertTrue(content.contentText.contains("A5 Autobahn"))
        assertTrue(content.contentText.contains("112 km/h"))
        assertFalse("no stop action in free driving", content.showStopAction)
    }

    @Test
    fun unknownSpeedLeavesNoSeparator() {
        // A known road with an unknown speed renders the street alone — no trailing
        // separator and no empty slot (spec: `navigation-ongoing-notification` —
        // Unknown free-driving values leave no empty fragment).
        val status = FreeDrivingStatus(roadRef = "A5", roadName = "Autobahn", speedKmH = Double.NaN)
        val content = NavigationNotificationContentFormatter.format(
            NavigationState(isNavigating = false),
            resolver = resolver,
            freeDrivingActive = true,
            freeDrivingStatus = status
        )
        assertEquals("A5 Autobahn", content.contentText)
        assertFalse(content.contentText.contains("·"))
    }

    @Test
    fun unknownRoadShowsFallback() {
        // A fix was processed but no road was found there: the off-road fallback,
        // never an empty text (spec: current-road-info — Status clears when the road
        // is lost).
        val status = FreeDrivingStatus(speedKmH = Double.NaN)
        val content = NavigationNotificationContentFormatter.format(
            NavigationState(isNavigating = false),
            resolver = resolver,
            freeDrivingActive = true,
            freeDrivingStatus = status
        )
        assertTrue(content.contentText.contains("Offroad"))
    }

    @Test
    fun noFixYetShowsNeutralTitle() {
        // No status at all means no fix has been processed: the neutral title with
        // no street and no speed — no road from an earlier session (spec:
        // `navigation-ongoing-notification` — No fix yet means no stale road).
        val content = NavigationNotificationContentFormatter.format(
            NavigationState(isNavigating = false),
            resolver = resolver,
            freeDrivingActive = true,
            freeDrivingStatus = null
        )
        assertEquals("Free driving", content.title)
        assertEquals("Free driving", content.contentText)
        assertTrue(content.bigTextLines.isEmpty())
        assertFalse(content.contentText.contains("Offroad"))
    }

    @Test
    fun noDrivingModeProducesEmptyContent() {
        val state = NavigationState(isNavigating = false)
        val content = NavigationNotificationContentFormatter.format(
            state,
            resolver = resolver, freeDrivingActive = false, etaClock = { "13:00" }
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
        freeDrivingStatus: FreeDrivingStatus? = null,
        etaClock: (Long) -> String = { "13:00" }
    ): NotificationPost = NotificationPost(
        content = NavigationNotificationContentFormatter.format(
            state,
            resolver = resolver,
            freeDrivingActive = freeDriving,
            freeDrivingStatus = freeDrivingStatus,
            etaClock = etaClock,
            locale = Locale.US
        ),
        hint = null
    )

    @Test
    fun aPositionOrSpeedOnlyTickDoesNotRepost() {
        // Navigation content: the next manoeuvre, the distance bucket, the arrival minute
        // and the remaining distance — speed is host-visible only in free driving.
        val first = NavigationState(
            isNavigating = true,
            nextStep = instruction,
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
        val first = NavigationState(isNavigating = true, nextStep = instruction)
        val next = first.copy(
            nextStep = stepOf(
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
        val first = NavigationState(isNavigating = true, nextStep = instruction)

        // Same rounded bucket (250 m -> the display rounds to whole metres there).
        val subBucket = first.copy(
            nextStep = stepOf(
                250.4, TurnType.LEFT, "Hauptstrasse", "Turn left into Hauptstrasse", "Turn left"
            )
        )
        assertFalse(
            NavigationNotificationContentFormatter.hostVisibleContentChanged(
                post(first), post(subBucket)
            )
        )

        val nextBucket = first.copy(
            nextStep = stepOf(
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
            nextStep = instruction,
            // A known arrival time: with etaMillis = 0 the hint falls back to the
            // unknown placeholder, which would make the two clocks indistinguishable.
            etaMillis = fakeNow + 30 * 60_000
        )
        val content = NavigationNotificationContentFormatter.format(
            state,
            resolver = resolver, freeDrivingActive = false, etaClock = { "13:00" }, locale = Locale.US
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
        val first = NavigationState(isNavigating = false)
        val firstStatus = FreeDrivingStatus(roadRef = "A5", roadName = "Autobahn", speedKmH = 100.0)
        val otherStatus = FreeDrivingStatus(roadRef = "B7", roadName = "Bundesstrasse", speedKmH = 100.0)

        assertTrue(
            NavigationNotificationContentFormatter.hostVisibleContentChanged(
                post(first, freeDriving = true, freeDrivingStatus = firstStatus),
                post(first, freeDriving = true, freeDrivingStatus = otherStatus)
            )
        )
    }

    @Test
    fun theFreeDrivingSpeedChangeReposts() {
        val state = NavigationState(isNavigating = false)
        val first = FreeDrivingStatus(roadRef = "A5", roadName = "Autobahn", speedKmH = 50.0)
        val faster = first.copy(speedKmH = 51.0)

        assertTrue(
            NavigationNotificationContentFormatter.hostVisibleContentChanged(
                post(state, freeDriving = true, freeDrivingStatus = first),
                post(state, freeDriving = true, freeDrivingStatus = faster)
            )
        )
    }

    @Test
    fun theFirstPostIsAlwaysAChange() {
        assertTrue(
            NavigationNotificationContentFormatter.hostVisibleContentChanged(null, post(NavigationState(isNavigating = true)))
        )
    }

    @Test
    fun anIdenticalStateDoesNotRepost() {
        val state = NavigationState(isNavigating = true, nextStep = instruction)

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
