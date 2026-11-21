package com.naviveylin.auto

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.StarredFavoriteLocation
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.core.FavoritesSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Tests for [FavoritesScreenObservations] (spec: auto/screen-observation — One instance of each
 * observation per started period; A stopped screen performs no renderer or host work; Observations are
 * re-established with the current state on start, and auto-favorites — AA place list reflects the
 * stored favorite order / follows the stored group order, both as clarified; design D6/D8).
 *
 * The screen itself needs a live `CarContext` and a Hilt entry point (`FavoritesScreenTest` covers the
 * screen wiring), so the observation contract is covered here through its seam: the real
 * [CarScreenObservations] plus a `:core` provider fake and a counting effect (the screen's callback ends
 * in `invalidate()`, so "no effect while stopped" is the same assertion). No `CarContext`.
 *
 * All "once" assertions are deltas against a counter taken before the emission — a `StateFlow` re-emits
 * its current value to each new collector by design (see [CarScreenObservationsTest]).
 *
 * Robolectric is the runner because the observation now records its coordinate-free timing line through
 * `DiagnosticsLog`, whose `android.util.Log` call a plain-JVM case cannot make (same reason
 * [DetailsScreenObservationsTest] and [CarScreenObservationsTest] use it). Nothing here touches
 * `OSMScoutClient`, so no JNI stub is involved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FavoritesScreenObservationsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    /** Favorites provider backed by state flows; the three list sources of this screen. */
    private class FakeFavoritesProvider : AutoFavoritesProvider {
        val flow = MutableStateFlow<Map<String, List<FavoriteLocation>>>(emptyMap())
        val order = MutableStateFlow<List<String>>(emptyList())
        val starred = MutableStateFlow<List<StarredFavoriteLocation>>(emptyList())

        /**
         * The calling thread of every source accessor, so a case can prove where the provider —
         * and with it the native client — is resolved (spec: auto-map-renderer — "A screen
         * observation resolves its provider off the host thread"). A concurrent queue because the
         * resolution happens on whichever thread the production default owns.
         */
        val accessorThreads = ConcurrentLinkedQueue<Pair<String, Thread>>()

        override fun favoriteLocations(): StateFlow<Map<String, List<FavoriteLocation>>> {
            accessorThreads += "favoriteLocations" to Thread.currentThread()
            return flow.asStateFlow()
        }

        override fun groupOrder(): StateFlow<List<String>> {
            accessorThreads += "groupOrder" to Thread.currentThread()
            return order.asStateFlow()
        }

        override fun starredOrder(): StateFlow<List<StarredFavoriteLocation>> {
            accessorThreads += "starredOrder" to Thread.currentThread()
            return starred.asStateFlow()
        }

        /** The store's initialisation outcome; an initialised store unless a case says otherwise. */
        val ready = MutableStateFlow(true)

        override fun storeReady(): StateFlow<Boolean> {
            accessorThreads += "storeReady" to Thread.currentThread()
            failIfAsked()
            return ready.asStateFlow()
        }

        /**
         * The path of every `init` call, in order (issue #995: the observation establishes the store it
         * reads, once per started period). A concurrent queue because the init runs inside the hop.
         */
        val initPaths = ConcurrentLinkedQueue<String>()

        /** The thread of every `init` call: the store is initialised off the car-app host thread. */
        val initThreads = ConcurrentLinkedQueue<Thread>()

        /**
         * How many source accessor calls throw before the provider behaves again (issue #995: the hop is
         * retried once after a fault). [Int.MAX_VALUE] makes every attempt fault.
         */
        var throwOnAccessorCalls = 0

        /**
         * The source accessor's fault injection: the transient failure a retried hop has to survive.
         * A plain unchecked exception, the shape a native refusal or a file read takes.
         */
        private fun failIfAsked() {
            if (throwOnAccessorCalls > 0) {
                throwOnAccessorCalls--
                throw IllegalStateException("provider unavailable")
            }
        }

        /**
         * The store's initialisation, as the real one behaves: it publishes the file's contents and ends
         * the attempt by reporting the store ready (spec `auto-favorites` — The observation establishes
         * the store it reads).
         */
        override suspend fun init(filePath: String): Boolean {
            initPaths += filePath
            initThreads += Thread.currentThread()
            flow.value = mapOf("Default" to listOf(FavoriteLocation("Home", 51.5, 7.5)))
            order.value = listOf("Default")
            ready.value = true
            return true
        }

        override suspend fun addFavorite(name: String, lat: Double, lon: Double): Boolean = false
        override suspend fun removeFavorite(lat: Double, lon: Double): Boolean = false

        /**
         * The state a provider that has already resolved the store hands out without a dispatch; null
         * until a case says otherwise (issue #110).
         */
        var snapshot: FavoritesSnapshot? = null

        override fun snapshotIfResolved(): FavoritesSnapshot? = snapshot
    }

    private val provider = FakeFavoritesProvider()

    /** One applied list state per emission; the screen's `invalidate()` rides its callback. */
    private val applied = mutableListOf<Triple<Map<String, List<FavoriteLocation>>, List<String>, List<StarredFavoriteLocation>>>()

    /** The readiness each applied state carried, so a case can assert what the screen would render. */
    private val readiness = mutableListOf<Boolean>()

    /**
     * Whether each applied state was the failure state (issue #995: a store that cannot be observed is
     * reported, not left loading), so a case can assert what the screen would render instead of rows.
     */
    private val failures = mutableListOf<Boolean>()

    private fun observations() = CarScreenObservations(mainDispatcher.dispatcher)

    private fun wiring(
        observations: CarScreenObservations,
        backgroundDispatcher: CoroutineDispatcher = mainDispatcher.dispatcher
    ) = FavoritesScreenObservations(
        observations = observations,
        favoritesProvider = provider,
        favoritesPath = FAVORITES_PATH,
        backgroundDispatcher = backgroundDispatcher,
        onFavorites = { state ->
            applied += Triple(state.favorites, state.groupOrder, state.starredOrder)
            readiness += state.storeReady
            failures += state.failed
        }
    )

    private fun favorite(name: String) = mapOf("Default" to listOf(FavoriteLocation(name, 51.5, 7.5)))

    private companion object {
        /** The favourites file the observation initialises an unready store with. */
        const val FAVORITES_PATH = "/test/files/favorites.json"
    }

    /**
     * Pump the test scheduler while the resolution's real background dispatcher progresses, until
     * every accessor has been reached. The bound is a backstop, not the assertion, and the loop reads
     * only the queue's size before the assertion reads it (spec `unit-test-suite-runtime` — A case
     * awaits the state it needs, not a proxy of it).
     */
    private fun TestScope.awaitAccessorThreads(expected: Int, timeoutMs: Long = 3_000) {
        awaitUntil(timeoutMs) { provider.accessorThreads.size >= expected }
    }

    /**
     * Pump the test scheduler until [condition] holds, or the [timeoutMs] backstop expires — a case that
     * asserts a state a real background thread has to produce waits for that state, not for a proxy
     * (spec `unit-test-suite-runtime`).
     */
    private fun TestScope.awaitUntil(timeoutMs: Long = 3_000, condition: () -> Boolean) {
        runBlocking {
            withTimeoutOrNull(timeoutMs) {
                while (!condition()) {
                    advanceUntilIdle()
                    yield()
                }
                true
            }
        }
        advanceUntilIdle()
    }

    /**
     * The requirement itself: the observation body runs on the scope's main dispatcher, which is the
     * car-app host thread, so it must reach the provider — and with it the native client, whose first
     * touch builds it (stylesheet sync, dlopen, native setup) — from a background context
     * (spec: auto-map-renderer — "A screen observation resolves its provider off the host thread";
     * design D1).
     */
    @Test
    fun favoriteSourcesResolveOffTheHostThread() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        // The thread this case drives the main dispatcher from is the session's host thread.
        val hostThread = Thread.currentThread()

        // The production default owns the hop: a test dispatcher would run it on this same thread,
        // which is the host thread the assertion is about.
        wiring(observations, backgroundDispatcher = Dispatchers.Default).start()
        awaitAccessorThreads(expected = 4)

        val resolved = provider.accessorThreads.toList()
        assertEquals(
            "the case must observe every source, otherwise the guard is vacuous: " +
                resolved.map { it.first },
            setOf("favoriteLocations", "groupOrder", "starredOrder", "storeReady"),
            resolved.map { it.first }.toSet()
        )
        assertTrue(
            "the provider was resolved on the host thread for " +
                resolved.filter { it.second === hostThread }.map { it.first },
            resolved.none { it.second === hostThread }
        )
    }

    @Test
    fun everyObservedSourceReachesTheEffect() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        wiring(observations).start()
        advanceUntilIdle()

        assertEquals("the favorites screen observes one list state", 1, observations.liveObservationCount)

        val before = applied.size
        provider.flow.value = favorite("Home")
        provider.order.value = listOf("Default")
        provider.starred.value = emptyList()
        advanceUntilIdle()

        assertEquals("the three sources arrive as one applied state", 1, applied.size - before)
        assertEquals("Home", applied.last().first.values.flatten().single().name)
        assertEquals(listOf("Default"), applied.last().second)
    }

    @Test
    fun tenStartStopCyclesLeaveOneInstance() = runTest(mainDispatcher.dispatcher) {
        // Spec: auto/screen-observation — "Favorites screen stopped and started repeatedly": the host
        // stops and starts a screen on every background round trip and on every push/pop, so a
        // per-collector job list would accumulate copies.
        val observations = observations()
        val wiring = wiring(observations)

        repeat(10) { cycle ->
            wiring.start()
            advanceUntilIdle()
            assertEquals("one observation in cycle ${cycle + 1}", 1, observations.liveObservationCount)

            observations.stop()
            assertEquals("stopped after cycle ${cycle + 1}", 0, observations.liveObservationCount)
        }

        wiring.start()
        advanceUntilIdle()
        assertEquals("a restarted screen still observes one source", 1, observations.liveObservationCount)

        val before = applied.size
        provider.flow.value = favorite("Home")
        advanceUntilIdle()
        assertEquals("one change reaches one observation", 1, applied.size - before)
    }

    @Test
    fun aStoppedScreenObservesNothing() = runTest(mainDispatcher.dispatcher) {
        // Spec: auto/screen-observation — "Favorites or order change while the favorites screen is
        // stopped": the collector used to run from `init`, so a change arriving while the screen was
        // covered or backgrounded still invalidated the host (TODO.md §103).
        val observations = observations()
        val wiring = wiring(observations)
        wiring.start()
        advanceUntilIdle()

        val started = applied.size
        provider.flow.value = favorite("Home")
        advanceUntilIdle()
        assertEquals("a change while started is applied", 1, applied.size - started)

        observations.stop()
        val before = applied.size
        provider.flow.value = favorite("Work")
        provider.order.value = listOf("Default")
        provider.starred.value = listOf(StarredFavoriteLocation("Default", FavoriteLocation("Work", 51.5, 7.5)))
        advanceUntilIdle()

        assertEquals("a stopped screen applies nothing and invalidates nothing", before, applied.size)
    }

    @Test
    fun aGroupReorderWhileStoppedIsAppliedOnceOnTheNextStart() = runTest(mainDispatcher.dispatcher) {
        // Spec: auto-favorites — "A group reorder received while the list is stopped is applied on the
        // next start"; spec: auto/screen-observation — "Favorites screen restores the current state on
        // start".
        val observations = observations()
        val wiring = wiring(observations)
        wiring.start()
        advanceUntilIdle()
        observations.stop()

        // The phone reorders the groups while the car list is stopped.
        provider.order.value = listOf("Zweite", "Default")

        val before = applied.size
        wiring.start()
        advanceUntilIdle()

        assertEquals("the current order is applied exactly once", 1, applied.size - before)
        assertEquals(listOf("Zweite", "Default"), applied.last().second)
    }

    /**
     * Issue #110 (spec: auto-favorites — A ready store's state is published without waiting for the
     * hop): on a cold session the map's first frame holds the background threads and the CPU, so the
     * `withContext` that resolves the provider arrived 623–1715 ms after the observation started while
     * the store had been ready for seconds — the loading row stayed on screen although the list was
     * known. A resolved store's state therefore reaches the screen from the start of the period, not
     * from the hop.
     *
     * The hop's dispatcher is a dispatcher on its own, never-advanced scheduler, so only the synchronous
     * publish can have happened — no wait, no proxy for the state the case asserts.
     */
    @Test
    fun aResolvedStoresStateReachesTheScreenBeforeTheBackgroundHop() = runTest(mainDispatcher.dispatcher) {
        val observations = CarScreenObservations(UnconfinedTestDispatcher(testScheduler))
        provider.snapshot = FavoritesSnapshot(
            favorites = favorite("Home"),
            groupOrder = listOf("Default"),
            starredOrder = emptyList(),
            storeReady = true
        )

        wiring(observations, backgroundDispatcher = StandardTestDispatcher(TestCoroutineScheduler())).start()

        assertEquals("the resolved store's state reaches the screen without the hop", 1, applied.size)
        assertEquals("Home", applied.single().first.values.flatten().single().name)
        assertEquals(listOf("Default"), applied.single().second)
        assertTrue("the state carries the store's readiness", readiness.single())
    }

    /**
     * Issue #995 (spec: auto-favorites — The observation establishes the store it reads): the readiness
     * a car favourites screen renders is produced by the store's one `init` call, and the only car caller
     * was the session's warmup step. A warmup that timed out before that step, was cancelled or failed
     * left the process with an unready store, so every favourites screen rendered the loading row for its
     * whole lifetime. The observation therefore establishes the store it reads, once per started period,
     * inside the hop that already touches the provider off the host thread.
     *
     * On HEAD `init` is never called and nothing is published, so the case fails at its first assertion.
     */
    @Test
    fun anUnreadyStoreIsInitialisedOnceAndItsRowsRender() = runTest(mainDispatcher.dispatcher) {
        provider.ready.value = false
        val hostThread = Thread.currentThread()
        // The production default owns the hop, so the thread the initialisation runs on is the real one
        // the assertion below is about: a test dispatcher would run it on this case's own thread.
        wiring(observations(), backgroundDispatcher = Dispatchers.Default).start()
        awaitUntil { provider.initPaths.isNotEmpty() && applied.isNotEmpty() }

        assertEquals(
            "the observation must initialise the store it reads, exactly once per started period: " +
                provider.initPaths.toList(),
            1,
            provider.initPaths.size
        )
        assertTrue(
            "the store must be initialised with the screen's favourites file, got " +
                provider.initPaths.toList(),
            provider.initPaths.single().endsWith("favorites.json")
        )
        assertTrue(
            "the store was initialised on the car-app host thread",
            provider.initThreads.none { it === hostThread }
        )
        assertEquals(
            "the initialised store's rows must reach the screen without re-entering it",
            "Home",
            applied.last().first.values.flatten().single().name
        )
        assertTrue("the applied state must carry the store's readiness", readiness.last())
    }

    /**
     * The guard on the case above: the observation initialises only a store that reports itself unready,
     * so the warmup's own `init` is not repeated by every screen the session opens (spec: auto-favorites —
     * A ready store is not initialised again).
     */
    @Test
    fun aReadyStoreIsNotInitialisedAgain() = runTest(mainDispatcher.dispatcher) {
        provider.flow.value = favorite("Home")
        wiring(observations()).start()
        advanceUntilIdle()

        assertEquals("a ready store must not be initialised again", 0, provider.initPaths.size)
        assertEquals("the ready store's rows reach the screen", "Home", applied.last().first.values.flatten().single().name)
    }

    /**
     * Issue #995, second half (spec: auto-favorites — A failed resolution is retried once before the
     * screen is told): a fault inside the observation is confined by [CarScreenObservations] and the
     * observation was not repeated within the started period, so one transient failure wedged that period.
     * The hop is therefore retried once, and the list publishes from the second attempt.
     *
     * On HEAD the first attempt's throw ends the observation with nothing published, so the case fails.
     */
    @Test
    fun anObservationFaultIsRetriedOnceAndTheListPublishes() = runTest(mainDispatcher.dispatcher) {
        provider.flow.value = favorite("Home")
        provider.order.value = listOf("Default")
        provider.throwOnAccessorCalls = 1
        wiring(observations()).start()
        advanceUntilIdle()

        assertEquals(
            "the faulting hop must be attempted twice: " + provider.accessorThreads.toList().map { it.first },
            2,
            provider.accessorThreads.count { it.first == "storeReady" }
        )
        assertEquals("the retry's state must reach the screen", "Home", applied.last().first.values.flatten().single().name)
    }

    /**
     * The last guard of the seam (spec `auto-favorites` — A store that cannot be observed is reported, not
     * left loading; issue #995): a store whose observation faulted twice still hands the screen a state, so
     * the screen can report the store as unavailable instead of keeping a loading row the observation will
     * never end.
     */
    @Test
    fun aStoreThatFailsTwiceIsReportedAsUnavailable() = runTest(mainDispatcher.dispatcher) {
        provider.throwOnAccessorCalls = Int.MAX_VALUE
        wiring(observations()).start()
        advanceUntilIdle()

        assertEquals(
            "the failure state must reach the screen exactly once: " + applied,
            1,
            applied.size
        )
        assertTrue("the applied state must be the failure state", failures.single())
        assertTrue("no rows may be invented for a store that was not read", applied.single().first.isEmpty())
    }

    /**
     * The guard on the case above: a provider that has not resolved the store answers null, so the
     * screen invents no state and the loading row stands until the hop publishes (spec: auto-favorites
     * — An uninitialised store is not reported as empty).
     */
    @Test
    fun anUnresolvedProviderPublishesNothingBeforeTheHop() = runTest(mainDispatcher.dispatcher) {
        val observations = CarScreenObservations(UnconfinedTestDispatcher(testScheduler))
        provider.snapshot = null

        wiring(observations, backgroundDispatcher = StandardTestDispatcher(TestCoroutineScheduler())).start()

        assertEquals("nothing is published before the resolution", 0, applied.size)
    }
}
