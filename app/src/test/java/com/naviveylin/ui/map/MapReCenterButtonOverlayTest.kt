package com.naviveylin.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the re-center button visibility matrix (spec: map-modes — drive
 * suspension and reset / browse re-center): the button appears when the
 * FREE_DRIVE preset is suspended, or when the BROWSE viewport has drifted from
 * the GPS position, or when auto-zoom is suspended during NAVIGATION — given a
 * GPS fix. Uses the production predicate
 * [MapCanvasViewModel.shouldShowReCenterButton] and the production button
 * composable, mirroring the MapCanvasScreen overlay wiring.
 */
@RunWith(RobolectricTestRunner::class)
class MapReCenterButtonOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class Harness {
        var mode = MapMode.BROWSE
        var driveSuspended = false
        var browseDrifted = false
        var gpsQuality = GpsFixQuality.GOOD
        var recenterTaps = 0
    }

    @Composable
    private fun Overlay(h: Harness) {
        if (MapCanvasViewModel.shouldShowReCenterButton(h.mode, h.driveSuspended, h.browseDrifted) &&
            h.gpsQuality != GpsFixQuality.NONE
        ) {
            MapReCenterButton(onReCenter = { h.recenterTaps++ }, modifier = Modifier)
        }
    }

    private fun setContent(h: Harness) {
        composeRule.setContent { Overlay(h) }
    }

    @Test
    fun buttonHiddenWhileDriveActive() {
        val h = Harness().apply {
            mode = MapMode.FREE_DRIVE
            driveSuspended = false
        }
        setContent(h)
        composeRule.onNodeWithContentDescription("Re-center on location").assertDoesNotExist()
    }

    @Test
    fun buttonVisibleWhenDriveSuspended() {
        val h = Harness().apply {
            mode = MapMode.FREE_DRIVE
            driveSuspended = true
        }
        setContent(h)
        composeRule.onNodeWithContentDescription("Re-center on location").assertExists()
    }

    @Test
    fun buttonVisibleWhenAutoZoomPausedWhileNavigating() {
        val h = Harness().apply {
            mode = MapMode.NAVIGATION
            driveSuspended = true
        }
        setContent(h)
        composeRule.onNodeWithContentDescription("Re-center on location").assertExists()
    }

    @Test
    fun buttonVisibleWhenBrowseDrifted() {
        val h = Harness().apply {
            mode = MapMode.BROWSE
            browseDrifted = true
        }
        setContent(h)
        composeRule.onNodeWithContentDescription("Re-center on location").assertExists()
    }

    @Test
    fun buttonHiddenWithoutGpsFix() {
        val h = Harness().apply {
            mode = MapMode.BROWSE
            browseDrifted = true
            gpsQuality = GpsFixQuality.NONE
        }
        setContent(h)
        composeRule.onNodeWithContentDescription("Re-center on location").assertDoesNotExist()
    }

    @Test
    fun tapInvokesReCenterAction() {
        val h = Harness().apply {
            mode = MapMode.NAVIGATION
            driveSuspended = true
        }
        setContent(h)
        composeRule.onNodeWithContentDescription("Re-center on location").performClick()
        assertEquals("tap must invoke the re-center action", 1, h.recenterTaps)
    }
}
