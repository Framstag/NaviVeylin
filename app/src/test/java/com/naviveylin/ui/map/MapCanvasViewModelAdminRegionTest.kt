package com.naviveylin.ui.map

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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
class MapCanvasViewModelAdminRegionTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var locationService: LocationService

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
            context = context
        )
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
        Dispatchers.resetMain()
    }

    private fun freshFix(lat: Double, lon: Double, accuracy: Float = 10f): Location =
        Location("gps").apply {
            this.latitude = lat
            this.longitude = lon
            this.accuracy = accuracy
            time = System.currentTimeMillis()
        }

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
    fun staleFixFallsBackToUnconstrained() {
        val stale = freshFix(51.5136, 7.4653).apply { time = System.currentTimeMillis() - 6_000 }
        assertEquals(0L, viewModel.searchAdminRegionHandleForFix(stale))
        assertEquals(emptyList<Long>(), client.adminRegionHandles)
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
    fun searchPassesUnconstrainedHandleWithoutLiveFix() = runTest {
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
    fun panelOpenResolvesRegionEagerly() = runTest {
        client.nextAdminRegionHandle = 7L
        locationService.setLocationForTest(freshFix(51.5136, 7.4653))
        viewModel.onSearchPanelOpened()
        // Resolution runs on Dispatchers.Default — poll the final state with real time
        var resolved = false
        repeat(200) {
            if (viewModel.uiState.value.searchAdminRegionName == "Dortmund") {
                resolved = true
                return@repeat
            }
            Thread.sleep(10)
        }
        assertTrue(resolved)
        assertEquals(listOf(7L), client.adminRegionHandles)
    }

    @Test
    fun panelOpenWithoutFixDoesNotResolve() = runTest {
        viewModel.onSearchPanelOpened()
        assertEquals(emptyList<Long>(), client.adminRegionHandles)
        assertEquals(null, viewModel.uiState.value.searchAdminRegionName)
    }

    @Test
    fun gpsFixTransitionResolvesWhilePanelOpen() = runTest {
        client.nextAdminRegionHandle = 7L
        viewModel.onSearchPanelOpened()
        // Fix arrives after panel opened (debounced GPS quality collector)
        locationService.setLocationForTest(freshFix(51.5136, 7.4653))
        advanceTimeBy(2500)
        runCurrent()
        var resolved = false
        repeat(200) {
            if (viewModel.uiState.value.searchAdminRegionName == "Dortmund") {
                resolved = true
                return@repeat
            }
            Thread.sleep(10)
        }
        assertTrue(resolved)
        assertEquals(listOf(7L), client.adminRegionHandles)
    }
}
