package com.naviveylin.ui.map

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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
        carMaxSpeedKmh: Int = 120,
        onSetCarMaxSpeed: (Int) -> Unit = {},
        bicycleMaxSpeedKmh: Int = 15,
        onSetBicycleMaxSpeed: (Int) -> Unit = {},
        footMaxSpeedKmh: Int = 5,
        onSetFootMaxSpeed: (Int) -> Unit = {}
    ) {
        composeRule.setContent {
            LocationOptionsOverlay(
                routingAnchor = VehicleAnchorPosition.CENTER,
                onSetRoutingAnchor = {},
                freeDrivingAnchor = VehicleAnchorPosition.CENTER,
                onSetFreeDrivingAnchor = {},
                carMaxSpeedKmh = carMaxSpeedKmh,
                onSetCarMaxSpeed = onSetCarMaxSpeed,
                bicycleMaxSpeedKmh = bicycleMaxSpeedKmh,
                onSetBicycleMaxSpeed = onSetBicycleMaxSpeed,
                footMaxSpeedKmh = footMaxSpeedKmh,
                onSetFootMaxSpeed = onSetFootMaxSpeed,
                mode = mode,
                availableStyles = styles,
                styleSheet = "standard",
                onSetStyleSheet = onSetStyleSheet,
                preferSymbolPoiIcons = preferSymbolPoiIcons,
                onSetPreferSymbolPoiIcons = onSetPreferSymbolPoiIcons
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
                routingAnchor = VehicleAnchorPosition.CENTER,
                onSetRoutingAnchor = {},
                freeDrivingAnchor = VehicleAnchorPosition.CENTER,
                onSetFreeDrivingAnchor = {},
                carMaxSpeedKmh = 120,
                onSetCarMaxSpeed = {},
                bicycleMaxSpeedKmh = 15,
                onSetBicycleMaxSpeed = {},
                footMaxSpeedKmh = 5,
                onSetFootMaxSpeed = {},
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
                routingAnchor = VehicleAnchorPosition.CENTER,
                onSetRoutingAnchor = {},
                freeDrivingAnchor = VehicleAnchorPosition.CENTER,
                onSetFreeDrivingAnchor = {},
                carMaxSpeedKmh = 120,
                onSetCarMaxSpeed = {},
                bicycleMaxSpeedKmh = 15,
                onSetBicycleMaxSpeed = {},
                footMaxSpeedKmh = 5,
                onSetFootMaxSpeed = {},
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
    fun overspeedSliderShowsCurrentValue() {
        composeRule.setContent {
            LocationOptionsOverlay(
                routingAnchor = VehicleAnchorPosition.CENTER,
                onSetRoutingAnchor = {},
                freeDrivingAnchor = VehicleAnchorPosition.CENTER,
                onSetFreeDrivingAnchor = {},
                carMaxSpeedKmh = 120,
                onSetCarMaxSpeed = {},
                bicycleMaxSpeedKmh = 15,
                onSetBicycleMaxSpeed = {},
                footMaxSpeedKmh = 5,
                onSetFootMaxSpeed = {},
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
                routingAnchor = VehicleAnchorPosition.CENTER,
                onSetRoutingAnchor = {},
                freeDrivingAnchor = VehicleAnchorPosition.CENTER,
                onSetFreeDrivingAnchor = {},
                carMaxSpeedKmh = 120,
                onSetCarMaxSpeed = {},
                bicycleMaxSpeedKmh = 15,
                onSetBicycleMaxSpeed = {},
                footMaxSpeedKmh = 5,
                onSetFootMaxSpeed = {},
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
                routingAnchor = VehicleAnchorPosition.CENTER,
                onSetRoutingAnchor = {},
                freeDrivingAnchor = VehicleAnchorPosition.CENTER,
                onSetFreeDrivingAnchor = {},
                carMaxSpeedKmh = 120,
                onSetCarMaxSpeed = {},
                bicycleMaxSpeedKmh = 15,
                onSetBicycleMaxSpeed = {},
                footMaxSpeedKmh = 5,
                onSetFootMaxSpeed = {},
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

    @Test
    fun anchorRowsShowCurrentPresets() {
        composeRule.setContent {
            LocationOptionsOverlay(
                onSetRoutingAnchor = {},
                onSetFreeDrivingAnchor = {},
                mode = MapMode.NAVIGATION,
                routingAnchor = VehicleAnchorPosition.BOTTOM_RIGHT,
                freeDrivingAnchor = VehicleAnchorPosition.TOP_CENTER,
                carMaxSpeedKmh = 120,
                onSetCarMaxSpeed = {},
                bicycleMaxSpeedKmh = 15,
                onSetBicycleMaxSpeed = {},
                footMaxSpeedKmh = 5,
                onSetFootMaxSpeed = {}
            )
        }
        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Vehicle position (navigation)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Bottom right").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Vehicle position (free driving)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Top center").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anchorPickerOffersTheFiveByThreeGridWithCurrentMarked() {
        var selected: VehicleAnchorPosition? = null
        composeRule.setContent {
            LocationOptionsOverlay(
                freeDrivingAnchor = VehicleAnchorPosition.CENTER,
                onSetFreeDrivingAnchor = {},
                mode = MapMode.NAVIGATION,
                routingAnchor = VehicleAnchorPosition.CENTER,
                onSetRoutingAnchor = { selected = it },
                carMaxSpeedKmh = 120,
                onSetCarMaxSpeed = {},
                bicycleMaxSpeedKmh = 15,
                onSetBicycleMaxSpeed = {},
                footMaxSpeedKmh = 5,
                onSetFootMaxSpeed = {}
            )
        }
        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Vehicle position (navigation)").performScrollTo().performClick()
        composeRule.waitForIdle()

        // All 15 presets appear with the shared enum labels (label parity with
        // the Android Auto picker, spec: location-options-ui — Picker labels
        // match Android Auto). Each is a clickable cell inside the dialog and
        // displayed: weighted cells share the dialog width as five equal
        // columns (regression: fixed 76.dp cells overflowed the AlertDialog
        // content width on phones, clipping the grid to a ragged ~3-4 columns).
        for (anchor in VehicleAnchorPosition.entries) {
            val cell = composeRule.onAllNodes(
                hasAnyAncestor(isDialog()) and hasText(anchor.label) and hasClickAction()
            ).onFirst()
            cell.performScrollTo().assertIsDisplayed()
        }

        // Picking replaces the current anchor and reports the selection.
        composeRule.onNodeWithText(VehicleAnchorPosition.BOTTOM_LEFT.label).performClick()
        composeRule.waitForIdle()
        assertEquals(VehicleAnchorPosition.BOTTOM_LEFT, selected)
    }

    @Test
    fun anchorPickerFreeDrivingReportsSelection() {
        var selected: VehicleAnchorPosition? = null
        composeRule.setContent {
            LocationOptionsOverlay(
                routingAnchor = VehicleAnchorPosition.CENTER,
                onSetRoutingAnchor = {},
                mode = MapMode.FREE_DRIVE,
                freeDrivingAnchor = VehicleAnchorPosition.CENTER,
                onSetFreeDrivingAnchor = { selected = it },
                carMaxSpeedKmh = 120,
                onSetCarMaxSpeed = {},
                bicycleMaxSpeedKmh = 15,
                onSetBicycleMaxSpeed = {},
                footMaxSpeedKmh = 5,
                onSetFootMaxSpeed = {}
            )
        }
        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Vehicle position (free driving)").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(VehicleAnchorPosition.BOTTOM_CENTER.label).performClick()
        composeRule.waitForIdle()
        assertEquals(VehicleAnchorPosition.BOTTOM_CENTER, selected)
    }

    // --- Usual maximum speed per vehicle (spec: location-options-ui — Vehicle maximum speed
    // sliders in the options sheet) ---

    /** All three sliders, scrolled into view: the sheet's content scrolls. */
    private fun assertMaxSpeedSlidersVisible() {
        composeRule.onNodeWithText("Routing speed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("carMaxSpeedSlider").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("bicycleMaxSpeedSlider").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("footMaxSpeedSlider").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun threeMaximumSpeedSlidersAreShown() {
        openSheet()

        assertMaxSpeedSlidersVisible()
    }

    @Test
    fun theRangesAreTheOnesTheSheetOffers() {
        // The bounds the spec names, written out here: `eachSliderCoversEveryWholeValueOfItsRange`
        // reads the table, so it moves with a change to it and cannot catch one. This case can.
        // (spec: location-options-ui — Each slider covers its vehicle's full range).
        assertEquals(30, MaxSpeedRange.CAR.minKmh)
        assertEquals(160, MaxSpeedRange.CAR.maxKmh)
        assertEquals(5, MaxSpeedRange.BICYCLE.minKmh)
        assertEquals(40, MaxSpeedRange.BICYCLE.maxKmh)
        assertEquals(2, MaxSpeedRange.FOOT.minKmh)
        assertEquals(15, MaxSpeedRange.FOOT.maxKmh)
    }

    @Test
    fun slidersShowTheStoredValues() {
        openSheet(carMaxSpeedKmh = 90, bicycleMaxSpeedKmh = 20, footMaxSpeedKmh = 4)

        // The value is read from the state, not from a default: a stored value is what the user sees
        // (spec: location-options-ui — Values are readable from the sheet).
        composeRule.onNodeWithText("90 km/h").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("20 km/h").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("4 km/h").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun slidersAreReachableInBrowse() {
        openSheet(mode = MapMode.BROWSE)

        assertMaxSpeedSlidersVisible()
    }

    @Test
    fun slidersAreReachableInFreeDrive() {
        openSheet(mode = MapMode.FREE_DRIVE)

        assertMaxSpeedSlidersVisible()
    }

    @Test
    fun slidersAreReachableInNavigation() {
        openSheet(mode = MapMode.NAVIGATION)

        assertMaxSpeedSlidersVisible()
    }

    @Test
    fun eachSliderCoversEveryWholeValueOfItsRange() {
        val reported = mutableMapOf<String, MutableList<Int>>()
        composeRule.setContent {
            LocationOptionsOverlay(
                routingAnchor = VehicleAnchorPosition.CENTER,
                onSetRoutingAnchor = {},
                freeDrivingAnchor = VehicleAnchorPosition.CENTER,
                onSetFreeDrivingAnchor = {},
                // Start away from both bounds: a slider does not report a value it already holds,
                // so a bound could not be observed from it.
                carMaxSpeedKmh = MaxSpeedRange.CAR.midKmh,
                onSetCarMaxSpeed = { reported.getOrPut("car") { mutableListOf() }.add(it) },
                bicycleMaxSpeedKmh = MaxSpeedRange.BICYCLE.midKmh,
                onSetBicycleMaxSpeed = { reported.getOrPut("bicycle") { mutableListOf() }.add(it) },
                footMaxSpeedKmh = MaxSpeedRange.FOOT.midKmh,
                onSetFootMaxSpeed = { reported.getOrPut("foot") { mutableListOf() }.add(it) },
                mode = MapMode.BROWSE
            )
        }
        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()

        for ((key, tag, range) in listOf(
            Triple("car", "carMaxSpeedSlider", MaxSpeedRange.CAR),
            Triple("bicycle", "bicycleMaxSpeedSlider", MaxSpeedRange.BICYCLE),
            Triple("foot", "footMaxSpeedSlider", MaxSpeedRange.FOOT)
        )) {
            // The ends of the track are the ends of the range: the left edge reports the lower
            // bound, the right edge the upper one. Exact, so the bound is not inferred from a
            // drag's stopping point.
            composeRule.onNodeWithTag(tag)
                .performScrollTo()
                .performTouchInput { click(Offset(centerLeft.x + 1f, center.y)) }
            composeRule.waitForIdle()
            assertEquals(
                "$key left edge selects the lower bound",
                range.minKmh,
                reported[key]!!.last()
            )

            composeRule.onNodeWithTag(tag)
                .performScrollTo()
                .performTouchInput { click(Offset(centerRight.x - 1f, center.y)) }
            composeRule.waitForIdle()
            assertEquals(
                "$key right edge selects the upper bound",
                range.maxKmh,
                reported[key]!!.last()
            )

            // A drag across the track reports whole km/h values, all inside the range (1 km/h
            // precision, no coarser step) and spanning most of it.
            composeRule.onNodeWithTag(tag)
                .performScrollTo()
                .performTouchInput { swipe(centerLeft, centerRight, durationMillis = 400) }
            composeRule.waitForIdle()

            val dragged = reported[key].orEmpty()
            assertTrue("$key slider must report values while dragged", dragged.isNotEmpty())
            assertTrue(
                "$key reports only whole km/h inside ${range.minKmh}..${range.maxKmh}: $dragged",
                dragged.all { it in range.minKmh..range.maxKmh }
            )
            val span = range.maxKmh - range.minKmh
            assertTrue(
                "$key drag spans most of its range (was ${dragged.max() - dragged.min()} of $span)",
                dragged.max() - dragged.min() >= span * 4 / 5
            )
        }
    }
}
