package com.naviveylin.auto

import android.content.Intent
import android.net.Uri

/**
 * Parsed destination from a phone → car deep link.
 *
 * @param lat latitude, or null when only a free-text query is present
 * @param lon longitude, or null when only a free-text query is present
 * @param query free-text location query (address/label), or null for pure coordinates
 */
data class DeepLinkDestination(
    val lat: Double?,
    val lon: Double?,
    val query: String?
) {
    /** True when the destination carries usable coordinates. */
    val hasCoordinates: Boolean get() = lat != null && lon != null
}

/**
 * Parses phone → car deep-link intents into a [DeepLinkDestination].
 *
 * Supported formats:
 * - `geo:` URIs — `geo:lat,lon`, `geo:lat,lon?q=label`, `geo:0,0?q=lat,lon(label)`, `geo:0,0?q=address`
 * - Google Maps URLs — `q=lat,lon` or `q=query`, `daddr=lat,lon`, `destination=lat,lon`
 * - `google.navigation:q=...`
 * - Share text ([Intent.EXTRA_TEXT]) — `"lat, lon"` or an address
 * - [Intent.EXTRA_QUERY]
 */
object DeepLinkParser {

    private const val GOOGLE_NAVIGATION_SCHEME = "google.navigation"
    private const val GEO_SCHEME = "geo"

    /** Assistant / search query extra — not exposed as a named constant in all SDKs. */
    private const val EXTRA_QUERY = "android.intent.extra.QUERY"

    /**
     * Parse a deep-link [Intent]. Returns null when nothing usable is found.
     */
    fun parse(intent: Intent?): DeepLinkDestination? {
        if (intent == null) return null

        val fromUri = parseUri(intent.dataString ?: intent.data?.toString())
        if (fromUri != null) return fromUri

        // Share / assistant text: "48.8566, 2.3522" or an address
        intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.let { text ->
            if (text.isNotEmpty()) {
                val fromText = fromFreeText(text)
                if (fromText != null) return fromText
            }
        }

        intent.getStringExtra(EXTRA_QUERY)?.trim()?.let { query ->
            if (query.isNotEmpty()) return DeepLinkDestination(null, null, query)
        }
        // Fall back to the raw data URI for schemes we did not pattern-match
        intent.dataString?.let { uri ->
            val raw = parseCoordinates(uri)
            if (raw != null) {
                return DeepLinkDestination(raw.first, raw.second, null)
            }
        }

        return null
    }

