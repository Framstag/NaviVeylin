package com.naviveylin.core

import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The structure-based label builder for route rows (spec: `turn-instruction-localization` — Route
 * analysis rows are localized from the step model).
 *
 * Plain JUnit — the builder is context-free; the resolver is a fake over the English and German
 * string sets.
 */
class ManeuverLabelTest {

    private class FakeResolver(private val strings: Map<Int, String>) : StringResolver {
        override fun get(resId: Int, vararg args: Any): String {
            val template = strings[resId] ?: error("no string for resId $resId")
            return if (args.isEmpty()) template else String.format(template, *args)
        }
    }

    private val en = FakeResolver(ENGLISH)
    private val de = FakeResolver(GERMAN)

    private fun manoeuvre(
        kind: RouteStep.Kind,
        move: TurnType? = null,
        name: String? = null,
        ref: String? = null,
        exit: Int? = null,
        motorwayFrom: String? = null,
        motorwayTo: String? = null
    ) = Manoeuvre(
        kind = kind,
        move = move,
        roundaboutExit = exit,
        clockwise = null,
        motorwayFrom = motorwayFrom,
        motorwayTo = motorwayTo,
        name = name,
        ref = ref,
        typeName = null,
        lat = 0.0,
        lon = 0.0,
        legDistanceMeters = 0.0,
        legTimeSeconds = 0.0,
        databaseId = 0L,
        pathObjectOffset = 0L,
        nodeIndex = 0L
    )

    // --- The seven moves ---

    @Test
    fun `every turn move has its own wording`() {
        val expected = mapOf(
            TurnType.SHARP_LEFT to "Scharf links abbiegen",
            TurnType.LEFT to "Links abbiegen",
            TurnType.SLIGHTLY_LEFT to "Leicht links abbiegen",
            TurnType.STRAIGHT_ON to "Geradeaus",
            TurnType.SLIGHTLY_RIGHT to "Leicht rechts abbiegen",
            TurnType.RIGHT to "Rechts abbiegen",
            TurnType.SHARP_RIGHT to "Scharf rechts abbiegen"
        )

        for ((move, text) in expected) {
            assertEquals(
                "move $move",
                text,
                ManoeuvreLabels.short(de, manoeuvre(RouteStep.Kind.TURN, move = move))
            )
        }
    }

    @Test
    fun `the keep wording follows the move as well`() {
        // A motorway change is a "keep" manoeuvre: the sign says keep left, not turn left.
        assertEquals(
            "Scharf links halten",
            ManoeuvreLabels.short(de, manoeuvre(RouteStep.Kind.MOTORWAY_CHANGE, TurnType.SHARP_LEFT))
        )
        assertEquals(
            "Rechts halten",
            ManoeuvreLabels.short(de, manoeuvre(RouteStep.Kind.MOTORWAY_CHANGE, TurnType.RIGHT))
        )
        assertEquals(
            "Keep straight",
            ManoeuvreLabels.short(en, manoeuvre(RouteStep.Kind.MOTORWAY_CHANGE, TurnType.STRAIGHT_ON))
        )
    }

    // --- Start and arrival ---

    @Test
    fun `the start and the arrival carry no street composition`() {
        assertEquals("Start", ManoeuvreLabels.description(de, manoeuvre(RouteStep.Kind.START, name = "Hauptstraße")))
        assertEquals("Ankunft", ManoeuvreLabels.description(de, manoeuvre(RouteStep.Kind.TARGET, name = "Zielweg")))
    }

    // --- Roundabout ---

    @Test
    fun `a roundabout enter is the bare roundabout`() {
        assertEquals("Kreisverkehr", ManoeuvreLabels.short(de, manoeuvre(RouteStep.Kind.ROUNDABOUT_ENTER)))
        assertEquals(
            "Roundabout",
            ManoeuvreLabels.description(en, manoeuvre(RouteStep.Kind.ROUNDABOUT_ENTER, name = "Kreis"))
        )
    }

