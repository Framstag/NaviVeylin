package com.naviveylin.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.search.MergedSearchResult
import com.naviveylin.data.SearchHistoryEntry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the unified search dialog's Places mode (spec:
 * search-dialog, location-search): the resolved admin region name is shown
 * above the search input only while a region is resolved, results show the
 * distance from the map center, and the recent-search suggestions are visible
 * on an empty query and hidden while typing.
 */
@RunWith(RobolectricTestRunner::class)
class SearchPanelComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun launchDialog(
        adminRegionName: String?,
        query: String = "",
        results: List<MergedSearchResult> = emptyList(),
        centerLat: Double = 51.5136,
        centerLon: Double = 7.4653,
        historyEntries: List<SearchHistoryEntry> = emptyList(),
        gpsAvailable: Boolean = true,
        onQueryChanged: (String) -> Unit = {}
    ) {
        composeRule.setContent {
            SearchDialog(
                searchMode = SearchMode.PLACES,
                onModeSelected = {},
                onDismiss = {},
                query = query,
                results = results,
                isSearching = false,
                gpsAvailable = gpsAvailable,
                adminRegionName = adminRegionName,
                centerLat = centerLat,
                centerLon = centerLon,
                historyEntries = historyEntries,
                favoriteGroups = emptyMap(),
                onQueryChanged = onQueryChanged,
                onResultSelected = {},
                onSelectCurrentLocation = {},
                onSelectFavorite = {},
                onHistoryEntrySelected = {},
                poiCategory = null,
                poiRadiusMeters = 5000.0,
                poiResults = emptyList(),
                isPoiSearching = false,
                poiError = null,
                client = null,
                poiCenterLat = Double.NaN,
                poiCenterLon = Double.NaN,
                currentPosition = null,
                selectedPoi = null,
                onPoiCategorySelected = {},
                onPoiRadiusChanged = {},
                onPoiSearch = {},
                onPoiEntryClick = {},
                addressBookAvailable = false,
                contactsQuery = "",
                onContactsQueryChanged = {},
                contactsContent = {}
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun regionNameShownAboveSearchField() {
        launchDialog("Dortmund")
        composeRule.onNodeWithText("Searching in Dortmund").assertIsDisplayed()
    }

    @Test
    fun noRegionNameWithoutResolvedRegion() {
        launchDialog(null)
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
        launchDialog(adminRegionName = null, query = "Dort", results = listOf(MergedSearchResult(distanceEntry(), isFavorite = false, isFavoriteHit = false)))
        composeRule.onNodeWithText("1.0 km").assertIsDisplayed()
    }

    @Test
    fun distanceFollowsMapCenter() {
        val entry = distanceEntry()
        var centerLat by mutableStateOf(51.5136)
        composeRule.setContent {
            SearchDialog(
                searchMode = SearchMode.PLACES,
                onModeSelected = {},
                onDismiss = {},
                query = "Dort",
                results = listOf(MergedSearchResult(entry, isFavorite = false, isFavoriteHit = false)),
                isSearching = false,
                gpsAvailable = true,
                adminRegionName = null,
                centerLat = centerLat,
                centerLon = 7.4653,
                historyEntries = emptyList(),
                favoriteGroups = emptyMap(),
                onQueryChanged = {},
                onResultSelected = {},
                onSelectCurrentLocation = {},
                onSelectFavorite = {},
                onHistoryEntrySelected = {},
                poiCategory = null,
                poiRadiusMeters = 5000.0,
                poiResults = emptyList(),
                isPoiSearching = false,
                poiError = null,
                client = null,
                poiCenterLat = Double.NaN,
                poiCenterLon = Double.NaN,
                currentPosition = null,
                selectedPoi = null,
                onPoiCategorySelected = {},
                onPoiRadiusChanged = {},
                onPoiSearch = {},
                onPoiEntryClick = {},
                addressBookAvailable = false,
                contactsQuery = "",
                onContactsQueryChanged = {},
                contactsContent = {}
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
        launchDialog(adminRegionName = null, historyEntries = listOf(SearchHistoryEntry("Café Central", 1000L)))
        composeRule.onNodeWithText("Café Central").assertIsDisplayed()
    }

    @Test
    fun historyEntryHiddenWhileTyping() {
        launchDialog(
            adminRegionName = null,
            query = "Dort",
            historyEntries = listOf(SearchHistoryEntry("Café Central", 1000L))
        )
        composeRule.onNodeWithText("Café Central").assertDoesNotExist()
    }

    @Test
    fun historyEntryRestoredOnClear() {
        var query by mutableStateOf("")
        composeRule.setContent {
            SearchDialog(
                searchMode = SearchMode.PLACES,
                onModeSelected = {},
                onDismiss = {},
                query = query,
                results = emptyList(),
                isSearching = false,
                gpsAvailable = true,
                adminRegionName = null,
                centerLat = 51.5136,
                centerLon = 7.4653,
                historyEntries = listOf(SearchHistoryEntry("Café Central", 1000L)),
                favoriteGroups = emptyMap(),
                onQueryChanged = { query = it },
                onResultSelected = {},
                onSelectCurrentLocation = {},
                onSelectFavorite = {},
                onHistoryEntrySelected = {},
                poiCategory = null,
                poiRadiusMeters = 5000.0,
                poiResults = emptyList(),
                isPoiSearching = false,
                poiError = null,
                client = null,
                poiCenterLat = Double.NaN,
                poiCenterLon = Double.NaN,
                currentPosition = null,
                selectedPoi = null,
                onPoiCategorySelected = {},
                onPoiRadiusChanged = {},
                onPoiSearch = {},
                onPoiEntryClick = {},
                addressBookAvailable = false,
                contactsQuery = "",
                onContactsQueryChanged = {},
                contactsContent = {}
            )
        }
        composeRule.onNodeWithText("Café Central").assertIsDisplayed()
        // Typing hides the suggestions.
        query = "Dort"
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Café Central").assertDoesNotExist()
        // Clearing restores them immediately.
        query = ""
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Café Central").assertIsDisplayed()
    }

    @Test
    fun currentLocationRowShownWithGps() {
        launchDialog(adminRegionName = null, gpsAvailable = true)

        composeRule.onNodeWithText("Current Location").assertIsDisplayed()
    }

    @Test
    fun currentLocationRowHiddenWithoutGps() {
        launchDialog(adminRegionName = null, gpsAvailable = false)

        composeRule.onNodeWithText("Current Location").assertDoesNotExist()
    }
}
