package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.DescriptionEntry
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.ObjectDescription
import com.naviveylin.R
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.share.SharedLocationOutcome
import com.naviveylin.share.SharedLocationRequest
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Shared-location flow: a request submitted to [SharedLocationHandler] is
 * processed by the map view model — coordinates go through the candidate
 * flow (fixed zoom, the shared name hint ranking the candidates, details
 * directly on a single hit and on the raw coordinate when nothing found),
 * address text opens the search panel, and a map link that cannot be read
 * is reported without running a search. No @Config — default Robolectric
 * sandbox so the FakeOSMScoutClient JNI stub loads correctly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelSharedLocationTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var handler: SharedLocationHandler
    private lateinit var historyRepo: SearchHistoryRepository
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        handler = SharedLocationHandler()
        historyRepo = SearchHistoryRepository(context)
        // The history write is part of selecting a result; point it at the test dispatcher so
        // the selection's state copy is ordered before the assertions instead of racing a
        // real-thread file write that `advanceUntilIdle` cannot wait for.
        historyRepo.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = historyRepo,
            locationService = LocationService(context),
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = handler,
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    /** Queue a usable share, the way [com.naviveylin.share.SharedLocationParser] hands one over. */
    private fun submit(request: SharedLocationRequest) {
        handler.submit(SharedLocationOutcome.Request(request))
    }

    private fun candidate(
        name: String,
        type: String,
        lat: Double = Double.NaN,
        lon: Double = Double.NaN,
        offset: Long = 0L
    ): ObjectDescription {
        val entries = mutableListOf<DescriptionEntry>()
        entries.add(DescriptionEntry().apply {
            sectionKey = "General"
            labelKey = "Name"
            value = name
        })
        entries.add(DescriptionEntry().apply {
            sectionKey = "General"
            labelKey = "Type"
            value = type
        })
        return ObjectDescription(entries, lat, lon, "area", type, offset)
    }

    private fun initMap() {
        viewModel.setScreenSize(100, 100)
        viewModel.initMap("/data/maps/testmap")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `shared coordinate with candidates shows picker`() = runTest(mainDispatcherRule.dispatcher) {
        initMap()
        client.nextCandidateDescriptions = listOf(
            candidate("Hotel Central", "tourism_hotel", 51.51, 7.41, 100L),
            candidate("Cafe", "amenity_cafe", 51.52, 7.42, 200L)
        )

        submit(SharedLocationRequest(lat = 51.5, lon = 7.4, label = "Shared point"))
        viewModel.uiState.first { it.showCandidatePicker }

        val s = viewModel.uiState.value
        assertTrue(s.showCandidatePicker)
        assertFalse(s.showDetailsSheet)
        assertEquals(2, s.candidateDescriptions.size)
        // Map centered on the shared coordinate.
        assertEquals(51.5, s.viewport.centerLat, 1e-9)
        assertEquals(7.4, s.viewport.centerLon, 1e-9)
    }

    @Test
    fun `shared coordinate with no candidates opens details on raw coordinate`() = runTest(mainDispatcherRule.dispatcher) {
        initMap()
        client.nextCandidateDescriptions = emptyList()

        submit(SharedLocationRequest(lat = 51.5, lon = 7.4, label = "Shared point"))
        viewModel.uiState.first { it.showDetailsSheet }

        val s = viewModel.uiState.value
        assertTrue(s.showDetailsSheet)
        assertFalse(s.showCandidatePicker)
        assertEquals("Shared point", s.selectedLocation?.label)
        assertEquals(51.5, s.selectedLocation?.lat ?: Double.NaN, 1e-9)
        assertEquals(7.4, s.selectedLocation?.lon ?: Double.NaN, 1e-9)
        assertNull(s.objectDescription)
    }

    /**
     * Exactly one object at the shared coordinate: the details sheet opens for it, without the
     * one-row picker in between (spec: share/location-receiving — Exactly one candidate opens the
     * details sheet).
     */
    @Test
    fun `shared coordinate with a single candidate opens the details sheet directly`() =
        runTest(mainDispatcherRule.dispatcher) {
            initMap()
            client.nextCandidateDescriptions = listOf(
                candidate("Hotel Central", "tourism_hotel", 51.51, 7.41, 100L)
            )

            submit(SharedLocationRequest(lat = 51.5, lon = 7.4, label = "Shared point"))
            viewModel.uiState.first { it.showDetailsSheet }
            advanceUntilIdle()

            val s = viewModel.uiState.value
            assertFalse("a one-row picker must not be shown", s.showCandidatePicker)
            assertTrue(s.showDetailsSheet)
            assertNotNull(s.objectDescription)
            assertTrue(s.candidateDescriptions.isEmpty())
            assertFalse(s.isLoading)
            // The marker and the details follow the object, not the raw share point.
            assertEquals(51.51, s.selectedLocation?.lat ?: Double.NaN, 1e-9)
            assertEquals(7.41, s.selectedLocation?.lon ?: Double.NaN, 1e-9)
            assertEquals("object", s.selectedLocation?.matchQuality)
        }

    /**
     * The place name a share carries ranks the candidate list — the matching object first, the map
     * database's own order for the rest, nothing dropped (spec: share/location-receiving — Shared
     * place name ranks the candidate list).
     */
    @Test
    fun `shared place name ranks the candidates`() = runTest(mainDispatcherRule.dispatcher) {
        initMap()
        client.nextCandidateDescriptions = listOf(
            candidate("Cafe", "amenity_cafe", 51.51, 7.41, 100L),
            candidate("Hotel Central", "tourism_hotel", 51.52, 7.42, 200L),
            candidate("Eiffel Tower", "tourism_attraction", 51.53, 7.43, 300L)
        )

        submit(SharedLocationRequest(lat = 51.5, lon = 7.4, label = "Eiffel Tower"))
        viewModel.uiState.first { it.showCandidatePicker }

        val names = viewModel.uiState.value.candidateDescriptions.map { it.objectName() }
        assertEquals(
            "the named candidate is first, the database order of the rest is preserved",
            listOf("Eiffel Tower", "Cafe", "Hotel Central"),
            names
        )
    }

    /**
     * A long press keeps the picker even for a single candidate: there the user chose the point
     * deliberately, so the list confirms it (only the share flow opens details directly).
     */
    @Test
    fun `long press single candidate still shows the picker`() = runTest(mainDispatcherRule.dispatcher) {
        initMap()
        client.nextCandidateDescriptions = listOf(
            candidate("Hotel Central", "tourism_hotel", 51.51, 7.41, 100L)
        )

        viewModel.onLongPress(51.5, 7.4)
        viewModel.uiState.first { it.showCandidatePicker }

        val s = viewModel.uiState.value
        assertTrue(s.showCandidatePicker)
        assertFalse(s.showDetailsSheet)
        assertEquals(1, s.candidateDescriptions.size)
    }

    /**
     * A shared map link that carries neither a coordinate nor a name is reported with the hint and
     * runs no search — the search field keeps its content and the panel stays closed (spec:
     * share/location-receiving — An unreadable shared map link is reported, never searched,
     * Unreadable link does not open the search panel).
     */
    @Test
    fun `unreadable shared link shows the hint and keeps the search untouched`() =
        runTest(mainDispatcherRule.dispatcher) {
            initMap()
            handler.submit(SharedLocationOutcome.UnusableLink)

            viewModel.uiState.first { it.snackbarMessage != null }
            advanceUntilIdle()

            val s = viewModel.uiState.value
            assertEquals(context.getString(R.string.shared_link_unreadable), s.snackbarMessage)
            assertFalse("an unreadable link must not open the search panel", s.searchOpen)
            assertEquals("", s.searchQuery)
            assertFalse(s.showCandidatePicker)
            assertFalse(s.showDetailsSheet)
            assertNull(handler.consume())
        }

    @Test
    fun `shared address opens search panel with query`() = runTest(mainDispatcherRule.dispatcher) {
        initMap()

        submit(SharedLocationRequest(query = "Brandenburger Tor"))
        viewModel.uiState.first { it.searchOpen }

        val s = viewModel.uiState.value
        assertTrue(s.searchOpen)
        assertEquals("Brandenburger Tor", s.searchQuery)
    }

    @Test
    fun `request waits for map ready`() = runTest(mainDispatcherRule.dispatcher) {
        // Submit before the map is initialised: the request must queue and
        // process once initMap completes. Two candidates, so the share flow
        // shows the picker: this case is about the queueing, not about the
        // single-candidate shortcut of the share flow.
        client.nextCandidateDescriptions = listOf(
            candidate("Hotel", "tourism_hotel", 51.51, 7.41, 100L),
            candidate("Cafe", "amenity_cafe", 51.52, 7.42, 200L)
        )
        submit(SharedLocationRequest(lat = 51.5, lon = 7.4, label = "Early share"))

        initMap()
        viewModel.uiState.first { it.showCandidatePicker }

        assertTrue(viewModel.uiState.value.showCandidatePicker)
    }

    @Test
    fun `request consumed after processing`() = runTest(mainDispatcherRule.dispatcher) {
        initMap()
        client.nextCandidateDescriptions = emptyList()

        submit(SharedLocationRequest(lat = 51.5, lon = 7.4, label = "Once"))
        viewModel.uiState.first { it.showDetailsSheet }
        advanceUntilIdle()

        // Consumed: a second collect must not reprocess it.
        assertNull(handler.consume())
    }

    /**
     * A shared address whose text repeats the last committed search still runs the
     * search: the request is an identity of its own (spec: `location-search` — Same
     * text arriving from another entry point).
     */
    @Test
    fun `shared address repeating the committed query still runs the search`() =
        runTest(mainDispatcherRule.dispatcher) {
            initMap()
            val bochum = LocationEntry().apply {
                label = "Bochum"
                lat = 51.4818
                lon = 7.2162
                name = "Bochum"
                matchQuality = "match"
            }
            client.nextSearchResults = arrayOf(bochum)

            // Commit a search for the same text: type it, then select a result.
            viewModel.onSearchQueryChanged("Bochum")
            advanceTimeBy(400)
            runCurrent()
            assertEquals(1, viewModel.uiState.value.searchResults.size)
            viewModel.onSearchResultSelected(bochum)
            // Wait for the selection itself: the state copy runs after the history write, so
            // observing it is what makes the precondition below independent of I/O timing.
            viewModel.uiState.first { it.showDetailsSheet }
            assertTrue(
                "precondition: selecting a result empties the results list, saw " +
                    "query='${viewModel.uiState.value.searchQuery}' results=" +
                    viewModel.uiState.value.searchResults.map { it.entry.label } +
                    " details=${viewModel.uiState.value.showDetailsSheet} " +
                    "selected=${viewModel.uiState.value.selectedLocation?.label} " +
                    "searchOpen=${viewModel.uiState.value.searchOpen}",
                viewModel.uiState.value.searchResults.isEmpty()
            )

            submit(SharedLocationRequest(query = "Bochum"))
            viewModel.uiState.first { it.searchOpen }
            advanceTimeBy(400)
            runCurrent()

            assertEquals("Bochum", viewModel.uiState.value.searchQuery)
            assertEquals(
                "the shared text must run the search again, not replay a stale trigger",
                1,
                viewModel.uiState.value.searchResults.size
            )
        }
}
