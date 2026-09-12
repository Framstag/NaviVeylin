package com.naviveylin.ui.map

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the dedicated drive mode toggle button (spec: map-modes — mode
 * toggle button): car icon in BROWSE, exit icon in FREE_DRIVE, hidden during
 * NAVIGATION, tap invokes the toggle.
 */
@RunWith(RobolectricTestRunner::class)
class DriveModeButtonComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun browseShowsStartFreeDriveButton() {
        var toggles = 0
        composeRule.setContent {
            DriveModeButton(mode = MapMode.BROWSE, onToggle = { toggles++ })
        }

        composeRule.onNodeWithContentDescription("Start free drive").assertExists()
        composeRule.onNodeWithContentDescription("Start free drive").performClick()
        assertEquals("tap must invoke the toggle", 1, toggles)
    }

    @Test
    fun freeDriveShowsExitButton() {
        var toggles = 0
        composeRule.setContent {
            DriveModeButton(mode = MapMode.FREE_DRIVE, onToggle = { toggles++ })
        }

        composeRule.onNodeWithContentDescription("Exit free drive").assertExists()
        composeRule.onNodeWithContentDescription("Exit free drive").performClick()
        assertEquals("tap must invoke the toggle", 1, toggles)
    }

    @Test
    fun navigationHidesDriveButton() {
        composeRule.setContent {
            DriveModeButton(mode = MapMode.NAVIGATION, onToggle = {})
        }

        composeRule.onNodeWithContentDescription("Start free drive").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Exit free drive").assertDoesNotExist()
    }
}
