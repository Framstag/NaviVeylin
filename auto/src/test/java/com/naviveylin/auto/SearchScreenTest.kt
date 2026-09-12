package com.naviveylin.auto

import android.content.Context
import android.content.pm.PackageManager
import androidx.car.app.OnDoneCallback
import androidx.car.app.ScreenManager
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.core.AutoSearchHistoryProvider
import com.naviveylin.core.AutoSearchProvider
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.addressbook.AddressBookContactsProvider
import com.naviveylin.core.addressbook.AddressBookSearchProvider
import dagger.hilt.EntryPoints
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Template tests for [SearchScreen] (spec: auto-search-suggestions).
 *
 * Empty query shows mode rows + recent searches, the contacts row is gated on
 * `READ_CONTACTS`, typing replaces the suggestions with places results,
 * clearing restores them, a no-results query keeps the mode rows below the
 * "No results found" row, and a history tap pushes a prefilled [SearchScreen].
 *
 * The screen's `mainDispatcher` / `ioDispatcher` are injected with the test
 * dispatcher so all coroutine work (history load, debounced search) is driven
 * deterministically via the shared [kotlinx.coroutines.test.TestCoroutineScheduler].
 */
@RunWith(RobolectricTestRunner::class)
class SearchScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val carContext = testCarContext()
    private val navigationViewModel = mockk<NavigationViewModel>()
    private val screenManager = mockk<ScreenManager>(relaxed = true)
    private val entryPoint = mockk<AutoEntryPoint>()
    private val searchProvider = mockk<AutoSearchProvider>()
    private val historyProvider = mockk<AutoSearchHistoryProvider>()
    private val favoritesProvider = mockk<AutoFavoritesProvider>(relaxed = true)

    @Before
    fun setUp() {
        every { carContext.getCarService(ScreenManager::class.java) } returns screenManager
        // SearchScreen resolves its providers via EntryPointAccessors, which
        // delegates to EntryPoints.get(applicationContext, ...). Stub the real
        // application context and intercept the Java static delegate (the
        // Kotlin object's @JvmStatic method cannot be mocked reliably).
        every { carContext.applicationContext } returns ApplicationProvider.getApplicationContext<Context>()
        mockkStatic(EntryPoints::class)
        every { EntryPoints.get(any(), AutoEntryPoint::class.java) } returns entryPoint
        every { entryPoint.autoSearchProvider() } returns searchProvider
        every { entryPoint.autoSearchHistoryProvider() } returns historyProvider
        every { entryPoint.autoFavoritesProvider() } returns favoritesProvider
        every { favoritesProvider.favoriteLocations() } returns MutableStateFlow(emptyMap())
        // AddressBookScreen (pushed from the contacts mode row) loads contacts
        // in init; keep it inert so the push is side-effect free.
        every { entryPoint.addressBookContactsProvider() } returns mockk<AddressBookContactsProvider>(relaxed = true)
        every { entryPoint.addressBookSearchProvider() } returns mockk<AddressBookSearchProvider>(relaxed = true)
        mockkStatic("androidx.core.content.ContextCompat")
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(any(), any())
        } returns PackageManager.PERMISSION_DENIED
    }

    private fun newScreen(initialQuery: String? = null) = SearchScreen(
        carContext, navigationViewModel,
        initialQuery = initialQuery,
        mainDispatcher = mainDispatcherRule.dispatcher,
        ioDispatcher = mainDispatcherRule.dispatcher
    )

    private fun titles(template: SearchTemplate): List<String> =
        template.itemList!!.items.map { (it as Row).title.toString() }

    /** Invoke a row's click delegate (host-side callback, no-op response). */
    private fun click(row: Row) = row.onClickDelegate!!.sendClick(object : OnDoneCallback {})

    private fun resultEntry(label: String): LocationEntry = LocationEntry().apply {
        this.label = label
        lat = 51.5136
        lon = 7.4653
        name = label
    }

    @Test
    fun emptyQueryShowsModeRowsAndHistory() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { historyProvider.load() } returns listOf("Dortmund Hbf", "Café Central")
        val screen = newScreen()
        advanceUntilIdle()

        val template = screen.onGetTemplate() as SearchTemplate
        assertEquals(
            listOf("Search POIs near me", "Recent searches", "Dortmund Hbf", "Café Central"),
            titles(template)
        )
    }

    @Test
    fun contactsRowHiddenWithoutPermission() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { historyProvider.load() } returns emptyList()
        val screen = newScreen()
        advanceUntilIdle()

        val template = screen.onGetTemplate() as SearchTemplate
        assertFalse("contacts row must be hidden without READ_CONTACTS", titles(template).contains("Search contacts"))
    }

    @Test
    fun contactsRowShownWithPermission() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { historyProvider.load() } returns emptyList()
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(any(), any())
        } returns PackageManager.PERMISSION_GRANTED
        val screen = newScreen()
        advanceUntilIdle()

        val template = screen.onGetTemplate() as SearchTemplate
        assertTrue("contacts row must be shown with READ_CONTACTS", titles(template).contains("Search contacts"))
    }

    @Test
    fun typingReplacesSuggestionsWithResults() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { historyProvider.load() } returns listOf("Dortmund Hbf")
        every {
            searchProvider.searchLocations("Dortmund", SearchScreenMapper.MAX_RESULTS)
        } returns listOf(resultEntry("Dortmund Hbf"))
        val screen = newScreen()
        advanceUntilIdle()

        // Suggestions first (history row present).
        assertTrue(titles(screen.onGetTemplate() as SearchTemplate).contains("Dortmund Hbf"))

        screen.SearchCallbackImpl().onSearchTextChanged("Dortmund")
        advanceTimeBy(SearchScreenMapper.SEARCH_DEBOUNCE_MS)
        advanceUntilIdle()

        // Typing replaced the suggestions with the places result row.
        assertEquals(listOf("Dortmund Hbf"), titles(screen.onGetTemplate() as SearchTemplate))
    }

    @Test
    fun clearingRestoresSuggestions() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { historyProvider.load() } returns listOf("Dortmund Hbf")
        every {
            searchProvider.searchLocations("Dortmund", SearchScreenMapper.MAX_RESULTS)
        } returns listOf(resultEntry("Dortmund Hbf"))
        val screen = newScreen()
        advanceUntilIdle()

        screen.SearchCallbackImpl().onSearchTextChanged("Dortmund")
        advanceTimeBy(SearchScreenMapper.SEARCH_DEBOUNCE_MS)
        advanceUntilIdle()
        assertEquals(listOf("Dortmund Hbf"), titles(screen.onGetTemplate() as SearchTemplate))

        screen.SearchCallbackImpl().onSearchTextChanged("")
        assertEquals(
            listOf("Search POIs near me", "Recent searches", "Dortmund Hbf"),
            titles(screen.onGetTemplate() as SearchTemplate)
        )
    }

    @Test
    fun noResultsShowsNoResultsRowThenModeRows() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { historyProvider.load() } returns emptyList()
        every {
            searchProvider.searchLocations("xyz", SearchScreenMapper.MAX_RESULTS)
        } returns emptyList()
        val screen = newScreen()
        advanceUntilIdle()

        screen.SearchCallbackImpl().onSearchTextChanged("xyz")
        advanceTimeBy(SearchScreenMapper.SEARCH_DEBOUNCE_MS)
        advanceUntilIdle()

        val template = screen.onGetTemplate() as SearchTemplate
        assertEquals(
            listOf("No results found", "Search POIs near me"),
            titles(template)
        )
    }

    @Test
    fun historyTapPushesScreenWithQuery() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { historyProvider.load() } returns listOf("Dortmund Hbf")
        every {
            searchProvider.searchLocations("Dortmund Hbf", SearchScreenMapper.MAX_RESULTS)
        } returns emptyList()
        val screen = newScreen()
        advanceUntilIdle()

        val template = screen.onGetTemplate() as SearchTemplate
        click(template.itemList!!.items[2] as Row)

        verify { screenManager.push(any<SearchScreen>()) }
        // The pushed screen runs the search for the tapped query (design D2).
        advanceTimeBy(SearchScreenMapper.SEARCH_DEBOUNCE_MS)
        advanceUntilIdle()
        verify { searchProvider.searchLocations("Dortmund Hbf", SearchScreenMapper.MAX_RESULTS) }
    }

    @Test
    fun favoriteHitListedAboveNativeResults() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { historyProvider.load() } returns emptyList()
        every {
            searchProvider.searchLocations("home", SearchScreenMapper.MAX_RESULTS)
        } returns listOf(resultEntry("Home Street"))
        every { favoritesProvider.favoriteLocations() } returns MutableStateFlow(
            mapOf("Home" to listOf(FavoriteLocation("Home", 51.5, 7.4)))
        )
        val screen = newScreen()
        advanceUntilIdle()

        screen.SearchCallbackImpl().onSearchTextChanged("home")
        advanceTimeBy(SearchScreenMapper.SEARCH_DEBOUNCE_MS)
        advanceUntilIdle()

        // Favorite hit on top (with heart image), native result below.
        val template = screen.onGetTemplate() as SearchTemplate
        assertEquals(listOf("Home", "Home Street"), titles(template))
        assertNotNull("favorite row must carry a heart image", (template.itemList!!.items[0] as Row).image)
    }

    @Test
    fun poiModeRowPushesPoiSearchScreen() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { historyProvider.load() } returns emptyList()
        val screen = newScreen()
        advanceUntilIdle()

        val template = screen.onGetTemplate() as SearchTemplate
        click(template.itemList!!.items[0] as Row)
        verify { screenManager.push(any<PoiSearchScreen>()) }
    }

    @Test
    fun contactsModeRowPushesAddressBookScreen() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { historyProvider.load() } returns emptyList()
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(any(), any())
        } returns PackageManager.PERMISSION_GRANTED
        val screen = newScreen()
        advanceUntilIdle()

        val template = screen.onGetTemplate() as SearchTemplate
        click(template.itemList!!.items[1] as Row)
        verify { screenManager.push(any<AddressBookScreen>()) }
    }
}
