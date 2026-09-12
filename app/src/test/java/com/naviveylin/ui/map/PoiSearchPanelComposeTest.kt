package com.naviveylin.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.PoiCategories
import com.framstag.libosmscout.client.PoiEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the search dialog's POIs mode (spec: poi-search,
 * search-dialog — POIs mode search flow): no category preselected, Search
 * disabled until a category chip is chosen, the shared search field filters
 * the category chips, re-selecting the chosen chip clears it, single click
 * opens details, and the Route/Show actions close both the dialog and the
 * details sheet.
 */
@RunWith(RobolectricTestRunner::class)
class PoiSearchPanelComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var selectedCategory by mutableStateOf<String?>(null)
    private var clickedEntries = mutableListOf<PoiEntry>()
    private var searchCount = 0

    private fun poiEntry(label: String, objectType: String, distance: Double): PoiEntry =
        PoiEntry().apply {
            this.label = label
            this.objectType = objectType
            this.distance = distance
            lat = 51.5136
            lon = 7.4653
        }

    private fun poiEntry(
        label: String,
        objectType: String,
        distance: Double,
        operator: String?,
        brand: String?
    ): PoiEntry =
        poiEntry(label, objectType, distance).apply {
            this.operator = operator
            this.brand = brand
        }

    private fun launchDialog(
        category: String? = null,
        results: List<PoiEntry> = emptyList(),
        isSearching: Boolean = false,
        error: String? = null
    ) {
        selectedCategory = category
        composeRule.setContent {
            SearchDialog(
                searchMode = SearchMode.POIS,
                onModeSelected = {},
                onDismiss = {},
                query = "",
                results = emptyList(),
                isSearching = false,
                gpsAvailable = false,
                adminRegionName = null,
                centerLat = 51.5136,
                centerLon = 7.4653,
                historyEntries = emptyList(),
                favoriteGroups = emptyMap(),
                onQueryChanged = {},
                onResultSelected = {},
                onSelectCurrentLocation = {},
                onSelectFavorite = {},
                onHistoryEntrySelected = {},
                poiCategory = selectedCategory,
                poiRadiusMeters = 5000.0,
                poiResults = results,
                isPoiSearching = isSearching,
                poiError = error,
                client = null,
                poiCenterLat = Double.NaN,
                poiCenterLon = Double.NaN,
                currentPosition = null,
                selectedPoi = null,
                onPoiCategorySelected = { selectedCategory = it },
                onPoiRadiusChanged = {},
                onPoiSearch = { searchCount++ },
                onPoiEntryClick = { clickedEntries.add(it) },
                addressBookAvailable = false,
                contactsQuery = "",
                onContactsQueryChanged = {},
                contactsContent = {}
            )
        }
        composeRule.waitForIdle()
    }

    /** The category dropdown field (the only editable field in POIs mode). */
    private fun categoryField() = composeRule.onNode(hasSetTextAction())

    /** A category row inside the dropdown menu (not the field text). */
    private fun categoryItem(name: String) =
        composeRule.onNode(hasText(name) and hasAnyAncestor(hasTestTag("poi_category_menu")))

    @Test
    fun noCategoryPreselected() {
        launchDialog()

        // No category label in the field and the search trigger is disabled
        composeRule.onNodeWithText("Hotels").assertDoesNotExist()
        composeRule.onNodeWithText("Search").assertIsNotEnabled()
    }

    @Test
    fun searchDisabledUntilCategoryChosen() {
        launchDialog()

        composeRule.onNodeWithText("Search").assertIsNotEnabled()

        categoryField().performClick()
        categoryItem("Hotels").performClick()
        composeRule.onNodeWithText("Search").assertIsEnabled()
        assertEquals(PoiCategories.HOTELS, selectedCategory)
    }

    @Test
    fun typingFilterNarrowsCategories() {
        launchDialog()

        categoryField().performClick()
        categoryField().performTextInput("Rest")

        categoryItem("Restaurants").assertIsDisplayed()
        composeRule.onNode(hasText("Hotels") and hasAnyAncestor(hasTestTag("poi_category_menu")))
            .assertDoesNotExist()
    }

    @Test
    fun filterNoMatchShowsNoCategories() {
        launchDialog()

        categoryField().performClick()
        categoryField().performTextInput("zzz")

        composeRule.onNodeWithText("No matching categories").assertIsDisplayed()
        assertEquals(null, selectedCategory)
        composeRule.onNodeWithText("Search").assertIsNotEnabled()
    }

    @Test
    fun reselectingCategoryClearsSelection() {
        launchDialog()

        categoryField().performClick()
        categoryItem("Hotels").performClick()
        assertEquals(PoiCategories.HOTELS, selectedCategory)
        composeRule.onNodeWithText("Search").assertIsEnabled()

        // Selecting the already-selected category clears it
        categoryField().performClick()
        categoryItem("Hotels").performClick()
        assertEquals(null, selectedCategory)
        composeRule.onNodeWithText("Search").assertIsNotEnabled()
    }

    @Test
    fun singleClickOpensDetails() {
        val entry = poiEntry("Hotel Central", "tourism_hotel", 1200.0)
        launchDialog(category = PoiCategories.HOTELS, results = listOf(entry))

        composeRule.onNodeWithText("Hotel Central").performClick()
        assertEquals(listOf(entry), clickedEntries)
    }

    @Test
    fun resultRowShowsTypeAndDistance() {
        launchDialog(category = PoiCategories.HOTELS, results = listOf(poiEntry("Hotel Central", "tourism_hotel", 1200.0)))

        composeRule.onNodeWithText("tourism_hotel · 1.2 km").assertIsDisplayed()
    }

    @Test
    fun emptyStateShownWhenNoResults() {
        launchDialog(category = PoiCategories.HOTELS, results = emptyList())

        composeRule.onNodeWithText("No POIs found").assertIsDisplayed()
    }

    @Test
    fun routeActionClosesBothSheets() {
        val entry = poiEntry("Hotel Central", "tourism_hotel", 1200.0)
        val locEntry = LocationEntry().apply {
            label = entry.label
            lat = entry.lat
            lon = entry.lon
        }
        var showDialog by mutableStateOf(true)
        var showDetails by mutableStateOf(false)
        var routeInvoked = false

        composeRule.setContent {
            if (showDialog) {
                SearchDialog(
                    searchMode = SearchMode.POIS,
                    onModeSelected = {},
                    onDismiss = { showDialog = false },
                    query = "",
                    results = emptyList(),
                    isSearching = false,
                    gpsAvailable = false,
                    adminRegionName = null,
                    centerLat = 51.5136,
                    centerLon = 7.4653,
                    historyEntries = emptyList(),
                    favoriteGroups = emptyMap(),
                    onQueryChanged = {},
                    onResultSelected = {},
                    onSelectCurrentLocation = {},
                    onSelectFavorite = {},
                    onHistoryEntrySelected = {},
                    poiCategory = PoiCategories.HOTELS,
                    poiRadiusMeters = 5000.0,
                    poiResults = listOf(entry),
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
                    onPoiEntryClick = {
                        showDialog = false
                        showDetails = true
                    },
                    addressBookAvailable = false,
                    contactsQuery = "",
                    onContactsQueryChanged = {},
                    contactsContent = {}
                )
            }
            if (showDetails) {
                LocationDetailsDialog(
                    entry = locEntry,
                    client = FakeOSMScoutClient(),
                    initialMag = 12.0,
                    isFavorite = false,
                    groupNames = emptyList(),
                    onAddToFavorites = { _, _, _ -> },
                    onRemoveFromFavorites = {},
                    onRouteToLocation = {
                        routeInvoked = true
                        showDetails = false
                    },
                    onDismiss = { showDetails = false }
                )
            }
        }

        composeRule.onNodeWithText("Hotel Central").performClick()
        composeRule.onNodeWithText("Calculate route").performClick()

        assertTrue(routeInvoked)
        assertFalse(showDialog)
        composeRule.onNodeWithText("Calculate route").assertDoesNotExist()
    }

    @Test
    fun showActionClosesDetailsAndKeepsDialogClosed() {
        val entry = poiEntry("Hotel Central", "tourism_hotel", 1200.0)
        val locEntry = LocationEntry().apply {
            label = entry.label
            lat = entry.lat
            lon = entry.lon
        }
        var showDialog by mutableStateOf(true)
        var showDetails by mutableStateOf(false)
        var showInvoked = false

        composeRule.setContent {
            if (showDialog) {
                SearchDialog(
                    searchMode = SearchMode.POIS,
                    onModeSelected = {},
                    onDismiss = { showDialog = false },
                    query = "",
                    results = emptyList(),
                    isSearching = false,
                    gpsAvailable = false,
                    adminRegionName = null,
                    centerLat = 51.5136,
                    centerLon = 7.4653,
                    historyEntries = emptyList(),
                    favoriteGroups = emptyMap(),
                    onQueryChanged = {},
                    onResultSelected = {},
                    onSelectCurrentLocation = {},
                    onSelectFavorite = {},
                    onHistoryEntrySelected = {},
                    poiCategory = PoiCategories.HOTELS,
                    poiRadiusMeters = 5000.0,
                    poiResults = listOf(entry),
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
                    onPoiEntryClick = {
                        showDialog = false
                        showDetails = true
                    },
                    addressBookAvailable = false,
                    contactsQuery = "",
                    onContactsQueryChanged = {},
                    contactsContent = {}
                )
            }
            if (showDetails) {
                LocationDetailsDialog(
                    entry = locEntry,
                    client = FakeOSMScoutClient(),
                    initialMag = 12.0,
                    isFavorite = false,
                    groupNames = emptyList(),
                    onAddToFavorites = { _, _, _ -> },
                    onRemoveFromFavorites = {},
                    onRouteToLocation = null,
                    onShowOnMap = {
                        showInvoked = true
                        showDetails = false
                    },
                    onDismiss = { showDetails = false }
                )
            }
        }

        composeRule.onNodeWithText("Hotel Central").performClick()
        composeRule.onNodeWithText("Show").assertIsDisplayed()
        composeRule.onNodeWithText("Show").performClick()

        assertTrue(showInvoked)
        assertFalse(showDialog)
    }

    // --- POI result label composition (spec: poi-search — POI results list) ---

    @Test
    fun resultLabelShowsNameAndBrand() {
        val entry = poiEntry("Tankstelle", "amenity_fuel", 500.0, operator = "Shell GmbH", brand = "Shell")
        launchDialog(category = PoiCategories.FUEL, results = listOf(entry))

        composeRule.onNodeWithText("Tankstelle (Shell)").assertIsDisplayed()
    }

    @Test
    fun resultLabelShowsNameAndOperatorWithoutBrand() {
        val entry = poiEntry("Filiale Mitte", "amenity_atm", 300.0, operator = "Sparkasse", brand = null)
        launchDialog(category = PoiCategories.ATM, results = listOf(entry))

        composeRule.onNodeWithText("Filiale Mitte (Sparkasse)").assertIsDisplayed()
    }

    @Test
    fun resultLabelPrefersBrandOverOperator() {
        val entry = poiEntry("Tankstelle", "amenity_fuel", 500.0, operator = "Shell GmbH", brand = "Shell")
        launchDialog(category = PoiCategories.FUEL, results = listOf(entry))

        composeRule.onNodeWithText("Tankstelle (Shell)").assertIsDisplayed()
        composeRule.onNodeWithText("Tankstelle (Shell GmbH)").assertDoesNotExist()
    }

    @Test
    fun resultLabelShowsBrandAloneWhenUnnamed() {
        val entry = poiEntry("", "amenity_fast_food", 200.0, operator = "McDonald's Deutschland", brand = "McDonald's")
        launchDialog(category = PoiCategories.RESTAURANTS, results = listOf(entry))

        composeRule.onNodeWithText("McDonald's").assertIsDisplayed()
    }

    @Test
    fun resultLabelShowsOperatorAloneWhenUnnamedAndNoBrand() {
        val entry = poiEntry("", "amenity_atm", 150.0, operator = "Sparkasse", brand = null)
        launchDialog(category = PoiCategories.ATM, results = listOf(entry))

        composeRule.onNodeWithText("Sparkasse").assertIsDisplayed()
    }

    @Test
    fun resultLabelDoesNotDuplicateNameEqualToBrand() {
        val entry = poiEntry("Shell", "amenity_fuel", 500.0, operator = null, brand = "Shell")
        launchDialog(category = PoiCategories.FUEL, results = listOf(entry))

        composeRule.onNodeWithText("Shell").assertIsDisplayed()
        composeRule.onNodeWithText("Shell (Shell)").assertDoesNotExist()
    }

    @Test
    fun resultLabelDoesNotDuplicateNameEqualToOperator() {
        val entry = poiEntry("Sparkasse", "amenity_atm", 300.0, operator = "Sparkasse", brand = null)
        launchDialog(category = PoiCategories.ATM, results = listOf(entry))

        composeRule.onNodeWithText("Sparkasse").assertIsDisplayed()
        composeRule.onNodeWithText("Sparkasse (Sparkasse)").assertDoesNotExist()
    }

    @Test
    fun resultLabelShowsUnnamedWhenNothingAvailable() {
        val entry = poiEntry("", "amenity_atm", 100.0, operator = null, brand = null)
        launchDialog(category = PoiCategories.ATM, results = listOf(entry))

        composeRule.onNodeWithText("(unnamed)").assertIsDisplayed()
    }

    @Test
    fun resultLabelShowsDifferingOperatorWhenNameEqualsBrand() {
        // name == brand suppresses the brand parenthetical, but a differing
        // operator is still shown (it is additional info, not a duplicate).
        val entry = poiEntry("Sparda-Bank", "amenity_atm", 300.0, operator = "Sparda-Bank West eG", brand = "Sparda-Bank")
        launchDialog(category = PoiCategories.ATM, results = listOf(entry))

        composeRule.onNodeWithText("Sparda-Bank (Sparda-Bank West eG)").assertIsDisplayed()
        composeRule.onNodeWithText("Sparda-Bank (Sparda-Bank)").assertDoesNotExist()
    }

    @Test
    fun resultLabelShowsDifferingBrandWhenNameEqualsOperator() {
        // name == operator suppresses the operator parenthetical, but a
        // differing brand is still shown.
        val entry = poiEntry("Shell", "amenity_fuel", 500.0, operator = "Shell", brand = "Shell GmbH")
        launchDialog(category = PoiCategories.FUEL, results = listOf(entry))

        composeRule.onNodeWithText("Shell (Shell GmbH)").assertIsDisplayed()
        composeRule.onNodeWithText("Shell (Shell)").assertDoesNotExist()
    }

    // --- Empty results and search failure states (spec: poi-search) ---

    @Test
    fun emptyResultsShowsEmptyState() {
        launchDialog(category = PoiCategories.ATM, results = emptyList())

        composeRule.onNodeWithText("No POIs found").assertIsDisplayed()
    }

    @Test
    fun searchFailureShowsErrorAndKeepsDialogUsable() {
        launchDialog(category = PoiCategories.ATM, error = "POI search failed")

        composeRule.onNodeWithText("POI search failed").assertIsDisplayed()
        // The dialog stays usable: the search trigger remains enabled.
        composeRule.onNodeWithText("Search").assertIsEnabled()
    }
}
