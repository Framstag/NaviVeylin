package com.naviveylin.core

import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.search.SearchReference

/**
 * Provider for location search, consumed by Android Auto screens.
 * Implemented in the [:app] module via Hilt.
 *
 * The provider owns the search pipeline — the larger candidate set, the
 * structured-address merge and the match-tier ranking (spec:
 * search-result-ranking) — so the car screen stays a thin adapter over the same
 * logic the phone uses.
 */
fun interface AutoSearchProvider {

    /**
     * Ranked location results for [query], at most [limit] entries.
     *
     * @param reference point the results are ordered by distance against — the
     *   last known GPS fix, else the car map viewport center, else null (order
     *   by tier and match quality only)
     */
    fun searchLocations(query: String, limit: Int, reference: SearchReference?): List<LocationEntry>
}