    @Test
    fun `a roundabout leave names its ordinal`() {
        assertEquals(
            "Ausfahrt 3",
            ManoeuvreLabels.short(de, manoeuvre(RouteStep.Kind.ROUNDABOUT_LEAVE, exit = 3))
        )
        assertEquals(
            "Ausfahrt 3 auf Bahnhofstraße",
            ManoeuvreLabels.description(
                de,
                manoeuvre(RouteStep.Kind.ROUNDABOUT_LEAVE, exit = 3, name = "Bahnhofstraße")
            )
        )
        assertEquals(
            "Exit 1",
            ManoeuvreLabels.short(en, manoeuvre(RouteStep.Kind.ROUNDABOUT_LEAVE, exit = 1))
        )
    }

    @Test
    fun `a roundabout leave without an ordinal does not invent one`() {
        // No ordinal known: the wording must still be produced and must name no number at all.
        val text = ManoeuvreLabels.short(de, manoeuvre(RouteStep.Kind.ROUNDABOUT_LEAVE))

        assertEquals("Kreisverkehr", text)
    }

    // --- Motorway ---

    @Test
    fun `a motorway enter names the motorway entered`() {
        assertEquals(
            "Autobahn auffahren",
            ManoeuvreLabels.short(de, manoeuvre(RouteStep.Kind.MOTORWAY_ENTER, motorwayTo = "A42"))
        )
        assertEquals(
            "Auffahren auf A42",
            ManoeuvreLabels.description(de, manoeuvre(RouteStep.Kind.MOTORWAY_ENTER, motorwayTo = "A42"))
        )
    }

    @Test
    fun `a motorway change is worded as a keep onto the next motorway`() {
        assertEquals(
            "Links halten auf A42",
            ManoeuvreLabels.description(
                de,
                manoeuvre(RouteStep.Kind.MOTORWAY_CHANGE, TurnType.LEFT, motorwayFrom = "A40", motorwayTo = "A42")
            )
        )
        assertEquals(
            "Keep right onto A42",
            ManoeuvreLabels.description(
                en,
                manoeuvre(RouteStep.Kind.MOTORWAY_CHANGE, TurnType.RIGHT, motorwayFrom = "A40", motorwayTo = "A42")
            )
        )
    }

    @Test
    fun `a motorway leave is worded as a leave`() {
        assertEquals(
            "Autobahn verlassen",
            ManoeuvreLabels.short(de, manoeuvre(RouteStep.Kind.MOTORWAY_LEAVE, TurnType.LEFT, motorwayFrom = "A40"))
        )
        assertEquals(
            "Autobahn verlassen auf Hauptstraße",
            ManoeuvreLabels.description(
                de,
                manoeuvre(
                    RouteStep.Kind.MOTORWAY_LEAVE, TurnType.LEFT,
                    name = "Hauptstraße", motorwayFrom = "A40"
                )
            )
        )
    }

    // --- Street name change ---

    @Test
    fun `a name change says which street the route continues on`() {
        assertEquals(
            "Weiter auf Hauptstraße",
            ManoeuvreLabels.description(de, manoeuvre(RouteStep.Kind.NAME_CHANGE, name = "Hauptstraße"))
        )
        assertEquals(
            "Continue on Main Street",
            ManoeuvreLabels.description(en, manoeuvre(RouteStep.Kind.NAME_CHANGE, name = "Main Street"))
        )
    }

    // --- Empty street composition ---

    @Test
    fun `an empty street leaves the manoeuvre alone`() {
        // An unnamed way must not produce "Turn left into ": the short text is the whole row.
        assertEquals(
            "Links abbiegen",
            ManoeuvreLabels.description(de, manoeuvre(RouteStep.Kind.TURN, TurnType.LEFT))
        )
        assertEquals(
            "Rechts abbiegen",
            ManoeuvreLabels.description(de, manoeuvre(RouteStep.Kind.TURN, TurnType.RIGHT, name = "  "))
        )
    }

    @Test
    fun `a named turn composes the street into the row`() {
        assertEquals(
            "Links abbiegen in Hauptstraße",
            ManoeuvreLabels.description(de, manoeuvre(RouteStep.Kind.TURN, TurnType.LEFT, name = "Hauptstraße"))
        )
        assertEquals(
            "Turn right into Main Street",
            ManoeuvreLabels.description(en, manoeuvre(RouteStep.Kind.TURN, TurnType.RIGHT, name = "Main Street"))
        )
    }


