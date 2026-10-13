package com.naviveylin.core

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
 * Outcome of reading a shared payload (share text, deep link or map URL).
 *
 * - [Destination] — the payload names exactly one place: coordinates, a free-text
 *   query, or both.
 * - [UnusableLink] — a *recognized* map link that carries neither a coordinate nor a
 *   place name (an unresolvable short link, a link with only an opaque place id). The
 *   caller reports it; the link text is never handed on as a query (spec:
 *   share/location-receiving — An unreadable shared map link is reported, never
 *   searched).
 * - [None] — the payload is not a link and carries no location.
 */
sealed interface DeepLinkResult {
    data class Destination(val destination: DeepLinkDestination) : DeepLinkResult
    data object UnusableLink : DeepLinkResult
    data object None : DeepLinkResult
}

/** The destination payload, or null for [DeepLinkResult.UnusableLink] and [DeepLinkResult.None]. */
val DeepLinkResult.destination: DeepLinkDestination?
    get() = (this as? DeepLinkResult.Destination)?.destination

/**
 * Parses phone → car deep-link intents into a [DeepLinkResult].
 *
 * Supported formats:
 * - `geo:` URIs — `geo:lat,lon`, `geo:lat,lon?q=label`, `geo:0,0?q=lat,lon(label)`, `geo:0,0?q=address`
 * - Google Maps URLs — modern place links (`/maps/place/<Name>/@…/data=!3d…!4d…`), viewport links,
 *   `q=`/`query=`/`daddr=`/`destination=`/`ll=` parameters, `/maps/place/<lat>,<lon>`, `goo.gl/maps/…`,
 *   `google.<country>` hosts; tracking parameters (`g_st`, `entry`, `g_ep`) are ignored
 * - `google.navigation:q=...`
 * - Share text ([Intent.EXTRA_TEXT]) — `"lat, lon"`, an address, or a map link in prose
 * - [Intent.EXTRA_QUERY]
 *
 * A URL is never returned as a query: a recognized map link that cannot be read yields
 * [DeepLinkResult.UnusableLink], and a non-link URL the same.
 */
object DeepLinkParser {

    private const val GOOGLE_NAVIGATION_SCHEME = "google.navigation"
    private const val GEO_SCHEME = "geo"

    /** Assistant / search query extra — not exposed as a named constant in all SDKs. */
    private const val EXTRA_QUERY = "android.intent.extra.QUERY"

    /** Google Maps place pin inside the `data=` path segment: `!3d<lat>!4d<lon>`. */
    private val DATA_PIN = Regex("""!3d(-?\d{1,3}(?:\.\d+)?)!4d(-?\d{1,3}(?:\.\d+)?)""")

    /** Google Maps viewport centre: `/@<lat>,<lon>[,<zoom>z]`. */
    private val VIEWPORT = Regex(
        """@(-?\d{1,3}(?:\.\d+)?),(-?\d{1,3}(?:\.\d+)?)(?:,\d+(?:\.\d+)?z)?(?=/|\?|$)"""
    )

    /** A bare URL, so it can be recognized before it is mistaken for a place name. */
    private val URL_LIKE = Regex("""^(?:https?://|geo:|google\.navigation:)\S+$""", RegexOption.IGNORE_CASE)

    /** A link inside share prose: `"Eiffel Tower\nhttps://maps.app.goo.gl/abc"`. */
    private val URL_IN_TEXT = Regex("""https?://\S+""")

    /**
     * Parse a deep-link [Intent].
     *
     * @param shortLinkResolver resolves a `maps.app.goo.gl` short link to its
     *   target URL (network call, injected so the parser stays pure); when null
     *   is returned the link is unreadable rather than a query.
     */
    fun parse(intent: Intent?, shortLinkResolver: (String) -> String? = { null }): DeepLinkResult {
        if (intent == null) return DeepLinkResult.None

        val fromUri = parseUri(intent.dataString ?: intent.data?.toString(), shortLinkResolver)
        if (fromUri != DeepLinkResult.None) return fromUri

        // Share / assistant text: "48.8566, 2.3522", an address, or a link in prose.
        intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.let { text ->
            if (text.isNotEmpty()) {
                val fromText = fromShareText(text, shortLinkResolver)
                if (fromText != DeepLinkResult.None) return fromText
            }
        }

        intent.getStringExtra(EXTRA_QUERY)?.trim()?.let { query ->
            if (query.isNotEmpty()) return queryOrUnusableLink(query)
        }
        // Fall back to the raw data URI for schemes we did not pattern-match
        intent.dataString?.let { uri ->
            val raw = parseCoordinates(uri)
            if (raw != null) {
                return DeepLinkResult.Destination(DeepLinkDestination(raw.first, raw.second, null))
            }
        }

        return DeepLinkResult.None
    }

