package com.naviveylin.data

import com.framstag.libosmscout.client.LocationEntry

/**
 * Parsed components of a free-text address query (spec: fix-address-lookup-accuracy —
 * full formatted address resolution). Postal codes embedded in the street field are
 * extracted, not treated as house numbers.
 */
data class ParsedAddress(
    val street: String,
    val streetWithHouse: String,
    val houseNumber: String,
    val postalCode: String,
    val city: String,
    val region: String
)

/**
 * Parser for free-text address queries and normalization of structured address
 * fields. Handles comma- and space-separated component orders:
 * "Erbstollenstraße 10, 58454 Witten", "Erbstollenstraße 10 58454 Witten",
 * and postal codes glued to the street field.
 */
object AddressParser {

    private val HOUSE_NUMBER_END = Regex("\\s+(\\d+[a-zA-Z]?(?:\\s*[-–]\\s*\\d+[a-zA-Z]?)?)$")
    private val HOUSE_NUMBER_START = Regex("^(\\d+[a-zA-Z]?(?:\\s*[-–]\\s*\\d+[a-zA-Z]?)?)\\s+")
    // Split on commas or whitespace only: house-number ranges ("1-3") and
    // hyphenated streets stay one token.
    private val COMBO_SPLIT = Regex("\\s*,\\s*|\\s+")
    // Tokens that describe the country or state, not the city; ignored when
    // picking the city/region from the address tail.
    private val CITY_STOP_WORDS = setOf(
        "germany", "deutschland", "de",
        "nrw", "nordrhein", "westfalen", "nordrhein-westfalen",
        "north", "rhine", "westphalia"
    )

    /**
     * Parse a free-text query into address components. Returns null when the
     * text carries no street-like content (pure city/PLZ queries).
     */
    fun parse(query: String): ParsedAddress? {
        val text = query.trim()
        if (text.isEmpty()) return null

        // The street portion ends at the postal code token (any position);
        // everything after it is the city/region tail. Comma or space
        // separation behave identically ("Erbstollenstraße 10, 58454 Witten"
        // vs "Erbstollenstraße 10 58454 Witten").
        val tokens = text.split(COMBO_SPLIT).filter { it.isNotEmpty() }
        val plzIndex = tokens.indexOfFirst { isPostal(it) }
        val streetTokens = if (plzIndex >= 0) tokens.take(plzIndex) else tokens
        if (streetTokens.isEmpty()) return null
        val streetRaw = streetTokens.joinToString(" ")
        val tail = if (plzIndex >= 0 && plzIndex + 1 < tokens.size) {
            tokens.drop(plzIndex + 1).joinToString(" ")
        } else {
            ""
        }

        val postalCode = tokens.firstOrNull { isPostal(it) } ?: ""

        val houseNumber = houseNumber(streetRaw).orEmpty()
        val streetName = streetWithoutHouseNumber(streetRaw)
        if (streetName.isEmpty()) return null

        // Tail city/region, case-preserving ("Witten" not "witten").
        val tailTokens = tail.split(COMBO_SPLIT).filter { it.isNotEmpty() && !isPostal(it) }
        val city = tailTokens.firstOrNull { !CITY_STOP_WORDS.contains(it.lowercase()) } ?: ""
        val regionAll = tailTokens.filter { it.lowercase() != city.lowercase() && !CITY_STOP_WORDS.contains(it.lowercase()) }
        val region = regionAll.joinToString(" ")

        if (houseNumber.isEmpty() && postalCode.isEmpty() && city.isEmpty()) {
            // Bare street-name-only queries are not address-like; the raw
            // string search handles them.
            return null
        }

        return ParsedAddress(
            street = streetName,
            streetWithHouse = listOf(streetName, houseNumber).filter { it.isNotEmpty() }.joinToString(" "),
            houseNumber = houseNumber,
            postalCode = postalCode,
            city = city,
            region = region
        )
    }

