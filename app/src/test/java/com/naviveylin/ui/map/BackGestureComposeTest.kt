package com.naviveylin.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.ui.favorites.FavoritesSheet
import com.naviveylin.ui.favorites.FavoritesViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog

/**
 * Compose tests for system back gesture/button handling:
 * back closes the favorites sheet on the main grid, returns to the main grid
 * from a group detail sub-screen, and dismisses the search panel.
 * No @Config — must run in the default Robolectric sandbox so the
 * FakeOSMScoutClient JNI stub loads correctly.
 */
@RunWith(RobolectricTestRunner::class)
class BackGestureComposeTest {

    @get:Rule
    // createAndroidComposeRule (not createComposeRule) so the test can reach the
    // activity's OnBackPressedDispatcher to dispatch system back events.
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
    }

    /** ModalBottomSheet renders in its own Dialog window with its own dispatcher. */
    private fun pressBackInDialog() {
        val dialog = ShadowDialog.getLatestDialog() as androidx.activity.ComponentDialog
        dialog.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
    }

    private fun launchFavoritesSheet(onDismiss: () -> Unit): FavoritesViewModel {
        val viewModel = FavoritesViewModel(FavoriteRepository(FakeOSMScoutClient()))
        composeRule.setContent {
            FavoritesSheet(
                mapCenterLat = 51.5136,
                mapCenterLon = 7.4653,
                onDismiss = onDismiss,
                viewModel = viewModel
            )
        }
        composeRule.waitForIdle()
        return viewModel
    }

    @Test
    fun backClosesFavoritesSheetOnMainGrid() {
        var dismissed = false
        launchFavoritesSheet(onDismiss = { dismissed = true })
        composeRule.onNodeWithText("Favorites").assertIsDisplayed()

        pressBack()

        assertTrue("back on main grid must close the sheet", dismissed)
    }

    @Test
    fun backFromGroupDetailReturnsToMainGrid() {
        var dismissed = false
        val viewModel = launchFavoritesSheet(onDismiss = { dismissed = true })

        // Enter the group detail sub-screen
        viewModel.selectGroup("Group1")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Group1").assertIsDisplayed()

        pressBack()

        assertFalse("back from detail must not close the sheet", dismissed)
        composeRule.onNodeWithText("Favorites").assertIsDisplayed()
    }

    @Test
    fun backClosesSearchDialog() {
        var dismissed = false
        composeRule.setContent {
            SearchDialog(
                searchMode = SearchMode.PLACES,
                onModeSelected = {},
                onDismiss = { dismissed = true },
                query = "",
                results = emptyList(),
                isSearching = false,
                gpsAvailable = true,
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
        // Let the SearchBar expansion animation finish so its back handler
        // is enabled.
        composeRule.mainClock.advanceTimeBy(2000)

        pressBack()

        assertTrue("back must dismiss the search dialog", dismissed)
    }
}
