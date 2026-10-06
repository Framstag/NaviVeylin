package com.naviveylin.ui.route

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The description bracket is the **fallback** for a route that carries no per-step arrays
 * (spec: `osmscout-jni` — Per-step leg values on a calculated route). Every case therefore asserts
 * metres and seconds, and each token is classified by its unit rather than by its position: a
 * bracket without a distance must not put its time into the distance slot (the positional parser did
 * exactly that for `[2 s]`, which the native layer emits for a leg below 10 m that takes at least a
 * second).
 */
class RouteStepDisplayTest {

    @Test
    fun `parseStepDisplay strips bracket with distance and time`() {
        val result = parseStepDisplay("Turn left into Main Street  [1.2 km, 5 min]")
        assertEquals("Turn left into Main Street", result.instruction)
        assertEquals(1200.0, result.distanceMeters, 1e-6)
        assertEquals(300.0, result.durationSeconds, 1e-6)
    }

    @Test
    fun `parseStepDisplay handles distance only`() {
        val result = parseStepDisplay("Straight on Unter den Linden  [800 m]")
        assertEquals("Straight on Unter den Linden", result.instruction)
        assertEquals(800.0, result.distanceMeters, 1e-6)
        assertEquals(0.0, result.durationSeconds, 1e-6)
    }

    @Test
    fun `parseStepDisplay keeps a time out of the distance slot`() {
        // The native side prints no distance below 10 m, so this is a time-only bracket.
        val result = parseStepDisplay("Turn right into Kirchstrasse  [2 s]")
        assertEquals("Turn right into Kirchstrasse", result.instruction)
        assertEquals(0.0, result.distanceMeters, 1e-6)
        assertEquals(2.0, result.durationSeconds, 1e-6)
    }

    @Test
    fun `parseStepDisplay handles a sub-minute time`() {
        val result = parseStepDisplay("Left onto Hauptstrasse  [14 m, 45 s]")
        assertEquals(14.0, result.distanceMeters, 1e-6)
        assertEquals(45.0, result.durationSeconds, 1e-6)
    }

    @Test
    fun `parseStepDisplay handles zero distance start`() {
        val result = parseStepDisplay("Start: Berlin Hauptbahnhof  [0.0 km]")
        assertEquals("Start: Berlin Hauptbahnhof", result.instruction)
        assertEquals(0.0, result.distanceMeters, 1e-6)
        assertEquals(0.0, result.durationSeconds, 1e-6)
    }

    @Test
    fun `parseStepDisplay handles destination reached`() {
        val result = parseStepDisplay("Destination reached  [0.0 km]")
        assertEquals("Destination reached", result.instruction)
        assertEquals(0.0, result.distanceMeters, 1e-6)
        assertEquals(0.0, result.durationSeconds, 1e-6)
    }

    @Test
    fun `parseStepDisplay handles no bracket`() {
        val result = parseStepDisplay("Plain description without bracket")
        assertEquals("Plain description without bracket", result.instruction)
        assertEquals(0.0, result.distanceMeters, 1e-6)
        assertEquals(0.0, result.durationSeconds, 1e-6)
    }

    @Test
    fun `parseStepDisplay handles hours and minutes`() {
        val result = parseStepDisplay("Merge onto A100  [15.3 km, 1 h 12 min]")
        assertEquals("Merge onto A100", result.instruction)
        assertEquals(15300.0, result.distanceMeters, 1e-6)
        assertEquals(4320.0, result.durationSeconds, 1e-6)
    }

    @Test
    fun `parseStepDisplay handles empty string`() {
        val result = parseStepDisplay("")
        assertEquals("", result.instruction)
        assertEquals(0.0, result.distanceMeters, 1e-6)
        assertEquals(0.0, result.durationSeconds, 1e-6)
    }

    @Test
    fun `parseStepDisplay reads a localized decimal separator`() {
        // The bridge writes C++ formatted values; a localized description would use a comma, and a
        // value that silently became zero would be worse than a guess.
        val result = parseStepDisplay("Links abbiegen  [1,2 km, 5 min]")
        assertEquals(1200.0, result.distanceMeters, 1e-6)
        assertEquals(300.0, result.durationSeconds, 1e-6)
    }

    @Test
    fun `parseStepDisplay does not put a distance into the time slot`() {
        val result = parseStepDisplay("Turn left  [300 m, 1.2 km]")
        assertEquals(1500.0, result.distanceMeters, 1e-6)
        assertEquals(0.0, result.durationSeconds, 1e-6)
    }
}
