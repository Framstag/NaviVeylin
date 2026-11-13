package com.naviveylin.auto

import android.content.Context
import androidx.car.app.AppManager
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.constraints.ConstraintManager
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
import com.naviveylin.core.search.POI_CANDIDATE_CAP
import dagger.hilt.EntryPoints
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Template tests for the car POI results list's row cap and fetch budget (spec: `car-list-row-limit` — A
 * car list never presents more rows than the host displays; Items beyond the capacity stay reachable;
 * spec: `auto-search` — Search result limit).
 *
 * The screen is a car list whose length is whatever the POI walk returns, so it obeys the capability like
 * the favourites lists, the address book and the search history: it asks the connected host for its row
 * capacity on the screen's own scope, presents at most that many rows and keeps the fetched remainder
 * behind the `More (n)` row — and it asks the native walk for twice the rows it shows (spec: `auto-search`
 * — "at least twice the displayed maximum"), instead of for the constant it used to render.
 *
 * The harness follows [PoiResultsScreenSearchLifecycleTest] (the provider seam) and
 * [SearchHistoryScreenTest] (the fake host that answers the capacity query).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PoiResultsScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val carContext = testCarContext()
    private val navigationViewModel = mockk<NavigationViewModel>(relaxed = true)
    private val entryPoint = mockk<AutoEntryPoint>()
    private val client = FakeMapScreenClient()
    private val locationProvider = mockk<AutoLocationProvider>()

    /** The host's constraint service: the row capacity of one list (spec: car-list-row-limit). */
    private val constraintManager = mockk<ConstraintManager>()

    /** The screen manager the pushes land on, with every pushed screen recorded. */
    private val screenManager = mockk<ScreenManager>(relaxed = true)
    private val pushed = mutableListOf<Screen>()

    /** How often the screen asked the host for its capacity, across all screens of a case. */
    private var capacityQueries = 0

    /** The row capacity the fake host reports for a list. */
    private var hostCapacity = CarListCapacityReader.LIBRARY_DEFAULT

    @Before
    fun setUp() {
        every { carContext.applicationContext } returns ApplicationProvider.getApplicationContext<Context>()
        every { carContext.getCarService(ScreenManager::class.java) } returns screenManager
        every { screenManager.push(any<Screen>()) } answers { pushed += firstArg<Screen>() }
        every { carContext.getCarService(AppManager::class.java) } returns mockk(relaxed = true)
        every { carContext.carAppApiLevel } returns 7
        every { carContext.getCarService(ConstraintManager::class.java) } returns constraintManager
        every { constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST) } answers {
            capacityQueries++
            hostCapacity
        }
        mockkStatic(EntryPoints::class)
        every { EntryPoints.get(any(), AutoEntryPoint::class.java) } returns entryPoint
        every { entryPoint.autoClientProvider() } returns mockk<AutoClientProvider>().also { provider ->
            every { provider.client() } returns client
        }
        every { entryPoint.autoLocationProvider() } returns locationProvider
        every { locationProvider.position() } returns MutableStateFlow(AutoPosition(lat = 51.5, lon = 7.4))
    }

    /** A screen in its started period, as the host starts it. */
    private fun newScreen(): PoiResultsScreen = start(
        PoiResultsScreen(
            carContext,
            navigationViewModel,
            PoiCategories.RESTAURANTS,
            "Restaurants",
            mainDispatcher = mainDispatcherRule.dispatcher,
            ioDispatcher = mainDispatcherRule.dispatcher
        )
    )

    private fun start(screen: PoiResultsScreen): PoiResultsScreen {
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
        return screen
    }

    /** The screens a row tap pushed, each started the way the host starts a pushed screen. */
    private fun startPushed(): List<PoiResultsScreen> =
        pushed.filterIsInstance<PoiResultsScreen>().onEach { start(it) }

    /** The rows of the single list a `ListTemplate` carries. */
    private fun rows(template: ListTemplate): List<Row> =
        template.singleList!!.items.filterIsInstance<Row>()

    private fun titles(template: ListTemplate): List<String> =
        rows(template).map { it.title.toString() }

    private fun more(count: Int): String = carContext.getString(R.string.more_items, count)

    /** Tap the row titled [title] — the way the host delivers it: through the row's click delegate. */
    private fun tapRow(template: ListTemplate, title: String) {
        val row = rows(template).firstOrNull { it.title.toString() == title }
        assertNotNull("no row titled $title in ${titles(template)}", row)
        row!!.onClickDelegate!!.sendClick(mockk(relaxed = true))
    }

    private fun poi(label: String, operator: String? = null, brand: String? = null): PoiEntry =
        PoiEntry().apply {
            this.label = label
            this.operator = operator
            this.brand = brand
            objectType = "amenity_restaurant"
            distance = 600.0
            lat = 51.51
            lon = 7.46
        }

    /** A screen carrying [term] from the start, as the filter screen hands it back (design D5). */
    private fun filteredScreen(term: String): PoiResultsScreen = start(
        PoiResultsScreen(
            carContext,
            navigationViewModel,
            PoiCategories.RESTAURANTS,
            "Restaurants",
            initialFilter = term,
            mainDispatcher = mainDispatcherRule.dispatcher,
            ioDispatcher = mainDispatcherRule.dispatcher
        )
    )

    private fun poiResults(count: Int): Array<PoiEntry> = (1..count).map { poi("POI $it") }.toTypedArray()

    // --- The row cap and the fetch budget (spec: car-list-row-limit — A car list never presents more rows
    // than the host displays; spec: auto-search — Search result limit) ---

    @Test
    fun thePoiResultsListFitsTheHostsCapacity() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "The list fits the host's capacity" and the requirement's "SHALL take that capacity from
        // the host rather than from a fixed constant". The fake host deliberately reports a capacity that
        // is NOT the library default: a screen that ignored the host's answer and used its own fallback of
        // 6 would show `POI 1 … POI 6` with `More (10)`, so both the row slice and the count in the page
        // row have to follow the host's 8. The capacity query count is what shows the number is the
        // host's rather than a constant the screen carries (the precedent is
        // FavoritesScreenTest.theTemplateBuildPerformsNoHostQuery).
        hostCapacity = 8
        client.nextPoiResults = poiResults(20)
        val screen = newScreen()
        advanceUntilIdle()

        // The filter row is the list's first row and the results keep the host's capacity behind it: the
        // filter is chrome of the screen, not a content row of the slice (spec: `auto-search` — The filter
        // row sits above the results; spec: `car-list-row-limit` — The POI results list fits the host's
        // capacity).
        assertEquals(
            listOf(carContext.getString(R.string.poi_filter_label)) + (1..8).map { "POI $it" } + more(8),
            titles(screen.onGetTemplate() as ListTemplate)
        )
        assertEquals("one capacity query per screen start", 1, capacityQueries)
        repeat(3) { screen.onGetTemplate() }
        assertEquals("a template build queried the host", 1, capacityQueries)
    }

    @Test
    fun thePoiSearchAsksForTwiceTheHostsCapacity() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: `auto-search` — "Search result limit": the walk SHALL be asked for at least twice the rows
        // the screen shows. Two rows per candidate page is not enough for the page row to continue the
        // list, so the budget follows the host's answer rather than the 20 the screen used to carry.
        hostCapacity = 8
        client.nextPoiResults = poiResults(20)
        newScreen()
        advanceUntilIdle()

        assertEquals(
            "the walk is asked for twice the host's capacity",
            listOf(16),
            client.poiSearchCandidateCounts
        )
    }

    @Test
    fun thePageRowCarriesTheAlreadyFetchedRows() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "The final row opens the next page" / "Every item is reachable exactly once" — the page
        // the row opens continues the same ordered rows: it begins with the first row the previous page did
        // not show, and it presents rows the first search already fetched rather than paying for a second
        // native walk.
        hostCapacity = 8
        client.nextPoiResults = poiResults(20)
        val screen = newScreen()
        advanceUntilIdle()
        tapRow(screen.onGetTemplate() as ListTemplate, more(8))
        advanceUntilIdle()
        val next = startPushed().single()
        advanceUntilIdle()

        assertEquals(
            "the page begins with the first row the previous page did not show",
            listOf(carContext.getString(R.string.poi_filter_label)) + (9..16).map { "POI $it" },
            titles(next.onGetTemplate() as ListTemplate)
        )
        assertEquals(
            "the page re-ran no native walk: it slices rows the first search already fetched",
            1,
            client.poiSearchGenerations.size
        )
    }

    // --- The name filter on the car (spec: `auto-search` — A car POI result list can be narrowed by
    // name; A car POI row names its operator or brand) ---

    @Test
    fun theFilterRowSitsAboveTheResults() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "The filter row sits above the results" — first row, and the active term as its text.
        client.nextPoiResults = arrayOf(poi("REWE Markt"))
        val screen = newScreen()
        advanceUntilIdle()
        assertEquals(
            listOf(carContext.getString(R.string.poi_filter_label), "REWE Markt"),
            titles(screen.onGetTemplate() as ListTemplate)
        )

        client.nextPoiResults = arrayOf(poi("REWE Markt"))
        val filtered = filteredScreen("rewe")
        advanceUntilIdle()
        assertEquals(
            "an active term is the filter row's text",
            "rewe",
            rows(filtered.onGetTemplate() as ListTemplate).first().title.toString()
        )
    }

    @Test
    fun theHeaderCarriesTheActiveTerm() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "The header carries the active term" — the category and the term, not the category alone.
        client.nextPoiResults = arrayOf(poi("REWE Markt"))
        val screen = filteredScreen("rewe")
        advanceUntilIdle()

        val template = screen.onGetTemplate() as ListTemplate
        assertEquals("Restaurants · rewe", template.header!!.title.toString())
    }

    @Test
    fun aFilteredWalkAsksForTheCandidateCap() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "A filtered walk asks for the candidate cap" — under a term the walk is asked for the
        // shared cap (design D2), so the filter narrows the radius rather than the nearest page of it.
        hostCapacity = 8
        client.nextPoiResults = arrayOf(poi("REWE Markt"))
        filteredScreen("rewe")
        advanceUntilIdle()

        assertEquals(
            "a filtered walk is asked for the candidate cap, not for twice the row capacity",
            listOf(POI_CANDIDATE_CAP),
            client.poiSearchCandidateCounts
        )
    }

    @Test
    fun aRowTitleCarriesTheOperator() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "A row title carries the operator" — the shared composer, so the row shows what matched.
        client.nextPoiResults = arrayOf(poi("Supermarkt", operator = "REWE"))
        val screen = filteredScreen("rewe")
        advanceUntilIdle()

        assertEquals(
            "Supermarkt (REWE)",
            rows(screen.onGetTemplate() as ListTemplate)[1].title.toString()
        )
    }

    @Test
    fun anUnnamedRowShowsTheOperatorAlone() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "An unnamed row shows the operator alone".
        client.nextPoiResults = arrayOf(poi("", operator = "REWE Markt"))
        val screen = filteredScreen("rewe")
        advanceUntilIdle()

        assertEquals(
            "REWE Markt",
            rows(screen.onGetTemplate() as ListTemplate)[1].title.toString()
        )
    }

    @Test
    fun theTextLineKeepsTypeAndDistance() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "The text line keeps type and distance" — the title is the composer's, the text line keeps
        // the two facts the row carried before the composer moved.
        client.nextPoiResults = arrayOf(poi("REWE Markt"))
        val screen = newScreen()
        advanceUntilIdle()

        assertEquals(
            "amenity_restaurant · 600 m away",
            rows(screen.onGetTemplate() as ListTemplate)[1].texts.single().toString()
        )
    }
}
