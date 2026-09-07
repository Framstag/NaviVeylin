package com.naviveylin.core

import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Turn instruction localization (spec: turn-instruction-localization).
 * Plain JUnit — the mapper is context-free; the resolver is a fake over the
 * English and German string sets.
 */
class TurnInstructionLocalizerTest {

    private class FakeResolver(private val strings: Map<Int, String>) : StringResolver {
        override fun get(resId: Int, vararg args: Any): String {
            val template = strings[resId] ?: error("no string for resId $resId")
            return if (args.isEmpty()) template else String.format(template, *args)
        }
    }

    private val en = FakeResolver(ENGLISH)
    private val de = FakeResolver(GERMAN)

    private fun instr(
        type: TurnType,
        street: String = "",
        short: String = "native short",
        desc: String = "native description"
    ) = RouteInstruction(100.0, type, street, desc, short)

    // --- Plain turns ---

    @Test
    fun leftTurn_english() {
        assertEquals("Turn left", TurnInstructionLocalizer.shortDescription(en, instr(TurnType.LEFT)))
    }

    @Test
    fun leftTurn_german() {
        assertEquals("Links abbiegen", TurnInstructionLocalizer.shortDescription(de, instr(TurnType.LEFT)))
    }

    @Test
    fun rightTurn_german() {
        assertEquals("Rechts abbiegen", TurnInstructionLocalizer.shortDescription(de, instr(TurnType.RIGHT)))
    }

    @Test
    fun sharpLeft_german() {
        assertEquals("Scharf links abbiegen", TurnInstructionLocalizer.shortDescription(de, instr(TurnType.SHARP_LEFT)))
    }

    @Test
    fun sharpRight_german() {
        assertEquals("Scharf rechts abbiegen", TurnInstructionLocalizer.shortDescription(de, instr(TurnType.SHARP_RIGHT)))
    }

    @Test
    fun slightlyLeft_german() {
        assertEquals("Leicht links abbiegen", TurnInstructionLocalizer.shortDescription(de, instr(TurnType.SLIGHTLY_LEFT)))
    }

    @Test
    fun slightlyRight_german() {
        assertEquals("Leicht rechts abbiegen", TurnInstructionLocalizer.shortDescription(de, instr(TurnType.SLIGHTLY_RIGHT)))
    }

    @Test
    fun straightOn_german() {
        assertEquals("Geradeaus", TurnInstructionLocalizer.shortDescription(de, instr(TurnType.STRAIGHT_ON)))
    }

    // --- Start / destination ---

    @Test
    fun start_german() {
        assertEquals("Start", TurnInstructionLocalizer.shortDescription(de, instr(TurnType.START)))
    }

    @Test
    fun targetReached_german() {
        assertEquals("Ankunft", TurnInstructionLocalizer.shortDescription(de, instr(TurnType.TARGET_REACHED)))
    }

    // --- Roundabout ---

    @Test
    fun roundaboutEnter_german() {
        assertEquals("Kreisverkehr", TurnInstructionLocalizer.shortDescription(de, instr(TurnType.ROUNDABOUT_ENTER)))
    }

    @Test
    fun roundaboutLeave_exitCount_german() {
        val i = instr(TurnType.ROUNDABOUT_LEAVE, short = "Exit 3")
        assertEquals("Ausfahrt 3", TurnInstructionLocalizer.shortDescription(de, i))
    }

    @Test
    fun roundaboutLeave_exitCount_english() {
        val i = instr(TurnType.ROUNDABOUT_LEAVE, short = "Exit 3")
        assertEquals("Exit 3", TurnInstructionLocalizer.shortDescription(en, i))
    }

    @Test
    fun roundaboutLeave_unparseableExit_fallsBackToZero() {
        val i = instr(TurnType.ROUNDABOUT_LEAVE, short = "Exit ?")
        assertEquals("Ausfahrt 0", TurnInstructionLocalizer.shortDescription(de, i))
    }

    // --- Motorway ---

