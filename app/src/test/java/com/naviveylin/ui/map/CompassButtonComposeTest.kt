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
 * toggles orientation mode.
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
                isNorthUp = true,
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
                isNorthUp = false,
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
                isNorthUp = true,
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
    fun followDirectionNeedlePointsUpWhileHeadingUp() {
        // Spec: compass-button — follow triangle points at the on-screen travel
        // direction = bearing + θ, which is 0 (straight up) while heading-up
        // follow is active. Westbound: heading 270°, θ = −270° → target 0°.
        // (Where NORTH renders — 90°, the driver's right — is the
        // compassRotationDegrees part, pinned in ProjectionUtilsTest.)
        val mapAngle = Math.toRadians(-270.0)
        composeRule.setContent {
            CompassButton(
                isNorthUp = false,
                mapAngleRadians = mapAngle,
                gpsFixQuality = GpsFixQuality.GOOD,
                onCenterClick = {},
                onToggleOrientation = {},
                bearingDegrees = 270.0
            )
        }
        composeRule.onNodeWithContentDescription("Compass").fetchSemanticsNode()
        assertEquals(
            "westbound follow needle must point up (travel direction)",
            0.0, compassNeedleTarget(false, 270.0, mapAngle), 1e-10
        )
    }

    @Test
    fun northUpNeedleComposesWithUnknownBearing() {
        // North-up mode ignores the bearing; the needle points at map north (0°).
        composeRule.setContent {
            CompassButton(
                isNorthUp = true,
                mapAngleRadians = 0.0,
                gpsFixQuality = GpsFixQuality.GOOD,
                onCenterClick = {},
                onToggleOrientation = {},
                bearingDegrees = null
            )
        }
        composeRule.onNodeWithContentDescription("Compass").fetchSemanticsNode()
        assertEquals(0.0, compassNeedleTarget(true, null, 0.0), 1e-10)
    }
}