    /**
     * Parse a URI string (geo, google maps URL, google.navigation, OSM, Apple/Waze).
     * Pure function — unit-testable without Android.
     *
     * @param shortLinkResolver resolves a `maps.app.goo.gl` short link to its
     *   target URL; when null is returned the link is unreadable, never a query.
     */
    fun parseUri(uriString: String?, shortLinkResolver: (String) -> String? = { null }): DeepLinkResult {
        if (uriString.isNullOrBlank()) return DeepLinkResult.None

        val raw = uriString.trim()
        val uri = try {
            Uri.parse(raw)
        } catch (e: Exception) {
            return DeepLinkResult.None
        }
        val scheme = uri.scheme?.lowercase() ?: return DeepLinkResult.None
        val host = uri.host?.lowercase()
        val path = uri.path.orEmpty()

        return when {
            scheme == GEO_SCHEME -> parseGeoUri(uri).asResult()
            scheme == GOOGLE_NAVIGATION_SCHEME -> {
                val body = uri.schemeSpecificPart ?: return DeepLinkResult.None
                // "q=..." — plus signs encode spaces in this scheme
                val q = body.removePrefix("q=").trim().replace("+", " ")
                if (q.isEmpty()) DeepLinkResult.None else fromFreeText(q).asResult()
            }
            scheme == "https" || scheme == "http" -> parseWebUri(uri, raw, host, path, shortLinkResolver)
            else -> DeepLinkResult.None
        }
    }

    private fun parseWebUri(
        uri: Uri,
        raw: String,
        host: String?,
        path: String,
        shortLinkResolver: (String) -> String?
    ): DeepLinkResult {
        // Short links: a direct payload parses without resolution, otherwise the link
        // must be resolved before it can be read (spec: share/location-receiving —
        // Link resolved through a short link). An unreadable one is reported, not searched.
        if (isShortLinkHost(host, path)) {
            parseMapsUrl(uri, raw)?.let { return DeepLinkResult.Destination(it) }
            val resolved = try {
                shortLinkResolver(raw)
            } catch (e: Exception) {
                null
            }
            val fromTarget =
                if (resolved.isNullOrBlank() || resolved == raw) DeepLinkResult.None
                else parseUri(resolved, shortLinkResolver)
            return if (fromTarget is DeepLinkResult.Destination) fromTarget else DeepLinkResult.UnusableLink
        }

        if (!isMapHost(host, path)) return DeepLinkResult.None
        if (isOsmHost(host)) {
            parseOsmUrl(uri)?.let { return DeepLinkResult.Destination(it) }
        }
        // Recognized map link: a coordinate, else the place name it carries, else unusable.
        return parseMapsUrl(uri, raw)?.let { DeepLinkResult.Destination(it) } ?: DeepLinkResult.UnusableLink
    }

    private fun DeepLinkDestination?.asResult(): DeepLinkResult =
        this?.let { DeepLinkResult.Destination(it) } ?: DeepLinkResult.None

    /** A free-text share payload: a bare URI, a link inside prose, or the text itself as a query. */
    private fun fromShareText(text: String, shortLinkResolver: (String) -> String?): DeepLinkResult {
        // The whole payload is a URI (map link, `geo:` or `google.navigation:`).
        if (URL_LIKE.matches(text)) {
            return when (val parsed = parseUri(text, shortLinkResolver)) {
                is DeepLinkResult.Destination -> parsed
                else -> DeepLinkResult.UnusableLink
            }
        }

        val link = URL_IN_TEXT.find(text)?.value?.trimEnd('.', ',', ';', ')', ']')
        if (link == null) return fromFreeText(text).asResult()

        val prose = text.replace(link, " ").trim()
        return when (val parsed = parseUri(link, shortLinkResolver)) {
            is DeepLinkResult.Destination -> DeepLinkResult.Destination(
                // The prose is a name hint for the link's destination — never the link text.
                parsed.destination.copy(query = parsed.destination.query ?: prose.ifEmpty { null })
            )
            // A link that cannot be read is never the query. The surrounding prose, if any,
            // is the only text left that can name the place.
            DeepLinkResult.UnusableLink, DeepLinkResult.None ->
                if (prose.isEmpty()) DeepLinkResult.UnusableLink
                else DeepLinkResult.Destination(DeepLinkDestination(null, null, prose))
        }
    }

