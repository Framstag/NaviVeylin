package com.naviveylin.ui.map

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onChildren
import com.naviveylin.core.BundledMapStyles
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.data.AmbientLightSensitivity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import com.naviveylin.test.ComposeSandboxRule
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

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val styles: List<String> = BundledMapStyles.USER_SELECTABLE

    private fun openSheet(
        mode: MapMode = MapMode.BROWSE,
        onSetStyleSheet: (String) -> Unit = {},
        preferSymbolPoiIcons: Boolean = false,
        onSetPreferSymbolPoiIcons: (Boolean) -> Unit = {},
        // Usual maximum speed per vehicle: the sheet's own defaults, overridable so a case can
        // assert that a stored value is what the sliders show (spec: location-options-ui).
        // Routing avoidance: overridable so a case can assert that a stored choice is what the
        // switches show and that a toggle is reported back (spec: `routing-avoidance` — The phone
        // edits the choices and the car reads them).
        // The long-press timeout row: overridable so a case can assert that the configured value
        // is what the slider shows and that a move is reported back (spec: location-options-ui —
        // Long-press timeout control).
        longPressTimeoutMs: Int = 500,
        onSetLongPressTimeoutMs: (Int) -> Unit = {},
        // Spoken-announcement switches (spec: `voice-guidance` — The phone offers both switches in its
        // settings sheet): overridable so a case can assert the stored value is what the switch shows
        // and that a toggle is reported back.
        voiceGuidanceEnabled: Boolean = true,
        onToggleVoiceGuidance: (Boolean) -> Unit = {},
        announceGpsState: Boolean = false,
        onToggleAnnounceGpsState: (Boolean) -> Unit = {}
    ) {
        composeRule.setContent {
            LocationOptionsOverlay(
                longPressTimeoutMs = longPressTimeoutMs,
                onSetLongPressTimeoutMs = onSetLongPressTimeoutMs,
                mode = mode,
                availableStyles = styles,
                styleSheet = "standard",
                onSetStyleSheet = onSetStyleSheet,
                preferSymbolPoiIcons = preferSymbolPoiIcons,
                onSetPreferSymbolPoiIcons = onSetPreferSymbolPoiIcons,
                voiceGuidanceEnabled = voiceGuidanceEnabled,
                onToggleVoiceGuidance = onToggleVoiceGuidance,
                announceGpsState = announceGpsState,
                onToggleAnnounceGpsState = onToggleAnnounceGpsState
            )
        }
        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()
    }

    /** The symbol/icon preference switch, found by the label it sits next to. */
    private fun symbolIconToggle() = composeRule.onNodeWithTag("preferSymbolIconsToggle")

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
                longPressTimeoutMs = 500,
                onSetLongPressTimeoutMs = {},
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
                longPressTimeoutMs = 500,
                onSetLongPressTimeoutMs = {},
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
    fun sheetShowsTheSymbolIconPreferenceOffByDefault() {
        openSheet()

        symbolIconToggle().performScrollTo().assertIsOff()
    }

    @Test
    fun symbolIconToggleReportsTheChange() {
        var received: Boolean? = null
        openSheet(preferSymbolPoiIcons = true, onSetPreferSymbolPoiIcons = { received = it })

        val toggle = symbolIconToggle().performScrollTo()
        toggle.assertIsOn()
        toggle.performClick()
        composeRule.waitForIdle()

        assertEquals(false, received)
    }

    @Test
    fun sheetHasNoModeSwitchControl() {
        openSheet(mode = MapMode.BROWSE)

        // The old "Map follows position" toggle is gone; no mode switch exists.
        composeRule.onNodeWithText("Map follows position").assertDoesNotExist()
    }

    @Test
    fun overspeedSliderCanSelectZero() {
        var reported: Int? = null
        composeRule.setContent {
            LocationOptionsOverlay(
                longPressTimeoutMs = 500,
                onSetLongPressTimeoutMs = {},
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

    /**
     * Spec: `voice-guidance` — The phone offers both switches in its settings sheet. The two rows
     * render at their stored values and a gesture is reported back to the surface.
     */
    @Test
    fun theVoiceAndGpsSwitchesAreShownAndReportAGesture() {
        var voice: Boolean? = null
        var gps: Boolean? = null

        openSheet(
            voiceGuidanceEnabled = false,
            onToggleVoiceGuidance = { voice = it },
            announceGpsState = false,
            onToggleAnnounceGpsState = { gps = it }
        )

        for (tag in listOf("voiceGuidanceSwitch", "announceGpsSwitch")) {
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }

        // The stored values are what the switches show.
        composeRule.onNodeWithTag("voiceGuidanceSwitch")
            .onChildren()
            .filterToOne(isToggleable())
            .assertIsOff()
        composeRule.onNodeWithTag("announceGpsSwitch")
            .onChildren()
            .filterToOne(isToggleable())
            .assertIsOff()

        composeRule.onNodeWithTag("voiceGuidanceSwitch")
            .onChildren()
            .filterToOne(isToggleable())
            .performClick()
        composeRule.onNodeWithTag("announceGpsSwitch")
            .onChildren()
            .filterToOne(isToggleable())
            .performClick()

        assertEquals(true, voice)
        assertEquals(true, gps)
    }

    /**
     * The long-press timeout row, scrolled into view: the sheet's content scrolls, and the
     * requirement is that the control renders in every mode, not only where it was added
     * (spec: `location-options-ui` — Long-press timeout control).
     */
    private fun assertLongPressTimeoutSliderVisible() {
        composeRule.onNodeWithText("Long-press delay").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("longPressTimeoutSlider").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun longPressTimeoutSliderIsReachableInBrowse() {
        openSheet(mode = MapMode.BROWSE)

        assertLongPressTimeoutSliderVisible()
    }

    @Test
    fun longPressTimeoutSliderIsReachableInFreeDrive() {
        openSheet(mode = MapMode.FREE_DRIVE)

        assertLongPressTimeoutSliderVisible()
    }

    @Test
    fun longPressTimeoutSliderIsReachableInNavigation() {
        openSheet(mode = MapMode.NAVIGATION)

        assertLongPressTimeoutSliderVisible()
    }

    /**
     * The long-press timeout row (spec: `location-options-ui` — Long-press timeout control):
     * it shows the configured value in milliseconds and reports a change to its caller, which
     * persists it (spec: `map-long-press-timeout` — The long-press timeout is a persisted setting).
     */
    @Test
    fun longPressTimeoutSliderShowsAndWritesTheValue() {
        val reported = mutableListOf<Int>()
        composeRule.setContent {
            LocationOptionsOverlay(
                longPressTimeoutMs = 500,
                onSetLongPressTimeoutMs = { reported.add(it) },
                mode = MapMode.BROWSE,
                availableStyles = listOf("standard"),
                styleSheet = "standard",
                onSetStyleSheet = {}
            )
        }
        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()

        assertLongPressTimeoutSliderVisible()
        composeRule.onNodeWithText("500 ms").performScrollTo().assertIsDisplayed()

        // A slider cannot report the value it already holds, so the row is moved to the
        // 900 ms tick of its 250-1000 ms range (50 ms steps) rather than dragged blind.
        composeRule.onNodeWithTag("longPressTimeoutSlider")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(900f) }
        composeRule.waitForIdle()

        assertEquals("the sheet reports the moved value", listOf(900), reported)
    }
}
