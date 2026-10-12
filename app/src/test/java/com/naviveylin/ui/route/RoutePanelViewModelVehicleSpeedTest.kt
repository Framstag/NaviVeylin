package com.naviveylin.ui.route

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The route panel hands the selected vehicle's stored usual maximum speed to the routing profile
 * (spec: `routing-max-speed` — Every calculation path carries the vehicle's maximum speed;
 * `route-panel-ui` — The selected vehicle's stored maximum speed reaches the profile, and Each
 * vehicle reads its own stored value).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoutePanelViewModelVehicleSpeedTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var storage: SettingsStorage
    private lateinit var viewModel: RoutePanelViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient()
        // Test-controlled storage: its write path must run on the test scheduler, or a value set by
        // a case races the calculation that reads it.
        storage = SettingsStorage(context).also { it.ioDispatcher = mainDispatcherRule.dispatcher }
        viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            context = context,
            settingsStorage = storage
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    private fun entry(label: String, lat: Double = 51.5, lon: Double = 7.4): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.lat = lat
            this.lon = lon
            matchQuality = "coordinate"
        }

    /** Calculate once with [vehicle] selected. */
    private suspend fun calculateWith(vehicle: Vehicle) {
        viewModel.setVehicle(vehicle)
        viewModel.updateLocationsForReroute(entry("start"), entry("dest"))
        viewModel.calculateRoute()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun routePanelBicycleCalculationCarriesTheStoredBicycleSpeed() =
        runTest(mainDispatcherRule.dispatcher) {
            storage.update { it.copy(bicycleMaxSpeedKmh = 20) }

            calculateWith(Vehicle.BICYCLE)

            val profile = client.lastRouteProfile
            assertEquals("the panel calculated once", 1, client.routeCalculationCount)
            assertEquals(Vehicle.BICYCLE, profile?.vehicle)
            assertEquals(20.0, profile?.maxSpeedKmh ?: -1.0, 0.0)
        }

    @Test
    fun eachVehicleReadsItsOwnStoredValue() = runTest(mainDispatcherRule.dispatcher) {
        storage.update {
            it.copy(carMaxSpeedKmh = 90, bicycleMaxSpeedKmh = 20, footMaxSpeedKmh = 4)
        }

        val expected = listOf(
            Vehicle.CAR to 90.0,
            Vehicle.BICYCLE to 20.0,
            Vehicle.PEDESTRIAN to 4.0
        )
        for ((vehicle, speedKmh) in expected) {
            calculateWith(vehicle)

            assertEquals(
                "$vehicle carries its own stored speed",
                speedKmh,
                client.lastRouteProfile?.maxSpeedKmh ?: -1.0,
                0.0
            )
            assertEquals(vehicle, client.lastRouteProfile?.vehicle)
        }
    }

    @Test
    fun theCalculationUsesTheDefaultSpeedWhenNothingIsStored() =
        runTest(mainDispatcherRule.dispatcher) {
            // No write at all: a fresh install routes a bicycle at the shipped default 15 km/h
            // (spec: `routing-max-speed` — A per-vehicle maximum speed is a persisted setting).
            calculateWith(Vehicle.BICYCLE)

            assertEquals(15.0, client.lastRouteProfile?.maxSpeedKmh ?: -1.0, 0.0)
        }
}
