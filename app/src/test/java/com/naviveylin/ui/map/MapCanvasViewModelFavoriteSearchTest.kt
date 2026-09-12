package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Verifies the favorite-search merge in [MapCanvasViewModel] (spec:
 * favorite-search): favorite hits are merged above native results, native
 * results near a favorite are marked, identical objects are deduplicated, and
 * selecting a favorite hit records the query in search history.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelFavoriteSearchTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var favRepo: FavoriteRepository
    private lateinit var historyRepo: SearchHistoryRepository
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient()
        favRepo = FavoriteRepository(client)
        favRepo.defaultDispatcher = mainDispatcherRule.dispatcher
        historyRepo = SearchHistoryRepository(context)
        historyRepo.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = favRepo,
            searchHistoryRepository = historyRepo,
            locationService = LocationService(context),
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private suspend fun seedFavorite(name: String, lat: Double, lon: Double) {
        favRepo.init(context.filesDir.absolutePath + "/fav-search-test.json")
        favRepo.addGroup("Home")
        favRepo.addFavorite("Home", name, lat, lon)
    }

    private fun nativeEntry(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.lat = lat
            this.lon = lon
            name = label
            matchQuality = "match"
        }

    @Test
    fun favoriteHitMergedAboveNativeResults() = runTest(mainDispatcherRule.dispatcher) {
        seedFavorite("Home Sweet Home", 51.5, 7.4)
        client.nextSearchResults = arrayOf(nativeEntry("Home Sweet Home", 52.0, 8.0))

        val results = viewModel.mergeSearchResults("sweet")

        assertEquals(2, results.size)
        assertTrue(results[0].isFavoriteHit)
        assertEquals("Home Sweet Home", results[0].entry.label)
        assertFalse(results[1].isFavoriteHit)
    }

    @Test
    fun nativeResultNearFavoriteIsMarked() = runTest(mainDispatcherRule.dispatcher) {
        seedFavorite("Home", 51.5, 7.4)
        // Query does not match the favorite name; native result sits at the
        // favorite's coordinates → kept (not a hit) and heart-marked.
        client.nextSearchResults = arrayOf(nativeEntry("Street", 51.5, 7.4))

        val results = viewModel.mergeSearchResults("street")

        assertEquals(1, results.size)
        assertFalse(results[0].isFavoriteHit)
        assertTrue(results[0].isFavorite)
    }

    @Test
    fun identicalFavoriteAndNativeResultDedupedToFavorite() = runTest(mainDispatcherRule.dispatcher) {
        seedFavorite("Home", 51.5, 7.4)
        client.nextSearchResults = arrayOf(nativeEntry("Home", 51.5, 7.4))

        val results = viewModel.mergeSearchResults("home")

        assertEquals(1, results.size)
        assertTrue(results[0].isFavoriteHit)
        assertEquals("Home", results[0].entry.label)
    }

    @Test
    fun shortQuerySkipsFavoriteSearch() = runTest(mainDispatcherRule.dispatcher) {
        seedFavorite("Home", 51.5, 7.4)
        client.nextSearchResults = arrayOf(nativeEntry("Home", 52.0, 8.0))

        val results = viewModel.mergeSearchResults("h")

        assertEquals(1, results.size)
        assertFalse(results[0].isFavoriteHit)
    }

    @Test
    fun selectingFavoriteHitRecordsHistory() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSearchQueryChanged("Home")
        val favHit = LocationEntry().apply {
            label = "Home"
            lat = 51.5
            lon = 7.4
            matchQuality = "favorite"
        }
        viewModel.onSearchResultSelected(favHit)

        val entries = historyRepo.history.first { it.isNotEmpty() }
        assertEquals(1, entries.size)
        assertEquals("Home", entries[0].text)
    }
}
