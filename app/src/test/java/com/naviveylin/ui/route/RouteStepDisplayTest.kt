package com.naviveylin.ui.route

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteStepDisplayTest {

    @Test
    fun `parseStepDisplay strips bracket with distance and time`() {
        val result = parseStepDisplay("Turn left into Main Street  [1.2 km, 5 min]")
        assertEquals("Turn left into Main Street", result.instruction)
        assertEquals("1.2 km", result.distanceText)
        assertEquals("5 min", result.timeText)
    }

    @Test
    fun `parseStepDisplay handles distance only`() {
        val result = parseStepDisplay("Straight on Unter den Linden  [800 m]")
        assertEquals("Straight on Unter den Linden", result.instruction)
        assertEquals("800 m", result.distanceText)
        assertEquals("", result.timeText)
    }

    @Test
    fun `parseStepDisplay handles zero distance start`() {
        val result = parseStepDisplay("Start: Berlin Hauptbahnhof  [0.0 km]")
        assertEquals("Start: Berlin Hauptbahnhof", result.instruction)
        assertEquals("0.0 km", result.distanceText)
        assertEquals("", result.timeText)
    }

    @Test
    fun `parseStepDisplay handles destination reached`() {
        val result = parseStepDisplay("Destination reached  [0.0 km]")
        assertEquals("Destination reached", result.instruction)
        assertEquals("0.0 km", result.distanceText)
        assertEquals("", result.timeText)
    }

    @Test
    fun `parseStepDisplay handles no bracket`() {
        val result = parseStepDisplay("Plain description without bracket")
        assertEquals("Plain description without bracket", result.instruction)
        assertEquals("", result.distanceText)
        assertEquals("", result.timeText)
    }

    @Test
    fun `parseStepDisplay handles hours and minutes`() {
        val result = parseStepDisplay("Merge onto A100  [15.3 km, 1 h 12 min]")
        assertEquals("Merge onto A100", result.instruction)
        assertEquals("15.3 km", result.distanceText)
        assertEquals("1 h 12 min", result.timeText)
    }

    @Test
    fun `parseStepDisplay handles empty string`() {
        val result = parseStepDisplay("")
        assertEquals("", result.instruction)
        assertEquals("", result.distanceText)
        assertEquals("", result.timeText)
    }
}
