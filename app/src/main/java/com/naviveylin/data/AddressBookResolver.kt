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
 * `OSMScoutClient.searchLocations` tokenizes the query and searches the
 * location index: the house-number token is what produces address-level
 * (house) results, the postal code disambiguates when the index has postal
 * areas, and every result carries an `objectType` (`address`/`place`/`poi`/
 * `boundary_administrative`) plus a native `matchQuality` (`match`/`candidate`).
 *
 * Strategy: structured form search first (city + postal area + street +
 * house number via `OSMScoutClient.searchLocationByForm`, which returns
 * house-level results with precise coordinates when the index has them),
 * then progressive string-query loosening, and a ranking that prefers
 * house-level addresses over streets over POIs, native exact matches over
 * candidates, and results whose region/postal area actually contains the
 * contact's city and postal code.
 */
@Singleton
class AddressBookResolver @Inject constructor(
    private val client: OSMScoutClient
) : AddressBookSearchProvider {

    override fun resolveAddress(address: ContactPostalAddress): List<LocationEntry> {
        if (address.isBlank) return emptyList()

        // 1. Structured form search (precise house-level matches): city
        // (admin region) + optional postal area + street (WITHOUT house
        // number — the form's location field is the street name) + house
        // number as the address field. The street field is normalized first:
        // an embedded postal code ("Erbstollenstraße 10 58454") must not be
        // mistaken for the house number (spec: address-book-search "Postal
        // code in street field parses correctly").
        val city = address.city.trim().ifEmpty { address.region.trim() }
        val (streetName, houseNumber, postalFromStreet) = AddressParser.normalizeStreetField(
            address.street, address.postalCode
        )
        val postalCode = address.postalCode.trim().ifEmpty { postalFromStreet }
        if (city.isNotEmpty() && streetName.isNotEmpty()) {
            val formResults = searchByForm(
                adminRegion = city,
                postalArea = postalCode,
                location = streetName,
                address = houseNumber
            )
            if (formResults.isNotEmpty()) {
                Log.d(TAG, "resolveAddress: form search -> ${formResults.size} results")
                return ranked(address, formResults, streetName, houseNumber, city, postalCode)
            }
            // Retry without the postal area — a postal code the index does
            // not know would otherwise block the street/house lookup.
            if (postalCode.isNotBlank()) {
                val formNoPostal = searchByForm(city, "", streetName, houseNumber)
                if (formNoPostal.isNotEmpty()) {
                    Log.d(TAG, "resolveAddress: form search (no postal) -> ${formNoPostal.size} results")
                    return ranked(address, formNoPostal, streetName, houseNumber, city, postalCode)
                }
            }
        }

        // 2. String search fallback chain (progressive loosening).
        for (query in buildQueries(address, postalCode, streetName, houseNumber)) {
            if (query.isBlank()) continue
            val results = search(query)
            if (results.isNotEmpty()) {
                Log.d(TAG, "resolveAddress: '$query' -> ${results.size} results")
                return ranked(address, results, streetName, houseNumber, city, postalCode)
            }
        }
        Log.d(TAG, "resolveAddress: no results for '${address.displayText}'")
        return emptyList()
    }

    /** Rank, then gate out results without street evidence (see [AddressRanker]). */
    private fun ranked(
        address: ContactPostalAddress,
        results: List<LocationEntry>,
        streetName: String,
        houseNumber: String,
        city: String,
        postalCode: String
    ): List<LocationEntry> {
        val streetTokens = AddressParser.tokenize(streetName)
        val cityTokens = AddressParser.tokenize(city)
        val ranked = AddressRanker.rank(
            results = results,
            streetTokens = streetTokens,
            cityTokens = cityTokens,
            houseNumberParts = AddressParser.tokenize(houseNumber),
            postalCode = postalCode
        )
        if (streetTokens.isEmpty()) return ranked
        // City-only free-text noise (bus stops whose name merely contains the
        // city token) must never auto-resolve a contact: only results with
        // street-token evidence qualify (spec: address-book-search
        // "Wrong-location free-text result not selected").
        val evidenced = ranked.filter { AddressRanker.hasStreetEvidence(it, streetTokens) }
        return if (evidenced.isNotEmpty()) evidenced else emptyList()
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
     * Progressive fallback: full address -> street+city (no postal code) ->
     * street+house+postal -> street+postal -> street+city without house number
     * (indexes without house data) -> street alone. Distinct and non-blank.
     * The bare city query is intentionally NOT included: city-only free-text
     * results (bus stops named after the city) would auto-resolve kilometers
     * away from the address (spec: address-book-search "Wrong-location
     * free-text result not selected").
     */
    private fun buildQueries(
        address: ContactPostalAddress,
        postalCode: String,
        streetName: String,
        houseNumber: String
    ): List<String> {
        val city = address.city.trim()
        val streetCity = listOf(streetName, houseNumber, city)
            .filter { it.isNotBlank() }.joinToString(" ")
        val streetHousePostal = listOf(streetName, houseNumber, postalCode)
            .filter { it.isNotBlank() }.joinToString(" ")
        val streetPostal = listOf(streetName, postalCode)
            .filter { it.isNotBlank() }.joinToString(" ")
        val streetCityNoHouse = listOf(streetName, city)
            .filter { it.isNotBlank() }.joinToString(" ")
        val streetAlone = listOf(streetName, houseNumber)
            .filter { it.isNotBlank() }.joinToString(" ")
        return listOf(address.queryText, streetCity, streetHousePostal, streetPostal, streetCityNoHouse, streetAlone)
            .filter { it.isNotBlank() }
            .distinct()
    }

    private companion object {
        const val TAG = "AddressBookResolver"
        const val RESULT_LIMIT = 50
        const val FORM_LIMIT = 20
    }
}
