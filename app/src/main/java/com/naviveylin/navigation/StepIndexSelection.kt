package com.naviveylin.navigation

import com.framstag.libosmscout.client.RouteInstruction

/**
 * Selects the list index of the live [instruction] inside the route's
 * instruction [list].
 *
 * The native engine re-emits the current (upcoming) instruction on every
 * position update; the app maps it back onto the route instruction list for
 * the route-summary highlight and the route-description screen ("current
 * step"). The lookup starts at [currentIndex] and takes the first description
 * match at or after it, so a repeated description ("Straight on", looped
 * street names) can never re-bind the highlight to an earlier, already-passed
 * step. Falls back to [currentIndex] when no match exists ahead.
 */
internal fun nextStepIndex(
    list: List<RouteInstruction>,
    currentIndex: Int,
    instruction: RouteInstruction
): Int {
    val start = currentIndex.coerceAtLeast(0)
    return (start until list.size).firstOrNull { index ->
        list[index].description == instruction.description
    } ?: currentIndex
}
