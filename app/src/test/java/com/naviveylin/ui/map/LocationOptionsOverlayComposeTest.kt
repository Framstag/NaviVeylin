package com.naviveylin.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import com.naviveylin.core.BundledMapStyles
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the map settings bottom sheet ([LocationOptionsOverlay]):
 * the gear button opens it, the sheet shows a mode header naming the current
 * state and that state's options only, and there is no mode-switch control
 * (spec: location-options-ui — mode header without mode switch). The map style
 * control is a compact exposed dropdown listing every bundled style.
 */
@RunWith(RobolectricTestRunner::class)
class LocationOptionsOverlayComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val styles: List<String> = BundledMapStyles.USER_SELECTABLE

    private fun openSheet(
        mode: MapMode = MapMode.BROWSE,
        onSetStyleSheet: (String) -> Unit = {}
    ) {
        composeRule.setContent {
            LocationOptionsOverlay(
                mode = mode,
                availableStyles = styles,
                styleSheet = "standard",
                onSetStyleSheet = onSetStyleSheet
            )
        }
        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun sheetShowsMapStyleDropdownWithAllStyles() {
        openSheet()

        composeRule.onNodeWithText("Map style").performScrollTo().assertIsDisplayed()
        // Expand the style dropdown (anchor shows the current style)
        composeRule.onNodeWithText("standard").performScrollTo().performClick()
        composeRule.waitForIdle()

        styles.forEach { style ->
            assertTrue(
                "style '$style' must appear in the dropdown (anchor + menu item)",
                composeRule.onAllNodesWithText(style).fetchSemanticsNodes().isNotEmpty()
            )
        }
        // No raw .oss names anywhere
        composeRule.onNodeWithText("standard.oss").assertDoesNotExist()
    }

    @Test
    fun dropdownSelectionReportsStyle() {
        var selected: String? = null
        openSheet(onSetStyleSheet = { selected = it })

        composeRule.onNodeWithText("standard").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("winter-sports").performScrollTo().performClick()

        assertEquals("winter-sports", selected)
    }

    @Test
    fun ambientLightToggleRendersAndReports() {
        var option: Boolean? = null
        composeRule.setContent {
            LocationOptionsOverlay(
                mode = MapMode.BROWSE,
                ambientLightDarkMode = false,
                onSetAmbientLightOption = { option = it }
            )
        }
        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Adaptive by ambient light")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("ambientLightToggle")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(true, option)
    }

    @Test
    fun ambientLightToggleReflectsState() {
        composeRule.setContent {
            LocationOptionsOverlay(
                mode = MapMode.BROWSE,
                ambientLightDarkMode = true,
                onSetAmbientLightOption = {}
            )
        }
        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Adaptive by ambient light")
            .performScrollTo()
            .assertIsDisplayed()
    }

    // --- Mode header + per-state sections (spec: location-options-ui) ---

    @Test
    fun browseHeaderShowsBrowseAndBrowseSection() {
        openSheet(mode = MapMode.BROWSE)

        composeRule.onNodeWithText("Browse").assertIsDisplayed()
        // Browse section: orientation with "Free rotation" (browse-specific).
        composeRule.onNodeWithText("Free rotation").performScrollTo().assertIsDisplayed()
        // Driving options must NOT appear in browse.
        composeRule.onNodeWithText("Auto zoom").assertDoesNotExist()
        composeRule.onNodeWithText("Follow direction").assertDoesNotExist()
    }

    @Test
    fun freeDriveHeaderShowsDrivingSection() {
        openSheet(mode = MapMode.FREE_DRIVE)

        composeRule.onNodeWithText("Free drive").assertIsDisplayed()
        // Driving section: auto-zoom + driving orientation.
        composeRule.onNodeWithText("Auto zoom").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Follow direction").performScrollTo().assertIsDisplayed()
        // Browse-specific option must NOT appear in driving.
        composeRule.onNodeWithText("Free rotation").assertDoesNotExist()
    }

    @Test
    fun navigationHeaderShowsDrivingSection() {
        openSheet(mode = MapMode.NAVIGATION)

        composeRule.onNodeWithText("Navigation").assertIsDisplayed()
        composeRule.onNodeWithText("Auto zoom").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Follow direction").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sheetHasNoModeSwitchControl() {
        openSheet(mode = MapMode.BROWSE)

        // The old "Map follows position" toggle is gone; no mode switch exists.
        composeRule.onNodeWithText("Map follows position").assertDoesNotExist()
    }
}
