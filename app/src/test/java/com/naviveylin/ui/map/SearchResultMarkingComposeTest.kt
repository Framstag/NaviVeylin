package com.naviveylin.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.search.MergedSearchResult
import com.naviveylin.core.search.SearchReference
import com.naviveylin.data.SearchHistoryEntry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the perfect-match marking in the search dialog's Places
 * results (spec: search-result-ranking — "Perfect-match marking and
 * cross-surface parity", location-search — "Result item display"): a perfect
 * match is marked, and a row that is both a favorite and a perfect match shows
 * both facts (composite marking) rather than one replacing the other.
 */
@RunWith(RobolectricTestRunner::class)
class SearchResultMarkingComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(label: String): LocationEntry = LocationEntry().apply {
        this.label = label
        this.lat = 51.5
        this.lon = 7.4
        this.matchedName = label
        this.matchedComponent = "location"
        this.locationMatchQuality = "match"
    }

    private fun launchDialog(results: List<MergedSearchResult>) {
        composeRule.setContent {
            SearchDialog(
                searchMode = SearchMode.PLACES,
                onModeSelected = {},
                onDismiss = {},
                query = "Waltro",
                results = results,
                isSearching = false,
                gpsAvailable = false,
                adminRegionName = null,
                distanceReference = SearchReference(51.5136, 7.4653),
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

    private fun row(label: String, isFavorite: Boolean, isPerfect: Boolean): MergedSearchResult =
        MergedSearchResult(
            entry(label),
            isFavorite = isFavorite,
            isFavoriteHit = false,
            isPerfectMatch = isPerfect
        )

    @Test
    fun perfectMatchIsMarkedAndExposesTheFactToAccessibility() {
        launchDialog(listOf(row("Waltrop", isFavorite = false, isPerfect = true)))

        composeRule.onNodeWithContentDescription("Exact match").assertIsDisplayed()
        composeRule.onNodeWithText("Waltrop").assertIsDisplayed()
    }

    @Test
    fun closeMatchIsNotMarkedAsPerfect() {
        launchDialog(listOf(row("Waltroper Straße", isFavorite = false, isPerfect = false)))

        composeRule.onNodeWithContentDescription("Exact match").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Exact match and favorite").assertDoesNotExist()
    }

    @Test
    fun favoriteOnlyRowKeepsTheFavoriteMarking() {
        launchDialog(listOf(row("Home", isFavorite = true, isPerfect = false)))

        composeRule.onNodeWithContentDescription("Favorite").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Exact match and favorite").assertDoesNotExist()
    }

    @Test
    fun favoriteAndPerfectRowShowsBothFacts() {
        // A native result that matches a favorite and is the exact answer.
        launchDialog(listOf(row("Waltrop", isFavorite = true, isPerfect = true)))

        composeRule.onNodeWithContentDescription("Exact match and favorite").assertIsDisplayed()
        // The composite marking replaces the two single markings, it does not stack them.
        composeRule.onNodeWithContentDescription("Favorite").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Exact match").assertDoesNotExist()
    }

    @Test
    fun perfectMatchRowIsListedAboveCloseMatches() {
        // Ordering comes from the ranker (asserted in the ViewModel tests); this
        // pins that the marked row is the one shown first.
        val perfect = row("Waltrop", isFavorite = false, isPerfect = true)
        val close = row("Waltroper Straße", isFavorite = false, isPerfect = false)
        launchDialog(listOf(perfect, close))

        val perfectBounds = composeRule.onNodeWithText("Waltrop").fetchSemanticsNode().boundsInRoot
        val closeBounds = composeRule.onNodeWithText("Waltroper Straße").fetchSemanticsNode().boundsInRoot
        assertTrue("perfect match must be first", perfectBounds.top < closeBounds.top)
    }
}