    /** A free-text query, refusing a bare URL (a URL is never a location name). */
    private fun queryOrUnusableLink(text: String): DeepLinkResult =
        if (URL_LIKE.matches(text)) DeepLinkResult.UnusableLink
        else DeepLinkResult.Destination(DeepLinkDestination(null, null, text))

    private fun isShortLinkHost(host: String?, path: String): Boolean =
        host == "maps.app.goo.gl" || (host == "goo.gl" && path.startsWith("/maps"))

    /** A map short link whose target needs a network resolution before it can be read. */
    private val SHORT_LINK = Regex(
        """^https?://(?:maps\.app\.goo\.gl|goo\.gl/maps)/""",
        RegexOption.IGNORE_CASE
    )

    /**
     * True when [payload] is a map short link — reading it needs a network resolution first
     * (`maps.app.goo.gl/…`, `goo.gl/maps/…`). The car session asks this before its one network hop
     * so a `geo:` URI or a full place link is never fetched. A plain string test on purpose: it is
     * called from the car session's hot path and from unit tests without an Android `Uri`.
     */
    fun isShortLink(payload: String?): Boolean =
        payload != null && SHORT_LINK.containsMatchIn(payload.trim())

    private fun isMapHost(host: String?, path: String): Boolean =
        host == "maps.google.com" || host == "www.google.com" || host == "google.com" ||
            (host != null && host.contains("google") && path.contains("maps")) ||
            isOsmHost(host) ||
            host == "maps.apple.com" || host == "waze.com" || host == "www.waze.com" ||
            host == "share.here.com" || host == "here.com"

    private fun isOsmHost(host: String?): Boolean =
        host == "www.openstreetmap.org" || host == "openstreetmap.org" || host == "osm.org"

