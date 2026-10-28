package com.naviveylin.auto

import com.naviveylin.auto.stepOf
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.NavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for the route description screen's rows and their images (spec: auto/navigation-view —
 * Route description screen).
 *
 * The screen itself is a host-driven `ListTemplate`, so what is testable here is what it is built
 * from: that a row carries its *own* roundabout exit ordinal and that the row image for it differs
 * from the image for another exit. Robolectric's canvas discards draws, so how the number looks is
 * the on-device `pixel-check` (change `fix-roundabout-exit-number`, task 7.4).
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
}
