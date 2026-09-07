package com.naviveylin.ui.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.naviveylin.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the shared right-side widget column (spec: phone-align-controls-in-all-modes):
 * compass directly above the speed widget, optional location-options slot, zoom controls
 * at the bottom below all other controls — in both the standard and routing variants.
 */
@RunWith(RobolectricTestRunner::class)
class MapRightWidgetColumnTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class Harness {
        var isLandscape = false
        var showLocationOptions = true
        var reserveSpeedSlot = false
        var speedKmH = 50.0
        var maxSpeedKmH = 50.0
        var zoomInTaps = 0
        var zoomOutTaps = 0
        var gearTaps = 0
    }

    @Composable
    private fun WidgetColumn(h: Harness) {
        MapRightWidgetColumn(
            isLandscape = h.isLandscape,
            compassNorthUp = true,
            mapAngleRadians = 0.0,
            gpsFixQuality = GpsFixQuality.GOOD,
            onCenterClick = {},
            onToggleOrientation = {},
            speedInput = if (h.speedKmH.isNaN()) null else SpeedWidgetInput(h.speedKmH, h.maxSpeedKmH),
            canZoomIn = true,
            canZoomOut = true,
            currentMag = 12.0,
            onZoomIn = { h.zoomInTaps++ },
            onZoomOut = { h.zoomOutTaps++ },
            locationOptions = if (h.showLocationOptions) {
                {
                    FilledTonalIconButton(onClick = { h.gearTaps++ }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.location_options)
                        )
                    }
                }
            } else null,
            reserveSpeedSlot = h.reserveSpeedSlot
        )
    }

    private fun setContent(h: Harness) {
        composeRule.setContent { WidgetColumn(h) }
    }

    @Test
    fun standardColumnShowsCompassSpeedGearAndZoom() {
        val h = Harness()
        setContent(h)
        composeRule.onNodeWithContentDescription("Compass").assertExists()
        composeRule.onNodeWithTag("speedWidget").assertExists()
        composeRule.onNodeWithContentDescription("Location options").assertExists()
        composeRule.onNodeWithContentDescription("Zoom in").assertExists()
        composeRule.onNodeWithContentDescription("Zoom out").assertExists()
    }

    @Test
    fun routingColumnShowsNoGear() {
        val h = Harness().apply {
            showLocationOptions = false
            reserveSpeedSlot = true
        }
        setContent(h)
        composeRule.onNodeWithContentDescription("Compass").assertExists()
        composeRule.onNodeWithTag("speedWidget").assertExists()
        composeRule.onNodeWithContentDescription("Zoom in").assertExists()
        composeRule.onNodeWithContentDescription("Zoom out").assertExists()
        composeRule.onNodeWithContentDescription("Location options").assertDoesNotExist()
    }

    @Test
    fun zoomSitsBelowAllOtherControls() {
        val h = Harness()
        setContent(h)
        val gearBounds = composeRule.onNodeWithContentDescription("Location options").getBoundsInRoot()
        val zoomInBounds = composeRule.onNodeWithContentDescription("Zoom in").getBoundsInRoot()
        assertTrue(
            "zoom controls must sit below the location-options button",
            zoomInBounds.top >= gearBounds.bottom
        )
    }

    @Test
    fun zoomTapsInvokeCallbacks() {
        val h = Harness()
        setContent(h)
        composeRule.onNodeWithContentDescription("Zoom in").performClick()
        composeRule.onNodeWithContentDescription("Zoom out").performClick()
        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()
        assertEquals("zoom in tap must invoke the callback", 1, h.zoomInTaps)
        assertEquals("zoom out tap must invoke the callback", 1, h.zoomOutTaps)
        assertEquals("gear tap must invoke the callback", 1, h.gearTaps)
    }

    @Test
    fun speedSlotReservedWhenHiddenInRoutingPlacement() {
        val h = Harness().apply {
            speedKmH = Double.NaN
            showLocationOptions = false
            reserveSpeedSlot = true
        }
        setContent(h)
        composeRule.onNodeWithTag("speedWidgetSlot").assertExists()
        composeRule.onNodeWithContentDescription("Compass").assertExists()
    }

    @Test
    fun noSpeedWidgetWithoutDataInStandardPlacement() {
        val h = Harness().apply { speedKmH = Double.NaN }
        setContent(h)
        composeRule.onNodeWithTag("speedWidget").assertDoesNotExist()
        composeRule.onNodeWithTag("speedWidgetSlot").assertDoesNotExist()
    }
}
