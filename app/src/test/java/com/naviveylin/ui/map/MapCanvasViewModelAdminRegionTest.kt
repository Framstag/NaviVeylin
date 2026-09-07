package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
 * Verifies admin-region scoping of local search:
 * - GPS gate (fresh fix + good accuracy) decides between scoped and unconstrained search
 * - resolved region handle is cached and reused within the movement threshold
 * - re-resolution after significant movement releases the old handle
 * - handle is released on clear / lost fix / ViewModel clear
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapCanvasViewModelAdminRegionTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var locationService: LocationService

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
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
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private fun freshFix(lat: Double, lon: Double, accuracy: Float = 10f): GpsFix =
        GpsFix(
            lat = lat,
            lon = lon,
            accuracy = accuracy.toDouble(),
            speedKmH = Double.NaN,
            smoothedBearing = Double.NaN,
            markerBearing = Double.NaN,
            time = System.currentTimeMillis()
        )

    @Test
    fun goodFixResolvesRegionAndReturnsHandle() {
        client.nextAdminRegionHandle = 7L
        val handle = viewModel.searchAdminRegionHandleForFix(freshFix(51.5136, 7.4653))
        assertEquals(7L, handle)
        assertEquals(listOf(7L), client.adminRegionHandles)
    }

    @Test
    fun noFixFallsBackToUnconstrained() {
        assertEquals(0L, viewModel.searchAdminRegionHandleForFix(null))
        assertEquals(emptyList<Long>(), client.adminRegionHandles)
    }

    @Test
    fun veryOldFixStillResolvesRegion() {
        // No age cap for region scoping: the last known position is valid
        // however old the fix (e.g. at home all day, searching for a target).
        // A fresh fix showing real movement re-resolves via the movement
        // threshold.
        client.nextAdminRegionHandle = 7L
        val old = freshFix(51.5136, 7.4653).copy(time = System.currentTimeMillis() - 20 * 60 * 60_000)
        assertEquals(7L, viewModel.searchAdminRegionHandleForFix(old))
        assertEquals(listOf(7L), client.adminRegionHandles)
    }

    @Test
    fun moderatelyStaleFixStillResolvesRegion() {
        // A fix minutes old (e.g. GPS gap while stationary) is still a valid
        // position for region scoping — the region only changes on movement.
        client.nextAdminRegionHandle = 7L
        val stale = freshFix(51.5136, 7.4653).copy(time = System.currentTimeMillis() - 54_000)
        assertEquals(7L, viewModel.searchAdminRegionHandleForFix(stale))
        assertEquals(listOf(7L), client.adminRegionHandles)
    }

    @Test
    fun inaccurateFixFallsBackToUnconstrained() {
        assertEquals(
            0L,
            viewModel.searchAdminRegionHandleForFix(freshFix(51.5136, 7.4653, accuracy = 80f))
        )
        assertEquals(emptyList<Long>(), client.adminRegionHandles)
    }

    @Test
    fun handleReusedWithinMovementThreshold() {
        client.nextAdminRegionHandle = 7L
        assertEquals(7L, viewModel.searchAdminRegionHandleForFix(freshFix(51.5136, 7.4653)))
        // ~200m north — below the 500m threshold
        assertEquals(7L, viewModel.searchAdminRegionHandleForFix(freshFix(51.5154, 7.4653)))
        assertEquals(listOf(7L), client.adminRegionHandles)
    }

    @Test
    fun reResolvedAfterSignificantMovement() {
        client.nextAdminRegionHandle = 7L
        assertEquals(7L, viewModel.searchAdminRegionHandleForFix(freshFix(51.5136, 7.4653)))
        client.nextAdminRegionHandle = 8L
        // ~1.1km north — beyond the 500m threshold
        assertEquals(8L, viewModel.searchAdminRegionHandleForFix(freshFix(51.5236, 7.4653)))
        assertEquals(listOf(7L, 8L), client.adminRegionHandles)
        // Old handle released before re-resolution
        assertEquals(listOf(7L), client.releasedAdminRegionHandles)
    }

    @Test
    fun resolveFailureFallsBackToUnconstrained() {
        client.nextAdminRegionHandle = 0L
        assertEquals(0L, viewModel.searchAdminRegionHandleForFix(freshFix(51.5136, 7.4653)))
    }

    @Test
    fun lostFixReleasesCachedHandle() {
        client.nextAdminRegionHandle = 7L
        viewModel.searchAdminRegionHandleForFix(freshFix(51.5136, 7.4653))
        viewModel.searchAdminRegionHandleForFix(null)
        assertEquals(listOf(7L), client.releasedAdminRegionHandles)
    }

    @Test
    fun clearSearchReleasesHandle() {
        client.nextAdminRegionHandle = 7L
        viewModel.searchAdminRegionHandleForFix(freshFix(51.5136, 7.4653))
        viewModel.clearSearch()
        assertEquals(listOf(7L), client.releasedAdminRegionHandles)
    }

    @Test
    fun searchPassesUnconstrainedHandleWithoutLiveFix() = runTest(mainDispatcherRule.dispatcher) {
        // No live fix in LocationService → search must pass handle 0 (unconstrained)
        val results = viewModel.searchLocations("Hauptstraße 12")
        assertEquals(emptyList<com.framstag.libosmscout.client.LocationEntry>(), results)
        assertEquals(listOf(0L), client.searchAdminRegionHandles)
    }

    @Test
    fun resolveExposesRegionNameInUiState() {
        client.nextAdminRegionHandle = 7L
        client.adminRegionName = "Dortmund"
        viewModel.searchAdminRegionHandleForFix(freshFix(51.5136, 7.4653))
        assertEquals("Dortmund", viewModel.uiState.value.searchAdminRegionName)
    }

    @Test
    fun resolveExposesScopeRegionNameInUiState() {
        // The panel shows the search scope region (parent when expanded), not
        // just the resolved region.
        client.nextAdminRegionHandle = 7L
        client.adminRegionName = "Dortmund"
        client.adminRegionScopeName = "Regierungsbezirk Arnsberg"
        viewModel.searchAdminRegionHandleForFix(freshFix(51.5136, 7.4653))
        assertEquals("Regierungsbezirk Arnsberg", viewModel.uiState.value.searchAdminRegionName)
    }

    @Test
    fun regionNameClearedOnLostFix() {
        client.nextAdminRegionHandle = 7L
        viewModel.searchAdminRegionHandleForFix(freshFix(51.5136, 7.4653))
        assertEquals("Dortmund", viewModel.uiState.value.searchAdminRegionName)
        viewModel.searchAdminRegionHandleForFix(null)
        assertEquals(null, viewModel.uiState.value.searchAdminRegionName)
    }

    @Test
    fun regionNameClearedOnClearSearch() {
        client.nextAdminRegionHandle = 7L
        viewModel.searchAdminRegionHandleForFix(freshFix(51.5136, 7.4653))
        viewModel.clearSearch()
        assertEquals(null, viewModel.uiState.value.searchAdminRegionName)
    }

    @Test
    fun regionNameFollowsReResolution() {
        client.nextAdminRegionHandle = 7L
        client.adminRegionName = "Dortmund"
        viewModel.searchAdminRegionHandleForFix(freshFix(51.5136, 7.4653))
        assertEquals("Dortmund", viewModel.uiState.value.searchAdminRegionName)

        client.nextAdminRegionHandle = 8L
        client.adminRegionName = "Essen"
        // ~1.1km north — beyond the 500m threshold
        viewModel.searchAdminRegionHandleForFix(freshFix(51.5236, 7.4653))
        assertEquals("Essen", viewModel.uiState.value.searchAdminRegionName)
    }

    @Test
    fun noRegionNameOnResolveFailure() {
        client.nextAdminRegionHandle = 0L
        viewModel.searchAdminRegionHandleForFix(freshFix(51.5136, 7.4653))
        assertEquals(null, viewModel.uiState.value.searchAdminRegionName)
    }

    @Test
    fun panelOpenResolvesRegionEagerly() = runTest(mainDispatcherRule.dispatcher) {
        client.nextAdminRegionHandle = 7L
        locationService.setGpsFixForTest(freshFix(51.5136, 7.4653))
        viewModel.onSearchPanelOpened()
        // The eager resolution runs on the test scheduler — drive it to completion.
        advanceUntilIdle()
        assertEquals("Dortmund", viewModel.uiState.value.searchAdminRegionName)
        assertEquals(listOf(7L), client.adminRegionHandles)
    }

    @Test
    fun panelOpenWithoutFixDoesNotResolve() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSearchPanelOpened()
        assertEquals(emptyList<Long>(), client.adminRegionHandles)
        assertEquals(null, viewModel.uiState.value.searchAdminRegionName)
    }

    @Test
    fun gpsFixTransitionResolvesWhilePanelOpen() = runTest(mainDispatcherRule.dispatcher) {
        client.nextAdminRegionHandle = 7L
        viewModel.onSearchPanelOpened()
        // Fix arrives after panel opened (debounced GPS quality collector).
        // Shared scheduler: advancing virtual time fires the 2s debounce.
        locationService.setGpsFixForTest(freshFix(51.5136, 7.4653))
        advanceTimeBy(2500)
        runCurrent()
        advanceUntilIdle()
        assertEquals("Dortmund", viewModel.uiState.value.searchAdminRegionName)
        assertEquals(listOf(7L), client.adminRegionHandles)
    }
}
