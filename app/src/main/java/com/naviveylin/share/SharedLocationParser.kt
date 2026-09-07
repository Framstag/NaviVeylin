package com.naviveylin.share

import android.content.Intent
import com.naviveylin.core.DeepLinkParser
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A location shared into NaviVeylin from another app, parsed from the intent.
 *
 * Exactly one of the two forms is populated:
 * - coordinates ([lat]/[lon], with a display [label]) — a raw geo point
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
 * Maps a share/deep-link [Intent] to a [SharedLocationRequest].
 *
 * Format parsing is delegated to [DeepLinkParser] (shared with the car flow);
 * this class adds the phone-specific bits: the display label from
 * [Intent.EXTRA_SUBJECT] and `maps.app.goo.gl` short-link resolution.
 *
 * @param shortLinkResolver resolves a short link to its target URL; the default
 *   follows HTTP redirects, a fake can be injected in tests. A null result
 *   degrades the link to a raw-text query.
 */
class SharedLocationParser(
    private val shortLinkResolver: (String) -> String? = SharedLocationParser::resolveShortLink
) {

    /** Parse the intent on the IO dispatcher (short-link resolution is network). */
    suspend fun parse(intent: Intent?): SharedLocationRequest? = withContext(Dispatchers.IO) {
        if (intent == null) return@withContext null
        val dest = DeepLinkParser.parse(intent, shortLinkResolver) ?: return@withContext null

        if (dest.hasCoordinates) {
            val label = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                ?.takeIf { it.isNotBlank() }
                ?: String.format(Locale.US, "%.5f, %.5f", dest.lat!!, dest.lon!!)
            SharedLocationRequest(lat = dest.lat, lon = dest.lon, label = label)
        } else {
            dest.query?.takeIf { it.isNotBlank() }?.let { SharedLocationRequest(query = it) }
        }
    }

    companion object {
        private const val TIMEOUT_MS = 5_000

        /**
         * Follow HTTP redirects to the final URL of a short link.
         * Returns null when the URL does not redirect or the request fails.
         */
        fun resolveShortLink(url: String): String? = try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"
            val finalUrl = conn.url.toString()
            conn.disconnect()
            if (finalUrl == url) null else finalUrl
        } catch (e: Exception) {
            null
        }
    }
}
