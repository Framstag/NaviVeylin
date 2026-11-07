package com.naviveylin.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.naviveylin.test.stepOf
import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.test.ComposeSandboxRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the next-turn overlay renders driver-seat readable text sizes
 * (spec: next-turn-overlay — Driver-seat readable font sizes): distance
 * >= 32sp, description/destination >= 22sp, next-next >= 20sp and smaller
 * than the primary instruction.
 */
@RunWith(RobolectricTestRunner::class)
class NextTurnOverlayTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val step = stepOf(
        450.0,
        300.0,
        900.0,
        TurnType.LEFT,
        "Hauptstraße",
        "Turn left into Hauptstraße",
        "Turn left",
        100.0,
        TurnType.RIGHT,
        "Turn right into Bahnhofstraße",
        "Turn right"
    )

    // --- the numbered roundabout exit (spec: next-turn-overlay - Turn type icon) ---

    private fun roundaboutInstruction(
        exit: Int,
        nextNextType: TurnType = TurnType.STRAIGHT_ON,
        nextNextExit: Int = 0
    ) = stepOf(
        450.0, 300.0, 900.0, TurnType.ROUNDABOUT_LEAVE, "Bahnhofstraße",
        "Take exit $exit onto Bahnhofstraße", "Exit $exit",
        100.0, nextNextType, "native next-next", "native next-next short",
        exit, true, nextNextExit
    )

    // --- the signposted destination (spec: next-turn-overlay - The second line carries it) ---

    /** A motorway change onto the A8: the manoeuvre a signposted destination exists for. */
    private fun motorwayChange(destination: String? = "München"): RouteStep = RouteStep().apply {
        kind = RouteStep.Kind.MOTORWAY_CHANGE
        remainingDistance = 450.0
        motorwayChange = RouteStep.MotorwayChange().apply {
            from = RouteStep.Name("", "A3")
            to = RouteStep.Name("", "A8")
            move = TurnType.RIGHT
        }
        this.destination = destination
    }

    @Test
    fun `a motorway change's second line carries the signposted destination`() {
        composeRule.setContent { NextTurnOverlay(step = motorwayChange()) }

        // The first line stays the manoeuvre alone; the destination joins the second one.
        composeRule.onNodeWithText("Keep right").assertIsDisplayed()
        composeRule.onNodeWithText("A8 towards München").assertIsDisplayed()
    }

    @Test
    fun `a motorway change without a destination keeps the bare reference`() {
        composeRule.setContent { NextTurnOverlay(step = motorwayChange(destination = null)) }

        composeRule.onNodeWithText("A8").assertExists()
        composeRule.onNodeWithText("towards", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the destination clause wraps inside the existing line limit`() {
        // The longer line stays inside the overlay's own budget: the description keeps its existing
        // limit and ellipsis, and the card gains no third text row.
        composeRule.setContent {
            NextTurnOverlay(step = motorwayChange(destination = "Garmisch-Partenkirchen"))
        }

        composeRule.onNodeWithText("A8 towards Garmisch-Partenkirchen").assertExists()
        composeRule.onNodeWithTag("nextTurnNextNextArrow").assertDoesNotExist()
    }

    @Test
    fun `a roundabout exit icon carries the exit number`() {
        val symbol = navSymbolForStep(roundaboutInstruction(exit = 3))

        assertEquals(NavSymbol.RoundaboutExit(3), symbol)
        // and the overlay renders it, so the symbol reaches the row rather than only the decision
        composeRule.setContent { NextTurnOverlay(step = roundaboutInstruction(exit = 3)) }
        composeRule.onNodeWithText("Exit 3").assertIsDisplayed()
    }

    @Test
    fun `a roundabout exit without an ordinal keeps the plain leave arrow`() {
        assertEquals(
            NavSymbol.TurnArrow(TurnType.ROUNDABOUT_LEAVE),
            navSymbolForStep(roundaboutInstruction(exit = 0))
        )
    }

    @Test
    fun `the next-next hint renders from the nested step`() {
        // The hint row exists because the published step carries a nested record; it names that
        // manoeuvre's own wording and its own distance (spec: next-turn-overlay — "Next next" hint).
        val withHint = roundaboutInstruction(
            exit = 3,
            nextNextType = TurnType.RIGHT,
            nextNextExit = 0
        )

        composeRule.setContent { NextTurnOverlay(step = withHint) }

        composeRule.onNodeWithTag("nextTurnNextNextArrow").assertExists()
        composeRule.onNodeWithText("Turn right").assertExists()
    }

    @Test
    fun `a step without a nested hint shows no second row`() {
        val withoutHint = stepOf(250.0, TurnType.LEFT, "Hauptstraße")

        composeRule.setContent { NextTurnOverlay(step = withoutHint) }

        composeRule.onNodeWithTag("nextTurnNextNextArrow").assertDoesNotExist()
    }

    @Test
    fun `the next-next roundabout exit shows its own number`() {
        val step = roundaboutInstruction(
            exit = 3,
            nextNextType = TurnType.ROUNDABOUT_LEAVE,
            nextNextExit = 1
        )

        assertEquals(NavSymbol.RoundaboutExit(1), navSymbolForNextNext(step))
        assertEquals(NavSymbol.RoundaboutExit(3), navSymbolForStep(step))
    }

    private fun styleFontSize(capture: () -> Unit): Float {
        var style: TextStyle? = null
        composeRule.setContent {
            capture()
            style = nextTurnDistanceStyle()
        }
        composeRule.waitForIdle()
        assertNotNull(style)
        return style!!.fontSize.value
    }

    @Test
    fun overlayRendersInstruction() {
        composeRule.setContent { NextTurnOverlay(step = step) }
        composeRule.onNodeWithText("450 m").assertIsDisplayed()
        composeRule.onNodeWithText("Turn left").assertIsDisplayed()
        composeRule.onNodeWithText("100 m").assertIsDisplayed()
    }

    @Test
    fun distanceAtLeast32sp() {
        var style: TextStyle? = null
        composeRule.setContent { style = nextTurnDistanceStyle() }
        composeRule.waitForIdle()
        assertNotNull(style)
        val size = style!!.fontSize.value
        assertTrue("distance must be >= 32sp (was $size)", size >= 32f)
    }

    @Test
    fun descriptionAtLeast22sp() {
        var style: TextStyle? = null
        composeRule.setContent { style = nextTurnDescriptionStyle() }
        composeRule.waitForIdle()
        assertNotNull(style)
        val size = style!!.fontSize.value
        assertTrue("description must be >= 22sp (was $size)", size >= 22f)
    }

    @Test
    fun nextNextAtLeast20spAndSmallerThanPrimary() {
        var distance: TextStyle? = null
        var nextNext: TextStyle? = null
        composeRule.setContent {
            distance = nextTurnDistanceStyle()
            nextNext = nextTurnNextNextStyle()
        }
        composeRule.waitForIdle()
        assertNotNull(distance)
        assertNotNull(nextNext)
        val nextNextSize = nextNext!!.fontSize.value
        assertTrue("next-next must be >= 20sp (was $nextNextSize)", nextNextSize >= 20f)
        assertTrue(
            "next-next must stay smaller than the primary instruction",
            nextNextSize < distance!!.fontSize.value
        )
    }

    @Test
    fun iconsAtLeast64And36dp() {
        composeRule.setContent { NextTurnOverlay(step = step) }
        val mainPx = with(composeRule.density) { 64.dp.toPx() }
        val nextNextPx = with(composeRule.density) { 36.dp.toPx() }
        val main = composeRule.onNodeWithTag("nextTurnArrow").fetchSemanticsNode().size
        val nextNext = composeRule.onNodeWithTag("nextTurnNextNextArrow").fetchSemanticsNode().size
        assertTrue(
            "turn icon must be >= 64dp (was ${main.width}px)",
            main.width >= mainPx - 1f && main.height >= mainPx - 1f
        )
        assertTrue(
            "next-next icon must be >= 36dp (was ${nextNext.width}px)",
            nextNext.width >= nextNextPx - 1f && nextNext.height >= nextNextPx - 1f
        )
    }
}
