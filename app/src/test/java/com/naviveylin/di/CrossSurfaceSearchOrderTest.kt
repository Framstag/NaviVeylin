package com.naviveylin.di

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.search.SearchReference
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import com.naviveylin.ui.map.MapCanvasViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Verifies the cross-surface parity scenario of the ranking (spec:
 * search-result-ranking — "Same query on both surfaces"): the phone search
 * dialog and the Android Auto search pipeline produce the same order for the
 * same candidates, because they share one ranker and one reference rule.
 */
@RunWith(RobolectricTestRunner::class)
class CrossSurfaceSearchOrderTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "maps/search_history.json").delete()
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
        ).also { it.defaultDispatcher = mainDispatcherRule.dispatcher }
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private fun exact(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            matchedName = label
            matchedComponent = "location"
            locationMatchQuality = "match"
            this.lat = lat
            this.lon = lon
        }

    private fun close(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            matchedName = label
            matchedComponent = "location"
            locationMatchQuality = "candidate"
            this.lat = lat
            this.lon = lon
        }

    @Test
    fun sameQueryProducesTheSameOrderOnPhoneAndCar() = runTest(mainDispatcherRule.dispatcher) {
        // Candidates in the backend's own order: close matches first, the exact
        // answer last. "Waltrop Mitte" is a close match too — the entry name
        // carries a word the query did not name (spec: the name must be fully
        // consumed) — so it can only rank in the close tier, by quality then
        // distance.
        client.nextSearchResults = arrayOf(
            close("Waltroper Straße 1", 51.5001, 7.4),
            close("Waltroper Straße 2", 51.6001, 7.4),
            exact("Waltrop", 52.0, 8.0),
            exact("Waltrop Mitte", 51.5002, 7.4)
        )
        // Same reference on both surfaces: a GPS fix at the same point the car
        // pipeline is handed explicitly.
        val fix = SearchReference(51.5, 7.4)
        locationService.setLocationForTest(
            Location("gps").apply {
                latitude = fix.lat
                longitude = fix.lon
                accuracy = 8f
                time = 1_000L
            }
        )
        locationService.location.first { it != null }
        // Wait for the ViewModel to see the fix: the reference comes from its
        // state, not from the location service directly.
        viewModel.uiState.first { it.gpsLocation != null }

        val phoneOrder = viewModel.mergeSearchResults("Waltrop").map { it.entry.label }
        val carOrder = AutoServiceModule.provideAutoSearchProvider(javax.inject.Provider { client })
            .searchLocations("Waltrop", 20, fix)
            .map { it.label }

        assertEquals(carOrder, phoneOrder)
        assertEquals(
            listOf("Waltrop", "Waltrop Mitte", "Waltroper Straße 1", "Waltroper Straße 2"),
            phoneOrder
        )
    }
}
