package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.data.AppSettings
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.core.BundledMapStyles
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

/**
 * Verifies that the selected map stylesheet is applied to the native client
 * after the database opens (initMap) and immediately when the user picks a
 * new style, and that a failed load does not wedge the retry path.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelStyleTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var settingsStorage: SettingsStorage
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var basemapReloadNotifier: BasemapReloadNotifier

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        settingsStorage = SettingsStorage(context)
        settingsStorage.ioDispatcher = mainDispatcherRule.dispatcher
        viewModel = createViewModel()
        // Settle init work (persisted-settings load applies its defaults to
        // uiState) so tests observe a stable starting state.
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    private fun createViewModel(): MapCanvasViewModel {
        val viewportStorage = ViewportStorage(context).also {
            it.ioDispatcher = mainDispatcherRule.dispatcher
        }
        val favoriteRepository = FavoriteRepository(client).also {
            it.defaultDispatcher = mainDispatcherRule.dispatcher
        }
        basemapReloadNotifier = BasemapReloadNotifier()
        val vm = MapCanvasViewModel(
            viewportStorage = viewportStorage,
            settingsStorage = settingsStorage,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = favoriteRepository,
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            darkModeController = DarkModeController(settingsStorage),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = basemapReloadNotifier,
            context = context
        )
        vm.defaultDispatcher = mainDispatcherRule.dispatcher
        return vm
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    @Test
    fun initMapAppliesPersistedStyleAfterDatabaseOpens() = runTest(mainDispatcherRule.dispatcher) {
        // Persist the selection before the ViewModel is built, so its init
        // settings-load observes it (the same order as a real app start).
        settingsStorage.save(AppSettings(styleSheet = "cycle"))
        viewModel = createViewModel()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.setScreenSize(100, 100)
        viewModel.initMap("/data/maps/testmap")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue("persisted style must reach loadStyleSheet", client.styleSheetLoads.contains("cycle"))
        assertTrue(client.openedDatabases.contains("/data/maps/testmap"))
        assertEquals("cycle", viewModel.uiState.value.styleSheet)
    }

    @Test
    fun initMapConfiguresNativeTileDataCacheAfterDatabaseOpens() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.setScreenSize(100, 100)

        viewModel.initMap("/data/maps/testmap")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue("database must have opened", client.openedDatabases.contains("/data/maps/testmap"))
        assertEquals(
            "native tile data cache capacity must be configured after a successful open",
            listOf(MapCanvasViewModel.NATIVE_TILE_DATA_CACHE_SIZE),
            client.nativeDataCacheSizes
        )
    }

    @Test
    fun initMapSkipsCacheConfigWhenDatabaseOpenFails() = runTest(mainDispatcherRule.dispatcher) {
        client.openDatabaseResult = false
        viewModel.setScreenSize(100, 100)

        viewModel.initMap("/data/maps/testmap")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue("cache config must not run when the database fails to open", client.nativeDataCacheSizes.isEmpty())
    }

    @Test
    fun basemapReloadKeepsSingleCacheConfigPoint() = runTest(mainDispatcherRule.dispatcher) {
        // Full lifecycle: initMap configures the native cache once. A basemap
        // change (download/update/delete) then invalidates the rendered tiles
        // via the notifier (renderer invalidation is covered by
        // MapRendererInvalidateDataTest), but must NOT re-configure the cache
        // from Kotlin — the C++ render job re-applies the stored size to every
        // open database INCLUDING the basemap on each render (design D1;
        // covers basemap-only viewports that never pass through the regional
        // loadDbData lambda). This pins the single application point:
        // removing the initMap call, or adding redundant Kotlin re-configuration
        // on basemap reload, breaks here.
        viewModel.setScreenSize(100, 100)
        viewModel.initMap("/data/maps/testmap")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "configured once after a successful open",
            listOf(MapCanvasViewModel.NATIVE_TILE_DATA_CACHE_SIZE),
            client.nativeDataCacheSizes
        )

        basemapReloadNotifier.bump()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "basemap reload must not re-configure the cache from Kotlin",
            listOf(MapCanvasViewModel.NATIVE_TILE_DATA_CACHE_SIZE),
            client.nativeDataCacheSizes
        )
    }

    @Test
    fun selectingStyleAppliesImmediately() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onStyleSheetSelected("cycle")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(client.styleSheetLoads.contains("cycle"))
        assertEquals("cycle", viewModel.uiState.first { it.styleSheet == "cycle" }.styleSheet)
    }

    @Test
    fun selectingStylePersists() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onStyleSheetSelected("winter-sports")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals("winter-sports", settingsStorage.load().styleSheet)
    }

    @Test
    fun availableStyleSheetsExposedInUiState() = runTest(mainDispatcherRule.dispatcher) {
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            BundledMapStyles.USER_SELECTABLE,
            viewModel.uiState.value.availableStyleSheets
        )
        // The basemap's internal stylesheet is never offered (spec: map-styles).
        assertTrue(
            "basemap-render must not be user-selectable",
            BundledMapStyles.BASEMAP_STYLE_NAME !in viewModel.uiState.value.availableStyleSheets
        )
    }

    @Test
    fun failedLoadKeepsPreviousStyleAndRetries() = runTest(mainDispatcherRule.dispatcher) {
        client.styleSheetLoadResult = false
        viewModel.onStyleSheetSelected("cycle")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertTrue("failed load must still be attempted", client.styleSheetLoads.contains("cycle"))

        // A later successful selection must not be skipped by a stale marker.
        client.styleSheetLoadResult = true
        viewModel.onStyleSheetSelected("motorways")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertTrue(client.styleSheetLoads.contains("motorways"))
    }
}
