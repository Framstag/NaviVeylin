package com.naviveylin.auto

import android.content.Context
import android.os.Looper
import androidx.car.app.AppManager
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.StarredFavoriteLocation
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.SurfaceOrigin
import dagger.hilt.EntryPoints
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Template tests for [FavoritesScreen] (spec: auto-favorites — favorites
 * appear without re-entering the screen; The all-favorites mode lists groups first; spec:
 * car-list-row-limit — A car list never presents more rows than the host displays).
 *
 * The screen collects the favorites flow reactively: it shows "Loading" until
 * the first emission, then renders the level (or the empty state) and updates in
 * place when the store finishes loading after the screen is already open. The
 * collect is cancelled when the screen is destroyed so collectors do not
 * accumulate across open/close cycles. The all-favorites mode renders the group level; a group's own
 * favorites are a second screen of the same class (`groupName`), and neither level sends more rows
 * than the host reports.
 *
 * The screen resolves its providers via `EntryPointAccessors`, which delegates
 * to `EntryPoints.get(applicationContext, ...)` — stub the real application
 * context and intercept the Java static delegate (the Kotlin object's
 * `@JvmStatic` method cannot be mocked reliably, see ki_processing_failures.log).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FavoritesScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val carContext = testCarContext()
    private val navigationViewModel = mockk<NavigationViewModel>()
    private val entryPoint = mockk<AutoEntryPoint>()
    private val favoritesProvider = FakeFavoritesProvider()

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

    /** When true, the fake host's capacity query fails (a dead or unanswering host). */
    private var hostQueryFails = false

    @Before
    fun setUp() {
        every { carContext.getCarService(ScreenManager::class.java) } returns screenManager
        every { screenManager.push(any<Screen>()) } answers { pushed += firstArg<Screen>() }
        // `Screen.invalidate()` asks the host through AppManager: the screen's list observation is
        // only invalidated while it is started, so this service is reached now (an unstubbed service
        // makes the observation fault instead of updating the list).
        every { carContext.getCarService(AppManager::class.java) } returns mockk(relaxed = true)
        every { carContext.applicationContext } returns ApplicationProvider.getApplicationContext<Context>()
        // A production host: a modern car API level that answers the capacity query with the Car App
        // Library's own default, so a case has to opt into a different host explicitly.
        every { carContext.carAppApiLevel } answers { hostApiLevel }
        every { carContext.getCarService(ConstraintManager::class.java) } returns constraintManager
        every { constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST) } answers {
            capacityQueries++
            if (hostQueryFails) throw IllegalStateException("host is dead")
            hostCapacity
        }
        mockkStatic(EntryPoints::class)
        every { EntryPoints.get(any(), AutoEntryPoint::class.java) } returns entryPoint
        every { entryPoint.autoFavoritesProvider() } returns favoritesProvider
    }

    /**
     * A screen in its started period, with the hop that touches the provider owned by the test
     * scheduler (design D1's injectable dispatcher). The favorites/order observation lives on that
     * period (spec: auto/screen-observation; design D6), so a case that wants the list to observe the
     * store has to start the screen the way the host does.
     */
    private fun newScreen(
        starredOnly: Boolean = false,
        groupName: String? = null,
        page: Int = 0
    ): FavoritesScreen = start(
        FavoritesScreen(
            carContext = carContext,
            navigationViewModel = navigationViewModel,
            starredOnly = starredOnly,
            groupName = groupName,
            page = page,
            backgroundDispatcher = mainDispatcherRule.dispatcher
        )
    )

    /**
     * A screen started with no dispatcher: the production default owns the hop that resolves the
     * provider, which is what
     * [theProductionDefaultResolvesTheFavoritesProviderOnABackgroundThread] asserts.
     */
    private fun newScreenWithProductionDefault(): FavoritesScreen =
        start(FavoritesScreen(carContext, navigationViewModel))

    private fun start(screen: FavoritesScreen): FavoritesScreen {
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
        return screen
    }

    /** The screens the row taps pushed, each started the way the host starts a pushed screen. */
    private fun startPushedFavorites(): List<FavoritesScreen> =
        pushed.filterIsInstance<FavoritesScreen>().onEach { start(it) }

    /** The rows of the single list a `ListTemplate` carries. */
    private fun rows(template: ListTemplate): List<Row> =
        template.singleList!!.items.filterIsInstance<Row>()

    private fun singleTitles(template: ListTemplate): List<String> =
        rows(template).map { it.title.toString() }

    private fun fav(name: String) = FavoriteLocation(name, 51.5136, 7.4653)

    /** Tap the row titled [title] — the way the host delivers it: through the row's click delegate. */
    private fun tapRow(template: ListTemplate, title: String) {
        val row = rows(template).firstOrNull { it.title.toString() == title }
        assertNotNull("no row titled $title in ${singleTitles(template)}", row)
        row!!.onClickDelegate!!.sendClick(mockk(relaxed = true))
    }

    /**
     * Pump the test scheduler while the resolution's real background dispatcher progresses, until
     * every source accessor has been reached. The bound is a backstop, not the assertion
     * (spec `unit-test-suite-runtime` — A case awaits the state it needs, not a proxy of it).
     */
    private fun TestScope.awaitAccessorThreads(expected: Int, timeoutMs: Long = 3_000) {
        runBlocking {
            withTimeoutOrNull(timeoutMs) {
                while (favoritesProvider.accessorThreads.size < expected) {
                    advanceUntilIdle()
                    yield()
                }
                true
            }
        }
        advanceUntilIdle()
    }

    /**
     * The risk the additive dispatcher parameter carries: every production call site (RootScreen,
     * MapScreen, the session) passes no dispatcher, so the default itself must keep the provider — and
     * with it the native client's first touch, which builds it — off the car-app host thread
     * (spec: auto-map-renderer — "A screen observation resolves its provider off the host thread";
     * design D1).
     */
    @Test
    fun theProductionDefaultResolvesTheFavoritesProviderOnABackgroundThread() =
        runTest(mainDispatcherRule.dispatcher) {
            // The thread the host drives its callbacks on is the one this case runs on.
            val hostThread = Thread.currentThread()
            val screen = newScreenWithProductionDefault()

            awaitAccessorThreads(expected = 3)
            val resolved = favoritesProvider.accessorThreads.toList()

            assertEquals(
                "the case must observe every source, otherwise the guard is vacuous: " +
                    resolved.map { it.first },
                setOf("favoriteLocations", "groupOrder", "starredOrder"),
                resolved.map { it.first }.toSet()
            )
            assertTrue(
                "the production default resolved the provider on the host thread for " +
                    resolved.filter { it.second === hostThread }.map { it.first },
                resolved.none { it.second === hostThread }
            )

            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }

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
    fun groupScreenTitleIsTheGroupsName() = runTest(mainDispatcherRule.dispatcher) {
        // A group's own level names the group it shows, so the driver sees which group the rows
        // belong to after the level opened (spec: auto-favorites — A group row opens that group's
        // favorites).
        val screen = newScreen(groupName = "Cities")

        assertEquals("Cities", screen.onGetTemplate().header!!.title.toString())
    }

    @Test
    fun unnamedFavoriteRowUsesTheResource() = runTest(mainDispatcherRule.dispatcher) {
        // A favorite the store holds without a name shows the resource row title,
        // not an English literal built in the screen (spec: i18n-l10n — All
        // user-facing text is translatable).
        favoritesProvider.flow.value =
            mapOf("Favorites" to listOf(FavoriteLocation(null, 51.5136, 7.4653)))
        val screen = newScreen(groupName = "Favorites")
        advanceUntilIdle()
        val template = screen.onGetTemplate()

        val titles = singleTitles(template)
        assertTrue("row titles were $titles", titles.contains(carContext.getString(R.string.unnamed_favorite)))
    }

    @Test
    fun showsLoadingBeforeFirstEmission() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        // The collect has not run yet (StandardTestDispatcher): the first
        // template is the loading row.
        assertEquals(listOf("Loading..."), singleTitles(screen.onGetTemplate()))
    }

    // --- The store's readiness (spec auto-favorites — The favourites list distinguishes a loading
    // store from an empty one; design D3) ---

    @Test
    fun unreadyStoreShowsTheLoadingRowOnly() = runTest(mainDispatcherRule.dispatcher) {
        // The store is constructed with an empty map, so "not initialised yet" and "initialised and
        // holds no favorites" are the same content: without the readiness signal this state would
        // render the empty message and tell the driver their favorites are gone.
        // (spec: auto-favorites — An uninitialised store is not reported as empty)
        favoritesProvider.ready.value = false
        val screen = newScreen()
        advanceUntilIdle()

        val titles = singleTitles(screen.onGetTemplate())
        assertEquals(listOf("Loading..."), titles)
        assertTrue(
            "an uninitialised store must not be reported as empty, got $titles",
            !titles.contains(carContext.getString(R.string.no_favorites_saved))
        )
    }

    @Test
    fun aReadyStoresPublishedStateIsShown() = runTest(mainDispatcherRule.dispatcher) {
        // The state the store already holds is what the screen renders once the observation has
        // published it: no loading row for a known state (spec: auto-favorites — A ready store's
        // published state is shown; the row may still cover the publication itself, which on device
        // was as long as the session's first render congested the main dispatcher, TODO.md §176).
        favoritesProvider.flow.value = mapOf("Favorites" to listOf(fav("Home"), fav("Work")))
        val screen = newScreen(groupName = "Favorites")
        advanceUntilIdle()

        val titles = singleTitles(screen.onGetTemplate())
        assertEquals(listOf("Home", "Work"), titles)
        assertTrue(
            "a ready store must not render a loading or empty row, got $titles",
            titles.none {
                it == carContext.getString(R.string.loading) ||
                    it == carContext.getString(R.string.no_favorites_saved)
            }
        )
    }

    @Test
    fun readyEmptyStoreShowsTheEmptyState() = runTest(mainDispatcherRule.dispatcher) {
        // Ready and genuinely empty: the empty message is the honest state, never the loading row.
        val screen = newScreen()
        advanceUntilIdle()

        assertEquals(listOf("No favorites saved"), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun readinessCompletesWhileOpenUpdatesInPlace() = runTest(mainDispatcherRule.dispatcher) {
        favoritesProvider.ready.value = false
        favoritesProvider.flow.value = mapOf("Favorites" to listOf(fav("Home")))
        val screen = newScreen(groupName = "Favorites")
        advanceUntilIdle()
        val unreadyTemplate = screen.onGetTemplate()
        assertEquals(
            "an uninitialised store shows the loading row even though its map already holds rows",
            listOf("Loading..."),
            singleTitles(unreadyTemplate)
        )

        // The store finishes initialising while the screen is open.
        favoritesProvider.ready.value = true
        advanceUntilIdle()

        assertEquals(listOf("Home"), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun emptyStoreShowsNoFavoritesSaved() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        advanceUntilIdle()
        assertEquals(listOf("No favorites saved"), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun lateEmissionUpdatesListWithoutReentering() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen(groupName = "Favorites")
        advanceUntilIdle() // collect runs, store still empty
        assertEquals(listOf("No favorites saved"), singleTitles(screen.onGetTemplate()))

        // The store finishes loading while the screen is already open.
        favoritesProvider.flow.value = mapOf("Favorites" to listOf(fav("Home"), fav("Work")))
        advanceUntilIdle()

        assertEquals(listOf("Home", "Work"), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun scopeCancelledOnDestroy() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen(groupName = "Favorites")
        favoritesProvider.flow.value = mapOf("Favorites" to listOf(fav("Home")))
        advanceUntilIdle()
        assertEquals(listOf("Home"), singleTitles(screen.onGetTemplate()))

        // Destroy the screen via the real lifecycle path: the observation must
        // stop. `newScreen()` already drove the registry to STARTED, so only the
        // down edge is left (dispatchLifecycleEvent runs synchronously on the
        // Robolectric main thread via ThreadUtils.runOnMain).
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()

        favoritesProvider.flow.value = mapOf("Favorites" to listOf(fav("Home"), fav("Work")))
        advanceUntilIdle()

        // Still the pre-destroy data: the cancelled collect did not re-render.
        assertEquals(listOf("Home"), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun aStoppedScreenAppliesNothingAndTheStateArrivesOnStart() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: auto/screen-observation — A stopped screen performs no renderer or host work, and
        // Observations are re-established with the current state on start; auto-favorites — a group
        // reorder received while the list is stopped is applied on the next start.
        val screen = newScreen(groupName = "Favorites")
        favoritesProvider.flow.value = mapOf("Favorites" to listOf(fav("Home")))
        advanceUntilIdle()
        assertEquals(listOf("Home"), singleTitles(screen.onGetTemplate()))

        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_STOP)
        advanceUntilIdle()

        // The phone adds a favorite while the car list is stopped.
        favoritesProvider.flow.value = mapOf("Favorites" to listOf(fav("Home"), fav("Work")))
        advanceUntilIdle()

        assertEquals(
            "a stopped screen applies nothing (and invalidates nothing)",
            listOf("Home"),
            singleTitles(screen.onGetTemplate())
        )

        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
        advanceUntilIdle()

        assertEquals(
            "the current state is applied on start",
            listOf("Home", "Work"),
            singleTitles(screen.onGetTemplate())
        )
    }

    @Test
    fun listFollowsTheStoredFavoriteOrder() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen(groupName = "Cities")
        favoritesProvider.flow.value = mapOf(
            "Cities" to listOf(fav("Rome"), fav("Berlin"), fav("Paris"))
        )
        advanceUntilIdle()

        assertEquals(listOf("Rome", "Berlin", "Paris"), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun aReorderedStoreUpdatesTheListInPlace() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen(groupName = "Cities")
        favoritesProvider.flow.value = mapOf("Cities" to listOf(fav("Berlin"), fav("Rome")))
        advanceUntilIdle()
        assertEquals(listOf("Berlin", "Rome"), singleTitles(screen.onGetTemplate()))

        // Same group, favorites reordered on the phone.
        favoritesProvider.flow.value = mapOf("Cities" to listOf(fav("Rome"), fav("Berlin")))
        advanceUntilIdle()

        assertEquals(listOf("Rome", "Berlin"), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun rowsOfferNoReorderAffordanceAndSelectionStillNavigates() =
        runTest(mainDispatcherRule.dispatcher) {
            every { navigationViewModel.navigateTo(any(), any(), any(), any()) } just Runs
            val screen = newScreen(groupName = "Cities")
            favoritesProvider.flow.value = mapOf("Cities" to listOf(fav("Rome"), fav("Berlin")))
            advanceUntilIdle()

            val rows = rows(screen.onGetTemplate())
            assertTrue("car rows must not carry reorder actions", rows.all { it.actions.isEmpty() })

            // Selecting a row still starts the destination-picker flow.
            rows.first().onClickDelegate?.sendClick(mockk(relaxed = true))
            verify { navigationViewModel.navigateTo(51.5136, 7.4653, any(), SurfaceOrigin.CAR) }
        }

    // --- The all-favorites level: one row per group (spec auto-favorites — The all-favorites mode
    // lists groups first; design D1/D3) ---

    /**
     * Replaces `theAllFavoritesModeStillUsesGroupSections`, which asserted the flat sectioned list
     * the host truncated: the all-favorites mode now starts one level above it.
     */
    @Test
    fun theAllFavoritesModeStartsAtTheGroupLevel() = runTest(mainDispatcherRule.dispatcher) {
        favoritesProvider.flow.value = mapOf(
            "Cities" to listOf(fav("Rome")),
            "Work" to listOf(fav("Office"))
        )
        favoritesProvider.order.value = listOf("Cities", "Work")
        val screen = newScreen()
        advanceUntilIdle()

        val template = screen.onGetTemplate()
        assertEquals(listOf("Cities", "Work"), singleTitles(template))
        assertTrue(
            "the group level is one list of group rows, not one section per group",
            template.sectionedLists.isEmpty()
        )
    }

    @Test
    fun groupRowsFollowTheStoredGroupOrder() = runTest(mainDispatcherRule.dispatcher) {
        favoritesProvider.flow.value = mapOf(
            "Cities" to listOf(fav("Rome")),
            "Work" to listOf(fav("Office")),
            "Home" to listOf(fav("Flat"))
        )
        favoritesProvider.order.value = listOf("Home", "Cities", "Work")
        val screen = newScreen()
        advanceUntilIdle()

        assertEquals(listOf("Home", "Cities", "Work"), singleTitles(screen.onGetTemplate()))
    }

    /**
     * The conflation case the order channel exists for: the map value is unchanged,
     * so a reorder reaches the car screen only through `groupOrder`.
     */
    @Test
    fun aGroupReorderUpdatesTheRowsInPlace() = runTest(mainDispatcherRule.dispatcher) {
        val groups = mapOf(
            "Cities" to listOf(fav("Rome")),
            "Work" to listOf(fav("Office"))
        )
        favoritesProvider.flow.value = groups
        favoritesProvider.order.value = listOf("Cities", "Work")
        val screen = newScreen()
        advanceUntilIdle()
        assertEquals(listOf("Cities", "Work"), singleTitles(screen.onGetTemplate()))

        // Same map instance, same contents — only the order moved.
        favoritesProvider.flow.value = groups
        favoritesProvider.order.value = listOf("Work", "Cities")
        advanceUntilIdle()

        assertEquals(listOf("Work", "Cities"), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun aGroupTheOrderDoesNotNameStillAppears() = runTest(mainDispatcherRule.dispatcher) {
        favoritesProvider.flow.value = mapOf(
            "Cities" to listOf(fav("Rome")),
            "Fresh" to listOf(fav("Brand new"))
        )
        favoritesProvider.order.value = listOf("Cities")
        val screen = newScreen()
        advanceUntilIdle()

        assertEquals(listOf("Cities", "Fresh"), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun aNameTheStoreNoLongerHoldsIsSkipped() = runTest(mainDispatcherRule.dispatcher) {
        favoritesProvider.flow.value = mapOf("Cities" to listOf(fav("Rome")))
        favoritesProvider.order.value = listOf("Gone", "Cities")
        val screen = newScreen()
        advanceUntilIdle()

        assertEquals(listOf("Cities"), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun groupRowsShowTheNameAndCount() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: auto-favorites — Group headers shown: the group name and the group's favorite count,
        // under the wording the phone's group card uses. A car list row carries no tinted background,
        // so there is no color indicator.
        favoritesProvider.flow.value = mapOf(
            "Cities" to listOf(fav("Rome"), fav("Berlin"), fav("Paris")),
            "Solo" to listOf(fav("Only"))
        )
        favoritesProvider.order.value = listOf("Cities", "Solo")
        val screen = newScreen()
        advanceUntilIdle()

        val rows = rows(screen.onGetTemplate())
        val cities = rows.single { it.title.toString() == "Cities" }
        assertEquals(
            carContext.resources.getQuantityString(R.plurals.favorite_count, 3, 3),
            cities.texts.single().toString()
        )
        assertEquals("3 favorites", cities.texts.single().toString())

        val solo = rows.single { it.title.toString() == "Solo" }
        assertEquals("1 favorite", solo.texts.single().toString())
    }

    @Test
    fun anEmptyGroupIsNotListedOnTheCar() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: auto-favorites — A group holding no favorites is not listed. No car affordance can
        // move a favorite into a group the driver just created, so a car row for it would be a dead
        // end. (The phone's group grid keeps listing it: FavoritesViewModelTest
        // `addGroup creates group and shows snackbar` asserts the new, empty group reaches the grid.)
        favoritesProvider.flow.value = mapOf(
            "Cities" to listOf(fav("Rome")),
            "Empty" to emptyList()
        )
        favoritesProvider.order.value = listOf("Cities", "Empty")
        val screen = newScreen()
        advanceUntilIdle()

        assertEquals(listOf("Cities"), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun theGroupLevelOffersNoManagementAction() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: auto-favorites — Groups stay read-only; fav-management-ui — Car favorites screen
        // offers no move action. The car templates cannot express the destination-group step, and the
        // surface has to stay operable while driving.
        every { navigationViewModel.navigateTo(any(), any(), any(), any()) } just Runs
        favoritesProvider.flow.value = mapOf(
            "Cities" to listOf(fav("Rome")),
            "Work" to listOf(fav("Office"))
        )
        favoritesProvider.order.value = listOf("Cities", "Work")
        val groupLevel = newScreen()
        advanceUntilIdle()

        val groupRows = rows(groupLevel.onGetTemplate())
        assertTrue(
            "a car group row must not carry a management action: $groupRows",
            groupRows.all { it.actions.isEmpty() }
        )

        // A group's own rows are read-only too, and selecting one still starts the picker.
        val group = newScreen(groupName = "Cities")
        advanceUntilIdle()
        val favoriteRows = rows(group.onGetTemplate())
        assertTrue(
            "a car favorite row must not carry a management action: $favoriteRows",
            favoriteRows.all { it.actions.isEmpty() }
        )
        favoriteRows.single().onClickDelegate!!.sendClick(mockk(relaxed = true))
        verify { navigationViewModel.navigateTo(51.5136, 7.4653, any(), SurfaceOrigin.CAR) }
    }

    @Test
    fun aGroupRowOpensTheGroupsFavorites() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: auto-favorites — A group row opens that group's favorites, in the stored favorite
        // order; the push leaves the host's click callback (spec: car-host-fault-isolation).
        favoritesProvider.flow.value = mapOf(
            "Cities" to listOf(fav("Rome"), fav("Berlin")),
            "Work" to listOf(fav("Office"))
        )
        favoritesProvider.order.value = listOf("Cities", "Work")
        val groupLevel = newScreen()
        advanceUntilIdle()

        pushed.clear()
        tapRow(groupLevel.onGetTemplate(), "Cities")
        assertEquals(
            "the click callback must not build the group screen",
            emptyList<FavoritesScreen>(),
            pushed.filterIsInstance<FavoritesScreen>()
        )
        advanceUntilIdle()

        val group = startPushedFavorites().single()
        advanceUntilIdle()

        assertEquals("Cities", group.onGetTemplate().header!!.title.toString())
        assertEquals(listOf("Rome", "Berlin"), singleTitles(group.onGetTemplate()))
    }

    @Test
    fun aFavoriteMovedOnThePhoneAppearsInTheDestinationGroup() =
        runTest(mainDispatcherRule.dispatcher) {
            // Spec: fav-management-ui — A move made on the phone is visible on the car.
            favoritesProvider.flow.value = mapOf(
                "Cities" to listOf(fav("Rome")),
                "Work" to listOf(fav("Office"))
            )
            favoritesProvider.order.value = listOf("Cities", "Work")
            val cities = newScreen(groupName = "Cities")
            val work = newScreen(groupName = "Work")
            advanceUntilIdle()
            assertEquals(listOf("Rome"), singleTitles(cities.onGetTemplate()))

            // The user moves the favorite into another group on the phone: only the store changes.
            favoritesProvider.flow.value = mapOf(
                "Cities" to emptyList(),
                "Work" to listOf(fav("Office"), fav("Rome"))
            )
            advanceUntilIdle()

            assertEquals(listOf("Office", "Rome"), singleTitles(work.onGetTemplate()))
            assertEquals(
                "the source group holds nothing any more",
                listOf(carContext.getString(R.string.no_favorites_saved)),
                singleTitles(cities.onGetTemplate())
            )
        }

    // --- The row capacity (spec: car-list-row-limit — A car list never presents more rows than the
    // host displays; Items beyond the capacity stay reachable; design D2/D4) ---

    /** A store of [count] single-favorite groups, in group order. */
    private fun groupStore(count: Int) {
        val names = (1..count).map { "Group$it" }
        favoritesProvider.flow.value = names.associateWith { listOf(fav("Fav-$it")) }
        favoritesProvider.order.value = names
    }

    @Test
    fun aLargerHostCapacityShowsMoreRows() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "The capacity follows the host" — the same store shows every group on a host that
        // reports 8 rows, while the library default of 6 caps the list and leaves the rest behind the
        // page row.
        groupStore(8)

        hostCapacity = 8
        val larger = newScreen()
        advanceUntilIdle()
        assertEquals((1..8).map { "Group$it" }, singleTitles(larger.onGetTemplate()))

        hostCapacity = 6
        val smaller = newScreen()
        advanceUntilIdle()
        assertEquals(
            (1..6).map { "Group$it" } + carContext.getString(R.string.more_items, 2),
            singleTitles(smaller.onGetTemplate())
        )
    }

    @Test
    fun anUnanswerableHostFallsBackToTheLibraryDefault() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "A host that cannot be asked uses the library's default" — a host below car API 2
        // (ConstraintManager is @RequiresCarApi(2), the manifest declares minCarApiLevel = 1) and a
        // host whose query fails. Both must render a bounded list, never an unbounded one.
        groupStore(8)
        val expected = (1..6).map { "Group$it" } + carContext.getString(R.string.more_items, 2)

        hostApiLevel = 1
        val oldHost = newScreen()
        advanceUntilIdle()
        assertEquals(
            "a host below car API 2 must not be queried at all",
            0,
            capacityQueries
        )
        assertEquals(expected, singleTitles(oldHost.onGetTemplate()))

        hostApiLevel = 7
        hostQueryFails = true
        val deadHost = newScreen()
        advanceUntilIdle()
        assertEquals(1, capacityQueries)
        assertEquals(expected, singleTitles(deadHost.onGetTemplate()))
    }

    @Test
    fun eachScreenReadsTheCapacityForItself() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "Capacity is re-read per screen" — a value cached from the first screen would be a
        // different content type's or moment's answer.
        groupStore(1)

        newScreen()
        advanceUntilIdle()
        assertEquals("one query per screen start", 1, capacityQueries)

        newScreen(groupName = "Group1")
        advanceUntilIdle()
        assertEquals("the second screen asked for itself", 2, capacityQueries)
    }

    @Test
    fun theTemplateBuildPerformsNoHostQuery() = runTest(mainDispatcherRule.dispatcher) {
        // The read belongs to the started period and runs off the host thread (design D2): building a
        // template, which the host calls from its own answering path, must not call the host.
        groupStore(2)
        val screen = newScreen()
        advanceUntilIdle()
        val afterStart = capacityQueries

        repeat(3) { screen.onGetTemplate() }

        assertEquals("a template build queried the host", afterStart, capacityQueries)
        assertEquals(1, capacityQueries)
    }

    @Test
    fun thePageRowOpensTheNextSlice() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "The final row opens the next page", "The list fits the host's capacity", "The last
        // page offers no further row" — the screen-level cap: page 0 carries exactly the capacity's
        // rows plus the page row, and following the row reaches the remainder.
        hostCapacity = 2
        groupStore(3)

        val first = newScreen()
        advanceUntilIdle()
        assertEquals(
            listOf("Group1", "Group2", carContext.getString(R.string.more_items, 1)),
            singleTitles(first.onGetTemplate())
        )

        pushed.clear()
        tapRow(first.onGetTemplate(), carContext.getString(R.string.more_items, 1))
        advanceUntilIdle()
        val second = startPushedFavorites().single()
        advanceUntilIdle()

        assertEquals(
            "the next page begins with the first item the previous page did not show",
            listOf("Group3"),
            singleTitles(second.onGetTemplate())
        )
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
    fun starredModeOffersNoReorderAffordanceAndSelectionStillNavigates() =
        runTest(mainDispatcherRule.dispatcher) {
            every { navigationViewModel.navigateTo(any(), any(), any(), any()) } just Runs
            val screen = newScreen(starredOnly = true)
            favoritesProvider.starred.value = starred("Cities" to "Rome")
            advanceUntilIdle()

            val rows = rows(screen.onGetTemplate())
            assertTrue("car rows must not carry reorder actions", rows.all { it.actions.isEmpty() })

            rows.first().onClickDelegate?.sendClick(mockk(relaxed = true))
            verify { navigationViewModel.navigateTo(51.5136, 7.4653, any(), SurfaceOrigin.CAR) }
        }

    @Test
    fun starredModeWithNothingStarredShowsTheHint() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen(starredOnly = true)
        favoritesProvider.flow.value = mapOf("Cities" to listOf(fav("Rome")))
        advanceUntilIdle()

        assertEquals(listOf("No starred favorites"), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun starredOrderBeyondTheCapacityPagesThroughTheRest() = runTest(mainDispatcherRule.dispatcher) {
        // The starred mode keeps its one-flat-list shape and gains only the cap (task 3.3): its rows
        // are ordered by the starred order and the page row reaches the remainder.
        hostCapacity = 2
        favoritesProvider.flow.value = mapOf(
            "Cities" to listOf(fav("Berlin"), fav("Rome")),
            "Work" to listOf(fav("Office"), fav("Gym"), fav("Home"))
        )
        favoritesProvider.starred.value = starred(
            "Cities" to "Berlin", "Work" to "Office", "Cities" to "Rome",
            "Work" to "Gym", "Work" to "Home"
        )

        val first = newScreen(starredOnly = true)
        advanceUntilIdle()
        assertEquals(
            listOf("Berlin", "Office", carContext.getString(R.string.more_items, 3)),
            singleTitles(first.onGetTemplate())
        )

        pushed.clear()
        tapRow(first.onGetTemplate(), carContext.getString(R.string.more_items, 3))
        advanceUntilIdle()
        val second = startPushedFavorites().single()
        advanceUntilIdle()
        assertEquals(
            listOf("Rome", "Gym", carContext.getString(R.string.more_items, 1)),
            singleTitles(second.onGetTemplate())
        )

        pushed.clear()
        tapRow(second.onGetTemplate(), carContext.getString(R.string.more_items, 1))
        advanceUntilIdle()
        val third = startPushedFavorites().single()
        advanceUntilIdle()
        assertEquals("the last page offers no further row", listOf("Home"), singleTitles(third.onGetTemplate()))
    }

    /** In-memory [AutoFavoritesProvider] backed by a [MutableStateFlow]. */
    private class FakeFavoritesProvider : AutoFavoritesProvider {
        val flow = MutableStateFlow<Map<String, List<FavoriteLocation>>>(emptyMap())

        /** The group order channel the screen renders the group level from. */
        val order = MutableStateFlow<List<String>>(emptyList())

        /** The starred-order channel the starred mode renders from. */
        val starred = MutableStateFlow<List<StarredFavoriteLocation>>(emptyList())

        /**
         * The calling thread of every source accessor: the production default must resolve the
         * provider (and the native client with it) off the car-app host thread. A concurrent queue
         * because that resolution runs on whichever thread the production default owns.
         */
        val accessorThreads = ConcurrentLinkedQueue<Pair<String, Thread>>()

        override fun favoriteLocations(): StateFlow<Map<String, List<FavoriteLocation>>> {
            accessorThreads += "favoriteLocations" to Thread.currentThread()
            return flow
        }

        override fun groupOrder(): StateFlow<List<String>> {
            accessorThreads += "groupOrder" to Thread.currentThread()
            return order
        }

        override fun starredOrder(): StateFlow<List<StarredFavoriteLocation>> {
            accessorThreads += "starredOrder" to Thread.currentThread()
            return starred
        }

        /**
         * The store's initialisation outcome (spec: `auto-favorites` — The favourites list
         * distinguishes a loading store from an empty one). An initialised store is the default, so a
         * case that asserts the list has to say nothing about it; the readiness cases flip it.
         */
        val ready = MutableStateFlow(true)

        override fun storeReady(): StateFlow<Boolean> = ready

        override suspend fun init(filePath: String) = true
        override suspend fun addFavorite(name: String, lat: Double, lon: Double) = true
        override suspend fun removeFavorite(lat: Double, lon: Double) = true
    }
}
