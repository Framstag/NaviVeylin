package com.naviveylin.service

import com.naviveylin.core.NavigationState
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
 * Pure mapping from (driving mode + navigation state) to notification
 * content (spec: navigation-ongoing-notification — R3 navigation guidance
 * content, R5 free-driving content). No Android dependencies — unit-testable
 * without Robolectric. Reuses the app's existing display conventions
 * ([formatRemainingTime], current-road text, [formatDistanceNumber]).
 */
object NavigationNotificationContentFormatter {

    private const val TITLE_NAVIGATION_ACTIVE = "Navigation active"
    private const val TITLE_FREE_DRIVING = "Free driving"

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
