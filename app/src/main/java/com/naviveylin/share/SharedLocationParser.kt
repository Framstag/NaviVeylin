package com.naviveylin.share

import android.content.Intent
import com.naviveylin.core.DeepLinkParser
import com.naviveylin.core.DeepLinkResult
import com.naviveylin.core.ShortLinkResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A location shared into NaviVeylin from another app, parsed from the intent.
 *
 * Exactly one of the two forms is populated:
 * - coordinates ([lat]/[lon], with an optional name hint in [label]) — a raw geo point
 * - [query] — free-text address/place name without coordinates
 */
data class SharedLocationRequest(
    val lat: Double? = null,
    val lon: Double? = null,
    val label: String? = null,
    val query: String? = null
) {
    /** True when the request carries usable coordinates. */
    val hasCoordinates: Boolean get() = lat != null && lon != null
}

/**
 * What a shared intent turned into on the phone.
 *
 * - [Request] — a usable location: coordinates (with a name hint) or free text.
 * - [UnusableLink] — a recognized map link that carries neither a coordinate nor a place
 *   name. It is reported with a hint; its text never reaches the search field (spec:
 *   share/location-receiving — An unreadable shared map link is reported, never searched).
 */
sealed interface SharedLocationOutcome {
    data class Request(val request: SharedLocationRequest) : SharedLocationOutcome
    data object UnusableLink : SharedLocationOutcome
}

/**
 * Maps a share/deep-link [Intent] to a [SharedLocationOutcome].
 *
 * Format parsing is delegated to [DeepLinkParser] (shared with the car flow); this class adds
 * the phone-specific bits: the display label from [Intent.EXTRA_SUBJECT] (falling back to the
 * place name the link itself carries) and short-link resolution through the shared
 * [ShortLinkResolver] transport.
 *
 * @param shortLinkResolver resolves a short link to its target URL; the default walks the redirect
 *   chain through the shared `:core` transport (on the IO dispatcher this method already runs on), a
 *   fake can be injected in tests. A null result makes the link unreadable rather than a query.
 */
class SharedLocationParser(
    private val shortLinkResolver: (String) -> String? = ShortLinkResolver()::resolveBlocking
) {

    /** Parse the intent on the IO dispatcher (short-link resolution is network). */
    suspend fun parse(intent: Intent?): SharedLocationOutcome? = withContext(Dispatchers.IO) {
        if (intent == null) return@withContext null

        when (val result = DeepLinkParser.parse(intent, shortLinkResolver)) {
            is DeepLinkResult.Destination -> destinationOutcome(intent, result)
            DeepLinkResult.UnusableLink -> SharedLocationOutcome.UnusableLink
            DeepLinkResult.None -> null
        }
    }

    private fun destinationOutcome(
        intent: Intent,
        result: DeepLinkResult.Destination
    ): SharedLocationOutcome? {
        val destination = result.destination
        if (!destination.hasCoordinates) {
            return destination.query?.takeIf { it.isNotBlank() }
                ?.let { SharedLocationOutcome.Request(SharedLocationRequest(query = it)) }
        }
        // The label is the share-subject hint, else the place name the link carries — never a
        // synthesized coordinate pair: it is identity for the log line, and a pair would be a
        // position in the diagnostics (spec: auto-diagnostics — Diagnostics carry no
        // coordinates). The display fallback is composed where the label is displayed
        // (MapCanvasViewModel).
        val label = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }
            ?: destination.query?.takeIf { it.isNotBlank() }
        return SharedLocationOutcome.Request(
            SharedLocationRequest(lat = destination.lat, lon = destination.lon, label = label)
        )
    }
}
