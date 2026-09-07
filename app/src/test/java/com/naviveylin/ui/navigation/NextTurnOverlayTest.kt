package com.naviveylin.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
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

    @get:Rule
    val composeRule = createComposeRule()

    private val instruction = RouteInstruction(
        450.0,
        300.0,
        TurnType.LEFT,
        "Hauptstraße",
        "Turn left into Hauptstraße",
        "Turn left",
        100.0,
        TurnType.RIGHT,
        "Turn right into Bahnhofstraße",
        "Turn right"
    )

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
        composeRule.setContent { NextTurnOverlay(instruction = instruction) }
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
        composeRule.setContent { NextTurnOverlay(instruction = instruction) }
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
