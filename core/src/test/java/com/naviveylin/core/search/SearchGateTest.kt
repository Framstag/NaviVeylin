package com.naviveylin.core.search

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search gate: debounce, the minimum query length, supersede and abandon
 * (spec: `search-request-lifecycle`).
 *
 * The gate is tested against a recording request client and a search the test completes by hand, so
 * "the predecessor must not publish" and "the successor is never cancelled by a stale request" are
 * observable without a native library or a client instance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchGateTest {

    /** Mirrors the native registry: one live generation per slot, cancels name a generation. */
    private class RecordingRequests : SearchRequestClient {
        val begins = mutableListOf<String>()

        /** Every cancellation with the outcome the double reported for it. */
        val cancels = mutableListOf<Pair<Long, Boolean>>()

        private val liveBySlot = mutableMapOf<String, Long>()
        private var nextGeneration = 0L

        override fun beginSearch(slot: String): Long {
            begins.add(slot)
            val generation = ++nextGeneration
            liveBySlot[slot] = generation
            return generation
        }

        override fun cancelSearch(generation: Long): Boolean {
            val slot = liveBySlot.entries.firstOrNull { it.value == generation }?.key
            val wasLive = slot != null

            if (wasLive) {
                liveBySlot.remove(slot)
            }

            cancels.add(generation to wasLive)
            return wasLive
        }

        fun isLive(generation: Long): Boolean = liveBySlot.values.contains(generation)

        fun generationOf(slot: String): Long = liveBySlot[slot] ?: SearchGate.NO_GENERATION
    }

    /**
     * A search that starts on demand and completes only when the test says so. Keyed by
     * generation, not by text, so a repeated query text gets its own outcome.
     */
    private class SuspendedSearch {
        val started = mutableListOf<Pair<String, Long>>()
        private val pending = mutableMapOf<Long, CompletableDeferred<String>>()

        suspend fun run(query: String, generation: Long): String {
            started.add(query to generation)
            return deferredFor(generation).await()
        }

        fun complete(generation: Long, value: String) {
            deferredFor(generation).complete(value)
        }

        private fun deferredFor(generation: Long) =
            pending.getOrPut(generation) { CompletableDeferred() }
    }

    private fun gate(
        scope: CoroutineScope,
        requests: RecordingRequests,
        search: SuspendedSearch,
        slot: String = SearchSlots.PLACES
    ) = SearchGate(
        scope,
        requests,
        slot,
        debounceMs = 300L,
        // The seam runs on the test dispatcher, so begin/cancel are ordered with the test's clock.
        requestDispatcher = { scope.coroutineContext[CoroutineDispatcher] ?: Dispatchers.Default },
        search = search::run
    )

    /** The debounce plus a tick, since `advanceTimeBy` is exclusive at its boundary. */
    private fun TestScope.advancePastDebounce() {
        advanceTimeBy(301)
        runCurrent()
    }

    /**
     * Spec: `search-request-lifecycle` — the minimum query length is 2 characters: a one-character
     * query runs nothing and shows no result.
     */
    @Test
    fun oneCharacterQueryRunsNothingAndClears() = runTest {
        val requests = RecordingRequests()
        val search = SuspendedSearch()
        val gate = gate(this, requests, search)

        gate.request("B")
        advanceUntilIdle()

        assertEquals("no request may be begun below the minimum length", emptyList<String>(), requests.begins)
        assertEquals(SearchGateState.Cleared, gate.state.value)
    }

    /**
     * Spec: `search-request-lifecycle` — falling below the minimum aborts the running search: the
     * request is cancelled although no newer search replaces it.
     */
    @Test
    fun fallingBelowTheMinimumAbandonsTheRunningSearch() = runTest {
        val requests = RecordingRequests()
        val search = SuspendedSearch()
        val gate = gate(this, requests, search)

        gate.request("Boch")
        advancePastDebounce()
        assertEquals(listOf("Boch" to 1L), search.started)

        gate.request("B")

        // The abandon cancels off the caller's dispatcher (the seam may resolve the client), so let it run.
        runCurrent()

        assertEquals(SearchGateState.Cleared, gate.state.value)
        assertFalse("the running request must have been aborted", requests.isLive(1L))
        assertEquals(
            "the cancellation named the running generation",
            listOf(1L to true),
            requests.cancels.filter { it.second }
        )
    }

    /** Spec: `search-request-lifecycle` — abandon without a successor stops the search. */
    @Test
    fun abandonStopsTheRunningSearch() = runTest {
        val requests = RecordingRequests()
        val search = SuspendedSearch()
        val gate = gate(this, requests, search)

        gate.request("Boch")
        advancePastDebounce()
        assertEquals(listOf("Boch" to 1L), search.started)

        gate.abandon()

        // The cancel is issued off the caller's dispatcher; the state clears synchronously.
        runCurrent()

        assertEquals(SearchGateState.Cleared, gate.state.value)
        assertFalse(requests.isLive(1L))
        assertEquals(
            "the abandoned generation is what was cancelled",
            listOf(1L to true),
            requests.cancels.filter { it.second }
        )

        // The native call returning after the abandon publishes nothing.
        search.complete(1L, "too late")
        advanceUntilIdle()
        assertEquals(SearchGateState.Cleared, gate.state.value)
    }

    /**
     * Spec: `search-request-lifecycle` — a late cancel does not kill the live search. The
     * superseded request's own cancellation never names the generation that replaced it, and the
     * newer result stands whatever order the two coroutines finish in.
     */
    @Test
    fun lateCancelOfTheSupersededSearchKeepsTheNewerResult() = runTest {
        val requests = RecordingRequests()
        val search = SuspendedSearch()
        val gate = gate(this, requests, search)

        gate.request("Boch")
        advancePastDebounce()
        gate.request("Bochu")
        advancePastDebounce()

        assertEquals(listOf("Boch" to 1L, "Bochu" to 2L), search.started)
        assertTrue("the newer request owns the slot", requests.isLive(2L))
        assertFalse(
            "the successor's generation must never be cancelled",
            requests.cancels.any { it.first == 2L }
        )

        search.complete(2L, "second")
        advanceUntilIdle()
        assertEquals(SearchGateState.Completed("Bochu", "second"), gate.state.value)

        // The superseded search returns late: its result must not replace the newer one.
        search.complete(1L, "first")
        advanceUntilIdle()
        assertEquals(SearchGateState.Completed("Bochu", "second"), gate.state.value)
    }

    /**
     * Spec: `osmscout-jni` — cancelling a stale generation aborts nothing; the double mirrors the
     * native rule the host case pins.
     */
    @Test
    fun aCancelForAReplacedGenerationReportsThatItAbortedNothing() = runTest {
        val requests = RecordingRequests()
        val superseded = requests.beginSearch(SearchSlots.PLACES)
        val current = requests.beginSearch(SearchSlots.PLACES)

        assertFalse(requests.cancelSearch(superseded))
        assertTrue(requests.isLive(current))
        assertEquals(listOf(superseded to false), requests.cancels)
    }

    /** Spec: `search-request-lifecycle` — supersede stays inside the slot. */
    @Test
    fun theGateSupersedesWithinItsSlot() = runTest {
        val requests = RecordingRequests()
        val search = SuspendedSearch()
        val gate = gate(this, requests, search)

        gate.request("Boch")
        advancePastDebounce()
        gate.request("Bochu")
        advancePastDebounce()

        assertEquals(listOf(SearchSlots.PLACES, SearchSlots.PLACES), requests.begins)
        assertFalse("the superseded generation is no longer live", requests.isLive(1L))
        assertTrue(requests.isLive(2L))

        // Finish the live request so no coroutine is left running for the test scope.
        search.complete(2L, "second")
        advanceUntilIdle()
        assertEquals(SearchGateState.Completed("Bochu", "second"), gate.state.value)
    }

    /** Spec: `search-request-lifecycle` — a car search does not stop a phone search. */
    @Test
    fun placesSearchSurvivesACarSearch() = runTest {
        val requests = RecordingRequests()
        val search = SuspendedSearch()
        val placesGate = gate(this, requests, search, slot = SearchSlots.PLACES)
        val carGate = gate(this, requests, search, slot = SearchSlots.CAR)

        placesGate.request("Boch")
        advancePastDebounce()
        val placesGeneration = requests.generationOf(SearchSlots.PLACES)

        carGate.request("Dortmund")
        advancePastDebounce()
        assertTrue("the car search must not abort the phone search", requests.isLive(placesGeneration))

        carGate.abandon()
        assertTrue(
            "abandoning the car search must not abort the phone search",
            requests.isLive(placesGeneration)
        )

        search.complete(placesGeneration, "phone result")
        advanceUntilIdle()
        assertEquals(SearchGateState.Completed("Boch", "phone result"), placesGate.state.value)
    }

    /**
     * Spec: `location-search` — a repeated query text still runs the search. A gate keyed by the
     * text (or fed through a conflating flow) would drop this one.
     */
    @Test
    fun repeatedQueryTextRunsAgain() = runTest {
        val requests = RecordingRequests()
        val search = SuspendedSearch()
        val gate = gate(this, requests, search)

        gate.request("Bochum")
        advancePastDebounce()
        search.complete(1L, "first")
        advanceUntilIdle()

        gate.request("Bochum")
        advancePastDebounce()

        assertEquals(2, requests.begins.size)
        assertEquals(listOf("Bochum" to 1L, "Bochum" to 2L), search.started)

        search.complete(2L, "second")
        advanceUntilIdle()
        assertEquals(SearchGateState.Completed("Bochum", "second"), gate.state.value)
    }
}
