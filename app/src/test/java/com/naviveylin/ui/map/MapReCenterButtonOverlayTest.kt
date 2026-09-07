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
 * Verifies the re-center button visibility matrix (spec: map-recenter-button):
 * the button appears when follow mode is off, or when auto-zoom is suspended
 * while navigating, given a GPS fix. Uses the production predicate
 * [MapCanvasViewModel.shouldShowReCenterButton] and the production button
 * composable, mirroring the MapCanvasScreen overlay wiring.
 */
@RunWith(RobolectricTestRunner::class)
class MapReCenterButtonOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class Harness {
        var followMode = false
        var autoZoomPaused = false
        var isNavigating = false
        var gpsQuality = GpsFixQuality.GOOD
        var recenterTaps = 0
    }

    @Composable
    private fun Overlay(h: Harness) {
        if (MapCanvasViewModel.shouldShowReCenterButton(h.followMode, h.autoZoomPaused, h.isNavigating) &&
            h.gpsQuality != GpsFixQuality.NONE
        ) {
            MapReCenterButton(onReCenter = { h.recenterTaps++ }, modifier = Modifier)
        }
    }

    private fun setContent(h: Harness) {
        composeRule.setContent { Overlay(h) }
    }

    @Test
    fun buttonHiddenWhileAutoZoomDriving() {
        val h = Harness().apply {
            followMode = true
            autoZoomPaused = false
            isNavigating = true
        }
        setContent(h)
        composeRule.onNodeWithContentDescription("Re-center on location").assertDoesNotExist()
    }

    @Test
    fun buttonVisibleWhenAutoZoomPausedWhileNavigating() {
        val h = Harness().apply {
            followMode = true
            autoZoomPaused = true
            isNavigating = true
        }
        setContent(h)
        composeRule.onNodeWithContentDescription("Re-center on location").assertExists()
    }

    @Test
    fun buttonHiddenWhenAutoZoomPausedButNotNavigating() {
        val h = Harness().apply {
            followMode = true
            autoZoomPaused = true
            isNavigating = false
        }
        setContent(h)
        composeRule.onNodeWithContentDescription("Re-center on location").assertDoesNotExist()
    }

    @Test
    fun buttonVisibleWhenFollowModeOff() {
        val h = Harness().apply {
            followMode = false
            autoZoomPaused = false
            isNavigating = true
        }
        setContent(h)
        composeRule.onNodeWithContentDescription("Re-center on location").assertExists()
    }

    @Test
    fun buttonHiddenWithoutGpsFix() {
        val h = Harness().apply {
            followMode = false
            gpsQuality = GpsFixQuality.NONE
        }
        setContent(h)
        composeRule.onNodeWithContentDescription("Re-center on location").assertDoesNotExist()
    }

    @Test
    fun tapInvokesReCenterAction() {
        val h = Harness().apply {
            followMode = true
            autoZoomPaused = true
            isNavigating = true
        }
        setContent(h)
        composeRule.onNodeWithContentDescription("Re-center on location").performClick()
        assertEquals("tap must invoke the re-center action", 1, h.recenterTaps)
    }
}
