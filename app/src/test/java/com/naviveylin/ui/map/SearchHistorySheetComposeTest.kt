package com.naviveylin.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.naviveylin.data.SearchHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the search dialog's recent-search suggestions
 * (spec: search-history — chips): entries render as chips (youngest first),
 * tapping a chip reports its search text, and the section is hidden when the
 * history is empty.
 */
@RunWith(RobolectricTestRunner::class)
class SearchHistorySheetComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val older = SearchHistoryEntry(text = "Café Central", timestamp = 1000L)
    private val newer = SearchHistoryEntry(text = "Dortmund Hbf", timestamp = 2000L)

    private fun launchDialog(historyEntries: List<SearchHistoryEntry>, onHistoryEntrySelected: (String) -> Unit = {}) {
        composeRule.setContent {
            SearchDialog(
                searchMode = SearchMode.PLACES,
                onModeSelected = {},
                onDismiss = {},
                query = "",
                results = emptyList(),
                isSearching = false,
                gpsAvailable = false,
                adminRegionName = null,
                centerLat = 51.5136,
                centerLon = 7.4653,
                historyEntries = historyEntries,
                favoriteGroups = emptyMap(),
                onQueryChanged = {},
                onResultSelected = {},
                onSelectCurrentLocation = {},
                onSelectFavorite = {},
                onHistoryEntrySelected = onHistoryEntrySelected,
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
    fun entriesRenderInOrder() {
        launchDialog(listOf(newer, older))
        composeRule.onNodeWithText("Dortmund Hbf").assertIsDisplayed()
        composeRule.onNodeWithText("Café Central").assertIsDisplayed()
    }

    @Test
    fun tapEntryReportsSearchText() {
        var selected: String? = null
        launchDialog(listOf(newer, older), onHistoryEntrySelected = { selected = it })
        composeRule.onNodeWithText("Café Central").performClick()
        assertEquals("Café Central", selected)
    }

    @Test
    fun emptyStateHidesRecentSection() {
        launchDialog(emptyList())
        composeRule.onNodeWithText("Recent searches").assertDoesNotExist()
    }
}