    @Test
    fun `an unmapped manoeuvre renders the neutral wording`() {
        // The retired native fallback is gone - the bridge publishes no text. A manoeuvre the model
        // carries but no wording exists for renders the neutral turn wording, never an empty row and
        // never an English sentence (spec: `turn-instruction-localization`, the removal's migration).
        assertEquals("Geradeaus", ManoeuvreLabels.short(de, manoeuvre(RouteStep.Kind.UNKNOWN)))
        assertEquals("Straight on", ManoeuvreLabels.short(en, manoeuvre(RouteStep.Kind.UNKNOWN)))
        // A named informational step shows its name instead - still no prose.
        assertEquals(
            "Kirchweg",
            ManoeuvreLabels.short(de, manoeuvre(RouteStep.Kind.INFORMATIVE, name = "Kirchweg"))
        )
    }

    private companion object {
        private val ENGLISH = mapOf(
            R.string.nav_start to "Start",
            R.string.nav_arrive to "Arrive",
            R.string.nav_roundabout to "Roundabout",
            R.string.nav_exit_roundabout to "Exit %1\$d",
            R.string.nav_enter_motorway to "Enter motorway",
            R.string.nav_leave_motorway to "Leave motorway",
            R.string.nav_turn_sharp_left to "Turn sharp left",
            R.string.nav_turn_left to "Turn left",
            R.string.nav_turn_slightly_left to "Turn slightly left",
            R.string.nav_straight_on to "Straight on",
            R.string.nav_turn_slightly_right to "Turn slightly right",
            R.string.nav_turn_right to "Turn right",
            R.string.nav_turn_sharp_right to "Turn sharp right",
            R.string.nav_keep_sharp_left to "Keep sharp left",
            R.string.nav_keep_left to "Keep left",
            R.string.nav_keep_slightly_left to "Keep slightly left",
            R.string.nav_keep_straight to "Keep straight",
            R.string.nav_keep_slightly_right to "Keep slightly right",
            R.string.nav_keep_right to "Keep right",
            R.string.nav_keep_sharp_right to "Keep sharp right",
            R.string.nav_turn_into to "%1\$s into %2\$s",
            R.string.nav_keep_onto to "%1\$s onto %2\$s",
            R.string.nav_exit_onto to "Take exit %1\$d onto %2\$s",
            R.string.nav_enter_onto to "Enter %1\$s",
            R.string.nav_continue_on to "Continue on %1\$s"
        )

        private val GERMAN = mapOf(
            R.string.nav_start to "Start",
            R.string.nav_arrive to "Ankunft",
            R.string.nav_roundabout to "Kreisverkehr",
            R.string.nav_exit_roundabout to "Ausfahrt %1\$d",
            R.string.nav_enter_motorway to "Autobahn auffahren",
            R.string.nav_leave_motorway to "Autobahn verlassen",
            R.string.nav_turn_sharp_left to "Scharf links abbiegen",
            R.string.nav_turn_left to "Links abbiegen",
            R.string.nav_turn_slightly_left to "Leicht links abbiegen",
            R.string.nav_straight_on to "Geradeaus",
            R.string.nav_turn_slightly_right to "Leicht rechts abbiegen",
            R.string.nav_turn_right to "Rechts abbiegen",
            R.string.nav_turn_sharp_right to "Scharf rechts abbiegen",
            R.string.nav_keep_sharp_left to "Scharf links halten",
            R.string.nav_keep_left to "Links halten",
            R.string.nav_keep_slightly_left to "Leicht links halten",
            R.string.nav_keep_straight to "Geradeaus halten",
            R.string.nav_keep_slightly_right to "Leicht rechts halten",
            R.string.nav_keep_right to "Rechts halten",
            R.string.nav_keep_sharp_right to "Scharf rechts halten",
            R.string.nav_turn_into to "%1\$s in %2\$s",
            R.string.nav_keep_onto to "%1\$s auf %2\$s",
            R.string.nav_exit_onto to "Ausfahrt %1\$d auf %2\$s",
            R.string.nav_enter_onto to "Auffahren auf %1\$s",
            R.string.nav_continue_on to "Weiter auf %1\$s"
        )
    }
}
