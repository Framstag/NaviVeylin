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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The phone's two spoken-announcement switches (spec: `voice-guidance` — The phone offers both switches
 * in its settings sheet): a toggle reaches the state at once, is written to the shared settings file,
 * and a fresh view model over that file restores it. The phone's voice switch defaults to on, the
 * shared GPS switch to off.
 */
@RunWith(RobolectricTestRunner::class)
class LocationOptionsVoiceSwitchesTest {

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

    /**
     * Spec case: both switches are shown and persisted — the state the sheet renders and the file it
     * writes agree, and a restart reads the stored values back.
     */
    @Test
    fun bothSwitchesAreShownAndPersisted() = runTest(mainDispatcherRule.dispatcher) {
        // The defaults the spec names: voice on, GPS off.
        assertTrue(viewModel.uiState.value.voiceGuidanceEnabled)
        assertFalse(viewModel.uiState.value.announceGpsState)

        viewModel.onToggleVoiceGuidance(false)
        viewModel.onToggleAnnounceGpsState(true)
        settle()

        assertFalse(viewModel.uiState.value.voiceGuidanceEnabled)
        assertTrue(viewModel.uiState.value.announceGpsState)
        assertFalse(storage.load().voiceGuidanceEnabled)
        assertTrue(storage.load().announceGpsState)

        val restarted = newViewModel()
        settle()
        try {
            assertFalse(restarted.uiState.value.voiceGuidanceEnabled)
            assertTrue(restarted.uiState.value.announceGpsState)
        } finally {
            restarted.cancelScopeForTest()
        }
    }

    /** The two switches are separate values: one is flipped without touching the other. */
    @Test
    fun oneSwitchLeavesTheOtherAlone() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onToggleAnnounceGpsState(true)
        settle()

        assertTrue(storage.load().announceGpsState)
        assertTrue("the phone's voice switch keeps its default", storage.load().voiceGuidanceEnabled)
        assertTrue(viewModel.uiState.value.voiceGuidanceEnabled)
    }
}