    /** OSM URLs: `?mlat=&mlon=` or `#map zoom/lat/lon`. */
    private fun parseOsmUrl(uri: Uri): DeepLinkDestination? {
        val mlat = uri.getQueryParameter("mlat")
        val mlon = uri.getQueryParameter("mlon")
        if (mlat != null && mlon != null) {
            val lat = mlat.toDoubleOrNull()
            val lon = mlon.toDoubleOrNull()
            if (validCoordinates(lat, lon)) {
                return DeepLinkDestination(lat, lon, null)
            }
        }
        // #map=16/48.8566/2.3522
        val fragment = uri.fragment
        if (fragment != null && fragment.startsWith("map=")) {
            val parts = fragment.removePrefix("map=").split("/")
            if (parts.size >= 3) {
                val lat = parts[1].toDoubleOrNull()
                val lon = parts[2].toDoubleOrNull()
                if (validCoordinates(lat, lon)) {
                    return DeepLinkDestination(lat, lon, null)
                }
            }
        }
        return null
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

    /**
     * Google Maps URL, read in this order (spec: share/location-receiving — Coordinate
     * priority favors the place pin):
     *
     * 1. `data=!3d<lat>!4d<lon>` — the place pin, the exact object
     * 2. `/@<lat>,<lon>,<zoom>z` — the viewport centre
     * 3. `ll=`, `q=`, `query=`, `daddr=`, `destination=` parameters
     * 4. `/place/<lat>,<lon>` path coordinates
     * 5. the place name from `/place/<Name>/` as a query (no coordinate)
     */
    private fun parseMapsUrl(uri: Uri, raw: String): DeepLinkDestination? {
        // 1. Place pin — today's share links carry it in the `data=` path segment.
        DATA_PIN.find(raw)?.let { pin ->
            val lat = pin.groupValues[1].toDoubleOrNull()
            val lon = pin.groupValues[2].toDoubleOrNull()
            if (validCoordinates(lat, lon)) {
                return DeepLinkDestination(lat, lon, placeName(uri))
            }
        }
        // 2. Viewport centre.
        VIEWPORT.find(raw)?.let { viewport ->
            val lat = viewport.groupValues[1].toDoubleOrNull()
            val lon = viewport.groupValues[2].toDoubleOrNull()
            if (validCoordinates(lat, lon)) {
                return DeepLinkDestination(lat, lon, placeName(uri))
            }
        }
        // 3. Query parameters: Apple Maps / Waze `ll=`, Google Maps `q=`/`daddr=`/`destination=`,
        //    modern search links `query=`.
        val ll = uri.getQueryParameter("ll")
        if (!ll.isNullOrBlank()) {
            val (lat, lon, label) = extractCoordinatesFromQuery(ll)
            if (lat != null && lon != null) return DeepLinkDestination(lat, lon, label)
        }
        for (key in PARAM_KEYS) {
            val value = uri.getQueryParameter(key)
            if (value.isNullOrBlank()) continue
            val (lat, lon, label) = extractCoordinatesFromQuery(value)
            if (lat != null && lon != null) return DeepLinkDestination(lat, lon, label)
            return DeepLinkDestination(null, null, value.trim())
        }
        // 4. Place path coordinates: `/maps/place/48.8566,2.3522`.
        placeSegment(uri)?.let { segment ->
            parseCoordinates(segment)?.let { return DeepLinkDestination(it.first, it.second, null) }
        }
        // 5. Place name without a coordinate: the caller searches for that name.
        return placeName(uri)?.let { DeepLinkDestination(null, null, it) }
    }

    /** The `/place/<segment>/` path segment, percent-decoded (`+` is a space). */
    private fun placeSegment(uri: Uri): String? {
        val path = uri.path ?: return null
        val marker = "/place/"
        val index = path.indexOf(marker, ignoreCase = true)
        if (index < 0) return null
        val segment = path.substring(index + marker.length).substringBefore('/')
        if (segment.isBlank()) return null
        val name = Uri.decode(segment.replace("+", " ")).trim()
        return name.ifEmpty { null }
    }

    /** The place name a link carries, or null when the segment is itself a coordinate pair. */
    private fun placeName(uri: Uri): String? =
        placeSegment(uri)?.takeIf { parseCoordinates(it) == null }

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

        // DMS (48°51'23.8"N 2°21'8.0"E) and hemisphere (48.8566N 2.3522E) forms
        val dms = parseDmsOrHemisphere(text)
        if (dms != null) return dms

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

    private fun validCoordinates(lat: Double?, lon: Double?): Boolean =
        lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0

    /**
     * Parse DMS (48°51'23.8"N 2°21'8.0"E) and hemisphere (48.8566N 2.3522E)
     * coordinate text. Returns null when neither form matches.
     */
    private fun parseDmsOrHemisphere(text: String): Triple<Double?, Double?, String?>? {
        val trimmed = text.trim()

        // DMS: 48°51'23.8"N 2°21'8.0"E  (seconds optional, quotes optional)
        val dms = DMS_REGEX.matchEntire(trimmed)
        if (dms != null) {
            val lat = dmsToDecimal(
                dms.groupValues[1].toDouble(),
                dms.groupValues[2].toDouble(),
                dms.groupValues[3].toDouble(),
                dms.groupValues[4]
            )
            val lon = dmsToDecimal(
                dms.groupValues[5].toDouble(),
                dms.groupValues[6].toDouble(),
                dms.groupValues[7].toDouble(),
                dms.groupValues[8]
            )
            if (lat != null && lon != null) return Triple(lat, lon, null)
        }

        // Hemisphere: 48.8566N 2.3522E
        val hem = HEMISPHERE_REGEX.matchEntire(trimmed)
        if (hem != null) {
            var lat = hem.groupValues[1].toDouble()
            var lon = hem.groupValues[3].toDouble()
            if (hem.groupValues[2] == "S") lat = -lat
            if (hem.groupValues[4] == "W") lon = -lon
            if (lat in -90.0..90.0 && lon in -180.0..180.0) return Triple(lat, lon, null)
        }

        return null
    }

    private fun dmsToDecimal(deg: Double, min: Double, sec: Double, hemi: String): Double? {
        if (deg > 180.0 || min >= 60.0 || sec >= 60.0) return null
        var value = deg + min / 60.0 + sec / 3600.0
        if (hemi == "S" || hemi == "W") value = -value
        return value
    }

    private val DMS_REGEX = Regex(
        """(\d{1,3})°\s*(\d{1,2})'\s*(\d{1,2}(?:\.\d+)?)"?\s*([NS]),?\s*""" +
            """(\d{1,3})°\s*(\d{1,2})'\s*(\d{1,2}(?:\.\d+)?)"?\s*([EW])"""
    )

    private val HEMISPHERE_REGEX = Regex(
        """(-?\d{1,3}(?:\.\d+)?)\s*([NS]),?\s*(-?\d{1,3}(?:\.\d+)?)\s*([EW])"""
    )

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

    /** Query parameters that carry a destination, in preference order. */
    private val PARAM_KEYS = listOf("q", "query", "daddr", "destination")
}
