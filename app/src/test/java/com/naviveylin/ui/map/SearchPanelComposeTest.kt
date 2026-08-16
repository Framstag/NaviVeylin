package com.naviveylin.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.framstag.libosmscout.client.LocationEntry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the resolved admin region indicator in the search panel:
 * the region name is shown above the search input only while a region is resolved.
 */
@RunWith(RobolectricTestRunner::class)
class SearchPanelComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun launchPanel(
        adminRegionName: String?,
        query: String = "",
        results: List<LocationEntry> = emptyList(),
        centerLat: Double = 51.5136,
        centerLon: Double = 7.4653
    ) {
        composeRule.setContent {
            SearchPanel(
                query = query,
                results = results,
                isSearching = false,
                gpsAvailable = true,
                adminRegionName = adminRegionName,
                centerLat = centerLat,
                centerLon = centerLon,
                onQueryChanged = {},
                onResultSelected = {},
                onSelectCurrentLocation = {},
                onSelectFavorite = {},
                onSelectFromHistory = {},
                onDismiss = {}
            )
        }
    }

    @Test
    fun regionNameShownAboveSearchField() {
        launchPanel("Dortmund")
        composeRule.onNodeWithText("Searching in Dortmund").assertIsDisplayed()
    }

    @Test
    fun noRegionNameWithoutResolvedRegion() {
        launchPanel(null)
        composeRule.onNodeWithText("Searching in", substring = true).assertDoesNotExist()
    }

    /** Entry ~1.0 km north of the default test center (0.009° latitude). */
    private fun distanceEntry(): LocationEntry = LocationEntry().apply {
        label = "Test Place"
        lat = 51.5226
        lon = 7.4653
    }

    @Test
    fun resultShowsDistanceFromMapCenter() {
        launchPanel(adminRegionName = null, query = "Dort", results = listOf(distanceEntry()))
        composeRule.onNodeWithText("1.0 km").assertIsDisplayed()
    }

    @Test
    fun distanceFollowsMapCenter() {
        val entry = distanceEntry()
        var centerLat by mutableStateOf(51.5136)
        composeRule.setContent {
            SearchPanel(
                query = "Dort",
                results = listOf(entry),
                isSearching = false,
                gpsAvailable = true,
                adminRegionName = null,
                centerLat = centerLat,
                centerLon = 7.4653,
                onQueryChanged = {},
                onResultSelected = {},
                onSelectCurrentLocation = {},
                onSelectFavorite = {},
                onSelectFromHistory = {},
                onDismiss = {}
            )
        }
        composeRule.onNodeWithText("1.0 km").assertIsDisplayed()
        // Move center ~2 km north of the entry: distance must recompute.
        centerLat = 51.5406
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2.0 km").assertIsDisplayed()
    }

    @Test
    fun historyEntryVisibleOnEmptyQuery() {
        launchPanel(adminRegionName = null)
        composeRule.onNodeWithText("Select from history").assertIsDisplayed()
    }

    @Test
    fun historyEntryHiddenWhileTyping() {
        launchPanel(adminRegionName = null, query = "Dort")
        composeRule.onNodeWithText("Select from history").assertDoesNotExist()
    }

    @Test
    fun historyEntryRestoredOnClear() {
        var query by mutableStateOf("")
        composeRule.setContent {
            SearchPanel(
                query = query,
                results = emptyList(),
                isSearching = false,
                gpsAvailable = true,
                adminRegionName = null,
                centerLat = 51.5136,
                centerLon = 7.4653,
                onQueryChanged = { query = it },
                onResultSelected = {},
                onSelectCurrentLocation = {},
                onSelectFavorite = {},
                onSelectFromHistory = {},
                onDismiss = {}
            )
        }
        composeRule.onNodeWithText("Select from history").assertIsDisplayed()
        // Typing hides the entry.
        query = "Dort"
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Select from history").assertDoesNotExist()
        // Clearing restores it immediately.
        query = ""
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Select from history").assertIsDisplayed()
    }
}
