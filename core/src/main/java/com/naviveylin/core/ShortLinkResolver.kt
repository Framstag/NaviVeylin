package com.naviveylin.core

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One response to a link request: the status code and, for a redirect, its `Location` header. */
data class LinkHop(val status: Int, val location: String? = null)

/**
 * Follows a map short link (`maps.app.goo.gl/…`, `goo.gl/maps/…`) to the URL it redirects to.
 *
 * Shared by the phone share flow (`:app`) and the car session (`:auto`), so the timeout, the redirect
 * policy and the failure handling exist once (`guidelines/Design.md` §1). [resolve] is a suspend
 * function with no dispatcher of its own: both callers already run it off their own thread — the
 * phone wrapper on `Dispatchers.IO`, the car session inside its own coroutine — so no caller can
 * resolve on the main/host thread by accident.
 *
 * The redirect chain is walked **by hand** rather than left to `HttpURLConnection`'s
 * `instanceFollowRedirects`: after following a redirect that connection still reports the *original*
 * URL through `getURL()`, so an automatic follow makes "did it redirect?" unanswerable (measured
 * 2026-10-12 on a real `maps.app.goo.gl` link, which resolved to a place URL in a shell but was
 * reported unresolved in the app). Each hop is one [hop] call, which is why the policy is testable
 * without a network.
 *
 * Timing bound: [MAX_HOPS] requests at [TIMEOUT_MS] each, i.e. up to ~25 s for a chain whose remote
 * stalls between hops (a real Google chain answers in well under a second and needs one or two hops).
 * The work never runs on a caller's own thread — see the class note above.
 *
 * @param hop one request (null when it could not be performed), replaceable in tests (unit tests
 *   never touch the network).
 */
class ShortLinkResolver(
    private val hop: (String) -> LinkHop? = ::httpHop
) {

    /**
     * The final URL of [url], or null when it does not redirect, the chain does not terminate, or a
     * request fails. Never throws: an unresolvable link degrades to "unreadable", not to a fault.
     */
    suspend fun resolve(url: String?): String? = withContext(Dispatchers.IO) { follow(url) }

    /**
     * The redirect walk without a dispatcher hop — for a caller that is already on a background
     * thread (the phone wrapper's synchronous parser seam). Never call it on the main thread.
     */
    fun resolveBlocking(url: String?): String? = follow(url)

    /** The redirect walk, synchronous so a test can drive it hop by hop. */
    internal fun follow(url: String?): String? {
        val start = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        var current = start
        repeat(MAX_HOPS) {
            val response = hop(current) ?: return null
            val location = response.location
            if (response.status !in REDIRECT_STATUSES || location.isNullOrBlank()) {
                // The chain ended: only a URL different from the link itself is a resolution.
                return current.takeIf { it != start }
            }
            current = absoluteLocation(current, location) ?: return null
        }
        // A chain that never terminates is not a resolution.
        return null
    }

    private fun absoluteLocation(base: String, location: String): String? = try {
        URL(URL(base), location).toString()
    } catch (e: Exception) {
        null
    }

    companion object {

        private const val TIMEOUT_MS = 5_000
        private const val MAX_HOPS = 5

        /** HTTP statuses that carry the next hop in their `Location` header. */
        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)

        /**
         * The production transport: one request without an automatic redirect follow, returning the
         * status and the `Location` header (or null when the URL is unusable or the request fails).
         */
        fun httpHop(url: String): LinkHop? = try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"
            val status = conn.responseCode
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            LinkHop(status, location)
        } catch (e: Exception) {
            null
        }
    }
}
