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
        // number as the address field.
        val city = address.city.trim().ifEmpty { address.region.trim() }
        val street = address.street.trim()
        val streetName = streetWithoutHouseNumber(street)
        val houseNumber = houseNumber(street).orEmpty()
        if (city.isNotEmpty() && streetName.isNotEmpty()) {
            val formResults = searchByForm(
                adminRegion = city,
                postalArea = address.postalCode.trim(),
                location = streetName,
                address = houseNumber
            )
            if (formResults.isNotEmpty()) {
                Log.d(TAG, "resolveAddress: form search -> ${formResults.size} results")
                return rank(address, formResults)
            }
            // Retry without the postal area — a postal code the index does
            // not know would otherwise block the street/house lookup.
            if (address.postalCode.isNotBlank()) {
                val formNoPostal = searchByForm(city, "", streetName, houseNumber)
                if (formNoPostal.isNotEmpty()) {
                    Log.d(TAG, "resolveAddress: form search (no postal) -> ${formNoPostal.size} results")
                    return rank(address, formNoPostal)
                }
            }
        }

        // 2. String search fallback chain (progressive loosening).
        for (query in buildQueries(address)) {
            if (query.isBlank()) continue
            val results = search(query)
            if (results.isNotEmpty()) {
                Log.d(TAG, "resolveAddress: '$query' -> ${results.size} results")
                return rank(address, results)
            }
        }
        Log.d(TAG, "resolveAddress: no results for '${address.displayText}'")
        return emptyList()
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
     * street+city without house number (indexes without house data) -> street
     * alone -> city alone. Distinct and non-blank.
     */
    private fun buildQueries(address: ContactPostalAddress): List<String> {
        val street = address.street.trim()
        val city = address.city.trim()
        val full = address.queryText
        val streetCity = listOf(street, city).filter { it.isNotBlank() }.joinToString(" ")
        val streetCityNoHouse = listOf(streetWithoutHouseNumber(street), city)
            .filter { it.isNotBlank() }.joinToString(" ")
        return listOf(full, streetCity, streetCityNoHouse, street, city)
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Rank candidates: house-level address > street/place > POI > region;
     * native exact match > candidate; house number, street tokens, city and
     * postal code in label/region/postal area all add score. Results matching
     * no street token at all are penalized hard (likely wrong-region noise).
     * Stable sort: ties keep the native result order.
     */
    private fun rank(address: ContactPostalAddress, results: List<LocationEntry>): List<LocationEntry> {
        val streetTokens = tokenize(address.street)
        val cityTokens = tokenize(address.city)
        val houseNumberParts = tokenize(houseNumber(address.street).orEmpty())
        val postalCode = address.postalCode.trim().lowercase()
        return results.sortedByDescending { entry ->
            val label = entry.label?.lowercase() ?: ""
            val labelTokens = tokenize(label)
            val regionText = buildString {
                entry.region?.forEach { append(' '); append(it.lowercase()) }
                entry.adminRegionHierarchy?.let { append(' '); append(it.lowercase()) }
                entry.postalArea?.let { append(' '); append(it.lowercase()) }
            }
            var score = 0
            when (entry.objectType) {
                "address" -> score += 100
                "place" -> score += 30
                "poi" -> score += 5
            }
            if (entry.matchQuality == "match") score += 25
            if (houseNumberParts.isNotEmpty() && houseNumberParts.any { labelTokens.contains(it) }) {
                score += 10
            }
            score += streetTokens.count { label.contains(it) } * 4
            val cityMatched = cityTokens.isNotEmpty() &&
                (cityTokens.any { regionText.contains(it) } || cityTokens.any { label.contains(it) })
            if (cityMatched) {
                score += 8
            } else if (cityTokens.isNotEmpty()) {
                // Result outside the contact's city — likely wrong-region noise.
                score -= 15
            }
            if (postalCode.isNotEmpty() &&
                (regionText.contains(postalCode) || label.contains(postalCode))
            ) {
                score += 10
            }
            if (streetTokens.isNotEmpty() && streetTokens.none { label.contains(it) }) {
                score -= 30
            }
            score
        }
    }

    /** Trailing or leading house number of a street string, e.g. "1", "12a", "1-3". */
    private fun houseNumber(street: String): String? {
        val trimmed = street.trim()
        HOUSE_NUMBER_END.find(trimmed)?.let { return it.groupValues[1] }
        return HOUSE_NUMBER_START.find(trimmed)?.groupValues?.get(1)
    }

    /** Street string without its house number, e.g. "Main Street 1" -> "Main Street". */
    private fun streetWithoutHouseNumber(street: String): String {
        val trimmed = street.trim()
        val withoutEnd = HOUSE_NUMBER_END.replace(trimmed, "")
        return HOUSE_NUMBER_START.replace(withoutEnd, "").trim()
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase().split(TOKEN_SPLIT).filter { it.isNotEmpty() }

    private companion object {
        const val TAG = "AddressBookResolver"
        const val RESULT_LIMIT = 50
        const val FORM_LIMIT = 20
        val TOKEN_SPLIT = Regex("[^a-z0-9äöüß]+")
        val HOUSE_NUMBER_END = Regex("\\s+(\\d+[a-zA-Z]?(?:\\s*[-–]\\s*\\d+[a-zA-Z]?)?)$")
        val HOUSE_NUMBER_START = Regex("^(\\d+[a-zA-Z]?(?:\\s*[-–]\\s*\\d+[a-zA-Z]?)?)\\s+")
    }
}
