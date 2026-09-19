package com.naviveylin.service

import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.NavigationState
import com.naviveylin.core.R
import com.naviveylin.core.StringResolver
import com.naviveylin.core.TurnInstructionLocalizer
import com.naviveylin.core.formatDistanceNumber
import com.naviveylin.core.distanceUsesKilometers
import com.naviveylin.ui.navigation.currentRoadText
import com.naviveylin.ui.navigation.formatRemainingTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Immutable notification content for the ongoing navigation/free-driving
 * notification, produced by [NavigationNotificationContentFormatter].
 *
 * @param title head line of the notification
 * @param contentText short text (collapsed shade row)
 * @param bigTextLines extra lines shown in the expanded notification
 * @param showStopAction whether a "stop" action is offered (navigation only)
 */
data class NavigationNotificationContent(
    val title: String,
    val contentText: String,
    val bigTextLines: List<String>,
    val showStopAction: Boolean
)

/**
 * Car-screen hint content (spec: auto-navigation-hints — "Car hint content"):
 * how the ongoing navigation notification is rendered on the car screen, where
 * the maneuver instruction leads and the destination name does not (the phone
 * notification keeps its destination-first roles).
 *
 * @param title primary car text — the maneuver instruction in the wording of
 *   the on-screen next-turn display, or the neutral fallback while no maneuver
 *   is known yet
 * @param text secondary car text — distance to the maneuver (to the
 *   destination while no maneuver is known) plus the arrival time
 * @param turnType turn type for the car large icon; null when no maneuver is
 *   known yet, in which case no large icon is offered
 */
data class CarHintContent(
    val title: String,
    val text: String,
    val turnType: TurnType?
)

/**
 * Pure mapping from (driving mode + navigation state) to notification
 * content (spec: navigation-ongoing-notification — R3 navigation guidance
 * content, R5 free-driving content). No Android dependencies — unit-testable
 * without Robolectric. Reuses the app's existing display conventions
 * ([formatRemainingTime], current-road text, [formatDistanceNumber]).
 */
object NavigationNotificationContentFormatter {

    private const val TITLE_NAVIGATION_ACTIVE = "Navigation active"
    private const val TITLE_FREE_DRIVING = "Free driving"

    /** Arrival time placeholder when no ETA is known. */
    private const val ETA_UNKNOWN = "--:--"

    /**
     * Format the content for [state] and driving mode.
     *
     * @param state live navigation state
     * @param freeDrivingActive true when a FREE_DRIVE mode is active
     *   (surface-independent shared flag); content only when not navigating
     * @param etaClock wall-clock formatter for the arrival time; injectable
     *   for tests, defaults to device-locale "HH:mm"
     */
    fun format(
        state: NavigationState,
        freeDrivingActive: Boolean,
        etaClock: (Long) -> String = ::formatEtaClock,
        locale: Locale = Locale.getDefault()
    ): NavigationNotificationContent {
        return if (state.isNavigating) {
            navigationContent(state, etaClock, locale)
        } else {
            freeDriveContent(state, freeDrivingActive, locale)
        }
    }

    /**
     * Car-screen hint content while NAVIGATION mode is active (spec:
     * auto-navigation-hints — "Car hint content"). Null when navigation is not
     * active: free driving has no car surface, so the app offers the host no
     * turn-by-turn hint (spec: "No car surface for free driving").
     *
     * @param state live navigation state
     * @param resolver localizes the maneuver instruction exactly like the
     *   on-screen next-turn display
     * @param etaClock wall-clock formatter for the arrival time; injectable for
     *   tests, defaults to device-locale "HH:mm"
     */
    fun carHint(
        state: NavigationState,
        resolver: StringResolver,
        etaClock: (Long) -> String = ::formatEtaClock,
        locale: Locale = Locale.getDefault()
    ): CarHintContent? {
        if (!state.isNavigating) return null
        // Same instruction source as the phone path: the live next instruction
        // when the engine emits one, otherwise the frozen route list entry.
        val instruction = state.nextInstruction
            ?: state.instructions.getOrNull(state.currentStepIndex)
        val eta = if (state.etaMillis > 0) etaClock(state.etaMillis) else ETA_UNKNOWN
        val distance = formatDistanceCompact(
            instruction?.distanceTo ?: state.remainingDistance,
            locale
        )
        return CarHintContent(
            title = instructionText(instruction, resolver),
            text = resolver.get(R.string.nav_hint_distance_eta, distance, eta),
            turnType = instruction?.turnType
        )
    }

