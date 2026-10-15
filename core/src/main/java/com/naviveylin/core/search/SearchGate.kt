package com.naviveylin.core.search

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What a surface observes from its search gate.
 *
 * `Idle` is the state before anything was asked for, `Cleared` the state after a query below the
 * minimum length or after an abandon — both mean "show no result", which the surfaces map onto
 * their own empty state.
 */
sealed interface SearchGateState<out R> {
    data object Idle : SearchGateState<Nothing>
    data object Searching : SearchGateState<Nothing>
    data object Cleared : SearchGateState<Nothing>
    data class Completed<R>(val query: String, val results: R) : SearchGateState<R>
}

/**
 * One surface's search requests: debounce, minimum query length, supersede and abandon
 * (spec: `search-request-lifecycle`).
 *
 * The gate owns the request's lifecycle so no surface has to: a newer query supersedes the one
 * in flight (the newer request's `beginSearch` stops the older walk, and the older coroutine is
 * cancelled so its result is never published), a query below [minQueryLength] runs nothing and
 * shows nothing, and [abandon] stops a running search although no successor exists.
 *
 * `search` does its own dispatching — the callers' native work already runs off the main thread —
 * and the gate is agnostic about the result type, so the phone's merged list and the car's rows
 * use the same mechanism.
 *
 * Threading and lifecycle: everything runs on the [scope] the caller supplies (a `viewModelScope`
 * or a car screen's scope), so cancelling that scope ends the gate's work with its surface. A
 * cancelled search's `finally` still calls `cancelSearch`, which is a no-op once a newer request
 * owns the slot — that is what makes the late cancellation safe.
 */
class SearchGate<R>(
    private val scope: CoroutineScope,
    private val requests: SearchRequestClient,
    private val slot: String,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val minQueryLength: Int = MIN_QUERY_LENGTH,
    /**
     * The dispatcher the request-lifecycle seam itself runs on. `beginSearch`/`cancelSearch` may resolve
     * the native client, which must not be built on a host-facing thread (spec: `auto-map-renderer` —
     * Provider resolution does not build the client); a lambda keeps a caller's lazily-assigned
     * dispatcher readable at call time. The search itself runs on whatever [search] uses.
     */
    private val requestDispatcher: () -> CoroutineDispatcher = { Dispatchers.Default },
    private val search: suspend (query: String, generation: Long) -> R
) {

    private val _state = MutableStateFlow<SearchGateState<R>>(SearchGateState.Idle)

    /** The gate's published state; surfaces map it onto their own search state. */
    val state: StateFlow<SearchGateState<R>> = _state.asStateFlow()

    private var debounceJob: Job? = null
    private var searchJob: Job? = null

    /** The running request's generation, or [NO_GENERATION] while none is live. */
    private var generation: Long = NO_GENERATION

    /**
     * The user's text changed: the newest text wins. Below the minimum length (and for an empty
     * query) nothing runs and the result area clears, so the previous query's results never stay
     * under a shorter text.
     */
    fun request(query: String) {
        debounceJob?.cancel()
        debounceJob = null

        if (query.length < minQueryLength) {
            abandon()
            return
        }

        debounceJob = scope.launch {
            delay(debounceMs)
            start(query)
        }
    }

    /**
     * Nobody wants the running search's result any more — the field was cleared, the surface was
     * left, the mode changed. The request is cancelled outright, so it stops even though no newer
     * search replaces it, and the state clears.
     */
    fun abandon() {
        debounceJob?.cancel()
        debounceJob = null

        val current = generation
        generation = NO_GENERATION
        if (current != NO_GENERATION) {
            // Cancelled off the caller's dispatcher, for the same reason as the begin below. A cancel that
            // lands after a successor began names the superseded generation and is a no-op, so launching it
            // cannot cancel the newer search (spec: `search-request-lifecycle`).
            scope.launch(requestDispatcher()) { requests.cancelSearch(current) }
        }

        searchJob?.cancel()
        searchJob = null

        _state.value = SearchGateState.Cleared
    }

    private fun start(query: String) {
        // Supersede: the predecessor must not publish its result — which is also why the
        // debounce job above is not enough on its own — and beginning the request below is what
        // stops the predecessor's native walk.
        searchJob?.cancel()

        searchJob = scope.launch {
            val request = withContext(requestDispatcher()) { requests.beginSearch(slot) }
            generation = request

            try {
                _state.value = SearchGateState.Searching
                val results = search(query, request)
                _state.value = SearchGateState.Completed(query, results)
            } finally {
                // A no-op when a newer request already owns the slot; ending the request here
                // keeps the slot idle for the next search. Non-cancellable and off the caller's
                // dispatcher, because this runs for a cancelled job too and the seam may resolve
                // the native client.
                withContext(NonCancellable + requestDispatcher()) {
                    requests.cancelSearch(request)
                }
                if (generation == request) {
                    generation = NO_GENERATION
                }
            }
        }
    }

    companion object {
        /** Below this many characters a surface runs nothing (spec: `search-request-lifecycle`). */
        const val MIN_QUERY_LENGTH = 2

        /** Debounce after the last keystroke; the car's spec allows up to 500 ms. */
        const val DEFAULT_DEBOUNCE_MS = 300L

        /** The sentinel for "no live request"; real generations start at 1 (see search_breaker.h). */
        const val NO_GENERATION = 0L
    }
}
