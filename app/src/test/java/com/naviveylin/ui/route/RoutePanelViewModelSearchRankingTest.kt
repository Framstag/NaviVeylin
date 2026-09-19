package com.naviveylin.ui.route

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.search.SearchReference
import com.naviveylin.core.search.SearchResultRanker
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Verifies the start/destination picker ordering (spec: location-search,
 * search-result-ranking): the same match-tier ranking as the map search, the
 * larger candidate set, and the reference point used for ordering and for the
 * displayed distance.
 */
@RunWith(RobolectricTestRunner::class)
class RoutePanelViewModelSearchRankingTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var viewModel: RoutePanelViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient()
        locationService = LocationService(context)
        viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = locationService,
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    private fun exactEntry(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            matchedName = label
            matchedComponent = "location"
            locationMatchQuality = "match"
            this.lat = lat
            this.lon = lon
        }

    private fun prefixEntry(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            matchedName = label
            matchedComponent = "location"
            locationMatchQuality = "candidate"
            this.lat = lat
            this.lon = lon
        }

    @Test
    fun searchFetchesMoreCandidatesThanItDisplays() = runTest(mainDispatcherRule.dispatcher) {
        client.nextSearchResults = arrayOf(exactEntry("Waltrop", 51.6, 7.5))

        viewModel.onSearchQueryChanged("Waltrop")
        advanceUntilIdle()

        assertEquals(listOf(SearchResultRanker.CANDIDATE_LIMIT), client.searchLimits)
    }

    @Test
    fun exactMatchOutranksPrefixMatchesAndTheListIsCapped() = runTest(mainDispatcherRule.dispatcher) {
        val close = (1..25).map { prefixEntry("Waltroper Straße $it", 51.5, 7.4) }
        client.nextSearchResults = (close + exactEntry("Waltrop", 51.6, 7.5)).toTypedArray()

        viewModel.onSearchQueryChanged("Waltrop")
        advanceUntilIdle()

        val results = viewModel.uiState.value.searchResults
        assertEquals("Waltrop", results.first().label)
        assertEquals(SearchResultRanker.DISPLAY_LIMIT, results.size)
    }

    @Test
    fun lastGpsFixIsTheReferencePoint() = runTest(mainDispatcherRule.dispatcher) {
        locationService.setLocationForTest(
            Location("gps").apply {
                latitude = 51.5
                longitude = 7.4
                accuracy = 8f
                time = 1_000L
            }
        )
        locationService.location.first { it != null }
        client.nextSearchResults = arrayOf(exactEntry("Waltrop", 51.6, 7.5))

        viewModel.onSearchQueryChanged("Waltrop")
        advanceUntilIdle()

        assertEquals(SearchReference(51.5, 7.4), viewModel.uiState.value.searchReference)
    }

    @Test
    fun mapCenterIsTheFallbackReferenceWithoutAFix() = runTest(mainDispatcherRule.dispatcher) {
        client.nextSearchResults = arrayOf(exactEntry("Waltrop", 51.6, 7.5))
        viewModel.setFallbackSearchCenter(51.5136, 7.4653)

        viewModel.onSearchQueryChanged("Waltrop")
        advanceUntilIdle()

        assertEquals(
            SearchReference(51.5136, 7.4653),
            viewModel.uiState.value.searchReference
        )
    }

    @Test
    fun withoutAFixAndWithoutACenterTheOrderUsesQualityOnly() = runTest(mainDispatcherRule.dispatcher) {
        client.nextSearchResults = arrayOf(
            prefixEntry("Waltroper Straße", 51.5, 7.4),
            exactEntry("Waltrop", 51.6, 7.5)
        )

        viewModel.onSearchQueryChanged("Waltrop")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Waltrop", state.searchResults.first().label)
        assertTrue("no reference means no distance", state.searchReference == null)
    }
}
