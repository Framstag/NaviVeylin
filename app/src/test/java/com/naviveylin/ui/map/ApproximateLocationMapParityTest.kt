package com.naviveylin.ui.map

import android.Manifest
import android.app.Application
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
import com.naviveylin.location.LocationConsumers
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The navigation gate must not over-reach (spec: `location-permissions` — Starting
 * navigation requires precise location / Free driving is unaffected): with an
 * approximate-only grant the map surface still takes its location lease and free
 * driving still engages. Only a *route request* is refused.
 *
 * Default Robolectric sandbox (no `@Config`) — this class instantiates
 * [FakeOSMScoutClient] and therefore loads the JNI stub, per the AGENTS.md rule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ApproximateLocationMapParityTest {

    private lateinit var app: Application
    private lateinit var context: Context
    private lateinit var locationService: LocationService
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private fun buildViewModelWithApproximateGrant() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val client = FakeOSMScoutClient()
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

    @Test
    fun theMapKeepsItsLocationLeaseOnAnApproximateGrant() = runTest {
        buildViewModelWithApproximateGrant()

        viewModel.startLocationUpdates()
        advanceUntilIdle()

        assertTrue(
            "approximate is enough for the map surface: ${locationService.heldLeaseConsumers()}",
            locationService.heldLeaseConsumers().contains(LocationConsumers.PHONE_MAP)
        )
    }

    @Test
    fun freeDrivingIsNotGated() = runTest {
        buildViewModelWithApproximateGrant()

        viewModel.startLocationUpdates()
        viewModel.onToggleFollowMode(true)
        advanceUntilIdle()

        assertTrue(
            "free driving is a map mode, not a route request",
            viewModel.uiState.value.followMode
        )
    }
}
