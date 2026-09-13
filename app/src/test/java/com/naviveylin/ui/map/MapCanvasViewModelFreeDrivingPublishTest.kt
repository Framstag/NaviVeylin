package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.DrivingModeProvider
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.navigation.DrivingModeProviderImpl
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
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
 * Verifies the phone surface publishes its FREE_DRIVE vote into the shared
 * driving-state provider (task 1.3): follow on ⇒ true; follow off with drive
 * suspension still counts as FREE_DRIVE (map-modes derivation); explicit exit
 * to BROWSE clears the vote.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapCanvasViewModelFreeDrivingPublishTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var drivingModeProvider: DrivingModeProviderImpl
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        locationService = LocationService(context)
        drivingModeProvider = DrivingModeProviderImpl()
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
            basemapReloadNotifier = com.naviveylin.core.BasemapReloadNotifier(),
            drivingModeProvider = drivingModeProvider,
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    @Test
    fun startsBrowseInactive() = runTest(mainDispatcherRule.dispatcher) {
        advanceUntilIdle()
        assertFalse(drivingModeProvider.freeDrivingActive.value)
    }

    @Test
    fun followOnActivatesPhoneVote() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onToggleFollowMode(true)
        advanceUntilIdle()
        val freeDriving = drivingModeProvider.freeDrivingActive.first { it }
        assertTrue(freeDriving)
    }

    @Test
    fun manualPanSuspendsButStaysFreeDriving() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onToggleFollowMode(true)
        viewModel.onManualRotation(0.5)
        advanceUntilIdle()
        // FREE_DRIVE keeps the vote while driveSuspended (map-modes: follow
        // off + drive suspended ⇒ FREE_DRIVE).
        assertTrue(drivingModeProvider.freeDrivingActive.value)
    }

    @Test
    fun explicitExitToBrowseClearsVote() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onToggleFollowMode(true)
        advanceUntilIdle()
        assertTrue(drivingModeProvider.freeDrivingActive.first { it })

        viewModel.exitFreeDrive()
        advanceUntilIdle()
        assertFalse(drivingModeProvider.freeDrivingActive.first { !it })
    }
}
