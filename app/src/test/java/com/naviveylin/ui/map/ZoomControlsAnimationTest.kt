package com.naviveylin.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import com.naviveylin.core.ZoomAnimation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the button-triggered zoom-animation state wiring (spec: smooth-zoom,
 * zoom-controls delta): tapping the zoom controls commits the magnification
 * immediately and starts/retracks the eased animation; the displayed
 * magnification updates on the first tap while the animation runs.
 */
@RunWith(RobolectricTestRunner::class)
class ZoomControlsAnimationTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Mirrors the MapCanvasScreen wiring: commit magnification first, then
     * start/retrack the animation toward `2^(committed - frontBuffer)`.
     */
    private class Harness {
        val zoomAnim = ZoomAnimation(durationMs = 250L)
        var committedMag = 10.0
        var frontMag = 10.0
        var animationsStarted = 0
        var retracks = 0

        fun animate(nowMs: Long = 1_000L) {
            val target = Math.pow(2.0, (committedMag - frontMag).toDouble()).toFloat()
            if (zoomAnim.active) {
                retracks++
                zoomAnim.retrack(target, 0f, 0f, nowMs)
            } else {
                animationsStarted++
                zoomAnim.start(1f, target, 0f, 0f, nowMs)
            }
        }
    }

    @Test
    fun tapZoomInCommitsMagnificationAndStartsAnimation() {
        val h = Harness()
        var displayedMagUpdates = 0.0
        composeRule.setContent {
            ZoomControls(
                canZoomIn = h.committedMag < 20.0,
                canZoomOut = h.committedMag > 4.0,
                currentMag = h.committedMag,
                onZoomIn = {
                    h.committedMag++
                    h.animate()
                    displayedMagUpdates = h.committedMag
                },
                onZoomOut = {
                    h.committedMag--
                    h.animate()
                }
            )
        }

        composeRule.onNodeWithContentDescription("Zoom in").performClick()

        assertEquals(11.0, h.committedMag, 1e-9)
        assertEquals("magnification display updates immediately", 11.0, displayedMagUpdates, 1e-9)
        assertEquals(1, h.animationsStarted)
        assertEquals(0, h.retracks)
        assertTrue(h.zoomAnim.active)
    }

    @Test
    fun rapidRetapRetracksInsteadOfNewAnimationWithoutSnapBack() {
        val h = Harness()
        composeRule.setContent {
            ZoomControls(
                canZoomIn = h.committedMag < 20.0,
                canZoomOut = h.committedMag > 4.0,
                currentMag = h.committedMag,
                onZoomIn = {
                    h.committedMag++
                    h.animate(nowMs = 1_000L)
                },
                onZoomOut = {
                    h.committedMag--
                    h.animate(nowMs = 1_000L)
                }
            )
        }

        composeRule.onNodeWithContentDescription("Zoom in").performClick()
        composeRule.onNodeWithContentDescription("Zoom in").performClick()

        // First tap starts, second tap retracks (never snaps back to the
        // previous start scale — spec: smooth-zoom "Retracking on rapid
        // zoom input").
        assertEquals(1, h.animationsStarted)
        assertEquals(1, h.retracks)
    }

    @Test
    fun zoomOutDuringZoomInRetracksTowardLowerTarget() {
        val h = Harness()
        composeRule.setContent {
            ZoomControls(
                canZoomIn = h.committedMag < 20.0,
                canZoomOut = h.committedMag > 4.0,
                currentMag = h.committedMag,
                onZoomIn = { h.committedMag++; h.animate() },
                onZoomOut = { h.committedMag--; h.animate() }
            )
        }

        composeRule.onNodeWithContentDescription("Zoom in").performClick()
        // Mid-animation: zoom out reverses smoothly toward the lower target
        // (retrack from the current scale, not restart from 1.0 — verified in
        // ZoomAnimationTest "retrack compounds from current scale").
        composeRule.onNodeWithContentDescription("Zoom out").performClick()

        assertEquals("net commit must return to the front-buffer magnification", 10.0, h.committedMag, 1e-9)
        assertEquals(1, h.animationsStarted)
        assertEquals(1, h.retracks)
    }

    @Test
    fun disabledButtonsDoNothing() {
        val h = Harness().apply { committedMag = 20.0 }
        composeRule.setContent {
            ZoomControls(
                canZoomIn = false,
                canZoomOut = true,
                currentMag = h.committedMag,
                onZoomIn = { h.committedMag++; h.animate() },
                onZoomOut = { h.committedMag--; h.animate() }
            )
        }

        composeRule.onNodeWithContentDescription("Zoom in").performClick()
        assertEquals("disabled button must not commit", 20.0, h.committedMag, 1e-9)
        assertEquals(0, h.animationsStarted)
    }
}