package com.naviveylin.auto

import android.content.Context
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.addressbook.AddressBookContactsProvider
import com.naviveylin.core.addressbook.AddressBookSearchProvider
import com.naviveylin.core.addressbook.ContactAddressBookEntry
import com.naviveylin.core.addressbook.ContactPostalAddress
import dagger.hilt.EntryPoints
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
 * Template tests for [AddressBookScreen] (spec: `car-list-row-limit` — A car list never presents more
 * rows than the host displays; Items beyond the capacity stay reachable; `address-book-search` for the
 * contact list itself).
 *
 * The address book is a car list whose length is whatever the device's address book holds, so it obeys
 * the capability like the favourites lists: it asks the connected host for its row capacity on the
 * screen's own period, presents at most that many contacts and keeps the remainder behind the `More`
 * row. The search text belongs to the page it opened: a page that dropped it would continue a different
 * list than the one the driver was looking at.
 *
 * The screen resolves its providers via `EntryPointAccessors`, which delegates to
 * `EntryPoints.get(applicationContext, ...)` — stub the real application context and intercept the Java
 * static delegate (the same harness as [FavoritesScreenTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AddressBookScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val carContext = testCarContext()
    private val navigationViewModel = mockk<NavigationViewModel>()
    private val entryPoint = mockk<AutoEntryPoint>()

    /** The host's constraint service: the row capacity of one list (spec: car-list-row-limit). */
    private val constraintManager = mockk<ConstraintManager>()

    /** The screen manager the pushes land on, with every pushed screen recorded. */
    private val screenManager = mockk<ScreenManager>(relaxed = true)
    private val pushed = mutableListOf<Screen>()

    /** How often the screen asked the host for its capacity, across all screens of a case. */
    private var capacityQueries = 0

    /** The car API level the fake host reports (car-app 1.7.0's `CarContext.getCarAppApiLevel`). */
    private var hostApiLevel = 7

    /** The row capacity the fake host reports for a list. */
    private var hostCapacity = CarListCapacityReader.LIBRARY_DEFAULT

    /** Contacts with one address each: seven matching `Alpha`, one that does not. */
    private val contacts: List<ContactAddressBookEntry> =
        (1..7).map { contact(it.toLong(), "Alpha$it") } + contact(8L, "Bob")

    @Before
    fun setUp() {
        every { carContext.getCarService(ScreenManager::class.java) } returns screenManager
        every { screenManager.push(any<Screen>()) } answers { pushed += firstArg<Screen>() }
        // `Screen.invalidate()` asks the host through AppManager, so the service is reached as the
        // contact load and the capacity read publish their results.
        every { carContext.getCarService(AppManager::class.java) } returns mockk(relaxed = true)
        every { carContext.applicationContext } returns ApplicationProvider.getApplicationContext<Context>()
        every { carContext.carAppApiLevel } answers { hostApiLevel }
        every { carContext.getCarService(ConstraintManager::class.java) } returns constraintManager
        every { constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST) } answers {
            capacityQueries++
            hostCapacity
        }
        mockkStatic(EntryPoints::class)
        every { EntryPoints.get(any(), AutoEntryPoint::class.java) } returns entryPoint
        val contactsProvider = mockk<AddressBookContactsProvider>()
        coEvery { contactsProvider.contactsWithAddresses() } returns contacts
        every { entryPoint.addressBookContactsProvider() } returns contactsProvider
        every { entryPoint.addressBookSearchProvider() } returns mockk<AddressBookSearchProvider>(relaxed = true)
    }

    /** A screen in its started period, as the host starts a pushed screen. */
    private fun newScreen(): AddressBookScreen = start(
        AddressBookScreen(carContext, navigationViewModel)
    )

    private fun start(screen: AddressBookScreen): AddressBookScreen {
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
        return screen
    }

    /** The screens a row tap pushed, each started the way the host starts a pushed screen. */
    private fun startPushed(): List<AddressBookScreen> =
        pushed.filterIsInstance<AddressBookScreen>().onEach { start(it) }

    /** The rows of the template's list. */
    private fun rows(template: SearchTemplate): List<Row> =
        template.itemList!!.items.filterIsInstance<Row>()

    private fun titles(template: SearchTemplate): List<String> =
        rows(template).map { it.title.toString() }

    private fun more(count: Int): String = carContext.getString(R.string.more_items, count)

    /** Tap the row titled [title] — the way the host delivers it: through the row's click delegate. */
    private fun tapRow(template: SearchTemplate, title: String) {
        val row = rows(template).firstOrNull { it.title.toString() == title }
        assertNotNull("no row titled $title in ${titles(template)}", row)
        row!!.onClickDelegate!!.sendClick(mockk(relaxed = true))
    }

    /**
     * The loaded template: the screen publishes the contacts from `Dispatchers.Default`, so the case
     * pumps the test scheduler while that real hop progresses and returns as soon as the list is
     * renderable. The bound is a backstop, not the assertion (spec `unit-test-suite-runtime` — A case
     * awaits the state it needs, not a proxy of it).
     */
    private fun TestScope.awaitLoaded(screen: AddressBookScreen, timeoutMs: Long = 5_000): SearchTemplate {
        val loaded = runBlocking {
            withTimeoutOrNull(timeoutMs) {
                while (true) {
                    advanceUntilIdle()
                    val template = screen.onGetTemplate()
                    if (!template.isLoading) {
                        // The capacity answer resumes on the screen's own dispatcher: pump the turn it is
                        // queued on before the template is read, so the rows reflect the host's answer.
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
        assertNotNull("the address book list did not load within $timeoutMs ms", loaded)
        return loaded!!
    }

    // --- The row capacity (spec: car-list-row-limit — A car list never presents more rows than the
    // host displays; Items beyond the capacity stay reachable) ---

    @Test
    fun theAddressBookListFitsTheHostsCapacity() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "The list fits the host's capacity" and the requirement's "SHALL take that capacity from
        // the host rather than from a fixed constant" — the address book is a car list, so its template
        // carries at most the host's rows and the remainder stays behind the page row. This host reports
        // the library default, which is also the floor the screen falls back to, so the asserted cap holds
        // whether or not the answer has arrived yet; the query count is what shows the number is the
        // host's rather than a constant the screen carries (the precedent is
        // FavoritesScreenTest.theTemplateBuildPerformsNoHostQuery).
        val screen = newScreen()

        assertEquals(
            (1..6).map { "Alpha$it" } + more(2),
            titles(awaitLoaded(screen))
        )
        // The read belongs to the screen's start and runs off the host thread (design D2): one query per
        // screen start, and a template build — which the host calls from its own answering path — asks the
        // host nothing.
        assertEquals("one capacity query per screen start", 1, capacityQueries)
        repeat(3) { screen.onGetTemplate() }
        assertEquals("a template build queried the host", 1, capacityQueries)
    }

    @Test
    fun thePageRowCarriesTheSearchTextToTheNextPage() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "The final row opens the next page" / "Every item is reachable exactly once" — the
        // address book's items are the *filtered* contacts, so the page the row opens continues that same
        // filtered sequence: it begins with the first matching contact the previous page did not show and
        // never re-serves a contact the search text excluded.
        val screen = newScreen()
        awaitLoaded(screen)
        screen.onGetTemplate().searchCallbackDelegate!!.sendSearchTextChanged("Alpha", mockk(relaxed = true))
        advanceTimeBy(AddressBookScreenMapper.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        assertEquals((1..6).map { "Alpha$it" } + more(1), titles(screen.onGetTemplate()))
        tapRow(screen.onGetTemplate(), more(1))
        advanceUntilIdle()
        val next = startPushed().single()

        assertEquals(
            "the page begins with the first matching contact the previous page did not show",
            listOf("Alpha7"),
            titles(awaitLoaded(next))
        )
    }

    private fun contact(id: Long, name: String) = ContactAddressBookEntry(
        id,
        name,
        listOf(ContactPostalAddress(street = "$name Street 1", city = "Berlin"))
    )
}
