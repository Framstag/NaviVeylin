package com.naviveylin.ui.route

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Verifies the navigation gate on the phone's route-calc path (spec:
 * `location-permissions` — Starting navigation requires precise location): with an
 * approximate-only grant no route request reaches the engine, the panel explains
 * why, and the upgrade prompt is offered. Free driving, the map and search are not
 * this path and are covered by their own suites.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoutePanelViewModelLocationGateTest {

    private lateinit var app: Application
    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: RoutePanelViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient()
        viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        shadowOf(app).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    private fun entry(label: String, lat: Double = 51.5, lon: Double = 7.4): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.lat = lat
            this.lon = lon
            matchQuality = "match"
        }

    private fun armRoute() {
        viewModel.setStartLocation(entry("Start", 52.5200, 13.4050))
        viewModel.setDestLocation(entry("Dest", 52.5300, 13.4100))
    }

    @Test
    fun approximateGrant_refusesWithoutCallingTheEngine() = runTest {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        armRoute()

        viewModel.calculateRoute()
        advanceUntilIdle()

        assertEquals("no route request may reach the engine", 0, client.routeCalculationCount)
        assertTrue(
            "the panel must explain the refusal",
            viewModel.uiState.value.routeState is RouteState.Error
        )
        assertTrue(
            "the upgrade prompt must be offered",
            viewModel.uiState.value.preciseLocationRequired
        )
        assertEquals(
            "the wording is the shared phone/car string",
            context.getString(com.naviveylin.core.R.string.location_precise_required_navigation),
            viewModel.uiState.value.error
        )
    }

    @Test
    fun noGrant_refusesWithoutCallingTheEngine() = runTest {
        armRoute()

        viewModel.calculateRoute()
        advanceUntilIdle()

        assertEquals(0, client.routeCalculationCount)
        assertTrue(viewModel.uiState.value.preciseLocationRequired)
    }

    @Test
    fun preciseGrant_calculatesTheRoute() = runTest {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        armRoute()

        viewModel.calculateRoute()
        advanceUntilIdle()

        assertEquals(1, client.routeCalculationCount)
        assertFalse(
            "a granted route must not leave the upgrade prompt up",
            viewModel.uiState.value.preciseLocationRequired
        )
    }

    @Test
    fun dismissingThePromptClearsTheFlagButKeepsTheExplanation() = runTest {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        armRoute()
        viewModel.calculateRoute()
        advanceUntilIdle()

        viewModel.dismissPreciseLocationRequirement()

        assertFalse(viewModel.uiState.value.preciseLocationRequired)
        assertTrue(
            "dismissing the prompt must not hide why the route was refused",
            viewModel.uiState.value.routeState is RouteState.Error
        )
    }
}
