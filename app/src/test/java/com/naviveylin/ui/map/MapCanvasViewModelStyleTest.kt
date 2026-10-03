package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.NativeTileDataCache

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
import org.junit.Assert.assertNull
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
            listOf(NativeTileDataCache.PHONE_TILES),
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
            listOf(NativeTileDataCache.PHONE_TILES),
            client.nativeDataCacheSizes
        )

        basemapReloadNotifier.bump()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "basemap reload must not re-configure the cache from Kotlin",
            listOf(NativeTileDataCache.PHONE_TILES),
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

    @Test
    fun failedSwitchReportsTheKeptStyleOnce() = runTest(mainDispatcherRule.dispatcher) {
        // The stylesheet of the requested style cannot be parsed; the native
        // client keeps "standard.oss" active (spec: map-styles — "Unparsable
        // stylesheet keeps current style" / "Failure is reported once per
        // attempt").
        client.failingStyleSheetLoads.add("cycle")
        client.activeStyleSheetName = "standard.oss"

        viewModel.onStyleSheetSelected("cycle")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "the failure names the requested style and the one still in effect",
            "Map style \"cycle\" could not be loaded — still using \"standard.oss\"",
            viewModel.uiState.value.snackbarMessage
        )
        assertEquals(
            "one load attempt for the requested style",
            1,
            client.styleSheetLoads.count { it == "cycle" }
        )

        // The report is consumed once: clearing it leaves no repeated message.
        viewModel.clearSnackbar()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun persistedStyleFailsAtStartupFallsBackToTheDefaultStyle() =
        runTest(mainDispatcherRule.dispatcher) {
            // Persisted style is unparsable: the first map display must not be
            // empty, so the default style is loaded (spec: map-styles —
            // "Persisted style fails at startup") and the failure is reported.
            settingsStorage.save(AppSettings(styleSheet = "cycle"))
            viewModel = createViewModel()
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            client.failingStyleSheetLoads.add("cycle")
            // Nothing else has loaded yet, so the client reports the configured
            // (persisted) style as the active one — the message then does not
            // claim a different style is in effect.
            client.activeStyleSheetName = "cycle"
            viewModel.setScreenSize(100, 100)
            viewModel.initMap("/data/maps/testmap")
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            assertTrue(
                "the default style must be loaded as the startup fallback",
                client.styleSheetLoads.contains(BundledMapStyles.DEFAULT_STYLE_NAME)
            )
            assertEquals(
                "one report for the failed persisted style, none for the fallback",
                "Map style \"cycle\" could not be loaded",
                viewModel.uiState.value.snackbarMessage
            )
        }

    @Test
    fun failedStyleFlagReloadIsReported() = runTest(mainDispatcherRule.dispatcher) {
        // A style-flag (daylight/night) change reloads the active stylesheet;
        // when that reload is rejected the previous variant stays and the
        // failure is reported (spec: map-styles — "Style flag change fails").
        client.activeStyleSheetName = "winter-sports.oss"
        client.styleLoadSuccessful = false

        viewModel.setScreenSize(100, 100)
        viewModel.initMap("/data/maps/testmap")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "the flag failure is reported for the stylesheet that failed to reload",
            "Map style \"winter-sports.oss\" could not be loaded",
            viewModel.uiState.value.snackbarMessage
        )
    }

    @Test
    fun unchangedStyleReapplyPerformsNoLoad() = runTest(mainDispatcherRule.dispatcher) {
        // A second application of the style/presentation pair that is already installed must
        // perform no native call at all: no stylesheet load and no flag push, because both
        // native calls reload the whole style set (spec: map-styles — One stylesheet load per
        // active style and flag set; spec: dark-mode — Unchanged presentation reloads nothing;
        // TODO 116: the phone loaded the set ~13 times per start).
        viewModel.setScreenSize(100, 100)
        viewModel.initMap("/data/maps/testmap")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val loadsAfterStart = client.styleSheetLoads.size
        val flagsAfterStart = client.styleFlags.size

        // Re-selecting the active style re-applies the same pair.
        viewModel.onStyleSheetSelected(viewModel.uiState.value.styleSheet)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "an unchanged style must not be loaded again",
            loadsAfterStart,
            client.styleSheetLoads.size
        )
        assertEquals(
            "an unchanged presentation must not push the daylight flag again",
            flagsAfterStart,
            client.styleFlags.size
        )
    }

    @Test
    fun reSelectingTheActiveStylePerformsNoLoad() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onStyleSheetSelected("cycle")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStyleSheetSelected("cycle")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "re-selecting the active style loads it once in total",
            1,
            client.styleSheetLoads.count { it == "cycle" }
        )
    }

    @Test
    fun styleSwitchLoadsExactlyOnceWithoutAFlagPush() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.setScreenSize(100, 100)
        viewModel.initMap("/data/maps/testmap")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val flagsBeforeSwitch = client.styleFlags.size
        viewModel.onStyleSheetSelected("motorways")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "one load for the switch",
            1,
            client.styleSheetLoads.count { it == "motorways" }
        )
        assertEquals(
            "a switch to an installed pair must not force a second reload through the flag",
            flagsBeforeSwitch,
            client.styleFlags.size
        )
    }

    @Test
    fun oneStartupLoadsTheStyleSetOnce() = runTest(mainDispatcherRule.dispatcher) {
        // The bundled-asset refresh runs at app start (AssetCopier) before this apply, so a
        // refreshed copy is picked up by this single load; a later settings re-read adds
        // nothing (spec: map-styles — Refreshed bundled stylesheet loads again / Repeated
        // applies do not multiply loads per start).
        settingsStorage.save(AppSettings(styleSheet = "cycle"))
        viewModel = createViewModel()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.setScreenSize(100, 100)
        viewModel.initMap("/data/maps/testmap")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        settingsStorage.update { it.copy(autoZoomEnabled = !it.autoZoomEnabled) }
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "the started style is loaded exactly once",
            1,
            client.styleSheetLoads.count { it == "cycle" }
        )
        assertEquals(
            "one startup loads the stylesheet set once",
            1,
            client.styleSheetLoads.size
        )
    }

    @Test
    fun startupRePushesTheFlagAfterTheDatabaseOpens() = runTest(mainDispatcherRule.dispatcher) {
        // `SetStyleFlag` is a silent no-op until a database is open, so a push made before one
        // exists must be repeated once the database is ready (spec: dark-mode — Map darkens from
        // the start; `guidelines/MapRendering.md` section 15, startup race). The presentation is
        // awaited through the published uiState, so the pre-database push is already recorded.
        viewModel.setEnvironmentDark(true)
        assertTrue(viewModel.uiState.first { it.isDarkPresentation }.isDarkPresentation)
        val flagsBeforeInitMap = client.styleFlags.size
        assertTrue("a push happened before the database exists", flagsBeforeInitMap >= 1)
        assertEquals(
            "the pre-database push carries the dark variant",
            "daylight" to false,
            client.styleFlags.last()
        )

        viewModel.setScreenSize(100, 100)
        viewModel.initMap("/data/maps/testmap")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "the flag is re-pushed after the database opens",
            flagsBeforeInitMap + 1,
            client.styleFlags.size
        )
        assertEquals(
            "the re-push carries the resolved (dark) variant",
            "daylight" to false,
            client.styleFlags.last()
        )
    }
}
