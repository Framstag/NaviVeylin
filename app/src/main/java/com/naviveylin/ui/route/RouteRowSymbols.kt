package com.naviveylin.ui.route

import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.Manoeuvre

/**
 * The turn type a row's icon is drawn from, resolved from the manoeuvre's **kind and move**
 * (spec: `turn-instruction-localization` — The row icon follows the manoeuvre, not the text).
 *
 * A motorway change is drawn with the motorway glyph, never as a plain turn: the bridge publishes no
 * turn type for it, which is exactly what made the old prose matcher unable to tell the two apart.
 */
internal fun rowTurnType(manoeuvre: Manoeuvre): TurnType = when (manoeuvre.kind) {
    RouteStep.Kind.START -> TurnType.START
    RouteStep.Kind.TARGET -> TurnType.TARGET_REACHED
    RouteStep.Kind.ROUNDABOUT_ENTER -> TurnType.ROUNDABOUT_ENTER
    RouteStep.Kind.ROUNDABOUT_LEAVE -> TurnType.ROUNDABOUT_LEAVE
    // Entering and changing a motorway share the motorway glyph; the direction it carries is the
    // move's, which the glyph does not draw anyway.
    RouteStep.Kind.MOTORWAY_ENTER, RouteStep.Kind.MOTORWAY_CHANGE -> TurnType.MOTORWAY_ENTER
    RouteStep.Kind.MOTORWAY_LEAVE, RouteStep.Kind.TURN, RouteStep.Kind.NAME_CHANGE,
    RouteStep.Kind.INFORMATIVE, RouteStep.Kind.UNKNOWN ->
        manoeuvre.move ?: TurnType.STRAIGHT_ON
}
