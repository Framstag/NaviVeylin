package com.naviveylin.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.naviveylin.core.VehicleAnchorPosition
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose tests for [FreeDrivingStreetPill] (spec: current-road-info —
 * row rule: bottom-center by default, top-center when the active follow
 * anchor preset is in the bottom row (`fy == 0.9`), bottom again for the top
 * row; nothing rendered for blank text).
 */
@RunWith(RobolectricTestRunner::class)
class FreeDrivingStreetPillComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(anchor: VehicleAnchorPosition, roadText: String? = "Hauptstrasse") {
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                FreeDrivingStreetPill(roadText = roadText, anchor = anchor)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun pillAtBottomCenterByDefault() {
        setContent(VehicleAnchorPosition.CENTER)
        val bounds = composeRule.onNodeWithTag("free-driving-street-pill").getBoundsInRoot()
        assertTrue("pill must sit at the bottom edge, was $bounds", bounds.bottom >= 450.dp)
        assertTrue("pill must be horizontally centered, was $bounds", centerX(bounds) in 150f..170f)
    }

    @Test
    fun pillMovesToTopCenterForBottomCenterAnchor() {
        setContent(VehicleAnchorPosition.BOTTOM_CENTER)
        val bounds = composeRule.onNodeWithTag("free-driving-street-pill").getBoundsInRoot()
        assertTrue("pill must sit at the top edge, was $bounds", bounds.top <= 50.dp)
        assertTrue("pill must be horizontally centered, was $bounds", centerX(bounds) in 150f..170f)
    }

    @Test
    fun topPillKeepsMarginBelowZeroInsets() {
        // The test environment reports zero window insets, so the top placement's
        // statusBarsPadding must be a no-op: the pill node stays at the top
        // edge, and the text keeps its 16.dp visual margin (plus the pill's
        // internal 8.dp vertical padding) below it. On a real device the node
        // lands at the status-bar inset instead, clearing the front camera.
        setContent(VehicleAnchorPosition.BOTTOM_CENTER)
        val nodeTop = composeRule.onNodeWithTag("free-driving-street-pill").getBoundsInRoot().top.value
        assertTrue(
            "pill node must sit at the top edge with zero insets, was $nodeTop",
            nodeTop in 0f..4f
        )
        val textTop = composeRule.onNodeWithText("Hauptstrasse").getBoundsInRoot().top.value
        assertTrue(
            "text must keep the 16.dp top margin (16 outer + 8 inner padding), was $textTop",
            textTop in 20f..28f
        )
    }

    @Test
    fun pillMovesToTopForWholeBottomRow() {
        // Row rule (street-name-host-views): every bottom-row preset moves
        // the pill to the top, not just bottom-center. One setContent call
        // (the rule forbids calling it twice); the anchor is driven by state.
        val bottomRow = listOf(
            VehicleAnchorPosition.BOTTOM_CENTER,
            VehicleAnchorPosition.BOTTOM_LEFT,
            VehicleAnchorPosition.BOTTOM_RIGHT,
            VehicleAnchorPosition.BOTTOM_FAR_LEFT,
            VehicleAnchorPosition.BOTTOM_FAR_RIGHT
        )
        var anchor by mutableStateOf(bottomRow.first())
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                FreeDrivingStreetPill(roadText = "Hauptstrasse", anchor = anchor)
            }
        }
        bottomRow.forEach { a ->
            composeRule.runOnUiThread { anchor = a }
            composeRule.waitForIdle()
            val bounds = composeRule.onNodeWithTag("free-driving-street-pill").getBoundsInRoot()
            assertTrue("pill must sit at the top edge for $a, was $bounds", bounds.top <= 50.dp)
            assertTrue("pill must be horizontally centered for $a, was $bounds", centerX(bounds) in 150f..170f)
        }
    }

    @Test
    fun pillStaysAtBottomForTopRowAnchors() {
        // Top-row presets keep the bottom placement (design D6 — mirrors the
        // old bottom-center-only rule's "other presets stay bottom").
        setContent(VehicleAnchorPosition.TOP_CENTER)
        composeRule.onNodeWithTag("free-driving-street-pill").assertIsDisplayed()
        val bounds = composeRule.onNodeWithTag("free-driving-street-pill").getBoundsInRoot()
        assertTrue("pill must stay at the bottom edge, was $bounds", bounds.bottom >= 450.dp)
    }

    @Test
    fun noPillWhenNoRoadText() {
        setContent(VehicleAnchorPosition.CENTER, roadText = null)
        composeRule.onNodeWithTag("free-driving-street-pill").assertDoesNotExist()
    }

    private fun centerX(bounds: androidx.compose.ui.unit.DpRect): Float =
        bounds.left.value + (bounds.right.value - bounds.left.value) / 2f
}
