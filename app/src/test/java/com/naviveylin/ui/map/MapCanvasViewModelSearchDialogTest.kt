package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ViewModel tests for the unified search dialog state (spec: search-dialog —
 * entry points, mode switch): open resets to Places mode, mode switching
 * preserves per-mode state, entering POIs mode resets POI state, and close
 * restores the POI viewport snapshot.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelSearchDialogTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    @Test
    fun openSearchOpensDialogInPlacesMode() {
        viewModel.openSearch()

        val state = viewModel.uiState.value
        assertTrue(state.searchOpen)
        assertEquals(SearchMode.PLACES, state.searchMode)
    }

    @Test
    fun closeSearchClosesDialog() {
        viewModel.openSearch()
        viewModel.setSearchMode(SearchMode.POIS)

        viewModel.closeSearch()

        assertFalse(viewModel.uiState.value.searchOpen)
    }

    @Test
    fun openSearchResetsToPlacesMode() {
        viewModel.openSearch()
        viewModel.setSearchMode(SearchMode.POIS)
        viewModel.closeSearch()

        viewModel.openSearch()

        val state = viewModel.uiState.value
        assertTrue(state.searchOpen)
        assertEquals(SearchMode.PLACES, state.searchMode)
    }

    @Test
    fun enteringPoisModeResetsPoiState() {
        viewModel.openSearch()
        viewModel.setSearchMode(SearchMode.POIS)
        viewModel.onPoiCategorySelected("hotels")
        viewModel.setSearchMode(SearchMode.PLACES)

        // Re-enter POIs mode: fresh state, no category, no results.
        viewModel.setSearchMode(SearchMode.POIS)

        val state = viewModel.uiState.value
        assertEquals(SearchMode.POIS, state.searchMode)
        assertNull("no category preselected", state.poiCategory)
        assertTrue("no preloaded results", state.poiResults.isEmpty())
    }

    @Test
    fun modeSwitchPreservesPlacesQuery() {
        viewModel.openSearch()
        viewModel.onSearchQueryChanged("Dortmund Hbf")

        viewModel.setSearchMode(SearchMode.POIS)
        viewModel.setSearchMode(SearchMode.PLACES)

        assertEquals("Dortmund Hbf", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun closeSearchInPoisModeRestoresViewport() {
        viewModel.updateCenter(52.0, 8.0)
        val magBefore = viewModel.uiState.value.viewport.magnification
        viewModel.openSearch()
        viewModel.setSearchMode(SearchMode.POIS)
        // Move the map while in POIs mode (e.g. a POI selection centered it).
        viewModel.updateCenter(51.5, 7.4)

        viewModel.closeSearch()

        val state = viewModel.uiState.value
        assertFalse(state.searchOpen)
        assertEquals("center restored", 52.0, state.viewport.centerLat, 1e-9)
        assertEquals("center restored", 8.0, state.viewport.centerLon, 1e-9)
        assertEquals("zoom restored", magBefore, state.viewport.magnification, 1e-9)
    }
}
