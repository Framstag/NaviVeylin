package com.naviveylin.core.search

import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.haversineDistanceMeters

/** Reference point a search distance is measured from. */
data class SearchReference(val lat: Double, val lon: Double)

/** Whether a result is the exact answer to the query or merely a near one. */
enum class SearchMatchTier { PERFECT, CLOSE }

/**
 * Classifies and orders location search results (spec: search-result-ranking).
 *
 * Pure, no IO, no framework types: called inside the existing background search
 * coroutine on every debounced query, so it stays allocation-light and must not
 * rely on the main thread.
 *
 * The rule, in one sentence: a result is a *perfect match* when every attribute
 * the query names matched exactly and the result carries no criterion-class
 * attribute the query did not name; perfect matches come first, ordered by
 * distance, then close matches by match quality and distance.
 */
object SearchResultRanker {

    /** Results a surface displays at most (spec: location-search, auto-search). */
    const val DISPLAY_LIMIT = 20

    /**
     * Candidates fetched per query, so ranking chooses from a superset instead
     * of being confined to the backend's own first page (spec:
     * search-result-ranking — "Candidate set larger than displayed list").
     */
    const val CANDIDATE_LIMIT = 60

    private const val QUALITY_MATCH = "match"
    private const val QUALITY_CANDIDATE = "candidate"

    /** The backend's coordinate result: the query *is* the coordinate, so it is the answer. */
    private const val TYPE_COORDINATE = "coordinate"

    /** Free-text (text index) hits report no component attribution. */
    private const val COMPONENT_FREE_TEXT = "freeText"

    private const val COMPONENT_LOCATION = "location"
    private const val COMPONENT_POI = "poi"
    private const val COMPONENT_ADMIN_REGION = "adminRegion"
    private const val COMPONENT_ADDRESS = "address"

    /**
     * True when [entry] is the exact answer to the query behind [criteria]
     * (spec: search-result-ranking — "Perfect match classification uses native
     * per-attribute quality").
     */
    fun isPerfectMatch(entry: LocationEntry, criteria: SearchQueryCriteria): Boolean {
        // A coordinate query is answered by the coordinate result itself.
        if (entry.type == TYPE_COORDINATE) return true

        // Text-index hits carry no per-attribute quality, so they can never be
        // proven exact (spec: search-free-text).
        if (entry.matchedComponent == COMPONENT_FREE_TEXT) return false

        // (1) every criterion the query named must be present and match exactly.
        criteria.postalCode?.let { postal ->
            if (qualityOf(entry.postalAreaMatchQuality) != QUALITY_MATCH) return false
            // Guard against a postal area that matched a different code.
            if (foldForSearchMatch(entry.postalArea.orEmpty()) != postal) return false
        }
        criteria.houseNumber?.let {
            if (!entry.hasHouseNumber) return false
            if (qualityOf(entry.addressMatchQuality) != QUALITY_MATCH) return false
        }
        if (criteria.namesName && !nameCriterionMatched(entry, criteria.nameTokens)) {
            return false
        }

        // (2) an entry filling a criterion-class attribute the query did not
        // name is not the exact answer (a house number, or a name).
        if (!criteria.namesHouseNumber && entry.hasHouseNumber) return false
        if (!criteria.namesName && entryHasName(entry)) return false

        return true
    }

    /**
     * Straight-line distance from [reference] to [entry] in meters, or null when
     * it cannot be measured (no reference point, or unusable coordinates) — in
     * which case the surface shows no distance for the row.
     */
    fun distanceMeters(entry: LocationEntry, reference: SearchReference?): Double? {
        if (reference == null) return null
        val meters = haversineDistanceMeters(reference.lat, reference.lon, entry.lat, entry.lon)
        return meters.takeIf { it.isFinite() }
    }

