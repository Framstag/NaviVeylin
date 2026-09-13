package com.naviveylin.data

import android.util.Log
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.addressbook.AddressBookSearchProvider
import com.naviveylin.core.addressbook.ContactPostalAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a contact's postal address into map locations through the offline
 * libosmscout search backend (spec: address-book-search — address resolution).
 *
 * `OSMScoutClient.searchLocations` / `searchLocationByForm` tokenize the query
 * and search the location index; every result carries an `objectType`
 * (`address`/`place`/`poi`/`boundary_administrative`) plus a native
 * `matchQuality`. Both native entry points run with partial matching enabled
 * (see `fix-address-lookup-accuracy`), so a query whose street/house part does
 * not match still yields an administrative-region or postal-area fallback
 * entry instead of an empty result set.
 *
 * Acceptance contract: a candidate (form search or string query) only counts
 * as a hit when it produces at least one entry carrying the address's street
 * evidence. A result set of nothing but street-less fallback entries is a
 * miss — the resolver continues with the next candidate and reports "not
 * found" only after every candidate failed that way. This is what keeps a
 * partially matching query from aborting the progressive chain (spec:
 * address-book-search "Street-less result does not abort the candidate chain",
 * "Not found requires the whole chain to fail") while still refusing
 * free-text noise ("Wrong-location free-text result not selected").
 *
 * Components are the structured fields merged with the formatted address
 * (see [AddressParser.components]); candidates are ordered from the most
 * precise (form search, full formatted address) to the loosest (street alone).
 */
@Singleton
class AddressBookResolver @Inject constructor(
    private val client: OSMScoutClient
) : AddressBookSearchProvider {

    override fun resolveAddress(address: ContactPostalAddress): List<LocationEntry> {
        if (address.isBlank) return emptyList()

        val components = AddressParser.components(
            street = address.street,
            postalCode = address.postalCode,
            city = address.city,
            region = address.region,
            formatted = address.formatted
        )
        val tokens = Tokens(
            street = AddressParser.tokenize(components.street),
            city = AddressParser.tokenize(components.city),
            house = AddressParser.tokenize(components.houseNumber)
        )

        // 1. Structured form search (precise house-level matches): city
        // (admin region) + optional postal area + street (WITHOUT house
        // number — the form's location field is the street name) + house
        // number as the address field. Retried without the postal area: a
        // postal code the index does not know must not block the street/house
        // lookup.
        if (components.street.isNotEmpty() && components.city.isNotEmpty()) {
            val postalAreaAttempts = buildList {
                add(components.postalCode)
                if (components.postalCode.isNotBlank()) add("")
            }
            for ((index, postalArea) in postalAreaAttempts.withIndex()) {
                val source = if (index == 0) "form" else "form (no postal)"
                val results = searchByForm(
                    adminRegion = components.city,
                    postalArea = postalArea,
                    location = components.street,
                    address = components.houseNumber
                )
                accept(address, results, components, tokens, source)?.let { return it }
            }
        }

        // 2. String search fallback chain (progressive loosening).
        for (query in buildQueries(components, address)) {
            if (query.isBlank()) continue
            val results = search(query)
            accept(address, results, components, tokens, "'$query'")?.let { return it }
        }

        Log.d(TAG, "resolveAddress: no street-evidenced result for '${address.displayText}'")
        return emptyList()
    }

    /**
     * Rank a candidate's results and return them when at least one entry
     * carries the address's street evidence; null (= miss) otherwise. Ranking
     * prefers house-level addresses over streets over POIs, native exact
     * matches over candidates, and results whose region/postal area actually
     * contains the contact's city and postal code.
     *
     * Without a street in the address there can be no street evidence, so the
     * candidate cannot be accepted — a city-only query must never auto-resolve
     * a contact to a free-text result (spec: address-book-search
     * "Wrong-location free-text result not selected").
     */
    private fun accept(
        address: ContactPostalAddress,
        results: List<LocationEntry>,
        components: AddressComponents,
        tokens: Tokens,
        source: String
    ): List<LocationEntry>? {
        val ranked = AddressRanker.rank(
            results = results,
            streetTokens = tokens.street,
            cityTokens = tokens.city,
            houseNumberParts = tokens.house,
            postalCode = components.postalCode
        )
        val evidenced = if (tokens.street.isEmpty()) {
            emptyList()
        } else {
            ranked.filter { AddressRanker.hasStreetEvidence(it, tokens.street) }
        }
        Log.d(
            TAG,
            "resolveAddress: $source -> ${ranked.size} results, " +
                "${evidenced.size} with street evidence (${address.displayText})"
        )
        return evidenced.ifEmpty { null }
    }

    private fun searchByForm(
        adminRegion: String,
        postalArea: String,
        location: String,
        address: String
    ): List<LocationEntry> = try {
        client.searchLocationByForm(adminRegion, postalArea, location, address, FORM_LIMIT)
            ?.toList() ?: emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "searchLocationByForm failed", e)
        emptyList()
    }

    private fun search(query: String): List<LocationEntry> = try {
        client.searchLocations(query, RESULT_LIMIT, OSMScoutClient.NO_ADMIN_REGION)
            ?.toList() ?: emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "searchLocations failed for '$query'", e)
        emptyList()
    }

    /**
     * Progressive fallback, precise to loose: full formatted address
     * (street + house + postal code + city — the query the unified search
     * dialog sends), the provider's raw formatted address, the components
     * without postal code, street + house + postal code, street + postal code,
     * street + city, street + house. Distinct and non-blank.
     *
     * Street-less queries are never issued: city-only free-text results (bus
     * stops named after the city) would auto-resolve kilometers away from the
     * address (spec: address-book-search "Wrong-location free-text result not
     * selected"). When the address has no street at all, only the raw
     * formatted text remains as a candidate.
     */
    private fun buildQueries(
        components: AddressComponents,
        address: ContactPostalAddress
    ): List<String> {
        val formatted = address.formatted.trim()
        val street = components.street
        val house = components.houseNumber
        val postal = components.postalCode
        val city = components.city
        val candidates = if (street.isBlank()) {
            // Nothing but the provider's own text remains; without it the
            // address is not resolvable (a city-only query is never issued).
            listOf(listOf(formatted))
        } else {
            // Street + city only when the address actually has a city: a
            // street alone must never be searched city-less by accident.
            val streetCity = if (city.isNotBlank()) listOf(street, city) else emptyList()
            listOf(
                listOf(street, house, postal, city),
                listOf(formatted),
                listOf(address.queryText),
                listOf(street, house, postal),
                listOf(street, postal),
                streetCity,
                listOf(street, house)
            )
        }
        return candidates
            .map { parts -> parts.filter { it.isNotBlank() }.joinToString(" ") }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /** Token forms of the address components, used for evidence and ranking. */
    private data class Tokens(
        val street: List<String>,
        val city: List<String>,
        val house: List<String>
    )

    private companion object {
        const val TAG = "AddressBookResolver"
        const val RESULT_LIMIT = 50
        const val FORM_LIMIT = 20
    }
}
