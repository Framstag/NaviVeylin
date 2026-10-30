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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The phone's routing-avoidance switches (spec: `routing-avoidance` — Avoidance is a persisted
 * per-vehicle-independent setting; spec: `routing-avoidance` — The phone edits the choices and the car
 * reads them): a toggle reaches the view model's state at once and is written to the shared settings
 * file, and a fresh view model over that file restores it.
 *
 * The values are read by every routing calculation, so losing one would silently return the route the
 * user asked to avoid.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelAvoidanceTest {

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
        // value set by a case.
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
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

    /** Spec: `routing-avoidance` — Avoidance is a persisted per-vehicle-independent setting. */
    @Test
    fun allThreeChoicesStartOff() = runTest(mainDispatcherRule.dispatcher) {
        assertFalse(viewModel.uiState.value.avoidTolls)
        assertFalse(viewModel.uiState.value.avoidFerries)
        assertFalse(viewModel.uiState.value.avoidUnpaved)
    }

    /** Spec: `routing-avoidance` — The phone edits the choices and the car reads them. */
    @Test
    fun togglingOneChoicePersistsItAndLeavesTheOthers() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSetAvoidFerries(true)
        settle()

        assertTrue(viewModel.uiState.value.avoidFerries)
        assertFalse(viewModel.uiState.value.avoidTolls)
        assertFalse(viewModel.uiState.value.avoidUnpaved)
        assertTrue(storage.load().avoidFerries)
        assertFalse(storage.load().avoidTolls)
    }

    /** Spec: `routing-avoidance` — a stored choice survives a restart. */
    @Test
    fun aChoiceSurvivesARestart() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSetAvoidTolls(true)
        viewModel.onSetAvoidUnpaved(true)
        settle()

        val restarted = newViewModel()
        settle()
        try {
            assertTrue(restarted.uiState.value.avoidTolls)
            assertFalse(restarted.uiState.value.avoidFerries)
            assertTrue(restarted.uiState.value.avoidUnpaved)
        } finally {
            restarted.cancelScopeForTest()
        }
    }

    /** A choice can be turned off again, and the file follows. */
    @Test
    fun aChoiceCanBeTurnedOffAgain() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSetAvoidTolls(true)
        settle()
        viewModel.onSetAvoidTolls(false)
        settle()

        assertFalse(viewModel.uiState.value.avoidTolls)
        assertFalse(storage.load().avoidTolls)
    }
}