    /**
     * Normalize a structured street field: strip an embedded trailing postal
     * code (the separate postal-code field wins when present), split off the
     * house number. Returns (streetName, houseNumber, postalCode).
     */
    fun normalizeStreetField(street: String, fieldPostalCode: String): Triple<String, String, String> {
        var raw = street.trim()
        var postal = fieldPostalCode.trim()
        val embedded = findPostal(raw)
        // An embedded trailing postal code is never part of the street name —
        // whether or not the separate field supplies the value.
        if (embedded != null) {
            // An embedded trailing postal code is never part of the street
            // name — whether or not the separate field supplies the value.
            raw = Regex("\\s+" + Regex.escape(embedded) + "$").replace(raw, "")
            if (postal.isEmpty()) {
                postal = embedded
            }
        }
        val house = houseNumber(raw).orEmpty()
        val name = streetWithoutHouseNumber(raw)
        return Triple(name, house, postal)
    }

    /** Trailing or leading house number of a street string, e.g. "1", "12a", "1-3". */
    fun houseNumber(street: String): String? {
        val trimmed = street.trim()
        HOUSE_NUMBER_END.find(trimmed)?.let { return it.groupValues[1] }
        return HOUSE_NUMBER_START.find(trimmed)?.groupValues?.get(1)
    }

    /** Street string without its house number, e.g. "Main Street 1" -> "Main Street". */
    fun streetWithoutHouseNumber(street: String): String {
        val trimmed = street.trim()
        return HOUSE_NUMBER_START.replace(HOUSE_NUMBER_END.replace(trimmed, ""), "").trim()
    }

    /** Lowercase, ß-normalized token list ("Straße" and "Strasse" compare equal). */
    fun tokenize(text: String): List<String> =
        text.lowercase().replace("ß", "ss")
            .split(COMBO_SPLIT).filter { it.isNotEmpty() }

    /** ß-normalized lowercase form for substring matching against tokens. */
    fun normalizedLower(text: String): String =
        text.lowercase().replace("ß", "ss")

    private fun findPostal(text: String): String? =
        text.split(COMBO_SPLIT).firstOrNull { isPostal(it) }

    private fun isPostal(token: String): Boolean =
        token.length == 5 && token.all { it.isDigit() }
}

/**
 * Shared ranking for address resolution: house-level address > street/place >
 * POI > region; native exact match > candidate; house number, street tokens,
 * city and postal code all add score. Structured regions score at or above
 * POI hits so city-only queries do not surface free-text bus stops. Stable
 * sort: ties keep the native result order.
 */
object AddressRanker {

    /**
     * @param streetTokens tokens of the street name (with house number removed)
     * @param houseNumberParts tokens of the house number, e.g. ["10"], empty when none
     */
    fun rank(
        results: List<LocationEntry>,
        streetTokens: List<String>,
        cityTokens: List<String>,
        houseNumberParts: List<String>,
        postalCode: String
    ): List<LocationEntry> {
        val postal = postalCode.trim().lowercase()
        return results.sortedByDescending { entry ->
            val label = AddressParser.normalizedLower(entry.label ?: "")
            val labelTokens = AddressParser.tokenize(entry.label ?: "")
            val regionText = buildString {
                entry.region?.forEach { append(' '); append(AddressParser.normalizedLower(it)) }
                entry.adminRegionHierarchy?.let { append(' '); append(AddressParser.normalizedLower(it)) }
                entry.postalArea?.let { append(' '); append(it.lowercase()) }
            }
            var score = 0
            when (entry.objectType) {
                "address" -> score += 100
                "place" -> score += 30
                "poi" -> score += 5
                // Admin regions must beat free-text POIs for city-only queries.
                "boundary_administrative" -> score += 20
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
                score -= 15
            }
            if (postal.isNotEmpty() &&
                (regionText.contains(postal) || label.contains(postal))
            ) {
                score += 10
            }
            if (streetTokens.isNotEmpty() && streetTokens.none { label.contains(it) }) {
                score -= 30
            }
            score
        }
    }

    /** True when the entry's label contains any street token (structured street/address evidence). */
    fun hasStreetEvidence(entry: LocationEntry, streetTokens: List<String>): Boolean {
        val label = AddressParser.normalizedLower(entry.label ?: "")
        return streetTokens.isNotEmpty() && streetTokens.any { label.contains(it) }
    }
}
