package com.naviveylin.auto

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.StarredFavoriteLocation
import com.naviveylin.core.AutoFavoritesProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
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
            return ready.asStateFlow()
        }

        override suspend fun init(filePath: String): Boolean = false
        override suspend fun addFavorite(name: String, lat: Double, lon: Double): Boolean = false
        override suspend fun removeFavorite(lat: Double, lon: Double): Boolean = false
    }

    private val provider = FakeFavoritesProvider()

    /** One applied list state per emission; the screen's `invalidate()` rides its callback. */
    private val applied = mutableListOf<Triple<Map<String, List<FavoriteLocation>>, List<String>, List<StarredFavoriteLocation>>>()

    /** The readiness each applied state carried, so a case can assert what the screen would render. */
    private val readiness = mutableListOf<Boolean>()

    private fun observations() = CarScreenObservations(mainDispatcher.dispatcher)

    private fun wiring(
        observations: CarScreenObservations,
        backgroundDispatcher: CoroutineDispatcher = mainDispatcher.dispatcher
    ) = FavoritesScreenObservations(
        observations = observations,
        favoritesProvider = provider,
        backgroundDispatcher = backgroundDispatcher,
        onFavorites = { favorites, order, starred, ready ->
            applied += Triple(favorites, order, starred)
            readiness += ready
        }
    )

    private fun favorite(name: String) = mapOf("Default" to listOf(FavoriteLocation(name, 51.5, 7.5)))

    /**
     * Pump the test scheduler while the resolution's real background dispatcher progresses, until
     * every accessor has been reached. The bound is a backstop, not the assertion, and the loop reads
     * only the queue's size before the assertion reads it (spec `unit-test-suite-runtime` — A case
     * awaits the state it needs, not a proxy of it).
     */
    private fun TestScope.awaitAccessorThreads(expected: Int, timeoutMs: Long = 3_000) {
        runBlocking {
            withTimeoutOrNull(timeoutMs) {
                while (provider.accessorThreads.size < expected) {
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
}
