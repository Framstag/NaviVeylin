package com.naviveylin.ui.map

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.search.SearchReference
import com.naviveylin.core.search.SearchResultRanker
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Verifies the phone search wiring of the match-tier ranking (spec:
 * search-result-ranking, location-search): the candidate set is larger than the
 * displayed list, a perfect match the backend's own order had pushed past the
 * page still reaches the display list, and the reference point the list was
 * ranked against is published for the row distances.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelSearchRankingTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient()
        locationService = LocationService(context)
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = locationService,
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

    /** The exact answer to the query: its name matched completely. */
    private fun exactEntry(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            matchedName = label
            matchedComponent = "location"
            locationMatchQuality = "match"
            this.lat = lat
            this.lon = lon
        }

    /** A near miss: the name matched only as a prefix of the entry's name. */
    private fun prefixEntry(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            matchedName = label
            matchedComponent = "location"
            locationMatchQuality = "candidate"
            this.lat = lat
            this.lon = lon
        }

    @Test
    fun perfectMatchBeyondTheBackendPageIsDisplayedFirst() = runTest(mainDispatcherRule.dispatcher) {
        // 25 close matches first, the exact answer last — the shape of the
        // reported bug (a page of "Waltroper Straße", "Waltrop" at the end).
        val close = (1..25).map { prefixEntry("Waltroper Straße $it", 51.5, 7.4) }
        client.nextSearchResults = (close + exactEntry("Waltrop", 51.6, 7.5)).toTypedArray()

        val results = viewModel.mergeSearchResults("Waltrop")

        assertEquals("Waltrop", results.first().entry.label)
        assertTrue("the exact match is marked as perfect", results.first().isPerfectMatch)
        assertTrue(results.size <= SearchResultRanker.DISPLAY_LIMIT)
    }

    @Test
    fun displayedListIsCappedAtTheDisplayLimit() = runTest(mainDispatcherRule.dispatcher) {
        client.nextSearchResults = (1..30)
            .map { exactEntry("Waltrop $it", 51.5, 7.4) }
            .toTypedArray()

        val results = viewModel.mergeSearchResults("Waltrop")

        assertEquals(SearchResultRanker.DISPLAY_LIMIT, results.size)
    }

    @Test
    fun rankingUsesTheLastGpsFixAsReference() = runTest(mainDispatcherRule.dispatcher) {
        locationService.setLocationForTest(
            Location("gps").apply {
                latitude = 51.5
                longitude = 7.4
                accuracy = 8f
                time = 1_000L
            }
        )
        viewModel.uiState.first { it.gpsLocation != null }
        client.nextSearchResults = arrayOf(exactEntry("Waltrop", 51.6, 7.5))

        viewModel.mergeSearchResults("Waltrop")

        assertEquals(SearchReference(51.5, 7.4), viewModel.uiState.value.searchReference)
    }

    @Test
    fun rankingFallsBackToTheMapCenterWithoutAFix() = runTest(mainDispatcherRule.dispatcher) {
        client.nextSearchResults = arrayOf(exactEntry("Waltrop", 51.6, 7.5))
        val center = viewModel.uiState.value.viewport

        viewModel.mergeSearchResults("Waltrop")

        assertEquals(
            SearchReference(center.centerLat, center.centerLon),
            viewModel.uiState.value.searchReference
        )
    }

    @Test
    fun orderFollowsTheFixNotThePannedMapCenter() = runTest(mainDispatcherRule.dispatcher) {
        val nearFix = exactEntry("Waltrop Nord", 51.5001, 7.4)
        val nearCenter = exactEntry("Waltrop Sued", 52.0, 8.0)
        client.nextSearchResults = arrayOf(nearCenter, nearFix)
        locationService.setLocationForTest(
            Location("gps").apply {
                latitude = 51.5
                longitude = 7.4
                accuracy = 8f
                time = 1_000L
            }
        )
        viewModel.uiState.first { it.gpsLocation != null }

        // Pan the map so the other entry is the nearer one.
        viewModel.updateCenter(52.0, 8.0)
        assertEquals(52.0, viewModel.uiState.value.viewport.centerLat, 1e-9)

        val withFix = viewModel.mergeSearchResults("Waltrop").map { it.entry.label }

        assertEquals(
            "a fix makes the order independent of the panned map center",
            listOf("Waltrop Nord", "Waltrop Sued"),
            withFix
        )
    }

    @Test
    fun favoriteHitsStayAboveThePerfectNativeMatch() = runTest(mainDispatcherRule.dispatcher) {
        // favorite-search wins over the tier order (spec: favorite-search).
        val favorites = FavoriteRepository(client)
        favorites.defaultDispatcher = mainDispatcherRule.dispatcher
        favorites.init(context.filesDir.absolutePath + "/fav-ranking-test.json")
        favorites.addGroup("Home")
        favorites.addFavorite("Home", "Waltrop", 52.0, 8.0)
        viewModel = rebuildViewModel(favorites)
        client.nextSearchResults = arrayOf(exactEntry("Waltrop", 51.6, 7.5))

        val results = viewModel.mergeSearchResults("Waltrop")

        assertTrue(results.first().isFavoriteHit)
        assertTrue(results[1].isPerfectMatch)
    }

    private fun rebuildViewModel(favorites: FavoriteRepository): MapCanvasViewModel =
        MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = favorites,
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = locationService,
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        ).also { it.defaultDispatcher = mainDispatcherRule.dispatcher }
}