    /**
     * The maneuver instruction in the wording of the on-screen next-turn
     * display ([com.naviveylin.ui.navigation.splitInstruction]'s generic line):
     * the localized short description, falling back to the native description
     * and finally to the neutral text while no maneuver is known.
     */
    private fun instructionText(instruction: RouteInstruction?, resolver: StringResolver): String {
        if (instruction == null) return resolver.get(R.string.nav_hint_neutral)
        return TurnInstructionLocalizer.shortDescription(resolver, instruction)
            .takeIf { it.isNotBlank() }
            ?: instruction.description.takeIf { it.isNotBlank() }
            ?: resolver.get(R.string.nav_hint_neutral)
    }

    private fun navigationContent(
        state: NavigationState,
        etaClock: (Long) -> String,
        locale: Locale
    ): NavigationNotificationContent {
        val instruction = state.nextInstruction
        val manoeuvre = instruction?.description
            ?.takeIf { it.isNotBlank() }
            ?: instruction?.shortDescription?.takeIf { it.isNotBlank() }
            ?: ""

        val title = state.destinationName?.takeIf { it.isNotBlank() }
            ?: TITLE_NAVIGATION_ACTIVE

        val distanceToTurn = formatDistanceCompact(instruction?.distanceTo ?: 0.0, locale)
        val remainingDistance = formatDistanceCompact(state.remainingDistance, locale)
        val remainingTime = formatRemainingTime(state.etaMillis)
        val eta = if (state.etaMillis > 0) etaClock(state.etaMillis) else "--:--"
        val statsLine = "in $distanceToTurn · arrive $eta · $remainingTime · $remainingDistance"

        return NavigationNotificationContent(
            title = title,
            contentText = manoeuvre.ifBlank { statsLine },
            bigTextLines = listOf(manoeuvre, statsLine).filter { it.isNotBlank() },
            showStopAction = true
        )
    }

    private fun freeDriveContent(
        state: NavigationState,
        freeDrivingActive: Boolean,
        locale: Locale
    ): NavigationNotificationContent {
        if (!freeDrivingActive) {
            // No driving mode active — no content (the caller should not
            // render a notification at all; defensive here).
            return NavigationNotificationContent(
                title = TITLE_FREE_DRIVING,
                contentText = "",
                bigTextLines = emptyList(),
                showStopAction = false
            )
        }
        val road = currentRoadText(state.currentRoadInfo)
        val speed = if (!state.currentSpeedKmH.isNaN()) {
            "${state.currentSpeedKmH.roundToInt()} km/h"
        } else {
            ""
        }
        return NavigationNotificationContent(
            title = TITLE_FREE_DRIVING,
            contentText = listOfNotNull(road, speed).joinToString(" · ").ifBlank { "Free driving" },
            bigTextLines = listOfNotNull(road, speed).filter { it.isNotBlank() },
            showStopAction = false
        )
    }

    /** Compact distance like the on-screen displays: "350 m" / "1.2 km". */
    private fun formatDistanceCompact(meters: Double, locale: Locale): String {
        if (meters.isNaN() || meters <= 0.0) return "--"
        val number = formatDistanceNumber(meters, locale)
        return if (distanceUsesKilometers(meters)) "$number km" else "$number m"
    }

    /** Wall-clock arrival time, device locale ("14:32"). */
    private fun formatEtaClock(etaMillis: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(etaMillis))
}
