package com.naviveylin.auto

import android.content.Context
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeMapScreenClient
import com.framstag.libosmscout.client.PoiCategories
import com.framstag.libosmscout.client.PoiEntry
import com.naviveylin.core.AutoClientProvider
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoLocationProvider
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.search.SearchSlots
import dagger.hilt.EntryPoints
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The car POI results screen's search request (spec: `poi-search`, spec: `search-request-lifecycle`).
 *
 * The screen is button-triggered (a category row was tapped), so its gate has no debounce and no minimum
 * text — but it must still run in a slot of its own and pass the request's generation to the native POI
 * search, which is what makes the walk abandonable.
 *
 * The half that asserts a *cancelled generation* while the walk is still running is the gate's own case
 * (`SearchGateTest#abandonStopsTheRunningSearch`): this double answers synchronously, so a search never
 * stays running long enough to be observed from here. What is asserted here is the wiring the gate cannot
 * prove for this screen: the slot, the generation-carrying call, and the no-fix path's sentence.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PoiResultsScreenSearchLifecycleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val carContext = testCarContext()
    private val navigationViewModel = mockk<NavigationViewModel>(relaxed = true)
    private val entryPoint = mockk<AutoEntryPoint>()
    private val client = FakeMapScreenClient()
    private val locationProvider = mockk<AutoLocationProvider>()

    @Before
    fun setUp() {
        every { carContext.applicationContext } returns ApplicationProvider.getApplicationContext<Context>()
        mockkStatic(EntryPoints::class)
        every { EntryPoints.get(any(), AutoEntryPoint::class.java) } returns entryPoint
        every { entryPoint.autoClientProvider() } returns mockk<AutoClientProvider>().also { provider ->
            every { provider.client() } returns client
        }
        every { entryPoint.autoLocationProvider() } returns locationProvider
        every { locationProvider.position() } returns MutableStateFlow(AutoPosition(lat = 51.5, lon = 7.4))
    }

    private fun newScreen() = PoiResultsScreen(
        carContext,
        navigationViewModel,
        PoiCategories.RESTAURANTS,
        "Restaurants",
        mainDispatcher = mainDispatcherRule.dispatcher,
        ioDispatcher = mainDispatcherRule.dispatcher
    )

    /** Drive the screen to the started state, so a destroy is a real down edge. */
    private fun startScreen(screen: PoiResultsScreen) {
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
    }

    private fun poi(label: String): PoiEntry = PoiEntry().apply {
        this.label = label
        objectType = "amenity_restaurant"
        distance = 600.0
        lat = 51.51
        lon = 7.46
    }

    private fun titles(template: ListTemplate): List<String> =
        template.singleList!!.items.map { (it as Row).title.toString() }

    /** Spec: `poi-search` — the car POI search keeps its own slot, and it carries a generation. */
    @Test
    fun theCarPoiSearchRunsInItsOwnSlot() = runTest(mainDispatcherRule.dispatcher) {
        client.nextPoiResults = arrayOf(poi("Mensa"))

        val screen = newScreen()
        advanceUntilIdle()

        assertEquals(listOf(SearchSlots.CAR_POI), client.begunSearchSlots)
        assertEquals("the search carries the request's generation", 1, client.poiSearchGenerations.size)
        assertTrue(
            "the rows the search returned are shown",
            titles(screen.onGetTemplate() as ListTemplate).contains("Mensa")
        )
    }

    /** The screen's own path is unchanged when no fix exists: a sentence instead of a search. */
    @Test
    fun withoutAFixTheScreenExplainsInsteadOfSearching() = runTest(mainDispatcherRule.dispatcher) {
        every { locationProvider.position() } returns MutableStateFlow(null)

        val screen = newScreen()
        advanceUntilIdle()

        assertEquals("the request is begun, so the screen can still be abandoned", listOf(SearchSlots.CAR_POI), client.begunSearchSlots)
        assertTrue("no native POI search runs without a position", client.poiSearchGenerations.isEmpty())
        assertEquals(
            listOf(
                carContext.getString(
                    com.naviveylin.core.R.string.navigation_failure_gps_signal_missing
                )
            ),
            titles(screen.onGetTemplate() as ListTemplate)
        )
    }

    /** Leaving the screen ends its own work; the search's cancel path is asserted where it is observable. */
    @Test
    fun leavingTheScreenEndsItsWork() = runTest(mainDispatcherRule.dispatcher) {
        client.nextPoiResults = arrayOf(poi("Mensa"))
        val screen = newScreen()
        startScreen(screen)
        advanceUntilIdle()

        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        advanceUntilIdle()

        val generation = client.poiSearchGenerations.single()
        assertTrue(
            "the request is over, so a later cancellation of it aborts nothing",
            client.cancelledSearches.any { it.first == generation }
        )
        assertTrue(
            "and it is no longer the slot's live request",
            client.liveSearchGenerations.values.none { it == generation }
        )
    }
}
