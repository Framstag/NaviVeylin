package com.naviveylin.ui.map

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.junit.Assert.assertEquals
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
}
