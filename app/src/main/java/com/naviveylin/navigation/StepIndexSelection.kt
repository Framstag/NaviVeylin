package com.naviveylin.navigation

import com.framstag.libosmscout.client.RouteStep

/**
 * Selects the list index of the live [step] inside the route's step [list].
 *
 * The native engine re-emits the step of the upcoming manoeuvre on every position update; the app
 * maps it back onto the route's step list for the route-summary highlight and the route-description
 * screen ("current step").
 *
 * The match is the step's **distance from the route start and its manoeuvre kind**, never a text:
 * one route node can produce two described manoeuvres (a turn and a motorway entry leave the same
 * node), so distance alone is ambiguous and the description the earlier implementation compared no
 * longer exists (change `route-steps-live-guidance`, design D5).
 *
 * The lookup starts at [currentIndex] and takes the first match at or after it, so a repeated
 * manoeuvre (a looped street, two identical turns) can never re-bind the highlight to an earlier,
 * already-passed step. Falls back to [currentIndex] when no match exists ahead.
 */
internal fun nextStepIndex(
    list: List<RouteStep>,
    currentIndex: Int,
    step: RouteStep
): Int {
    val start = currentIndex.coerceAtLeast(0)
    return (start until list.size).firstOrNull { index ->
        list[index].kind == step.kind &&
            kotlin.math.abs(list[index].distanceFromStart - step.distanceFromStart) <= MATCH_WINDOW_METERS
    } ?: currentIndex
}

/**
 * How far the live step's distance-from-start may sit from the published list entry's before the two
 * are considered different manoeuvres, in metres. The published list and the live walk are collected
 * from the same description, so the values agree; the window absorbs the snap of the position onto the
 * route, which moves the remaining distance but not the node's own distance from the start.
 */
internal const val MATCH_WINDOW_METERS = 50.0
