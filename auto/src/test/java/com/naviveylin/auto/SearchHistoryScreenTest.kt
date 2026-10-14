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
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoSearchHistoryProvider
import com.naviveylin.core.NavigationViewModel
import dagger.hilt.EntryPoints
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Template tests for [SearchHistoryScreen] (spec: `car-list-row-limit` — A car list never presents
 * more rows than the host displays; Items beyond the capacity stay reachable).
 *
 * The car search history is a car list whose length is whatever the shared store holds — up to
 * `SearchHistoryRepository.MAX_ENTRIES` = 50 entries — so it obeys the capability like the favourites
 * lists and the address book: it asks the connected host for its row capacity on the screen's own
 * scope, presents at most that many entries and keeps the remainder behind the `More (n)` row. The
 * library's own default is 6, so any host whose list budget is smaller than the store's 50 would lose
 * the older searches silently without the cap.
 *
 * The screen resolves its provider via `EntryPointAccessors`, which delegates to
 * `EntryPoints.get(applicationContext, ...)` — stub the real application context and intercept the Java
 * static delegate (the same harness as [FavoritesScreenTest] and [AddressBookScreenTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchHistoryScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val carContext = testCarContext()
    private val navigationViewModel = mockk<NavigationViewModel>()
    private val entryPoint = mockk<AutoEntryPoint>()
    private val historyProvider = mockk<AutoSearchHistoryProvider>()

    /** The host's constraint service: the row capacity of one list (spec: car-list-row-limit). */
    private val constraintManager = mockk<ConstraintManager>()

    /** The screen manager the pushes land on, with every pushed screen recorded. */
    private val screenManager = mockk<ScreenManager>(relaxed = true)
    private val pushed = mutableListOf<Screen>()

    /** How often the screen asked the host for its capacity, across all screens of a case. */
    private var capacityQueries = 0

    /** The row capacity the fake host reports for a list. */
    private var hostCapacity = CarListCapacityReader.LIBRARY_DEFAULT

    /** Eleven stored searches, youngest first — one more than the library default and than 8. */
    private val entries: List<String> = (1..11).map { "query-$it" }

    @Before
    fun setUp() {
        every { carContext.getCarService(ScreenManager::class.java) } returns screenManager
        every { screenManager.push(any<Screen>()) } answers { pushed += firstArg<Screen>() }
        // `Screen.invalidate()` asks the host through AppManager: the screen publishes the loaded
        // history and the capacity answer asynchronously, and both re-render.
        every { carContext.getCarService(AppManager::class.java) } returns mockk(relaxed = true)
        every { carContext.applicationContext } returns ApplicationProvider.getApplicationContext<Context>()
        every { carContext.carAppApiLevel } returns 7
        every { carContext.getCarService(ConstraintManager::class.java) } returns constraintManager
        every { constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST) } answers {
            capacityQueries++
            hostCapacity
        }
        mockkStatic(EntryPoints::class)
        every { EntryPoints.get(any(), AutoEntryPoint::class.java) } returns entryPoint
        coEvery { historyProvider.load() } returns entries
        every { entryPoint.autoSearchHistoryProvider() } returns historyProvider
    }

    /** A screen in its started period, as the host starts it. */
    private fun newScreen(): SearchHistoryScreen = start(
        SearchHistoryScreen(carContext, navigationViewModel)
    )

    private fun start(screen: SearchHistoryScreen): SearchHistoryScreen {
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
        return screen
    }

    /** The screens a row tap pushed, each started the way the host starts a pushed screen. */
    private fun startPushed(): List<SearchHistoryScreen> =
        pushed.filterIsInstance<SearchHistoryScreen>().onEach { start(it) }

    /** The rows of the single list a `ListTemplate` carries. */
    private fun rows(template: ListTemplate): List<Row> =
        template.singleList!!.items.filterIsInstance<Row>()

    private fun singleTitles(template: ListTemplate): List<String> =
        rows(template).map { it.title.toString() }

    private fun more(count: Int): String = carContext.getString(R.string.more_items, count)

    /** Tap the row titled [title] — the way the host delivers it: through the row's click delegate. */
    private fun tapRow(template: ListTemplate, title: String) {
        val row = rows(template).firstOrNull { it.title.toString() == title }
        assertNotNull("no row titled $title in ${singleTitles(template)}", row)
        row!!.onClickDelegate!!.sendClick(mockk(relaxed = true))
    }

    /**
     * The loaded template: the screen publishes the history from `Dispatchers.Default`, so the case
     * pumps the test scheduler while that real hop progresses and returns as soon as the list is
     * renderable. The bound is a backstop, not the assertion (spec `unit-test-suite-runtime` — A case
     * awaits the state it needs, not a proxy of it).
     */
    private fun TestScope.awaitLoaded(
        screen: SearchHistoryScreen,
        timeoutMs: Long = 5_000
    ): ListTemplate {
        val loading = listOf(carContext.getString(R.string.loading))
        val loaded = runBlocking {
            withTimeoutOrNull(timeoutMs) {
                while (true) {
                    advanceUntilIdle()
                    if (singleTitles(screen.onGetTemplate()) != loading) {
                        // The capacity answer resumes on the screen's own dispatcher: pump the turn it
                        // is queued on before the template is read, so the rows reflect the host's
                        // answer.
                        advanceUntilIdle()
                        yield()
                        advanceUntilIdle()
                        return@withTimeoutOrNull screen.onGetTemplate()
                    }
                    yield()
                }
                @Suppress("UNREACHABLE_CODE") null
            }
        }
        assertNotNull("the car search history did not load within $timeoutMs ms", loaded)
        return loaded!!
    }

    // --- The row capacity (spec: car-list-row-limit — A car list never presents more rows than the
    // host displays; Items beyond the capacity stay reachable) ---

    @Test
    fun theSearchHistoryListFitsTheHostsCapacity() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "The list fits the host's capacity" and the requirement's "SHALL take that capacity
        // from the host rather than from a fixed constant". The fake host deliberately reports a
        // capacity that is NOT the library default: a screen that ignored the host's answer and used
        // its own fallback of 6 would show `query-1 … query-6` with `More (5)`, so both the row slice
        // and the count in the page row have to follow the host's 8. The query count is what shows the
        // number is the host's rather than a constant the screen carries (the precedent is
        // FavoritesScreenTest.theTemplateBuildPerformsNoHostQuery).
        hostCapacity = 8
        val screen = newScreen()

        assertEquals(
            (1..8).map { "query-$it" } + more(3),
            singleTitles(awaitLoaded(screen))
        )
        assertEquals("one capacity query per screen start", 1, capacityQueries)
        repeat(3) { screen.onGetTemplate() }
        assertEquals("a template build queried the host", 1, capacityQueries)
    }

    @Test
    fun thePageRowOpensTheNextSliceOfTheHistory() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "The final row opens the next page" / "Every item is reachable exactly once" — the
        // page the row opens continues the same ordered history: it begins with the first entry the
        // previous page did not show and reaches the last entry.
        hostCapacity = 8
        val screen = newScreen()
        awaitLoaded(screen)
        tapRow(screen.onGetTemplate(), more(3))
        advanceUntilIdle()
        val next = startPushed().single()

        assertEquals(
            "the page begins with the first entry the previous page did not show",
            (9..11).map { "query-$it" },
            singleTitles(awaitLoaded(next))
        )
    }
}