    /**
     * Parse a URI string (geo, google maps URL, google.navigation).
     * Pure function — unit-testable without Android.
     */
    fun parseUri(uriString: String?): DeepLinkDestination? {
        if (uriString.isNullOrBlank()) return null

        val uri = try {
            Uri.parse(uriString.trim())
        } catch (e: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase()
        val path = uri.path?.lowercase().orEmpty()

        return when {
            scheme == GEO_SCHEME -> parseGeoUri(uri)
            scheme == GOOGLE_NAVIGATION_SCHEME -> {
                val body = uri.schemeSpecificPart ?: return null
                // "q=..." — plus signs encode spaces in this scheme
                val q = body.removePrefix("q=").trim().replace("+", " ")
                if (q.isEmpty()) return null
                fromFreeText(q)
            }
            scheme == "https" || scheme == "http" -> {
                val isMapsHost = host == "maps.google.com" || host == "www.google.com" ||
                    host == "maps.app.goo.gl" || host == "google.com" ||
                    (host?.contains("google") == true && path.contains("maps"))
                if (!isMapsHost) return null
                parseMapsUrl(uri)
            }
            else -> null
        }
    }

    private fun parseGeoUri(uri: Uri): DeepLinkDestination? {
        val body = uri.schemeSpecificPart ?: return null
        // body is "lat,lon" or "lat,lon?q=..." or "0,0?q=..."
        val coordsPart = body.substringBefore("?")
        val queryPart = body.substringAfter("?", "")
        // Decode the raw query: "q=48.8566,2.3522(Eiffel%20Tower)" (geo: is opaque —
        // Uri.getQueryParameter throws UnsupportedOperationException on it)
        val q = queryPart.removePrefix("q=").let { Uri.decode(it) }.trim()

        val coord = parseCoordinates(coordsPart)
        if (coord != null && !(coord.first == 0.0 && coord.second == 0.0 && q.isNotEmpty())) {
            // geo:lat,lon or geo:lat,lon?q=label
            return DeepLinkDestination(coord.first, coord.second, q.takeIf { it.isNotEmpty() }?.let { stripLabel(it) })
        }

        // geo:0,0?q=lat,lon(label) or geo:0,0?q=address
        if (q.isNotEmpty()) {
            // Try "48.8566,2.3522(Label)" / "48.8566, 2.3522 Label"
            val (lat, lon, label) = extractCoordinatesFromQuery(q)
            if (lat != null && lon != null) {
                return DeepLinkDestination(lat, lon, label)
            }
            return DeepLinkDestination(null, null, stripLabel(q))
        }
        return null
    }

    private fun parseMapsUrl(uri: Uri): DeepLinkDestination? {
        // q=lat,lon | q=query | daddr=lat,lon | destination=lat,lon
        val q = uri.getQueryParameter("q")
        if (!q.isNullOrBlank()) {
            val (lat, lon, label) = extractCoordinatesFromQuery(q)
            if (lat != null && lon != null) return DeepLinkDestination(lat, lon, label)
            return DeepLinkDestination(null, null, q.trim())
        }
        val daddr = uri.getQueryParameter("daddr")
        if (!daddr.isNullOrBlank()) {
            val (lat, lon, label) = extractCoordinatesFromQuery(daddr)
            if (lat != null && lon != null) return DeepLinkDestination(lat, lon, label)
            return DeepLinkDestination(null, null, daddr.trim())
        }
        val destination = uri.getQueryParameter("destination")
        if (!destination.isNullOrBlank()) {
            val (lat, lon, label) = extractCoordinatesFromQuery(destination)
            if (lat != null && lon != null) return DeepLinkDestination(lat, lon, label)
            return DeepLinkDestination(null, null, destination.trim())
        }
        return null
    }

    /** Parse free text (share text / query) into a destination. */
    private fun fromFreeText(text: String): DeepLinkDestination? {
        val (lat, lon, label) = extractCoordinatesFromQuery(text)
        if (lat != null && lon != null) {
            return DeepLinkDestination(lat, lon, label)
        }
        if (text.isNotBlank()) {
            return DeepLinkDestination(null, null, text.trim())
        }
        return null
    }

    /**
     * Extract a coordinate pair from text: "48.8566,2.3522", "48.8566, 2.3522",
     * "48.8566,2.3522(Label)", "48.8566, 2.3522 Label".
     * Returns (null, null, null) when no coordinate pair is present.
     */
    fun extractCoordinatesFromQuery(text: String?): Triple<Double?, Double?, String?> {
        if (text.isNullOrBlank()) return Triple(null, null, null)

        // Strip parenthesized label: "48.8566,2.3522(Label)"
        var candidate = text.trim()
        var label: String? = null
        val parenIdx = candidate.indexOf('(')
        if (parenIdx > 0 && candidate.endsWith(")")) {
            label = candidate.substring(parenIdx + 1, candidate.length - 1).trim()
            candidate = candidate.substring(0, parenIdx).trim()
        }

        val coords = parseCoordinates(candidate)
        if (coords != null) {
            // Trailing label without parens: "48.8566, 2.3522 Eiffel Tower"
            if (label == null) {
                val spaceIdx = candidate.indexOf(' ')
                if (spaceIdx > 0) {
                    val tail = candidate.substring(spaceIdx + 1).trim()
                    if (tail.isNotEmpty()) label = tail
                }
            }
            return Triple(coords.first, coords.second, label)
        }

        // "48.8566, 2.3522 Label" — coords separated by comma, label after
        val parts = text.trim().split(",").map { it.trim() }
        if (parts.size >= 2) {
            val lat = parts[0].toDoubleOrNull()
            val second = parts[1].split(Regex("\\s+"), limit = 2)
            val lon = second[0].toDoubleOrNull()
            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                val tail = second.getOrNull(1)?.takeIf { it.isNotBlank() }
                return Triple(lat, lon, tail ?: label)
            }
        }
        return Triple(null, null, null)
    }

    /**
     * Parse a bare coordinate pair: "48.8566,2.3522" (comma-separated, no label).
     */
    fun parseCoordinates(text: String?): Pair<Double, Double>? {
        if (text.isNullOrBlank()) return null
        val parts = text.trim().split(",").map { it.trim() }
        if (parts.size < 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].substringBefore(" ").toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }

    private fun stripLabel(query: String): String? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        // "48.8566,2.3522(Label)" → label only
        val parenIdx = trimmed.indexOf('(')
        if (parenIdx > 0 && trimmed.endsWith(")")) {
            return trimmed.substring(parenIdx + 1, trimmed.length - 1).trim()
        }
        return trimmed
    }
}
