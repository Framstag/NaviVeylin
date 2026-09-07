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
 * the gear button opens it, and the map style control is a compact exposed
 * dropdown (one row) listing every bundled style (name without the `.oss`
 * postfix); selecting reports the style. There is no style entry in the
 * overflow menu (spec: map-styles — phone settings entry lives in the on-map
 * settings view).
 */
@RunWith(RobolectricTestRunner::class)
class LocationOptionsOverlayComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val styles: List<String> = BundledMapStyles.ALL

    private fun openSheet(
        onSetStyleSheet: (String) -> Unit = {}
    ) {
        composeRule.setContent {
            LocationOptionsOverlay(
                followMode = false,
                onToggleFollowMode = {},
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
                followMode = false,
                onToggleFollowMode = {},
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
                followMode = false,
                onToggleFollowMode = {},
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
}
