package com.naviveylin.core

import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.search.SearchReference
import com.naviveylin.core.search.SearchRequestClient
import com.naviveylin.core.search.SearchSlots

/**
 * Provider for location search, consumed by Android Auto screens.
 * Implemented in the [:app] module via Hilt.
 *
 * The provider owns the search pipeline — the larger candidate set, the
 * structured-address merge and the match-tier ranking (spec:
 * search-result-ranking) — so the car screen stays a thin adapter over the same
 * logic the phone uses.
 *
 * It is also the car surface's request-lifecycle seam: the screen's search gate begins a request for
 * the car slot, hands the generation to the search below, and cancels it when nobody wants the
 * result any more (spec: `search-request-lifecycle`). The generation-less overload is the standalone
 * path — a caller that owns no request of its own begins and ends one here.
 */
interface AutoSearchProvider : SearchRequestClient {

    /**
     * Ranked location results for [query], at most [limit] entries, under [generation]'s request.
     *
     * @param reference point the results are ordered by distance against — the
     *   last known GPS fix, else the car map viewport center, else null (order
     *   by tier and match quality only)
     * @param generation the car request's generation: the native walk stops at its next abort check
     *   once a newer car request began or this one was cancelled
     */
    fun searchLocations(
        query: String,
        limit: Int,
        reference: SearchReference?,
        generation: Long
    ): List<LocationEntry>

    /** A standalone search that owns no caller request: it begins and ends one for the car slot. */
    fun searchLocations(query: String, limit: Int, reference: SearchReference?): List<LocationEntry> {
        val generation = beginSearch(SearchSlots.CAR)

        try {
            return searchLocations(query, limit, reference, generation)
        } finally {
            cancelSearch(generation)
        }
    }
}
