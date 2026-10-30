package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * A viewport save without a map key writes no file
 * (spec: viewport-persist — Save viewport state, scenario "Save without a map key is skipped").
 *
 * The removed fallback was the literal `"default"`: a save before a map was known wrote
 * `viewport-default.json`, which no load path reads (issue #54, residual half of §9). The decision is to
 * skip such a save. `saveViewport`'s own save is gated by `viewportRestored`, and a real restore always
 * sets the key four lines earlier, so the state this case builds (`viewportRestored` true, no key) is
 * not reachable on a device: it guards the accessor both save paths read. The reachable-in-a-test
 * path is the navigation-end save, which has no restore guard;
 * [MapCanvasViewModelNavEndRestoreTest] carries that case.
 *
 * Robolectric with the default sandbox so the `FakeOSMScoutClient` JNI stub loads (AGENTS.md
 * classloader rule).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelViewportPreInitSaveTest {

    private lateinit var context: Context
    private lateinit var viewportStorage: ViewportStorage
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Files the save paths wrote under `filesDir/maps`, empty when the directory does not exist. */
    private fun viewportFiles(): List<String> =
        File(context.filesDir, "maps").listFiles()?.map { it.name }.orEmpty()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val client = FakeOSMScoutClient()
        viewportStorage = ViewportStorage(context).also { it.ioDispatcher = mainDispatcherRule.dispatcher }
        viewModel = MapCanvasViewModel(
            viewportStorage = viewportStorage,
            settingsStorage = SettingsStorage(context).also { it.ioDispatcher = mainDispatcherRule.dispatcher },
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context)
                .also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
            locationService = LocationService(context),
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel.rendererDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    @Test
    fun `a lifecycle save without a map key writes no viewport file`() =
        runTest(mainDispatcherRule.dispatcher) {
            // The restore guard is not the behaviour under test: hold it open as a completed restore
            // would, and leave the map key unknown — the state a save before initMap sees.
            viewModel.viewportRestored = true
            viewModel.currentMapKey = null

            viewModel.saveViewport()
            advanceUntilIdle()

            println("viewportPreInit files=[${viewportFiles()}]")
            assertFalse(
                "a save with no map key must not write viewport-default.json",
                File(context.filesDir, "maps/viewport-default.json").exists()
            )
            assertEquals(
                "a save with no map key must leave filesDir/maps empty",
                0,
                viewportFiles().size
            )
        }
}
