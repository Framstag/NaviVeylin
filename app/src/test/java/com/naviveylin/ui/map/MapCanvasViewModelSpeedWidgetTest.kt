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
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.flow.first
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
 * Verifies the follow-mode speed data for the on-map speed widget
 * (spec: map-speed-widget): current speed from the GPS fix, max speed
 * resolved via getMaxSpeedAt with movement/cooldown throttling.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelSpeedWidgetTest {

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
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private fun pushFix(lat: Double, lon: Double, speedKmH: Double, time: Long) {
        val loc = Location("gps").apply {
            latitude = lat
            longitude = lon
            speed = (speedKmH / 3.6).toFloat()
            accuracy = 8f
            this.time = time
        }
        locationService.setLocationForTest(loc)
    }

    @Test
    fun currentSpeedTracksGpsFix() = runTest(mainDispatcherRule.dispatcher) {
        pushFix(51.5136, 7.4653, speedKmH = 48.0, time = 1_000L)
        val state = viewModel.uiState.first { it.gpsLocation != null }
        assertEquals(48.0, state.currentSpeedKmH, 1e-3)
    }

    @Test
    fun currentSpeedNaNWithoutGpsSpeed() = runTest(mainDispatcherRule.dispatcher) {
        val loc = Location("gps").apply {
            latitude = 51.5136
            longitude = 7.4653
            accuracy = 8f
            time = 1_000L
            // no speed set → hasSpeed() false → speedKmH NaN
        }
        locationService.setLocationForTest(loc)
        val state = viewModel.uiState.first { it.gpsLocation != null }
        assertTrue(state.currentSpeedKmH.isNaN())
    }

    @Test
    fun maxSpeedResolvedFromClient() = runTest(mainDispatcherRule.dispatcher) {
        client.maxSpeedAt = 50.0
        pushFix(51.5136, 7.4653, speedKmH = 48.0, time = 1_000L)
        val state = viewModel.uiState.first { it.maxSpeedKmH == 50.0 }
        assertEquals(50.0, state.maxSpeedKmH, 1e-9)
        assertEquals(listOf(51.5136 to 7.4653), client.maxSpeedLookupCoords)
    }

    @Test
    fun maxSpeedNaNWhenNoLimit() = runTest(mainDispatcherRule.dispatcher) {
        client.maxSpeedAt = -1.0 // native convention: no limit
        pushFix(51.5136, 7.4653, speedKmH = 48.0, time = 1_000L)
        val state = viewModel.uiState.first { it.gpsLocation != null }
        assertTrue(state.maxSpeedKmH.isNaN())
    }

    @Test
    fun maxSpeedNaNOnClientFailure() = runTest(mainDispatcherRule.dispatcher) {
        client.maxSpeedAtError = RuntimeException("boom")
        pushFix(51.5136, 7.4653, speedKmH = 48.0, time = 1_000L)
        val state = viewModel.uiState.first { it.gpsLocation != null }
        assertTrue(state.maxSpeedKmH.isNaN())
    }

    @Test
    fun maxSpeedThrottledOnStationaryFixes() = runTest(mainDispatcherRule.dispatcher) {
        client.maxSpeedAt = 50.0
        // Same position, distinct fixes within the cooldown → single resolve.
        pushFix(51.5136, 7.4653, speedKmH = 48.0, time = 1_000L)
        viewModel.uiState.first { it.maxSpeedKmH == 50.0 }
        pushFix(51.5136, 7.4653, speedKmH = 49.0, time = 2_000L)
        viewModel.uiState.first { it.gpsLocation?.time == 2_000L }
        assertEquals("stationary fixes must not re-resolve", 1, client.maxSpeedLookupCoords.size)
    }

    @Test
    fun maxSpeedReResolvedAfterMovement() = runTest(mainDispatcherRule.dispatcher) {
        client.maxSpeedAt = 50.0
        pushFix(51.5136, 7.4653, speedKmH = 48.0, time = 1_000L)
        viewModel.uiState.first { it.maxSpeedKmH == 50.0 }

        // ~1.1 km away → beyond the 50 m movement threshold → re-resolve.
        client.maxSpeedAt = 30.0
        pushFix(51.52, 7.4653, speedKmH = 48.0, time = 2_000L)
        val state = viewModel.uiState.first { it.maxSpeedKmH == 30.0 }
        assertEquals(30.0, state.maxSpeedKmH, 1e-9)
        assertEquals(2, client.maxSpeedLookupCoords.size)
    }
}
