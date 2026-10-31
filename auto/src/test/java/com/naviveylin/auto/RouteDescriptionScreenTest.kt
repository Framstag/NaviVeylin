package com.naviveylin.auto

import com.naviveylin.auto.stepOf
import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.NavigationState
import com.naviveylin.core.formatDistanceNumber
import com.naviveylin.core.routeRows
import com.naviveylin.core.turnTypeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for the route description screen's rows and their images (spec: auto/navigation-view —
 * Route description screen).
 *
 * The screen itself is a host-driven `ListTemplate`, so what is testable here is what it is built
 * from: that a row carries its *own* roundabout exit ordinal and that the row image for it differs
 * from the image for another exit, and — since change `share-route-rows-in-core` — that the row list is
 * the shared row policy's row set rather than one row per published step. Robolectric's canvas
 * discards draws, so how the number looks is the on-device `pixel-check` (change
 * `fix-roundabout-exit-number`, task 7.4).
 */
@RunWith(RobolectricTestRunner::class)
class RouteDescriptionScreenTest {

    private fun roundaboutExit(exitNumber: Int, street: String) = stepOf(
        350.0, 12.0, 350.0, TurnType.ROUNDABOUT_LEAVE, street,
        "Take exit $exitNumber onto $street", "Exit $exitNumber",
        0.0, TurnType.STRAIGHT_ON, "", "",
        exitNumber, true, 0
    )

    @Test
    fun `a roundabout exit row shows its exit number`() {
        val state = NavigationState(
            isNavigating = true,
            currentStepIndex = 0,
            steps = listOf(
                roundaboutExit(3, "Bahnhofstraße"),
                roundaboutExit(1, "Marktplatz")
            )
        )

        val rows = NavigationTemplateMapper.routeDescriptionRows(testCarContext(), state)

        assertEquals(2, rows.size)
        assertEquals(3, rows[0].roundaboutExitNumber)
        assertEquals(1, rows[1].roundaboutExitNumber)
        assertNotSame(
            "each row's image carries its own number, not a neighbour's",
            ManeuverGlyphs.forTurnTypeAndExit(rows[0].turnType, rows[0].roundaboutExitNumber),
            ManeuverGlyphs.forTurnTypeAndExit(rows[1].turnType, rows[1].roundaboutExitNumber)
        )
    }

    @Test
    fun `a roundabout exit row without an ordinal keeps the plain image`() {
        val state = NavigationState(
            isNavigating = true,
            currentStepIndex = 0,
            steps = listOf(roundaboutExit(0, "Ringstraße"))
        )

        val rows = NavigationTemplateMapper.routeDescriptionRows(testCarContext(), state)

        assertEquals(0, rows[0].roundaboutExitNumber)
        assertEquals(
            ManeuverGlyphs.forTurnType(TurnType.ROUNDABOUT_LEAVE),
            ManeuverGlyphs.forTurnTypeAndExit(rows[0].turnType, rows[0].roundaboutExitNumber)
        )
    }

    // --- The list is the shared row policy's row set, not one row per step -------------------

    private fun navigating(vararg steps: RouteStep, currentStepIndex: Int = 0) = NavigationState(
        isNavigating = true,
        currentStepIndex = currentStepIndex,
        steps = steps.toList()
    )

    private fun nameChange(street: String, leg: Double = 40.0) = RouteStep().apply {
        kind = RouteStep.Kind.NAME_CHANGE
        legDistance = leg
        name = RouteStep.Name(street, "")
    }

    private fun informational(leg: Double, street: String = "") = informativeNode(0.0, street).apply {
        legDistance = leg
    }

    /**
     * A route shaped like the device census (`guidelines/Build.md` §10k): 330 published steps of which
     * 302 are facts-only nodes around four turns, five name changes, sixteen roundabout manoeuvres and one
     * motorway entry.
     */
    private fun censusShapedSteps(): List<RouteStep> = buildList {
        add(stepOf(0.0, TurnType.START))
        repeat(302) { add(informational(10.0)) }
        repeat(4) { add(stepOf(300.0, TurnType.LEFT, "Turn street $it")) }
        repeat(5) { add(nameChange("Name change street $it")) }
        repeat(16) { add(stepOf(200.0, TurnType.ROUNDABOUT_LEAVE, "Roundabout street $it")) }
        add(stepOf(900.0, TurnType.MOTORWAY_ENTER, "A42"))
        add(stepOf(0.0, TurnType.TARGET_REACHED))
    }

