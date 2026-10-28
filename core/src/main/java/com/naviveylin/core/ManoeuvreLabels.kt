package com.naviveylin.core

import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType

/**
 * Localized text for a route row, built from the manoeuvre's own values (spec:
 * `turn-instruction-localization` — Route analysis rows are localized from the step model).
 *
 * The native layer publishes values, never sentences ([Manoeuvre]), so every word a row shows is
 * resolved here from `:core` resources in the device language — the same wording every other
 * surface uses: one wording set, resolved from `:core` resources.
 *
 * `short` is the manoeuvre alone ("Turn left", "Exit 3", "Keep left"); `description` adds the street
 * or motorway the manoeuvre leads onto ("Turn left into Hauptstraße"). A manoeuvre that names
 * nothing is its own description, so no row ever ends in a dangling preposition.
 */
object ManoeuvreLabels {

    /** Localized text for the manoeuvre alone. */
    fun short(resolver: StringResolver, manoeuvre: Manoeuvre): String = when (manoeuvre.kind) {
        RouteStep.Kind.START -> resolver.get(R.string.nav_start)
        RouteStep.Kind.TARGET -> resolver.get(R.string.nav_arrive)
        RouteStep.Kind.ROUNDABOUT_ENTER -> resolver.get(R.string.nav_roundabout)
        RouteStep.Kind.ROUNDABOUT_LEAVE -> exitText(resolver, manoeuvre)
        RouteStep.Kind.MOTORWAY_ENTER -> resolver.get(R.string.nav_enter_motorway)
        RouteStep.Kind.MOTORWAY_CHANGE -> keepText(resolver, manoeuvre.move)
        RouteStep.Kind.MOTORWAY_LEAVE -> resolver.get(R.string.nav_leave_motorway)
        RouteStep.Kind.NAME_CHANGE -> resolver.get(R.string.nav_continue_on, manoeuvre.displayName ?: "")
        RouteStep.Kind.TURN -> turnText(resolver, manoeuvre.move)
        // An unmapped manoeuvre (a kind the model carries but no wording exists for) renders the
        // neutral turn wording, never an empty row and never native text — none is published
        // (spec: `turn-instruction-localization` — the retired fallback's migration).
        RouteStep.Kind.INFORMATIVE, RouteStep.Kind.UNKNOWN ->
            manoeuvre.displayName ?: resolver.get(R.string.nav_straight_on)
    }

    /**
     * Localized text for the row: the manoeuvre plus the name it leads onto, where the kind
     * composes one.
     */
    fun description(resolver: StringResolver, manoeuvre: Manoeuvre): String {
        val short = short(resolver, manoeuvre)
        val name = compositionTarget(manoeuvre) ?: return short
        return when (manoeuvre.kind) {
            // The start and the arrival carry no street in their wording, exactly as on the live path.
            RouteStep.Kind.START, RouteStep.Kind.TARGET -> short
            RouteStep.Kind.ROUNDABOUT_ENTER -> short
            RouteStep.Kind.ROUNDABOUT_LEAVE ->
                resolver.get(R.string.nav_exit_onto, manoeuvre.roundaboutExit ?: 0, name)
            RouteStep.Kind.MOTORWAY_ENTER -> resolver.get(R.string.nav_enter_onto, name)
            RouteStep.Kind.MOTORWAY_CHANGE, RouteStep.Kind.MOTORWAY_LEAVE ->
                resolver.get(R.string.nav_keep_onto, short, name)
            RouteStep.Kind.NAME_CHANGE -> short
            RouteStep.Kind.TURN -> resolver.get(R.string.nav_turn_into, short, name)
            RouteStep.Kind.INFORMATIVE, RouteStep.Kind.UNKNOWN -> short
        }
    }

    /**
     * The name the row composes: the motorway a motorway manoeuvre leads onto, else the street the
     * manoeuvre names. `null` when the manoeuvre names nothing, so no row ends in a dangling
     * preposition.
     */
    private fun compositionTarget(manoeuvre: Manoeuvre): String? {
        val candidate = when (manoeuvre.kind) {
            RouteStep.Kind.MOTORWAY_ENTER, RouteStep.Kind.MOTORWAY_CHANGE ->
                manoeuvre.motorwayTo ?: manoeuvre.displayName
            else -> manoeuvre.displayName
        }
        return candidate?.takeIf { it.isNotBlank() }
    }

    /** The roundabout exit with its ordinal, or the bare roundabout when no ordinal is known. */
    private fun exitText(resolver: StringResolver, manoeuvre: Manoeuvre): String =
        when (val exit = manoeuvre.roundaboutExit) {
            null -> resolver.get(R.string.nav_roundabout)
            else -> resolver.get(R.string.nav_exit_roundabout, exit)
        }

    private fun turnText(resolver: StringResolver, move: TurnType?): String = when (move) {
        TurnType.SHARP_LEFT -> resolver.get(R.string.nav_turn_sharp_left)
        TurnType.LEFT -> resolver.get(R.string.nav_turn_left)
        TurnType.SLIGHTLY_LEFT -> resolver.get(R.string.nav_turn_slightly_left)
        TurnType.SLIGHTLY_RIGHT -> resolver.get(R.string.nav_turn_slightly_right)
        TurnType.RIGHT -> resolver.get(R.string.nav_turn_right)
        TurnType.SHARP_RIGHT -> resolver.get(R.string.nav_turn_sharp_right)
        // An absent move is not a turn direction the route described: "straight on" is the only
        // wording that claims nothing the route did not say (spec: `turn-instruction-localization`).
        TurnType.STRAIGHT_ON, null -> resolver.get(R.string.nav_straight_on)
        else -> resolver.get(R.string.nav_straight_on)
    }

    private fun keepText(resolver: StringResolver, move: TurnType?): String = when (move) {
        TurnType.SHARP_LEFT -> resolver.get(R.string.nav_keep_sharp_left)
        TurnType.LEFT -> resolver.get(R.string.nav_keep_left)
        TurnType.SLIGHTLY_LEFT -> resolver.get(R.string.nav_keep_slightly_left)
        TurnType.SLIGHTLY_RIGHT -> resolver.get(R.string.nav_keep_slightly_right)
        TurnType.RIGHT -> resolver.get(R.string.nav_keep_right)
        TurnType.SHARP_RIGHT -> resolver.get(R.string.nav_keep_sharp_right)
        TurnType.STRAIGHT_ON, null -> resolver.get(R.string.nav_keep_straight)
        else -> resolver.get(R.string.nav_keep_straight)
    }
}
