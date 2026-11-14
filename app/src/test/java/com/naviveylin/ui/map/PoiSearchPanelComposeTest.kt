package com.naviveylin.ui.map

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToIndex
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.PoiCategories
import com.framstag.libosmscout.client.PoiEntry
import com.naviveylin.core.search.SearchReference
import com.naviveylin.core.search.filterPoiEntries
import com.naviveylin.test.ComposeSandboxRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider

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

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()

    private var selectedCategory by mutableStateOf<String?>(null)
    private var clickedEntries = mutableListOf<PoiEntry>()
    private var searchCount = 0

    /** The filter term the panel was last handed, and every term it asked the state to apply. */
    private var filterTerm by mutableStateOf("")
    private var filterChanges = mutableListOf<String>()

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
        error: String? = null,
        filter: String = "",
        candidates: List<PoiEntry> = emptyList()
    ) {
        selectedCategory = category
        filterTerm = filter
        filterChanges = mutableListOf()
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
                distanceReference = SearchReference(51.5136, 7.4653),
                historyEntries = emptyList(),
                favoriteGroups = emptyMap(),
                onQueryChanged = {},
                onResultSelected = {},
                onSelectCurrentLocation = {},
                onSelectFavorite = {},
                onHistoryEntrySelected = {},
                poiCategory = selectedCategory,
                poiRadiusMeters = 5000.0,
                // The panel renders what the state hands it: with candidates given, the displayed list is
                // the phone state's own derivation (spec: `poi-search` — A name filter narrows a POI
                // result list), so a case drives the field and reads what a user would see.
                poiResults = if (candidates.isEmpty()) {
                    results
                } else {
                    filterPoiEntries(
                        candidates, filterTerm, MapCanvasViewModel.MAX_POI_RESULTS
                    )
                },
                poiFilter = filterTerm,
                poiCandidatesEmpty = candidates.isEmpty(),
                onPoiFilterChanged = {
                    filterTerm = it
                    filterChanges.add(it)
                },
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
                isDarkPresentation = false,
                contactsContent = {}
            )
        }
        composeRule.waitForIdle()
    }

    /** The category dropdown field (tagged: the filter field is the other editable one). */
    private fun categoryField() =
        composeRule.onNode(hasSetTextAction() and hasTestTag("poi_category_field"))

    /** The name filter field beside the category dropdown and the radius slider. */
    private fun filterField() = composeRule.onNodeWithTag("poi_filter_field")

    /** A category row inside the dropdown menu (not the field text). */
    private fun categoryItem(name: String) =
        composeRule.onNode(hasText(name) and hasAnyAncestor(hasTestTag("poi_category_menu")))

    @Test
    fun theFilterFieldIsOfferedInPoisMode() {
        launchDialog()

        // Beside the category dropdown and the radius slider, not instead of either of them
        // (spec: `search-dialog` — POIs mode offers a name filter beside category and radius).
        filterField().assertIsDisplayed()
        categoryField().assertIsDisplayed()
        composeRule.onNodeWithText("Search").assertIsDisplayed()
    }

    @Test
    fun anEmptyFilterShowsEveryCandidate() {
        val candidates = listOf(
            poiEntry("REWE Markt", "shop_supermarket", 100.0),
            poiEntry("Bäckerei Schmitt", "shop_bakery", 200.0)
        )
        launchDialog(category = PoiCategories.GROCERY, filter = "", candidates = candidates)

        // The list is a lazy column: the first row is on screen, the second is one scroll away.
        composeRule.onNodeWithText("REWE Markt").assertIsDisplayed()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        composeRule.onNodeWithText("Bäckerei Schmitt").assertIsDisplayed()
    }

    @Test
    fun aFilterWithNoMatchSaysSo() {
        launchDialog(
            category = PoiCategories.GROCERY,
            filter = "aldi",
            candidates = listOf(poiEntry("REWE Markt", "shop_supermarket", 100.0))
        )

        composeRule.onNodeWithText(noMatch("aldi")).assertIsDisplayed()
        composeRule.onNodeWithText("No POIs found").assertDoesNotExist()
    }

    @Test
    fun noPoisInRangeReadsDifferentlyFromNoMatch() {
        launchDialog(category = PoiCategories.GROCERY, filter = "aldi", candidates = emptyList())

        composeRule.onNodeWithText("No POIs found").assertIsDisplayed()
        composeRule.onNodeWithText(noMatch("aldi")).assertDoesNotExist()
    }

    @Test
    fun typingAFilterEnablesNothing() {
        launchDialog()

        filterField().performTextInput("rewe")

        composeRule.onNodeWithText("Search").assertIsNotEnabled()
        assertEquals("the field is handed to the state", "rewe", filterChanges.last())
        assertEquals("and runs no search", 0, searchCount)
    }

    @Test
    fun aFilterTypedBeforeTheSearchAppliesToIt() {
        launchDialog(
            category = PoiCategories.GROCERY,
            candidates = listOf(
                poiEntry("REWE Markt", "shop_supermarket", 100.0),
                poiEntry("Bäckerei Schmitt", "shop_bakery", 200.0)
            )
        )

        filterField().performTextInput("rewe")
        composeRule.onNodeWithText("Search").performClick()

        assertEquals("the filter is in the state before the search runs", "rewe", filterChanges.last())
        assertEquals(1, searchCount)
        composeRule.onNodeWithText("REWE Markt").assertIsDisplayed()
        composeRule.onNodeWithText("Bäckerei Schmitt").assertDoesNotExist()
    }

    @Test
    fun clearingTheFilterRestoresTheList() {
        launchDialog(
            category = PoiCategories.GROCERY,
            filter = "rewe",
            candidates = listOf(
                poiEntry("REWE Markt", "shop_supermarket", 100.0),
                poiEntry("Bäckerei Schmitt", "shop_bakery", 200.0)
            )
        )
        composeRule.onNodeWithText("Bäckerei Schmitt").assertDoesNotExist()

        filterField().performTextReplacement("")

        // Both rows are back: the first on screen, the second one scroll away.
        composeRule.onNodeWithText("REWE Markt").assertIsDisplayed()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        composeRule.onNodeWithText("Bäckerei Schmitt").assertIsDisplayed()
        assertEquals("clearing runs no search", 0, searchCount)
    }

    /** The no-match sentence the panel words for [term], from the resource both variants share. */
    private fun noMatch(term: String): String =
        ApplicationProvider.getApplicationContext<Context>()
            .getString(com.naviveylin.core.R.string.poi_filter_no_match, term)

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
                    distanceReference = SearchReference(51.5136, 7.4653),
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
                    isDarkPresentation = false,
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
                    isDarkPresentation = false,
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
                    distanceReference = SearchReference(51.5136, 7.4653),
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
                    isDarkPresentation = false,
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
                    isDarkPresentation = false,
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
