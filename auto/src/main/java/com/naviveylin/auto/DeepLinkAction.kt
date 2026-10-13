package com.naviveylin.auto

import com.naviveylin.core.DeepLinkParser
import com.naviveylin.core.DeepLinkResult

/**
 * What the car session does with a deep link (spec: auto-deep-links).
 *
 * Separated from [NavigationSession] so the mapping from a parse outcome to an action is
 * unit-testable without a host-provided `CarContext` — the same seam shape the session's
 * log line uses.
 */
internal sealed interface DeepLinkAction {
    /** Navigate to the coordinate the link carries. */
    data class Navigate(val lat: Double, val lon: Double) : DeepLinkAction

    /** Geocode the free-text query and navigate to the first match. */
    data class Geocode(val query: String) : DeepLinkAction

    /** A map link that carries neither a coordinate nor a name: report it, never geocode it. */
    data object ReportUnusable : DeepLinkAction

    /** Nothing usable arrived: do nothing. */
    data object Nothing : DeepLinkAction
}

/**
 * The action a parse outcome implies:
 *
 * - a coordinate navigates directly,
 * - a name/address is geocoded,
 * - an unreadable map link is reported — its text is never handed to the search
 *   (spec: auto-deep-links — Short link does not resolve; share/location-receiving — An unreadable
 *   shared map link is reported, never searched),
 * - anything else does nothing.
 */
internal fun deepLinkAction(result: DeepLinkResult): DeepLinkAction = when (result) {
    is DeepLinkResult.Destination -> {
        val destination = result.destination
        val query = destination.query
        when {
            destination.hasCoordinates -> DeepLinkAction.Navigate(destination.lat!!, destination.lon!!)
            !query.isNullOrBlank() -> DeepLinkAction.Geocode(query)
            else -> DeepLinkAction.Nothing
        }
    }
    DeepLinkResult.UnusableLink -> DeepLinkAction.ReportUnusable
    DeepLinkResult.None -> DeepLinkAction.Nothing
}

/**
 * Read a deep-link payload: resolve it through [resolver] when it is a map short link, then parse
 * the target; otherwise parse the intent as received.
 *
 * [resolver] is a suspend call and is consulted *only* for a short link, so a `geo:` URI or a full
 * place link costs no network hop. The session calls this inside its own coroutine, which is what
 * keeps the resolution off the host path (`guidelines/Design.md` §4 and §8); that property is a fact
 * of the call site rather than of this seam, and is measured on the car run rather than asserted
 * here.
 */
internal suspend fun readDeepLinkPayload(
    payload: String?,
    parseIntent: () -> DeepLinkResult,
    resolver: suspend (String) -> String?
): DeepLinkResult {
    if (!DeepLinkParser.isShortLink(payload)) return parseIntent()
    val resolved = resolver(payload!!) ?: return DeepLinkResult.UnusableLink
    val fromTarget = DeepLinkParser.parseUri(resolved)
    // A target that is not a map link either: still an unreadable link, never a query.
    return if (fromTarget == DeepLinkResult.None) DeepLinkResult.UnusableLink else fromTarget
}
