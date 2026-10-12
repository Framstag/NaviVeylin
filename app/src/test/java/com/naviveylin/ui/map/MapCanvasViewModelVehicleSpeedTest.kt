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
 * The phone's per-vehicle usual maximum speed (spec: `routing-max-speed` — A per-vehicle maximum
 * speed is a persisted setting; spec: `location-options-ui` — A slider change persists
 * immediately): a change reaches the view model's state at once and is written to the shared
 * settings file, and a fresh view model over that file restores it. The value is read by every
 * routing calculation for that vehicle, so losing or cross-writing it would change routes silently.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelVehicleSpeedTest {

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
        searchHistoryRepository = SearchHistoryRepository(context),
        locationService = LocationService(context),
        darkModeController = DarkModeController(storage),
        sharedLocationHandler = SharedLocationHandler(),
        basemapReloadNotifier = BasemapReloadNotifier(),
        context = context
    ).also { it.defaultDispatcher = mainDispatcherRule.dispatcher }

    private fun settle() {
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    /** Spec: `routing-max-speed` — A per-vehicle maximum speed is a persisted setting (defaults). */
    @Test
    fun defaultsAreRestoredFromSettings() = runTest(mainDispatcherRule.dispatcher) {
        assertEquals(120, viewModel.uiState.value.carMaxSpeedKmh)
        assertEquals(15, viewModel.uiState.value.bicycleMaxSpeedKmh)
        assertEquals(5, viewModel.uiState.value.footMaxSpeedKmh)
    }

    /** Spec: `location-options-ui` — A slider change persists immediately. */
    @Test
    fun movingABicycleSliderPersistsTheValue() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSetBicycleMaxSpeed(20)
        settle()

        assertEquals(20, viewModel.uiState.value.bicycleMaxSpeedKmh)
        // One slider writes its own field: the other two keep their values.
        assertEquals(120, viewModel.uiState.value.carMaxSpeedKmh)
        assertEquals(5, viewModel.uiState.value.footMaxSpeedKmh)
        assertEquals(20, storage.load().bicycleMaxSpeedKmh)
        assertEquals(120, storage.load().carMaxSpeedKmh)
    }

    /** Spec: `location-options-ui` — A slider change survives a restart. */
    @Test
    fun aSliderChangeSurvivesARestart() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSetCarMaxSpeed(90)
        viewModel.onSetBicycleMaxSpeed(20)
        viewModel.onSetFootMaxSpeed(4)
        settle()

        val restarted = newViewModel()
        settle()
        try {
            assertEquals(90, restarted.uiState.value.carMaxSpeedKmh)
            assertEquals(20, restarted.uiState.value.bicycleMaxSpeedKmh)
            assertEquals(4, restarted.uiState.value.footMaxSpeedKmh)
        } finally {
            restarted.cancelScopeForTest()
        }
    }
}
