package com.naviveylin.core

import android.content.Context
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType

/**
 * Resolves a string resource with optional format arguments.
 * Implemented by callers against their own [Context] (Compose
 * `LocalContext`, Car App Library `carContext`, or a test fake).
 */
fun interface StringResolver {
    fun get(resId: Int, vararg args: Any): String
}

/** [StringResolver] backed by [Context.getString]. */
fun Context.stringResolver(): StringResolver =
    StringResolver { resId, args -> getString(resId, *args) }

/**
 * Localizes turn-by-turn instruction text (spec: turn-instruction-localization).
 *
 * The native JNI bridge (libosmscout-client-java `OSMScoutClient.cpp`, inside
 * the pinned libosmscout submodule) generates instruction descriptions in
 * hardcoded English ("Turn left", "Exit 3", "Enter motorway", "Keep left").
 * Per the i18n-l10n spec the native layer stays unchanged; this mapper rebuilds
 * the display text from the structured fields the bridge already exposes
 * ([RouteInstruction.turnType], [RouteInstruction.streetName]) using Android
 * string resources, falling back to the native English text when a turn type
 * or text pattern is not recognized.
 *
 * The bridge does not expose a distinct turn type for motorway change/leave
 * (it reuses the plain move types) and embeds the roundabout exit count only
 * in the English "Exit N" text, so those cases are disambiguated by parsing
 * the stable native short description.
 */
object TurnInstructionLocalizer {

    // English markers in the native short description used to disambiguate
    // maneuvers the bridge does not expose as a distinct TurnType.
    private const val KEEP_PREFIX = "Keep "
    private const val LEAVE_MOTORWAY = "Leave motorway"
    private const val EXIT_PREFIX = "Exit "

    /**
     * Localized short description ("Turn left" / "Links abbiegen").
     * Falls back to the native English short description when the turn type
     * or text pattern is not recognized.
     */
    fun shortDescription(resolver: StringResolver, instruction: RouteInstruction): String =
        shortDescriptionFor(resolver, instruction.turnType, instruction.shortDescription)

    /**
     * Localized short description for a (turn type, native short description)
     * pair — also usable for the "next next" hint, which carries its own
     * turn type and text on the same [RouteInstruction].
     */
    fun shortDescriptionFor(
        resolver: StringResolver,
        turnType: TurnType,
        nativeShort: String
    ): String = when (turnType) {
        TurnType.START -> resolver.get(R.string.nav_start)
        TurnType.TARGET_REACHED -> resolver.get(R.string.nav_arrive)
        TurnType.ROUNDABOUT_ENTER -> resolver.get(R.string.nav_roundabout)
        TurnType.ROUNDABOUT_LEAVE ->
            resolver.get(R.string.nav_exit_roundabout, exitCount(nativeShort))
        TurnType.MOTORWAY_ENTER -> resolver.get(R.string.nav_enter_motorway)
        else -> when {
            nativeShort.startsWith(KEEP_PREFIX) -> keepText(resolver, turnType, nativeShort)
            nativeShort == LEAVE_MOTORWAY -> resolver.get(R.string.nav_leave_motorway)
            else -> turnText(resolver, turnType, nativeShort)
        }
    }

    /**
     * Localized full description ("Turn left into Hauptstrasse" /
     * "Links abbiegen in Hauptstraße"). Equals the localized short description
     * when the instruction has no street name.
     */
    fun description(resolver: StringResolver, instruction: RouteInstruction): String {
        val short = shortDescription(resolver, instruction)
        val street = instruction.streetName.takeIf { it.isNotBlank() } ?: return short
        return when (instruction.turnType) {
            // Start/arrival carry no street composition in the native text.
            TurnType.START, TurnType.TARGET_REACHED -> short
            TurnType.ROUNDABOUT_LEAVE ->
                resolver.get(R.string.nav_exit_onto, exitCount(instruction.shortDescription), street)
            TurnType.MOTORWAY_ENTER -> resolver.get(R.string.nav_enter_onto, street)
            else -> when {
                instruction.shortDescription.startsWith(KEEP_PREFIX) ||
                    instruction.shortDescription == LEAVE_MOTORWAY ->
                    resolver.get(R.string.nav_keep_onto, short, street)
                else -> resolver.get(R.string.nav_turn_into, short, street)
            }
        }
    }

    private fun turnText(
        resolver: StringResolver,
        turnType: TurnType,
        nativeShort: String
    ): String = when (turnType) {
        TurnType.SHARP_LEFT -> resolver.get(R.string.nav_turn_sharp_left)
        TurnType.LEFT -> resolver.get(R.string.nav_turn_left)
        TurnType.SLIGHTLY_LEFT -> resolver.get(R.string.nav_turn_slightly_left)
        TurnType.STRAIGHT_ON -> resolver.get(R.string.nav_straight_on)
        TurnType.SLIGHTLY_RIGHT -> resolver.get(R.string.nav_turn_slightly_right)
        TurnType.RIGHT -> resolver.get(R.string.nav_turn_right)
        TurnType.SHARP_RIGHT -> resolver.get(R.string.nav_turn_sharp_right)
        // Defensive: unknown turn type falls back to the native text.
        else -> nativeShort
    }

    private fun keepText(
        resolver: StringResolver,
        turnType: TurnType,
        nativeShort: String
    ): String = when (turnType) {
        TurnType.SHARP_LEFT -> resolver.get(R.string.nav_keep_sharp_left)
        TurnType.LEFT -> resolver.get(R.string.nav_keep_left)
        TurnType.SLIGHTLY_LEFT -> resolver.get(R.string.nav_keep_slightly_left)
        TurnType.STRAIGHT_ON -> resolver.get(R.string.nav_keep_straight)
        TurnType.SLIGHTLY_RIGHT -> resolver.get(R.string.nav_keep_slightly_right)
        TurnType.RIGHT -> resolver.get(R.string.nav_keep_right)
        TurnType.SHARP_RIGHT -> resolver.get(R.string.nav_keep_sharp_right)
        // Defensive: unknown turn type falls back to the native text.
        else -> nativeShort
    }

    /** Exit count from the native short description ("Exit 3" → 3); 0 if unparseable. */
    private fun exitCount(shortDescription: String): Int =
        shortDescription.removePrefix(EXIT_PREFIX).trim().toIntOrNull() ?: 0
}
