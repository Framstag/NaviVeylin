package com.naviveylin.core.search

/**
 * The attributes a user's query names, split into criteria.
 *
 * Admin region and postal area are *context* attributes (spec:
 * search-result-ranking — "Query attributes classified as criteria or
 * context"): they are only criteria when the query names them, so an entry
 * carrying a region or a postal area the user never typed is not penalised.
 * Street/place name, house number and POI name are criteria the user can only
 * have meant explicitly, so an entry filling one of them that the query did not
 * name is *not* a perfect match.
 *
 * @property nameTokens folded tokens of the name part of the query (street,
 *   place, POI or city), empty when the query names no name
 * @property postalCode folded postal code the query named, or null
 * @property houseNumber house number the query named, or null
 */
data class SearchQueryCriteria(
    val nameTokens: List<String> = emptyList(),
    val postalCode: String? = null,
    val houseNumber: String? = null
) {
    /** True when the query names a name (street/place/POI/city) attribute. */
    val namesName: Boolean get() = nameTokens.isNotEmpty()

    /** True when the query names a postal code. */
    val namesPostalCode: Boolean get() = postalCode != null

    /** True when the query names a house number. */
    val namesHouseNumber: Boolean get() = houseNumber != null
}

/**
 * Splits a free-text search query into the criteria of [SearchQueryCriteria].
 *
 * Pure and allocation-light (called per query, off the main thread). The split
 * is deliberately conservative: it only recognises what the query spells out —
 * a postal code and a house number — and treats everything else as name tokens.
 * Which entry attribute a name token belongs to (a city in the region
 * hierarchy, a street in the entry name) is decided later against the entry,
 * not guessed here.
 */
object SearchQueryParser {

    /** German postal codes are five digits; shorter or longer digit runs are not postal codes. */
    private const val POSTAL_CODE_LENGTH = 5

    /** House numbers: a number with an optional letter suffix, or a range ("12a", "1-3"). */
    private val HOUSE_NUMBER = Regex("^\\d{1,4}[a-zA-Z]?(?:\\s*-\\s*\\d{1,4}[a-zA-Z]?)?$")

    private val SEPARATORS = Regex("[,;/]+")
    private val WHITESPACE = Regex("\\s+")

    fun criteriaOf(query: String): SearchQueryCriteria {
        val tokens = query
            .split(SEPARATORS)
            .flatMap { it.split(WHITESPACE) }
            .filter { it.isNotBlank() }

        var postalCode: String? = null
        var houseNumber: String? = null
        val nameTokens = mutableListOf<String>()

        for (rawToken in tokens) {
            val token = rawToken.trim()
            if (token.isEmpty()) continue
            when {
                isPostalCode(token) && postalCode == null -> postalCode = token
                // Kept as typed: the house number is a criterion marker ("the
                // query named a house number"), not a comparison key.
                HOUSE_NUMBER.matches(token) && houseNumber == null -> houseNumber = token
                else -> {
                    val folded = foldedTokens(token)
                    if (folded.isNotEmpty()) {
                        nameTokens.addAll(folded)
                    }
                }
            }
        }

        return SearchQueryCriteria(
            nameTokens = nameTokens,
            postalCode = postalCode,
            houseNumber = houseNumber
        )
    }

    private fun isPostalCode(token: String): Boolean =
        token.length == POSTAL_CODE_LENGTH && token.all { it.isDigit() }
}
