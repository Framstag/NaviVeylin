package com.naviveylin.ui.map

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for [CompassButton]: short-press re-centers, long-press
 * toggles orientation mode, the needle indicates north in every orientation
 * mode (spec: compass-button — change compass-always-north-phone).
 *
 * The needle has one drawing path (north half + neutral south half + "N") and
 * takes no orientation/bearing input, so "no travel-direction triangle" is
 * structurally guaranteed — there is no second branch to select. These tests
 * pin the composition, the needle angle and the widget geometry.
 */
@RunWith(RobolectricTestRunner::class)
class CompassButtonComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shortPressTriggersCenterClick() {
        var centerClicks = 0
        var toggleClicks = 0
        composeRule.setContent {
            CompassButton(
                mapAngleRadians = 0.0,
                gpsFixQuality = GpsFixQuality.GOOD,
                onCenterClick = { centerClicks++ },
                onToggleOrientation = { toggleClicks++ }
            )
        }

        composeRule.onNodeWithContentDescription("Compass").performClick()

        assertEquals("short press must trigger onCenterClick", 1, centerClicks)
        assertEquals("short press must not trigger onToggleOrientation", 0, toggleClicks)
    }

    @Test
    fun longPressTriggersToggleOrientation() {
        var centerClicks = 0
        var toggleClicks = 0
        composeRule.setContent {
            CompassButton(
                mapAngleRadians = 0.0,
                gpsFixQuality = GpsFixQuality.POOR,
                onCenterClick = { centerClicks++ },
                onToggleOrientation = { toggleClicks++ }
            )
        }

        composeRule.onNodeWithContentDescription("Compass").performTouchInput { longClick() }

        assertEquals("long press must trigger onToggleOrientation", 1, toggleClicks)
        assertEquals("long press must not trigger onCenterClick", 0, centerClicks)
    }

    @Test
    fun buttonLargerThanOtherOverlayButtons() {
        // Spec: compass-button — the compass is 56dp layout / 48dp visual,
        // larger than the other overlay buttons (48dp / 40dp).
        composeRule.setContent {
            CompassButton(
                mapAngleRadians = 0.0,
                gpsFixQuality = GpsFixQuality.GOOD,
                onCenterClick = {},
                onToggleOrientation = {}
            )
        }
        val layoutPx = with(composeRule.density) { 56.dp.toPx() }
        val size = composeRule.onNodeWithContentDescription("Compass")
            .fetchSemanticsNode().size
        assertTrue(
            "compass layout must be >= 56dp (was ${size.width}px / ${size.height}px)",
            size.width >= layoutPx - 1f && size.height >= layoutPx - 1f
        )
    }

    @Test
    fun headingUpFollowShowsTheNorthNeedle() {
        // Heading-up follow (westbound: heading 270°, θ = −270°): the needle is the
        // NORTH pointer and sits 90° clockwise from up — the driver's right — not at
        // 0° (which the removed travel-direction triangle would have shown).
        val mapAngle = Math.toRadians(-270.0)
        composeRule.setContent {
            CompassButton(
                mapAngleRadians = mapAngle,
                gpsFixQuality = GpsFixQuality.GOOD,
                onCenterClick = {},
                onToggleOrientation = {}
            )
        }
        composeRule.onNodeWithContentDescription("Compass").fetchSemanticsNode()
        assertEquals(
            "westbound heading-up: north is to the driver's right",
            90.0, compassNeedleTarget(mapAngle), 1e-10
        )
    }

    @Test
    fun northUpShowsTheNorthNeedle() {
        // North-up mode is unchanged: north is straight up.
        composeRule.setContent {
            CompassButton(
                mapAngleRadians = 0.0,
                gpsFixQuality = GpsFixQuality.GOOD,
                onCenterClick = {},
                onToggleOrientation = {}
            )
        }
        composeRule.onNodeWithContentDescription("Compass").fetchSemanticsNode()
        assertEquals(0.0, compassNeedleTarget(0.0), 1e-10)
    }

    @Test
    fun fillColorReflectsGpsFixQuality() {
        // Spec: compass-button — GPS fix status fill color. The palette itself is
        // internal; what the spec requires (and what must not regress with the
        // needle change) is that the three fix qualities stay distinguishable.
        val colors = listOf(GpsFixQuality.NONE, GpsFixQuality.POOR, GpsFixQuality.GOOD)
            .map { compassFillColor(it) }
        assertEquals("one distinct fill color per fix quality", 3, colors.toSet().size)
    }
}
