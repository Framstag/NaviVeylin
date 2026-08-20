package com.naviveylin.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.PoiEntry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose tests for the POI results map layout (spec: map-for-poi-search):
 * in portrait the embedded map sits above the result list, in landscape it
 * sits to the left of it. The map renders with all result markers, the
 * current position, and a selected-result highlight.
 *
 * No @Config — default Robolectric sandbox so the FakeOSMScoutClient JNI
 * stub loads correctly (see AGENTS.md classloader rule).
 */
@RunWith(RobolectricTestRunner::class)
class PoiSearchPanelMapComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun results(): List<PoiEntry> = listOf(
        PoiEntry().apply {
            label = "Hotel Central"
            objectType = "tourism_hotel"
            distance = 1200.0
            lat = 51.5136
            lon = 7.4653
        },
        PoiEntry().apply {
            label = "Hotel North"
            objectType = "tourism_hotel"
            distance = 2500.0
            lat = 51.52
            lon = 7.46
        }
    )

    private fun launch(container: Modifier, landscape: Boolean = false) {
        composeRule.setContent {
            Box(modifier = container) {
                PoiResultsWithMap(
                    client = FakeOSMScoutClient(),
                    results = results(),
                    centerLat = 51.515,
                    centerLon = 7.465,
                    radiusMeters = 5000.0,
                    currentPosition = 51.514 to 7.466,
                    selectedPoi = 51.5136 to 7.4653,
                    onEntryClick = {},
                    modifier = Modifier.fillMaxSize(),
                    landscapeOverride = landscape.takeIf { it }
                )
            }
        }
    }

    @Test
    fun portraitShowsMapAboveResultList() {
        launch(Modifier.size(400.dp, 800.dp))

        val map = composeRule.onNodeWithTag("MiniMapCanvas").getUnclippedBoundsInRoot()
        val firstResult = composeRule.onNodeWithText("Hotel Central").getUnclippedBoundsInRoot()

        composeRule.onNodeWithTag("MiniMapCanvas").assertIsDisplayed()
        composeRule.onNodeWithText("Hotel North").assertIsDisplayed()
        assertTrue("map must sit above the result list in portrait", map.bottom <= firstResult.top)
    }

    @Test
    fun landscapeShowsMapLeftOfResultList() {
        // The Robolectric window is portrait; force the landscape branch.
        launch(Modifier.size(800.dp, 400.dp), landscape = true)

        val map = composeRule.onNodeWithTag("MiniMapCanvas").getUnclippedBoundsInRoot()
        val firstResult = composeRule.onNodeWithText("Hotel Central").getUnclippedBoundsInRoot()

        composeRule.onNodeWithTag("MiniMapCanvas").assertIsDisplayed()
        assertTrue("map must sit left of the result list in landscape", map.right <= firstResult.left)
    }
}
