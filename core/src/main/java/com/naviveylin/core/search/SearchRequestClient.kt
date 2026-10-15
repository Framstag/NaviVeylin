package com.naviveylin.core.search

import com.framstag.libosmscout.client.OSMScoutClient

/**
 * The search-request lifecycle as the search gate needs it (spec: `search-request-lifecycle`).
 *
 * A gate begins a request for its own slot, hands the generation to the search, and cancels it
 * when nobody wants the result any more. The native side owns the rule — one slot per surface,
 * one generation per request, a request aborted once a newer one of its slot began or it was
 * cancelled (`search_breaker.h`) — and this seam is what keeps the gate testable without a
 * client: the tests use a recording double, never the native library.
 */
interface SearchRequestClient {
    /**
     * Begins this slot's next request and returns its generation. A request still running for
     * that slot is superseded here, which stops it at its next abort check.
     */
    fun beginSearch(slot: String): Long

    /**
     * Cancels the request with this generation and reports whether it was still live. A
     * generation a newer request already replaced aborts nothing and reports `false`, which is
     * why a cancellation arriving late is harmless.
     */
    fun cancelSearch(generation: Long): Boolean
}

/**
 * The search slots. The slots' normative home is `search_breaker.h`, whose constants carry the
 * same names; these exist so Kotlin (and a `:core` test) never has to touch the bridge class,
 * whose initializer loads the native library.
 */
object SearchSlots {
    const val PLACES = "places"
    const val POI = "poi"
    const val ROUTE = "route"
    const val CAR = "car"

    /** The car's POI results screen: its own surface, so it keeps its own slot. */
    const val CAR_POI = "car-poi"
}

/** The bridge as the gate's request-lifecycle seam. */
class ClientSearchRequests(private val client: OSMScoutClient) : SearchRequestClient {
    override fun beginSearch(slot: String): Long = client.beginSearch(slot)

    override fun cancelSearch(generation: Long): Boolean = client.cancelSearch(generation)
}
