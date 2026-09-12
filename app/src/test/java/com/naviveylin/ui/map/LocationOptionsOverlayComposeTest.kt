package com.naviveylin.ui.map

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import com.naviveylin.core.BundledMapStyles
import com.naviveylin.data.AmbientLightSensitivity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
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
    fun ambientSensitivityRendersAndReports() {
        var reported: AmbientLightSensitivity? = null
        composeRule.setContent {
            LocationOptionsOverlay(
                mode = MapMode.BROWSE,
                ambientLightSensitivity = AmbientLightSensitivity.OFF,
                onSetAmbientLightSensitivity = { reported = it }
            )
        }
        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Adaptive by ambient light")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Medium")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(AmbientLightSensitivity.MEDIUM, reported)
    }

    @Test
    fun ambientSensitivityReflectsState() {
        composeRule.setContent {
            LocationOptionsOverlay(
                mode = MapMode.BROWSE,
                ambientLightSensitivity = AmbientLightSensitivity.MEDIUM,
                onSetAmbientLightSensitivity = {}
            )
        }
        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Adaptive by ambient light")
            .performScrollTo()
            .assertIsDisplayed()
        // All four sensitivity levels present ("Off" also exists in the dark
        // mode section, "On"/"Automatic" too — the sensitivity-specific rows
        // are High/Medium/Low plus one of the shared Off labels).
        composeRule.onNodeWithText("High").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Medium").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Low").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Off")[0].performScrollTo().assertIsDisplayed()
        // The selected level (Medium) is check-marked.
        assertTrue(
            composeRule.onAllNodesWithText("Medium")[0].fetchSemanticsNode().config.contains(
                SemanticsProperties.Selected
            )
        )
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

    @Test
    fun overspeedSliderShowsCurrentValue() {
        composeRule.setContent {
            LocationOptionsOverlay(
                mode = MapMode.BROWSE,
                overspeedWarningDeltaKmh = 10,
                onSetOverspeedWarningDelta = {}
            )
        }
        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Overspeed warning").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("+10 km/h").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("overspeedDeltaSlider").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun overspeedSliderReportsWholeKmhValues() {
        val reported = mutableListOf<Int>()
        composeRule.setContent {
            LocationOptionsOverlay(
                mode = MapMode.BROWSE,
                overspeedWarningDeltaKmh = 0,
                onSetOverspeedWarningDelta = { reported.add(it) }
            )
        }
        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()

        // Drag across the full track: 0..30, steps snap to whole km/h.
        // Scroll the slider into view first — the sheet content scrolls and
        // off-screen touch input is dropped.
        composeRule.onNodeWithTag("overspeedDeltaSlider")
            .performScrollTo()
            .performTouchInput {
                swipe(centerLeft, centerRight, durationMillis = 400)
            }
        composeRule.waitForIdle()

        assertTrue("slider must report values while dragged", reported.isNotEmpty())
        // The drag may stop short of the very end (track padding); the point
        // is that it reaches the upper range with whole-km/h values.
        val last = reported.last()
        assertTrue("drag should reach the upper range (was $last)", last >= 25)
        assertTrue("every reported value is a whole km/h in 0..30", reported.all { it in 0..30 })
    }

    @Test
    fun overspeedSliderCanSelectZero() {
        var reported: Int? = null
        composeRule.setContent {
            LocationOptionsOverlay(
                mode = MapMode.BROWSE,
                overspeedWarningDeltaKmh = 20,
                onSetOverspeedWarningDelta = { reported = it }
            )
        }
        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()

        // Tap the left edge: value snaps to 0 (warn at the limit).
        composeRule.onNodeWithTag("overspeedDeltaSlider")
            .performScrollTo()
            .performTouchInput {
                click(Offset(centerLeft.x + 1f, center.y))
            }
        composeRule.waitForIdle()

        assertEquals(0, reported)
    }
}
