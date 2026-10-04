package com.naviveylin.ui.route

import com.framstag.libosmscout.client.RouteEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the per-step manoeuvre positions (spec: `route-analysis`).
 *
 * `RouteEntry` is a plain data holder, so these run as plain JUnit tests — no
 * native library is loaded.
 */
class RouteStepAnchorsTest {

    private fun route(
        descriptions: Array<String>?,
        instructionLats: DoubleArray? = null,
        instructionLons: DoubleArray? = null
    ) = RouteEntry().apply {
        this.descriptions = descriptions
        this.instructionLats = instructionLats
        this.instructionLons = instructionLons
    }

    @Test
    fun `aligned arrays yield one anchor per instruction line`() {
        val entry = route(
            descriptions = arrayOf("Start: Hauptbahnhof  [0.0 km]", "Left onto Main Street  [1.2 km]"),
            instructionLats = doubleArrayOf(52.5, 52.6),
            instructionLons = doubleArrayOf(13.3, 13.4)
        )

        assertEquals(
            listOf(StepAnchor(52.5, 13.3), StepAnchor(52.6, 13.4)),
            instructionAnchors(entry)
        )
    }

    @Test
    fun `header line is not counted as an instruction`() {
        val entry = route(
            descriptions = arrayOf("--- Route ---", "Start: Hauptbahnhof  [0.0 km]", "Left onto Main Street  [1.2 km]"),
            instructionLats = doubleArrayOf(52.5, 52.6),
            instructionLons = doubleArrayOf(13.3, 13.4)
        )

        assertEquals(
            listOf(StepAnchor(52.5, 13.3), StepAnchor(52.6, 13.4)),
            instructionAnchors(entry)
        )
    }

    @Test
    fun `shorter position array yields no anchors`() {
        val entry = route(
            descriptions = arrayOf("Start: Hauptbahnhof  [0.0 km]", "Left onto Main Street  [1.2 km]"),
            instructionLats = doubleArrayOf(52.5),
            instructionLons = doubleArrayOf(13.3)
        )

        assertTrue(instructionAnchors(entry).isEmpty())
    }

    @Test
    fun `longer position array yields no anchors`() {
        val entry = route(
            descriptions = arrayOf("Start: Hauptbahnhof  [0.0 km]"),
            instructionLats = doubleArrayOf(52.5, 52.6),
            instructionLons = doubleArrayOf(13.3, 13.4)
        )

        assertTrue(instructionAnchors(entry).isEmpty())
    }

    @Test
    fun `latitude and longitude arrays of different lengths yield no anchors`() {
        val entry = route(
            descriptions = arrayOf("Start  [0.0 km]", "Left  [1.2 km]"),
            instructionLats = doubleArrayOf(52.5, 52.6),
            instructionLons = doubleArrayOf(13.3)
        )

        assertTrue(instructionAnchors(entry).isEmpty())
    }

    @Test
    fun `missing position arrays yield no anchors`() {
        val entry = route(descriptions = arrayOf("Start  [0.0 km]", "Left  [1.2 km]"))

        assertTrue(instructionAnchors(entry).isEmpty())
    }

    @Test
    fun `missing descriptions yield no anchors`() {
        val entry = route(
            descriptions = null,
            instructionLats = doubleArrayOf(52.5),
            instructionLons = doubleArrayOf(13.3)
        )

        assertTrue(instructionAnchors(entry).isEmpty())
    }

    @Test
    fun `header only yield no anchors`() {
        val entry = route(
            descriptions = arrayOf("--- Route ---"),
            instructionLats = doubleArrayOf(),
            instructionLons = doubleArrayOf()
        )

        assertTrue(instructionAnchors(entry).isEmpty())
    }
}