    @Test
    fun motorwayEnter_german() {
        assertEquals("Autobahn auffahren", TurnInstructionLocalizer.shortDescription(de, instr(TurnType.MOTORWAY_ENTER)))
    }

    @Test
    fun motorwayChange_keepLeft_german() {
        val i = instr(TurnType.LEFT, short = "Keep left")
        assertEquals("Links halten", TurnInstructionLocalizer.shortDescription(de, i))
    }

    @Test
    fun motorwayChange_keepRight_english() {
        val i = instr(TurnType.RIGHT, short = "Keep right")
        assertEquals("Keep right", TurnInstructionLocalizer.shortDescription(en, i))
    }

    @Test
    fun motorwayLeave_german() {
        val i = instr(TurnType.LEFT, short = "Leave motorway")
        assertEquals("Autobahn verlassen", TurnInstructionLocalizer.shortDescription(de, i))
    }

    // --- Fallback ---

    @Test
    fun knownMoveTypeAlwaysLocalizes() {
        // STRAIGHT_ON is a recognized move type, so it localizes even when the
        // native text is unusual (the native text is only a fallback for
        // unrecognized turn types, which the closed enum cannot produce).
        val i = instr(TurnType.STRAIGHT_ON, short = "Some exotic maneuver")
        assertEquals("Geradeaus", TurnInstructionLocalizer.shortDescription(de, i))
    }

    // --- Description composition ---

    @Test
    fun description_withStreet_german() {
        val i = instr(TurnType.LEFT, street = "Hauptstraße", short = "Turn left")
        assertEquals("Links abbiegen in Hauptstraße", TurnInstructionLocalizer.description(de, i))
    }

    @Test
    fun description_withStreet_english() {
        val i = instr(TurnType.LEFT, street = "Hauptstrasse", short = "Turn left")
        assertEquals("Turn left into Hauptstrasse", TurnInstructionLocalizer.description(en, i))
    }

    @Test
    fun description_withoutStreet_equalsShort() {
        val i = instr(TurnType.LEFT, short = "Turn left")
        assertEquals("Links abbiegen", TurnInstructionLocalizer.description(de, i))
    }

    @Test
    fun description_start_ignoresStreet() {
        val i = instr(TurnType.START, street = "Start St", short = "Start")
        assertEquals("Start", TurnInstructionLocalizer.description(de, i))
    }

    @Test
    fun description_targetReached_ignoresStreet() {
        val i = instr(TurnType.TARGET_REACHED, street = "Ziel", short = "Arrive")
        assertEquals("Ankunft", TurnInstructionLocalizer.description(de, i))
    }

    @Test
    fun description_roundaboutLeave_german() {
        val i = instr(TurnType.ROUNDABOUT_LEAVE, street = "A1", short = "Exit 3")
        assertEquals("Ausfahrt 3 auf A1", TurnInstructionLocalizer.description(de, i))
    }

    @Test
    fun description_motorwayEnter_german() {
        val i = instr(TurnType.MOTORWAY_ENTER, street = "A1", short = "Enter motorway")
        assertEquals("Auffahren auf A1", TurnInstructionLocalizer.description(de, i))
    }

    @Test
    fun description_motorwayChange_german() {
        val i = instr(TurnType.LEFT, street = "A1", short = "Keep left")
        assertEquals("Links halten auf A1", TurnInstructionLocalizer.description(de, i))
    }

    @Test
    fun description_motorwayLeave_german() {
        val i = instr(TurnType.LEFT, street = "A1", short = "Leave motorway")
        assertEquals("Autobahn verlassen auf A1", TurnInstructionLocalizer.description(de, i))
    }

    // --- Next-next pair API ---

    @Test
    fun shortDescriptionFor_nextNextHint() {
        assertEquals(
            "Links abbiegen",
            TurnInstructionLocalizer.shortDescriptionFor(de, TurnType.LEFT, "Turn left")
        )
    }

    companion object {
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
            R.string.nav_enter_onto to "Enter %1\$s"
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
            R.string.nav_enter_onto to "Auffahren auf %1\$s"
        )
    }
}
