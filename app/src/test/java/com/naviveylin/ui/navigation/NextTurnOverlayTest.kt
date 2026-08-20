package com.naviveylin.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextTurnOverlayTest {

    @Test
    fun `splitInstruction uses shortDescription as generic and streetName as destination`() {
        val lines = splitInstruction(
            description = "Turn left into Hauptstrasse",
            shortDescription = "Turn left",
            streetName = "Hauptstrasse"
        )
        assertEquals("Turn left", lines.generic)
        assertEquals("Hauptstrasse", lines.destination)
    }

    @Test
    fun `splitInstruction extracts destination from description when streetName empty`() {
        val lines = splitInstruction(
            description = "Take exit 3 onto A1",
            shortDescription = "Exit 3"
        )
        assertEquals("Exit 3", lines.generic)
        assertEquals("A1", lines.destination)
    }

    @Test
    fun `splitInstruction returns single line when no destination`() {
        val lines = splitInstruction(
            description = "Enter roundabout",
            shortDescription = "Roundabout"
        )
        assertEquals("Roundabout", lines.generic)
        assertNull(lines.destination)
    }

    @Test
    fun `splitInstruction falls back to full description when shortDescription blank`() {
        val lines = splitInstruction(
            description = "Destination reached",
            shortDescription = ""
        )
        assertEquals("Destination reached", lines.generic)
        assertNull(lines.destination)
    }

    @Test
    fun `splitInstruction handles onto marker`() {
        val lines = splitInstruction(
            description = "Merge onto A100",
            shortDescription = "Merge"
        )
        assertEquals("Merge", lines.generic)
        assertEquals("A100", lines.destination)
    }
}
