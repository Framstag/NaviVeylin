package com.naviveylin.ui.map

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
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the details-sheet "Add to Favorites" flow: creating a brand-new
 * group succeeds, and a "new group" name that already exists shows an error
 * instead of silently adding the favorite.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelFavAddTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private var viewModel: MapCanvasViewModel? = null

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
    }

    @After
    fun tearDown() {
        viewModel?.cancelScopeForTest()
        viewModel = null
    }

    private suspend fun createViewModel(): Pair<MapCanvasViewModel, FavoriteRepository> {
        val favRepo = FavoriteRepository(client)
        favRepo.defaultDispatcher = mainDispatcherRule.dispatcher
        favRepo.init(context.filesDir.absolutePath + "/fav-add-test.json")
        val vm = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = favRepo,
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            darkModeController = DarkModeController(SettingsStorage(context)),
            context = context
        )
        vm.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel = vm
        return vm to favRepo
    }

    private suspend fun selectLocation(viewModel: MapCanvasViewModel) {
        viewModel.onLongPress(51.5, 7.4)
        viewModel.uiState.first { it.selectedLocation != null }
    }

    @Test
    fun addToFavoritesWithNewGroupCreatesGroupAndFavorite() = runTest(mainDispatcherRule.dispatcher) {
        val (viewModel, favRepo) = createViewModel()
        selectLocation(viewModel)

        viewModel.addSelectedToFavorites("NewGroup", "My Place", isNewGroup = true)

        viewModel.uiState.first { it.snackbarMessage != null }
        assertEquals("Added to NewGroup", viewModel.uiState.value.snackbarMessage)
        val groups = favRepo.favorites.value
        assertEquals(listOf("NewGroup"), groups.keys.toList())
        assertEquals("My Place", groups["NewGroup"]?.first()?.name)
    }

    @Test
    fun addToFavoritesWithDuplicateNewGroupNameShowsError() = runTest(mainDispatcherRule.dispatcher) {
        val (viewModel, favRepo) = createViewModel()
        assertTrue(favRepo.addGroup("Home"))
        selectLocation(viewModel)

        viewModel.addSelectedToFavorites("Home", "Dupe", isNewGroup = true)

        viewModel.uiState.first { it.snackbarMessage != null }
        assertEquals("Group \"Home\" already exists", viewModel.uiState.value.snackbarMessage)
        assertTrue(favRepo.favorites.value["Home"].isNullOrEmpty())
    }
}
