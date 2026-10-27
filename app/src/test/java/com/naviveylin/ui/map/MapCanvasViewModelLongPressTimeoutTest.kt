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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The phone's long-press duration (spec: `map-long-press-timeout` — The long-press timeout is a
 * persisted setting; spec: `location-options-ui` — Long-press timeout control): a change reaches the
 * canvas state at once — which is what the map's gesture handler draws its timeout from — and is
 * written to the shared settings file, and a fresh view model over that file restores it. A value
 * outside the offered range is clamped before it is stored, so the sheet and the handler never
 * disagree about what the setting can be.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelLongPressTimeoutTest {

    private lateinit var context: Context
    private lateinit var storage: SettingsStorage
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        // Test-controlled storage: the write path (load → save) must run on the test scheduler, or
        // the persisted value races the reload below.
        storage = SettingsStorage(context).also { it.ioDispatcher = mainDispatcherRule.dispatcher }
        viewModel = newViewModel()
        // Settle the init settings-load before mutating, so its async copy cannot clobber the
        // value set by a test.
        settle()
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private fun newViewModel(): MapCanvasViewModel = MapCanvasViewModel(
        viewportStorage = ViewportStorage(context),
        settingsStorage = storage,
        assetCopier = AssetCopier(context),
        client = client,
        favoriteRepository = FavoriteRepository(client),
        searchHistoryRepository = SearchHistoryRepository(context).also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
        locationService = LocationService(context),
        darkModeController = DarkModeController(storage),
        sharedLocationHandler = SharedLocationHandler(),
        basemapReloadNotifier = BasemapReloadNotifier(),
        context = context
    ).also { it.defaultDispatcher = mainDispatcherRule.dispatcher }

    private fun settle() {
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    /** Spec: `map-long-press-timeout` — A change applies immediately and persists. */
    @Test
    fun longPressTimeoutAppliesAndPersists() = runTest(mainDispatcherRule.dispatcher) {
        assertEquals(500, viewModel.uiState.value.longPressTimeoutMs)

        viewModel.onSetLongPressTimeoutMs(900)
        settle()

        assertEquals(900, viewModel.uiState.value.longPressTimeoutMs)
        assertEquals(900, storage.load().longPressTimeoutMs)

        val restarted = newViewModel()
        settle()
        try {
            assertEquals(
                "a fresh view model restores the persisted long-press timeout",
                900,
                restarted.uiState.value.longPressTimeoutMs
            )
        } finally {
            restarted.cancelScopeForTest()
        }
    }

    /** Spec: `map-long-press-timeout` — A value outside the offered range is clamped. */
    @Test
    fun aValueOutsideTheRangeIsClampedBeforeItIsStored() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSetLongPressTimeoutMs(40)
        settle()
        assertEquals(250, viewModel.uiState.value.longPressTimeoutMs)
        assertEquals(250, storage.load().longPressTimeoutMs)

        viewModel.onSetLongPressTimeoutMs(4000)
        settle()
        assertEquals(1000, viewModel.uiState.value.longPressTimeoutMs)
        assertEquals(1000, storage.load().longPressTimeoutMs)
    }
}