    /**
     * Order [entries] for display: perfect matches first (nearest first), then
     * close matches by match quality and distance, with a deterministic label
     * tie-break so the same query always produces the same list. Truncation to
     * [DISPLAY_LIMIT] is the caller's job, after this ordering.
     */
    fun rank(
        entries: List<LocationEntry>,
        criteria: SearchQueryCriteria,
        reference: SearchReference?
    ): List<LocationEntry> {
        val (perfect, close) = entries.partition { isPerfectMatch(it, criteria) }
        return perfect.sortedWith(byDistanceThenLabel(reference)) +
            close.sortedWith(
                compareByDescending<LocationEntry> { qualityPoints(it) }
                    .then(byDistanceThenLabel(reference))
            )
    }

    /** Order [entries] for display and cut them to what a surface shows. */
    fun rankForDisplay(
        entries: List<LocationEntry>,
        criteria: SearchQueryCriteria,
        reference: SearchReference?
    ): List<LocationEntry> = rank(entries, criteria, reference).take(DISPLAY_LIMIT)

    private fun byDistanceThenLabel(reference: SearchReference?): Comparator<LocationEntry> =
        compareBy(
            { distanceMeters(it, reference) ?: Double.POSITIVE_INFINITY },
            { foldForSearchMatch(it.label.orEmpty()) },
            { it.objectFileOffset }
        )

    /**
     * Native match quality of the component that supplied the label, taken from
     * the field `matchedComponent` names (the label and `objectType` follow
     * different component precedences, so neither identifies it).
     */
    private fun matchedNameQuality(entry: LocationEntry): String? = when (entry.matchedComponent) {
        COMPONENT_LOCATION -> entry.locationMatchQuality
        COMPONENT_POI -> entry.poiMatchQuality
        COMPONENT_ADMIN_REGION -> entry.adminRegionMatchQuality
        COMPONENT_ADDRESS -> entry.addressMatchQuality
        else -> null
    }

    private fun nameCriterionMatched(entry: LocationEntry, nameTokens: List<String>): Boolean {
        val entryNameTokens = foldedTokens(entry.matchedName.orEmpty())
        if (entryNameTokens.isEmpty()) return false

        val regionTokens = regionTokensOf(entry)

        // The entry's own name must be fully consumed by the query: a result
        // named "Waltroper Straße" is not the exact answer to "Waltrop".
        if (entryNameTokens.any { it !in nameTokens }) return false

        // Every queried name token must be covered by the entry's name or by
        // its region hierarchy (a query naming city and street covers both).
        if (nameTokens.any { it !in entryNameTokens && it !in regionTokens }) return false

        if (qualityOf(matchedNameQuality(entry)) != QUALITY_MATCH) return false

        // When part of the query was covered by the region hierarchy, the
        // region component must have matched exactly too.
        val regionUsed = nameTokens.any { it !in entryNameTokens && it in regionTokens }
        if (regionUsed && qualityOf(entry.adminRegionMatchQuality) != QUALITY_MATCH) return false

        return true
    }

    private fun entryHasName(entry: LocationEntry): Boolean =
        !entry.matchedName.isNullOrBlank()

    private fun regionTokensOf(entry: LocationEntry): Set<String> {
        val tokens = mutableSetOf<String>()
        entry.region?.forEach { tokens.addAll(foldedTokens(it)) }
        entry.adminRegionHierarchy?.let { tokens.addAll(foldedTokens(it.replace('/', ' '))) }
        return tokens
    }

    /**
     * How well a close match answers the query: a matched name (location, POI or
     * house number) counts more than a matched region or postal area, which a
     * result may carry without the query having asked for it.
     */
    private fun qualityPoints(entry: LocationEntry): Int =
        componentPoints(entry.locationMatchQuality) +
            componentPoints(entry.poiMatchQuality) +
            componentPoints(entry.addressMatchQuality) +
            contextPoints(entry.adminRegionMatchQuality) +
            contextPoints(entry.postalAreaMatchQuality)

    private fun componentPoints(quality: String?): Int = when (qualityOf(quality)) {
        QUALITY_MATCH -> 4
        QUALITY_CANDIDATE -> 2
        else -> 0
    }

    private fun contextPoints(quality: String?): Int =
        if (qualityOf(quality) == QUALITY_MATCH) 1 else 0

    /** Null quality (backend without the per-attribute fields) counts as "none". */
    private fun qualityOf(value: String?): String = value?.lowercase() ?: "none"
}
