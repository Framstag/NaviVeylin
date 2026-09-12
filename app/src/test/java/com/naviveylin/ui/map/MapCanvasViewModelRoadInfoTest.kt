package com.naviveylin.ui.map

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RoadInfo
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the free-driving street label data (spec: current-road-info):
 * the current road is resolved via the bearing-aware `getRoadAt` with
 * movement/cooldown throttling, mirroring the max-speed resolution.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelRoadInfoTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var viewModel: MapCanvasViewModel

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
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private fun pushFix(lat: Double, lon: Double, bearing: Double, time: Long) {
        val loc = Location("gps").apply {
            latitude = lat
            longitude = lon
            speed = 13.9f // 50 km/h
            accuracy = 8f
            this.bearing = bearing.toFloat()
            this.time = time
        }
        locationService.setLocationForTest(loc)
    }

    @Test
    fun currentRoadResolvedFromClient() = runTest(mainDispatcherRule.dispatcher) {
        client.roadAt = RoadInfo("Hauptstrasse", "B 1", "highway_primary", 50.0)
        pushFix(51.5136, 7.4653, bearing = 90.0, time = 1_000L)
        val state = viewModel.uiState.first { it.currentRoadInfo != null }
        assertEquals("B 1", state.currentRoadInfo?.ref)
        assertEquals("Hauptstrasse", state.currentRoadInfo?.name)
        assertEquals(1, client.roadAtLookupCalls.size)
        assertEquals(51.5136, client.roadAtLookupCalls[0].first, 1e-9)
        assertEquals(7.4653, client.roadAtLookupCalls[0].second, 1e-9)
    }

    @Test
    fun currentRoadNullWhenNoRoadFound() = runTest(mainDispatcherRule.dispatcher) {
        client.roadAt = null
        pushFix(51.5136, 7.4653, bearing = 90.0, time = 1_000L)
        // Run the async lookup on the shared test scheduler, then assert the
        // state stayed null (no road found).
        advanceUntilIdle()
        assertEquals(1, client.roadAtLookupCalls.size)
        assertNull(viewModel.uiState.value.currentRoadInfo)
    }

    @Test
    fun currentRoadThrottledOnStationaryFixes() = runTest(mainDispatcherRule.dispatcher) {
        client.roadAt = RoadInfo("Hauptstrasse", "B 1", "highway_primary", 50.0)
        pushFix(51.5136, 7.4653, bearing = 90.0, time = 1_000L)
        viewModel.uiState.first { it.currentRoadInfo != null }
        pushFix(51.5136, 7.4653, bearing = 91.0, time = 2_000L)
        viewModel.uiState.first { it.gpsLocation?.time == 2_000L }
        assertEquals("stationary fixes must not re-resolve", 1, client.roadAtLookupCalls.size)
    }

    @Test
    fun currentRoadReResolvedAfterMovement() = runTest(mainDispatcherRule.dispatcher) {
        client.roadAt = RoadInfo("Hauptstrasse", "B 1", "highway_primary", 50.0)
        pushFix(51.5136, 7.4653, bearing = 90.0, time = 1_000L)
        viewModel.uiState.first { it.currentRoadInfo != null }

        // ~1.1 km away → beyond the 50 m movement threshold → re-resolve.
        client.roadAt = RoadInfo("Nebenstrasse", "", "highway_residential", Double.NaN)
        pushFix(51.52, 7.4653, bearing = 90.0, time = 2_000L)
        val state = viewModel.uiState.first { it.currentRoadInfo?.name == "Nebenstrasse" }
        assertEquals("Nebenstrasse", state.currentRoadInfo?.name)
        assertEquals(2, client.roadAtLookupCalls.size)
    }
}
