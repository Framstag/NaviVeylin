package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.data.AppSettings
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
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

/**
 * Verifies the symbol/icon preference of the map: it reaches the native client as a change of
 * client state (no stylesheet reload), it is persisted, it is applied at startup before the first
 * map display, and a change discards the map tiles rendered under the previous preference
 * (spec: `map-styles` — Icon-versus-symbol preference is a persisted phone setting; Preference is
 * applied at start and on change).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SymbolIconPreferenceTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        viewModel = makeViewModel(client)
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private fun makeViewModel(client: FakeOSMScoutClient): MapCanvasViewModel {
        val settingsStorage = SettingsStorage(context)
        // The settings read must complete on the same virtual clock the test advances, or
        // `advanceUntilIdle` cannot see the start-up application of the value.
        settingsStorage.ioDispatcher = mainDispatcherRule.dispatcher
        val viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = settingsStorage,
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
        // The renderer's own scope runs the debounced render; on its default dispatcher the virtual
        // clock cannot drive it.
        viewModel.rendererDispatcher = mainDispatcherRule.dispatcher
        return viewModel
    }

    /** Writes the settings file directly, so the start-up load reads the value under test. */
    private fun writeSettings(settings: AppSettings) {
        val file = java.io.File(context.filesDir, "maps/settings.json")
        file.parentFile?.mkdirs()
        file.writeText(
            kotlinx.serialization.json.Json.encodeToString(AppSettings.serializer(), settings)
        )
    }

    @Test
    fun aChangeReachesTheClientOnceAndIsPersisted() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSetPreferSymbolPoiIcons(true)
        advanceUntilIdle()

        assertEquals(listOf(true), client.preferSymbolIconsCalls)
        assertTrue(viewModel.uiState.value.preferSymbolPoiIcons)
        assertTrue("the setting is persisted", SettingsStorage(context).load().preferSymbolPoiIcons)
    }

    @Test
    fun eachChangeIsPushedOnce() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSetPreferSymbolPoiIcons(true)
        viewModel.onSetPreferSymbolPoiIcons(true)
        viewModel.onSetPreferSymbolPoiIcons(false)
        advanceUntilIdle()

        assertEquals("one push per value change, not per call", listOf(true, false),
                     client.preferSymbolIconsCalls)
    }

    @Test
    fun theDefaultValueIssuesNoNativeCall() = runTest(mainDispatcherRule.dispatcher) {
        advanceUntilIdle()

        assertTrue("the client default already is the icon-first rendering",
                   client.preferSymbolIconsCalls.isEmpty())
        assertFalse(viewModel.uiState.value.preferSymbolPoiIcons)
    }

    @Test
    fun thePersistedValueIsAppliedAtStartup() = runTest(mainDispatcherRule.dispatcher) {
        writeSettings(AppSettings(preferSymbolPoiIcons = true))

        val startupClient = FakeOSMScoutClient()
        val startupViewModel = makeViewModel(startupClient)
        try {
            advanceUntilIdle()

            assertEquals(listOf(true), startupClient.preferSymbolIconsCalls)
            assertTrue(startupViewModel.uiState.value.preferSymbolPoiIcons)
        } finally {
            startupViewModel.cancelScopeForTest()
        }
    }

    @Test
    fun aChangeDiscardsTheTilesRenderedWithThePreviousPreference() =
        runTest(mainDispatcherRule.dispatcher) {
            // The renderer is injected (test seam), so the invalidation is exercised without
            // opening a map database: the debounced render runs on the shared test dispatcher.
            val rendererScope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher)
            val renderer = MapRenderer(client, 320.0, rendererScope)
            renderer.screenWidth = 1200
            renderer.screenHeight = 1200
            viewModel.setMapRendererForTest(renderer)

            try {
                // Warm the tile cache at one viewport.
                renderer.requestRender(51.5, 7.5, 14.0, 0.0)
                advanceUntilIdle()

                val tilesBefore = client.renderIntoCount.get() +
                    client.renderWithRouteAndPoisCount.get()
                check(tilesBefore >= 1) {
                    "the warm-up frame rendered no tile: into=${client.renderIntoCount.get()} " +
                        "tiles=${client.renderWithRouteAndPoisCount.get()}"
                }

                viewModel.onSetPreferSymbolPoiIcons(true)
                advanceUntilIdle()

                val tilesAfter = client.renderIntoCount.get() +
                    client.renderWithRouteAndPoisCount.get()
                assertTrue(
                    "a cleared cache must be refilled, tiles $tilesBefore -> $tilesAfter",
                    tilesAfter > tilesBefore
                )
                assertEquals(listOf(true), client.preferSymbolIconsCalls)
            } finally {
                renderer.shutdown()
                rendererScope.cancel()
            }
        }
}
