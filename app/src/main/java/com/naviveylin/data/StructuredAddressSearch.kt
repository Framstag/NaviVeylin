package com.naviveylin.data

import android.util.Log
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.OSMScoutClient

/**
 * Structured (form) search for free-text address queries (spec:
 * location-search — full formatted address resolution). When a raw query
 * tokenizes as an address (street + house number + postal code + city), the
 * structured location-index search returns house-level results with precise
 * coordinates; the raw string search alone drops such queries when a postal
 * code sits between the house number and the city.
 *
 * Used by the phone search dialog ([MapCanvasViewModel]), the Android Auto
 * search provider, and the contact resolver's query fallbacks.
 */
object StructuredAddressSearch {

    private const val TAG = "StructuredAddressSearch"
    private const val FORM_LIMIT = 20

    /**
     * Run a form search for [query] and return the ranked house/street/region
     * results, or an empty list when the query is not address-like or the
     * index has no match. Postal-area retry mirrors the contact resolver:
     * an unknown postal code must not block the street/house lookup.
     */
    fun resolve(query: String, client: OSMScoutClient): List<LocationEntry> {
        val parsed = AddressParser.parse(query) ?: return emptyList()
        if (parsed.city.isEmpty() || parsed.street.isEmpty()) return emptyList()

        val first = form(client, parsed.city, parsed.postalCode, parsed.street, parsed.houseNumber)
        val results = if (first.isNotEmpty() || parsed.postalCode.isBlank()) {
            first
        } else {
            form(client, parsed.city, "", parsed.street, parsed.houseNumber)
        }
        if (results.isEmpty()) return emptyList()

        return AddressRanker.rank(
            results = results,
            streetTokens = AddressParser.tokenize(parsed.street),
            cityTokens = AddressParser.tokenize(parsed.city),
            houseNumberParts = AddressParser.tokenize(parsed.houseNumber),
            postalCode = parsed.postalCode
        )
    }

    /**
     * Merge structured form results ahead of raw string results, deduplicated
     * by object file offset (form and string search may return the same
     * house/street object). Raw results are kept in their native order.
     */
    fun merge(structured: List<LocationEntry>, raw: List<LocationEntry>): List<LocationEntry> {
        if (structured.isEmpty()) return raw
        val seen = HashSet<Long>()
        structured.forEach { e -> if (e.objectFileOffset != 0L) seen.add(e.objectFileOffset) }
        val keptRaw = raw.filter { e ->
            val offset = e.objectFileOffset
            offset == 0L || seen.add(offset)
        }
        return structured + keptRaw
    }

    private fun form(
        client: OSMScoutClient,
        city: String,
        postalCode: String,
        street: String,
        houseNumber: String
    ): List<LocationEntry> = try {
        client.searchLocationByForm(city, postalCode, street, houseNumber, FORM_LIMIT)
            ?.toList() ?: emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "searchLocationByForm failed", e)
        emptyList()
    }
}
