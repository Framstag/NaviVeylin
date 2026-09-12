package com.naviveylin.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the search dialog's mode switch (spec: search-dialog —
 * mode switch): three segments with READ_CONTACTS, Contacts hidden without it,
 * and per-mode state preserved when switching modes.
 */
@RunWith(RobolectricTestRunner::class)
class SearchDialogModeSwitchComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var mode by mutableStateOf(SearchMode.PLACES)
    private var query by mutableStateOf("")

    private fun launchDialog(addressBookAvailable: Boolean) {
        mode = SearchMode.PLACES
        query = ""
        composeRule.setContent {
            SearchDialog(
                searchMode = mode,
                onModeSelected = { mode = it },
                onDismiss = {},
                query = query,
                results = emptyList(),
                isSearching = false,
                gpsAvailable = false,
                adminRegionName = null,
                centerLat = 51.5136,
                centerLon = 7.4653,
                historyEntries = emptyList(),
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
                addressBookAvailable = addressBookAvailable,
                contactsQuery = "",
                onContactsQueryChanged = {},
                contactsContent = {}
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun threeModesShownWithPermission() {
        launchDialog(addressBookAvailable = true)

        composeRule.onNodeWithText("Places").assertIsDisplayed()
        composeRule.onNodeWithText("POIs").assertIsDisplayed()
        composeRule.onNodeWithText("Contacts").assertIsDisplayed()
    }

    @Test
    fun contactsModeHiddenWithoutPermission() {
        launchDialog(addressBookAvailable = false)

        composeRule.onNodeWithText("Places").assertIsDisplayed()
        composeRule.onNodeWithText("POIs").assertIsDisplayed()
        composeRule.onNodeWithText("Contacts").assertDoesNotExist()
    }

    @Test
    fun modeSwitchPreservesPerModeState() {
        launchDialog(addressBookAvailable = true)

        // Type a query in Places mode, switch to POIs, switch back: the query
        // must still be present (per-mode state preserved for the session).
        composeRule.onNode(hasSetTextAction()).performTextInput("Dort")
        composeRule.onNodeWithText("POIs").performClick()
        composeRule.onNodeWithText("Places").performClick()

        assertEquals(SearchMode.PLACES, mode)
        assertEquals("Dort", query)
    }
}
