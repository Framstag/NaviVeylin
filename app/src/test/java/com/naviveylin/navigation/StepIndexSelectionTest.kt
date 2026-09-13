package com.naviveylin.navigation

import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure unit tests for the step-index lookup shared by the phone and car
 * navigation handlers (spec: auto/navigation-view — "Current step shown
 * during navigation", next-turn-overlay). Plain JUnit: no native client, no
 * Robolectric sandbox requirement.
 *
 * The lookup must advance monotonically: a repeated description ("Straight
 * on", looped street names) must never re-bind the active step to an
 * earlier, already-passed occurrence.
 */
class StepIndexSelectionTest {

    private fun instr(description: String) =
        RouteInstruction(100.0, TurnType.LEFT, "Main St", description, "Turn left")

    @Test
    fun `matches first occurrence at or after current index`() {
        val list = listOf(instr("start"), instr("Turn left into A"), instr("Straight on"))
        assertEquals(1, nextStepIndex(list, 0, instr("Turn left into A")))
    }

    @Test
    fun `advances forward with the live instruction`() {
        val list = listOf(instr("start"), instr("Turn left into A"), instr("Straight on"))
        // Current step is the left turn; live instruction is the straight-ahead.
        assertEquals(2, nextStepIndex(list, 1, instr("Straight on")))
    }

    @Test
    fun `duplicate description does not jump backward`() {
        // "Turn left into A" appears at index 0 (already passed) and index 2
        // (the real upcoming step). Starting from index 2, the lookup must NOT
        // fall back to the passed occurrence at index 0.
        val list = listOf(
            instr("Turn left into A"),
            instr("Straight on"),
            instr("Turn left into A")
        )
        assertEquals(2, nextStepIndex(list, 2, instr("Turn left into A")))
    }

    @Test
    fun `skips passed duplicates and binds to the ahead occurrence`() {
        val list = listOf(
            instr("Turn left into A"),
            instr("Turn left into A"),
            instr("Straight on")
        )
        // First occurrence passed (current = 1): bind to the second one.
        assertEquals(1, nextStepIndex(list, 1, instr("Turn left into A")))
    }

    @Test
    fun `keeps current index when description not found ahead`() {
        val list = listOf(instr("Turn left into A"), instr("Straight on"))
        assertEquals(1, nextStepIndex(list, 1, instr("Unknown maneuver")))
    }

    @Test
    fun `clamps negative current index to zero`() {
        val list = listOf(instr("Turn left into A"))
        assertEquals(0, nextStepIndex(list, -1, instr("Turn left into A")))
    }

    @Test
    fun `keeps index when current index beyond list`() {
        val list = listOf(instr("Turn left into A"))
        assertEquals(2, nextStepIndex(list, 2, instr("Turn left into A")))
    }

    @Test
    fun `empty list keeps current index`() {
        assertEquals(0, nextStepIndex(emptyList(), 0, instr("Turn left into A")))
    }
}
