package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
 * Verifies that free-text search results (POIs, regions, other objects found
 * via the MARISA text index in the native layer) flow through the ViewModel
 * unchanged, and that the search call contract (query, limit, admin region
 * handle) is preserved. The native merge/dedup/limit logic itself lives in
 * libosmscout-client-java and is exercised on-device (see change tasks 4.x).
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelFreeTextSearchTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
            context = context
        )
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
        Dispatchers.resetMain()
    }

    private fun poiEntry(
        label: String,
        objectTypeName: String,
        lat: Double,
        lon: Double
    ): LocationEntry = LocationEntry().apply {
        this.label = label
        type = "object"
        objectType = "amenity_cafe"
        this.objectTypeName = objectTypeName
        this.lat = lat
        this.lon = lon
        name = label
        matchQuality = "match"
        refType = "node"
    }

    @Test
    fun freeTextPoiResultSurfacesWithCoordinates() = runTest {
        client.nextSearchResults = arrayOf(poiEntry("Café Central", "cafe", 51.5136, 7.4653))

        val results = viewModel.searchLocations("cafe central")

        assertEquals(1, results.size)
        assertEquals("Café Central", results[0].label)
        assertEquals("cafe", results[0].objectTypeName)
        assertEquals(51.5136, results[0].lat, 1e-9)
        assertEquals(7.4653, results[0].lon, 1e-9)
    }

    @Test
    fun freeTextAndStructuredResultsPassThroughInOrder() = runTest {
        val structured = poiEntry("Hauptstraße 12", "address", 51.5, 7.4)
        val freeText = poiEntry("Café Central", "cafe", 51.5136, 7.4653)
        client.nextSearchResults = arrayOf(structured, freeText)

        val results = viewModel.searchLocations("cafe central")

        // Ordering is decided by the native merge; the ViewModel must not reorder.
        assertEquals(listOf("Hauptstraße 12", "Café Central"), results.map { it.label })
    }

    @Test
    fun searchCallPassesQueryAndLimitToClient() = runTest {
        client.nextSearchResults = arrayOf(poiEntry("Café Central", "cafe", 51.5136, 7.4653))

        viewModel.searchLocations("cafe central")

        assertEquals(listOf("cafe central"), client.searchQueries)
        assertEquals(listOf(20), client.searchLimits)
        // No live fix → unconstrained search (handle 0), same as structured search.
        assertEquals(listOf(0L), client.searchAdminRegionHandles)
    }

    @Test
    fun noTextIndexFallsBackToEmptyResultsWithoutCrash() = runTest {
        // Database without a text index: native layer returns structured results
        // only; with none matching, the client returns an empty array.
        client.nextSearchResults = emptyArray()

        val results = viewModel.searchLocations("cafe central")

        assertTrue(results.isEmpty())
        assertEquals(listOf("cafe central"), client.searchQueries)
    }

    @Test
    fun nullClientResultFallsBackToEmptyList() = runTest {
        client.nextSearchResults = null

        val results = viewModel.searchLocations("cafe central")

        assertTrue(results.isEmpty())
    }
}