    @Test
    fun `the car list has one row per derived decision`() {
        val steps = censusShapedSteps()

        val rows = NavigationTemplateMapper.routeDescriptionRows(
            testCarContext(),
            navigating(*steps.toTypedArray())
        )

        assertEquals(330, steps.size)
        assertEquals(routeRows(steps).size, rows.size)
        assertTrue(
            "a facts-only step must not become a car row (rows=${rows.size})",
            rows.size < 30
        )
        assertFalse("one row per published step would be the defect", rows.size == steps.size)
    }

    @Test
    fun `a row's distance is its own leg`() {
        val state = navigating(
            stepOf(0.0, TurnType.START),
            stepOf(350.0, 12.0, 350.0, TurnType.LEFT, "Bahnhofstraße", "", ""),
            stepOf(1200.0, 30.0, 1200.0, TurnType.RIGHT, "Marktplatz", "", "")
        )

        val rows = NavigationTemplateMapper.routeDescriptionRows(testCarContext(), state)

        assertEquals(3, rows.size)
        assertTrue(rows[1].title, rows[1].title.contains(formatDistanceNumber(350.0)))
        assertTrue(rows[2].title, rows[2].title.contains(formatDistanceNumber(1200.0)))
        assertFalse(
            "1550 m is the distance from the route's start, not the row's leg: ${rows[2].title}",
            rows[2].title.contains(formatDistanceNumber(1550.0))
        )
    }

    @Test
    fun `an informational step earns no car row`() {
        val state = navigating(
            stepOf(0.0, TurnType.START),
            stepOf(350.0, 12.0, 350.0, TurnType.LEFT, "Bahnhofstraße", "", ""),
            informational(300.0, "Bahnhofstraße"),
            stepOf(200.0, 20.0, 200.0, TurnType.RIGHT, "Marktplatz", "", "")
        )

        val rows = NavigationTemplateMapper.routeDescriptionRows(testCarContext(), state)

        assertEquals("start, two manoeuvres: the informative node is no row", 3, rows.size)
        assertTrue(
            "the informative leg belongs to the row above: ${rows[1].title}",
            rows[1].title.contains(formatDistanceNumber(650.0))
        )
    }

    @Test
    fun `the car list is the same row set as the phone's`() {
        val steps = censusShapedSteps()
        val state = navigating(*steps.toTypedArray())

        val rows = NavigationTemplateMapper.routeDescriptionRows(testCarContext(), state)
        val phoneRows = routeRows(steps)

        assertEquals(phoneRows.size, rows.size)
        assertEquals(phoneRows.map { turnTypeFor(it.manoeuvre) }, rows.map { it.turnType })
    }

    @Test
    fun `route description diagnostic carries counts only`() {
        assertEquals(
            "route description rows=24 steps=330",
            NavigationTemplateMapper.routeDescriptionDiagnostic(24, 330)
        )
    }

    @Test
    fun `the marked row is the next decision`() {        val state = navigating(
            stepOf(0.0, TurnType.START),
            informational(100.0, "Bahnhofstraße"),
            stepOf(350.0, 12.0, 350.0, TurnType.LEFT, "Bahnhofstraße", "", "")
        , currentStepIndex = 1)

        val rows = NavigationTemplateMapper.routeDescriptionRows(testCarContext(), state)

        assertEquals(1, rows.size)
        assertTrue("the driving decision in hand is marked", rows[0].isCurrent)
        assertEquals(TurnType.LEFT, rows[0].turnType)
        assertTrue(
            "its leg includes the facts-only step it follows: ${rows[0].title}",
            rows[0].title.contains(formatDistanceNumber(450.0))
        )
    }
}
