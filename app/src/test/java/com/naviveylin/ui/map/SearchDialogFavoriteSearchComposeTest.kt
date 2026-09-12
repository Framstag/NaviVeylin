package com.naviveylin.ui.map

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.search.MergedSearchResult
import com.naviveylin.data.SearchHistoryEntry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for favorite hits in the search dialog's Places results
 * (spec: favorite-search): favorite hits render with a heart icon, appear
 * above native results, and identical objects are deduplicated.
 */
@RunWith(RobolectricTestRunner::class)
class SearchDialogFavoriteSearchComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.lat = lat
            this.lon = lon
            name = label
            matchQuality = "match"
        }

    private fun launchDialog(results: List<MergedSearchResult>) {
        composeRule.setContent {
            SearchDialog(
                searchMode = SearchMode.PLACES,
                onModeSelected = {},
                onDismiss = {},
                query = "home",
                results = results,
                isSearching = false,
                gpsAvailable = false,
                adminRegionName = null,
                centerLat = 51.5136,
                centerLon = 7.4653,
                historyEntries = emptyList<SearchHistoryEntry>(),
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
        composeRule.waitForIdle()
    }

    @Test
    fun favoriteHitShowsHeartIcon() {
        val favHit = MergedSearchResult(entry("Home", 51.5, 7.4), isFavorite = true, isFavoriteHit = true)
        launchDialog(listOf(favHit))

        composeRule.onNodeWithContentDescription("Favorite").assertIsDisplayed()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
    }

    @Test
    fun nativeResultMarkedAsFavoriteShowsHeartIcon() {
        val native = MergedSearchResult(entry("Home Street", 51.5, 7.4), isFavorite = true, isFavoriteHit = false)
        launchDialog(listOf(native))

        composeRule.onNodeWithContentDescription("Favorite").assertIsDisplayed()
    }

    @Test
    fun plainNativeResultShowsNoHeartIcon() {
        val native = MergedSearchResult(entry("Home Street", 51.5, 7.4), isFavorite = false, isFavoriteHit = false)
        launchDialog(listOf(native))

        composeRule.onNodeWithContentDescription("Favorite").assertDoesNotExist()
    }

    @Test
    fun favoriteHitListedAboveNativeResult() {
        val favHit = MergedSearchResult(entry("Home", 51.5, 7.4), isFavorite = true, isFavoriteHit = true)
        val native = MergedSearchResult(entry("Home Street", 52.0, 8.0), isFavorite = false, isFavoriteHit = false)
        launchDialog(listOf(favHit, native))

        val favBounds = composeRule.onNodeWithText("Home").fetchSemanticsNode().boundsInRoot
        val nativeBounds = composeRule.onNodeWithText("Home Street").fetchSemanticsNode().boundsInRoot
        assertTrue("favorite hit must be above native result", favBounds.top < nativeBounds.top)
    }

    @Test
    fun identicalFavoriteAndNativeResultShownOnce() {
        // Dedup happens in the merger; the dialog must render the single
        // favorite hit without a duplicate native row.
        val favHit = MergedSearchResult(entry("Home", 51.5, 7.4), isFavorite = true, isFavoriteHit = true)
        launchDialog(listOf(favHit))

        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onAllNodesWithText("Home").assertCountEquals(1)
    }
}
