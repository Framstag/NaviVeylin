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
 * Compose UI tests for the POI search sheet (spec: poi-search): no category
 * preselected, Search disabled until a category is chosen, the searchable
 * category dropdown filters by typed text, re-selecting the chosen category
 * clears it, single click opens details, and closing the details sheet via
 * the Route action closes both the details sheet and the POI sheet.
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

    private fun launchPanel(
        category: String? = null,
        results: List<PoiEntry> = emptyList(),
        isSearching: Boolean = false,
        error: String? = null
    ) {
        selectedCategory = category
        composeRule.setContent {
            PoiSearchPanel(
                category = selectedCategory,
                radiusMeters = 5000.0,
                results = results,
                isSearching = isSearching,
                error = error,
                onCategorySelected = { selectedCategory = it },
                onRadiusChanged = {},
                onSearch = { searchCount++ },
                onEntryClick = { clickedEntries.add(it) },
                onDismiss = {}
            )
        }
    }

    /** A category row inside the dropdown menu (not the field text). */
    private fun categoryItem(name: String) =
        composeRule.onNode(hasText(name) and hasAnyAncestor(hasTestTag("poi_category_menu")))

    /** The editable category filter field. */
    private fun categoryField() = composeRule.onNode(hasSetTextAction())

    @Test
    fun noCategoryPreselected() {
        launchPanel()

        // No category label in the field and the search trigger is disabled
        composeRule.onNodeWithText("Hotels").assertDoesNotExist()
        composeRule.onNodeWithText("Search").assertIsNotEnabled()
    }

    @Test
    fun searchDisabledUntilCategoryChosen() {
        launchPanel()

        composeRule.onNodeWithText("Search").assertIsNotEnabled()

        categoryField().performClick()
        categoryItem("Hotels").performClick()
        composeRule.onNodeWithText("Search").assertIsEnabled()
        assertEquals(PoiCategories.HOTELS, selectedCategory)
    }

    @Test
    fun typingFilterNarrowsCategories() {
        launchPanel()

        categoryField().performClick()
        categoryField().performTextInput("Rest")

        categoryItem("Restaurants").assertIsDisplayed()
        composeRule.onNode(hasText("Hotels") and hasAnyAncestor(hasTestTag("poi_category_menu")))
            .assertDoesNotExist()
    }

    @Test
    fun filterNoMatchShowsNoCategories() {
        launchPanel()

        categoryField().performClick()
        categoryField().performTextInput("zzz")

        composeRule.onNodeWithText("No matching categories").assertIsDisplayed()
        assertEquals(null, selectedCategory)
        composeRule.onNodeWithText("Search").assertIsNotEnabled()
    }

    @Test
    fun reselectingCategoryClearsSelection() {
        launchPanel()

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
        launchPanel(category = PoiCategories.HOTELS, results = listOf(entry))

        composeRule.onNodeWithText("Hotel Central").performClick()
        assertEquals(listOf(entry), clickedEntries)
    }

    @Test
    fun resultRowShowsTypeAndDistance() {
        launchPanel(category = PoiCategories.HOTELS, results = listOf(poiEntry("Hotel Central", "tourism_hotel", 1200.0)))

        composeRule.onNodeWithText("tourism_hotel · 1.2 km").assertIsDisplayed()
    }

    @Test
    fun emptyStateShownWhenNoResults() {
        launchPanel(category = PoiCategories.HOTELS, results = emptyList())

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
        var showPoi by mutableStateOf(true)
        var showDetails by mutableStateOf(false)
        var routeInvoked = false

        composeRule.setContent {
            if (showPoi) {
                PoiSearchPanel(
                    category = PoiCategories.HOTELS,
                    radiusMeters = 5000.0,
                    results = listOf(entry),
                    isSearching = false,
                    error = null,
                    onCategorySelected = {},
                    onRadiusChanged = {},
                    onSearch = {},
                    onEntryClick = {
                        showPoi = false
                        showDetails = true
                    },
                    onDismiss = { showPoi = false }
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
        composeRule.onNodeWithText("Search POIs").assertDoesNotExist()
        composeRule.onNodeWithText("Calculate route").assertDoesNotExist()
    }

    @Test
    fun showActionClosesDetailsAndKeepsPoiClosed() {
        val entry = poiEntry("Hotel Central", "tourism_hotel", 1200.0)
        val locEntry = LocationEntry().apply {
            label = entry.label
            lat = entry.lat
            lon = entry.lon
        }
        var showPoi by mutableStateOf(true)
        var showDetails by mutableStateOf(false)
        var showInvoked = false

        composeRule.setContent {
            if (showPoi) {
                PoiSearchPanel(
                    category = PoiCategories.HOTELS,
                    radiusMeters = 5000.0,
                    results = listOf(entry),
                    isSearching = false,
                    error = null,
                    onCategorySelected = {},
                    onRadiusChanged = {},
                    onSearch = {},
                    onEntryClick = {
                        showPoi = false
                        showDetails = true
                    },
                    onDismiss = { showPoi = false }
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
        assertFalse(showPoi)
        composeRule.onNodeWithText("Search POIs").assertDoesNotExist()
    }

    // --- POI result label composition (spec: poi-search — POI results list) ---

    @Test
    fun resultLabelShowsNameAndBrand() {
        val entry = poiEntry("Tankstelle", "amenity_fuel", 500.0, operator = "Shell GmbH", brand = "Shell")
        launchPanel(category = PoiCategories.FUEL, results = listOf(entry))

        composeRule.onNodeWithText("Tankstelle (Shell)").assertIsDisplayed()
    }

    @Test
    fun resultLabelShowsNameAndOperatorWithoutBrand() {
        val entry = poiEntry("Filiale Mitte", "amenity_atm", 300.0, operator = "Sparkasse", brand = null)
        launchPanel(category = PoiCategories.ATM, results = listOf(entry))

        composeRule.onNodeWithText("Filiale Mitte (Sparkasse)").assertIsDisplayed()
    }

    @Test
    fun resultLabelPrefersBrandOverOperator() {
        val entry = poiEntry("Tankstelle", "amenity_fuel", 500.0, operator = "Shell GmbH", brand = "Shell")
        launchPanel(category = PoiCategories.FUEL, results = listOf(entry))

        composeRule.onNodeWithText("Tankstelle (Shell)").assertIsDisplayed()
        composeRule.onNodeWithText("Tankstelle (Shell GmbH)").assertDoesNotExist()
    }

    @Test
    fun resultLabelShowsBrandAloneWhenUnnamed() {
        val entry = poiEntry("", "amenity_fast_food", 200.0, operator = "McDonald's Deutschland", brand = "McDonald's")
        launchPanel(category = PoiCategories.RESTAURANTS, results = listOf(entry))

        composeRule.onNodeWithText("McDonald's").assertIsDisplayed()
    }

    @Test
    fun resultLabelShowsOperatorAloneWhenUnnamedAndNoBrand() {
        val entry = poiEntry("", "amenity_atm", 150.0, operator = "Sparkasse", brand = null)
        launchPanel(category = PoiCategories.ATM, results = listOf(entry))

        composeRule.onNodeWithText("Sparkasse").assertIsDisplayed()
    }

    @Test
    fun resultLabelDoesNotDuplicateNameEqualToBrand() {
        val entry = poiEntry("Shell", "amenity_fuel", 500.0, operator = null, brand = "Shell")
        launchPanel(category = PoiCategories.FUEL, results = listOf(entry))

        composeRule.onNodeWithText("Shell").assertIsDisplayed()
        composeRule.onNodeWithText("Shell (Shell)").assertDoesNotExist()
    }

    @Test
    fun resultLabelDoesNotDuplicateNameEqualToOperator() {
        val entry = poiEntry("Sparkasse", "amenity_atm", 300.0, operator = "Sparkasse", brand = null)
        launchPanel(category = PoiCategories.ATM, results = listOf(entry))

        composeRule.onNodeWithText("Sparkasse").assertIsDisplayed()
        composeRule.onNodeWithText("Sparkasse (Sparkasse)").assertDoesNotExist()
    }

    @Test
    fun resultLabelShowsUnnamedWhenNothingAvailable() {
        val entry = poiEntry("", "amenity_atm", 100.0, operator = null, brand = null)
        launchPanel(category = PoiCategories.ATM, results = listOf(entry))

        composeRule.onNodeWithText("(unnamed)").assertIsDisplayed()
    }

    @Test
    fun resultLabelShowsDifferingOperatorWhenNameEqualsBrand() {
        // name == brand suppresses the brand parenthetical, but a differing
        // operator is still shown (it is additional info, not a duplicate).
        val entry = poiEntry("Sparda-Bank", "amenity_atm", 300.0, operator = "Sparda-Bank West eG", brand = "Sparda-Bank")
        launchPanel(category = PoiCategories.ATM, results = listOf(entry))

        composeRule.onNodeWithText("Sparda-Bank (Sparda-Bank West eG)").assertIsDisplayed()
        composeRule.onNodeWithText("Sparda-Bank (Sparda-Bank)").assertDoesNotExist()
    }

    @Test
    fun resultLabelShowsDifferingBrandWhenNameEqualsOperator() {
        // name == operator suppresses the operator parenthetical, but a
        // differing brand is still shown.
        val entry = poiEntry("Shell", "amenity_fuel", 500.0, operator = "Shell", brand = "Shell GmbH")
        launchPanel(category = PoiCategories.FUEL, results = listOf(entry))

        composeRule.onNodeWithText("Shell (Shell GmbH)").assertIsDisplayed()
        composeRule.onNodeWithText("Shell (Shell)").assertDoesNotExist()
    }

    // --- Empty results and search failure states (spec: poi-search) ---

    @Test
    fun emptyResultsShowsEmptyState() {
        launchPanel(category = PoiCategories.ATM, results = emptyList())

        composeRule.onNodeWithText("No POIs found").assertIsDisplayed()
    }

    @Test
    fun searchFailureShowsErrorAndKeepsSheetUsable() {
        launchPanel(category = PoiCategories.ATM, error = "POI search failed")

        composeRule.onNodeWithText("POI search failed").assertIsDisplayed()
        // The sheet stays usable: the search trigger remains enabled.
        composeRule.onNodeWithText("Search").assertIsEnabled()
    }
}
