package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies that full formatted address queries (street + house number +
 * postal code + city) resolve through the structured form search first, ahead
 * of the raw string results, which drop such queries when a postal code sits
 * inside the query (spec: location-search "Full formatted address
 * resolution").
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelStructuredAddressSearchTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
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

    private fun houseEntry(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            objectType = "address"
            matchQuality = "match"
            this.lat = lat
            this.lon = lon
            objectFileOffset = 123L
        }

    private fun streetEntry(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            objectType = "place"
            matchQuality = "match"
            this.lat = lat
            this.lon = lon
        }

    @Test
    fun `full formatted address runs structured form search first`() = runTest {
        client.nextSearchResults = arrayOf(streetEntry("Erbstollenstraße", 51.4501, 7.4113))
        client.nextFormResults = arrayOf(houseEntry("Erbstollenstraße 10", 51.44959, 7.41038))

        val results = viewModel.mergeSearchResults("Erbstollenstraße 10 58454 Witten")

        // Form search called with the parsed structured fields (postal code in
        // the middle of the query must not zero out the result).
        assertEquals(
            listOf(listOf("Witten", "58454", "Erbstollenstraße", "10")),
            client.formSearchArgs
        )
        // The house-level structured hit ranks above the raw string street hit.
        assertTrue(results.isNotEmpty())
        assertEquals("Erbstollenstraße 10", results[0].entry.label)
        assertEquals("Erbstollenstraße", results[1].entry.label)
    }

    @Test
    fun `structured results dedup against raw results`() = runTest {
        // Both searches return the same house object (same file offset).
        client.nextFormResults = arrayOf(houseEntry("Erbstollenstraße 10", 51.44959, 7.41038))
        client.nextSearchResults = arrayOf(houseEntry("Erbstollenstraße 10", 51.44959, 7.41038))

        val results = viewModel.mergeSearchResults("Erbstollenstraße 10 58454 Witten")

        assertEquals(1, results.size)
        assertEquals("Erbstollenstraße 10", results[0].entry.label)
    }

    @Test
    fun `non-address query keeps raw results only`() = runTest {
        client.nextSearchResults = arrayOf(streetEntry("cafe central", 51.5, 7.4))
        client.nextFormResults = emptyArray()

        val results = viewModel.mergeSearchResults("cafe central")

        // "cafe central" is not address-like — no form search runs.
        assertTrue(client.formSearchArgs.isEmpty())
        assertEquals(1, results.size)
        assertEquals("cafe central", results[0].entry.label)
    }
}
