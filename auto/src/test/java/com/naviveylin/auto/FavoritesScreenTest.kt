package com.naviveylin.auto

import android.content.Context
import android.os.Looper
import androidx.car.app.AppManager
import androidx.car.app.ScreenManager
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.StarredFavoriteLocation
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.core.NavigationViewModel
import dagger.hilt.EntryPoints
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.verify
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
 * Template tests for [FavoritesScreen] (spec: auto-favorites — favorites
 * appear without re-entering the screen).
 *
 * The screen collects the favorites flow reactively: it shows "Loading" until
 * the first emission, then renders the list (or the empty state) and updates in
 * place when the store finishes loading after the screen is already open. The
 * collect is cancelled when the screen is destroyed so collectors do not
 * accumulate across open/close cycles.
 *
 * The screen resolves its providers via `EntryPointAccessors`, which delegates
 * to `EntryPoints.get(applicationContext, ...)` — stub the real application
 * context and intercept the Java static delegate (the Kotlin object's
 * `@JvmStatic` method cannot be mocked reliably, see ki_processing_failures.log).
 */
@RunWith(RobolectricTestRunner::class)
class FavoritesScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val carContext = testCarContext()
    private val navigationViewModel = mockk<NavigationViewModel>()
    private val entryPoint = mockk<AutoEntryPoint>()
    private val favoritesProvider = FakeFavoritesProvider()

    @Before
    fun setUp() {
        every { carContext.getCarService(ScreenManager::class.java) } returns mockk(relaxed = true)
        // `Screen.invalidate()` asks the host through AppManager: the screen's list observation is
        // only invalidated while it is started, so this service is reached now (an unstubbed service
        // makes the observation fault instead of updating the list).
        every { carContext.getCarService(AppManager::class.java) } returns mockk(relaxed = true)
        every { carContext.applicationContext } returns ApplicationProvider.getApplicationContext<Context>()
        mockkStatic(EntryPoints::class)
        every { EntryPoints.get(any(), AutoEntryPoint::class.java) } returns entryPoint
        every { entryPoint.autoFavoritesProvider() } returns favoritesProvider
    }

    /**
     * A screen in its started period. The favorites/order observation lives on that period
     * (spec: auto/screen-observation; design D6), so a case that wants the list to observe the store
     * has to start the screen the way the host does.
     */
    private fun newScreen(starredOnly: Boolean = false): FavoritesScreen {
        val screen = FavoritesScreen(carContext, navigationViewModel, starredOnly)
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
        return screen
    }

    private fun singleTitles(template: ListTemplate): List<String> =
        template.singleList!!.items.map { (it as Row).title.toString() }

    private fun sectionTitles(template: ListTemplate): List<String> =
        template.sectionedLists.flatMap { section ->
            section.itemList.items.map { (it as Row).title.toString() }
        }

    /** The section headers (group names), in template order. */
    private fun sectionHeaders(template: ListTemplate): List<String> =
        template.sectionedLists.map { it.header.toString() }

    private fun fav(name: String) = FavoriteLocation(name, 51.5136, 7.4653)

    @Test
    fun screenTitleIsTheFavoritesResource() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: i18n-l10n — All user-facing text is translatable: the title is a
        // resource, never an English literal in the screen.
        val screen = newScreen()
        val template = screen.onGetTemplate()

        assertEquals(
            carContext.getString(R.string.favorites),
            template.header!!.title.toString()
        )
    }

    @Test
    fun starredScreenTitleIsTheStarredFavoritesResource() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen(starredOnly = true)
        val template = screen.onGetTemplate()

        assertEquals(
            carContext.getString(R.string.starred_favorites),
            template.header!!.title.toString()
        )
        assertEquals("Starred favorites", template.header!!.title.toString())
    }

    @Test
    fun unnamedFavoriteRowUsesTheResource() = runTest(mainDispatcherRule.dispatcher) {
        // A favorite the store holds without a name shows the resource row title,
        // not an English literal built in the screen (spec: i18n-l10n — All
        // user-facing text is translatable).
        favoritesProvider.flow.value =
            mapOf("Favorites" to listOf(FavoriteLocation(null, 51.5136, 7.4653)))
        val screen = newScreen()
        advanceUntilIdle()
        val template = screen.onGetTemplate()

        val titles = sectionTitles(template)
        assertTrue("row titles were $titles", titles.contains(carContext.getString(R.string.unnamed_favorite)))
    }

    @Test
    fun showsLoadingBeforeFirstEmission() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        // The collect has not run yet (StandardTestDispatcher): the first
        // template is the loading row.
        assertEquals(listOf("Loading..."), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun emptyStoreShowsNoFavoritesSaved() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        advanceUntilIdle()
        assertEquals(listOf("No favorites saved"), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun lateEmissionUpdatesListWithoutReentering() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        advanceUntilIdle() // collect runs, store still empty
        assertEquals(listOf("No favorites saved"), singleTitles(screen.onGetTemplate()))

        // The store finishes loading while the screen is already open.
        favoritesProvider.flow.value = mapOf("Favorites" to listOf(fav("Home"), fav("Work")))
        advanceUntilIdle()

        assertEquals(listOf("Home", "Work"), sectionTitles(screen.onGetTemplate()))
    }

    @Test
    fun scopeCancelledOnDestroy() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        favoritesProvider.flow.value = mapOf("Favorites" to listOf(fav("Home")))
        advanceUntilIdle()
        assertEquals(listOf("Home"), sectionTitles(screen.onGetTemplate()))

        // Destroy the screen via the real lifecycle path: the observation must
        // stop. `newScreen()` already drove the registry to STARTED, so only the
        // down edge is left (dispatchLifecycleEvent runs synchronously on the
        // Robolectric main thread via ThreadUtils.runOnMain).
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()

        favoritesProvider.flow.value = mapOf("Favorites" to listOf(fav("Home"), fav("Work")))
        advanceUntilIdle()

        // Still the pre-destroy data: the cancelled collect did not re-render.
        assertEquals(listOf("Home"), sectionTitles(screen.onGetTemplate()))
    }

    @Test
    fun aStoppedScreenAppliesNothingAndTheStateArrivesOnStart() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: auto/screen-observation — A stopped screen performs no renderer or host work, and
        // Observations are re-established with the current state on start; auto-favorites — a group
        // reorder received while the list is stopped is applied on the next start.
        val screen = newScreen()
        favoritesProvider.flow.value = mapOf("Favorites" to listOf(fav("Home")))
        advanceUntilIdle()
        assertEquals(listOf("Home"), sectionTitles(screen.onGetTemplate()))

        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_STOP)
        advanceUntilIdle()

        // The phone adds a favorite while the car list is stopped.
        favoritesProvider.flow.value = mapOf("Favorites" to listOf(fav("Home"), fav("Work")))
        advanceUntilIdle()

        assertEquals(
            "a stopped screen applies nothing (and invalidates nothing)",
            listOf("Home"),
            sectionTitles(screen.onGetTemplate())
        )

        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
        advanceUntilIdle()

        assertEquals(
            "the current state is applied on start",
            listOf("Home", "Work"),
            sectionTitles(screen.onGetTemplate())
        )
    }

    @Test
    fun listFollowsTheStoredFavoriteOrder() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        favoritesProvider.flow.value = mapOf(
            "Cities" to listOf(fav("Rome"), fav("Berlin"), fav("Paris"))
        )
        advanceUntilIdle()

        assertEquals(listOf("Rome", "Berlin", "Paris"), sectionTitles(screen.onGetTemplate()))
    }

    @Test
    fun aReorderedStoreUpdatesTheListInPlace() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        favoritesProvider.flow.value = mapOf("Cities" to listOf(fav("Berlin"), fav("Rome")))
        advanceUntilIdle()
        assertEquals(listOf("Berlin", "Rome"), sectionTitles(screen.onGetTemplate()))

        // Same group, favorites reordered on the phone.
        favoritesProvider.flow.value = mapOf("Cities" to listOf(fav("Rome"), fav("Berlin")))
        advanceUntilIdle()

        assertEquals(listOf("Rome", "Berlin"), sectionTitles(screen.onGetTemplate()))
    }

    @Test
    fun rowsOfferNoReorderAffordanceAndSelectionStillNavigates() =
        runTest(mainDispatcherRule.dispatcher) {
            every { navigationViewModel.navigateTo(any(), any()) } just Runs
            val screen = newScreen()
            favoritesProvider.flow.value = mapOf("Cities" to listOf(fav("Rome"), fav("Berlin")))
            advanceUntilIdle()

            val rows = screen.onGetTemplate().sectionedLists.flatMap { section ->
                section.itemList.items.map { it as Row }
            }
            assertTrue("car rows must not carry reorder actions", rows.all { it.actions.isEmpty() })

            // Selecting a row still starts the destination-picker flow.
            rows.first().onClickDelegate?.sendClick(mockk(relaxed = true))
            verify { navigationViewModel.navigateTo(51.5136, 7.4653) }
        }

    @Test
    fun groupHeadersFollowTheStoredGroupOrder() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        favoritesProvider.flow.value = mapOf(
            "Cities" to listOf(fav("Rome")),
            "Work" to listOf(fav("Office")),
            "Home" to listOf(fav("Flat"))
        )
        favoritesProvider.order.value = listOf("Home", "Cities", "Work")
        advanceUntilIdle()

        assertEquals(listOf("Home", "Cities", "Work"), sectionHeaders(screen.onGetTemplate()))
    }

    /**
     * The conflation case the order channel exists for: the map value is unchanged,
     * so a reorder reaches the car screen only through `groupOrder`.
     */
    @Test
    fun aGroupReorderUpdatesTheHeadersInPlace() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        val groups = mapOf(
            "Cities" to listOf(fav("Rome")),
            "Work" to listOf(fav("Office"))
        )
        favoritesProvider.flow.value = groups
        favoritesProvider.order.value = listOf("Cities", "Work")
        advanceUntilIdle()
        assertEquals(listOf("Cities", "Work"), sectionHeaders(screen.onGetTemplate()))

        // Same map instance, same contents — only the order moved.
        favoritesProvider.flow.value = groups
        favoritesProvider.order.value = listOf("Work", "Cities")
        advanceUntilIdle()

        assertEquals(listOf("Work", "Cities"), sectionHeaders(screen.onGetTemplate()))
    }

    @Test
    fun aGroupTheOrderDoesNotNameStillAppears() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        favoritesProvider.flow.value = mapOf(
            "Cities" to listOf(fav("Rome")),
            "Fresh" to listOf(fav("Brand new"))
        )
        favoritesProvider.order.value = listOf("Cities")
        advanceUntilIdle()

        assertEquals(listOf("Cities", "Fresh"), sectionHeaders(screen.onGetTemplate()))
    }

    @Test
    fun aNameTheStoreNoLongerHoldsIsSkipped() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        favoritesProvider.flow.value = mapOf("Cities" to listOf(fav("Rome")))
        favoritesProvider.order.value = listOf("Gone", "Cities")
        advanceUntilIdle()

        assertEquals(listOf("Cities"), sectionHeaders(screen.onGetTemplate()))
    }

    // --- Starred mode (spec auto-favorites — one ordered list; spec starred-ordering) ---

    private fun starred(vararg entries: Pair<String, String>) =
        entries.map { StarredFavoriteLocation(it.first, fav(it.second)) }

    @Test
    fun starredModeRendersTheStoredStarredOrderAsOneList() =
        runTest(mainDispatcherRule.dispatcher) {
            val screen = newScreen(starredOnly = true)
            favoritesProvider.flow.value = mapOf(
                "Cities" to listOf(fav("Berlin"), fav("Rome")),
                "Work" to listOf(fav("Office"))
            )
            // One order spanning groups: the Work favorite sits between the two
            // Cities favorites, which group sections could not express.
            favoritesProvider.starred.value = starred(
                "Cities" to "Berlin", "Work" to "Office", "Cities" to "Rome"
            )
            advanceUntilIdle()

            val template = screen.onGetTemplate()
            assertEquals(listOf("Berlin", "Office", "Rome"), singleTitles(template))
            assertTrue(
                "the starred mode is one list, not group sections",
                template.sectionedLists.isEmpty()
            )
        }

    @Test
    fun aStarredReorderUpdatesTheListInPlace() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen(starredOnly = true)
        favoritesProvider.starred.value = starred("Cities" to "Berlin", "Cities" to "Rome")
        advanceUntilIdle()
        assertEquals(listOf("Berlin", "Rome"), singleTitles(screen.onGetTemplate()))

        // Reordered on the phone while the car screen is open.
        favoritesProvider.starred.value = starred("Cities" to "Rome", "Cities" to "Berlin")
        advanceUntilIdle()

        assertEquals(listOf("Rome", "Berlin"), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun theAllFavoritesModeStillUsesGroupSections() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        favoritesProvider.flow.value = mapOf(
            "Cities" to listOf(fav("Rome")),
            "Work" to listOf(fav("Office"))
        )
        favoritesProvider.starred.value = starred("Cities" to "Rome")
        advanceUntilIdle()

        val template = screen.onGetTemplate()
        assertEquals(listOf("Cities", "Work"), sectionHeaders(template))
        assertTrue(template.singleList == null)
    }

    @Test
    fun starredModeOffersNoReorderAffordanceAndSelectionStillNavigates() =
        runTest(mainDispatcherRule.dispatcher) {
            every { navigationViewModel.navigateTo(any(), any()) } just Runs
            val screen = newScreen(starredOnly = true)
            favoritesProvider.starred.value = starred("Cities" to "Rome")
            advanceUntilIdle()

            val rows = screen.onGetTemplate().singleList!!.items.map { it as Row }
            assertTrue("car rows must not carry reorder actions", rows.all { it.actions.isEmpty() })

            rows.first().onClickDelegate?.sendClick(mockk(relaxed = true))
            verify { navigationViewModel.navigateTo(51.5136, 7.4653) }
        }

    @Test
    fun starredModeWithNothingStarredShowsTheHint() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen(starredOnly = true)
        favoritesProvider.flow.value = mapOf("Cities" to listOf(fav("Rome")))
        advanceUntilIdle()

        assertEquals(listOf("No starred favorites"), singleTitles(screen.onGetTemplate()))
    }

    /** In-memory [AutoFavoritesProvider] backed by a [MutableStateFlow]. */
    private class FakeFavoritesProvider : AutoFavoritesProvider {
        val flow = MutableStateFlow<Map<String, List<FavoriteLocation>>>(emptyMap())

        /** The group order channel the screen renders headers from. */
        val order = MutableStateFlow<List<String>>(emptyList())

        /** The starred-order channel the starred mode renders from. */
        val starred = MutableStateFlow<List<StarredFavoriteLocation>>(emptyList())

        override fun favoriteLocations() = flow
        override fun groupOrder() = order
        override fun starredOrder() = starred
        override suspend fun init(filePath: String) = true
        override suspend fun addFavorite(name: String, lat: Double, lon: Double) = true
        override suspend fun removeFavorite(lat: Double, lon: Double) = true
    }
}
