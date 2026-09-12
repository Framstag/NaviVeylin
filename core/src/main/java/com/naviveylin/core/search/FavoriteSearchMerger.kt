package com.naviveylin.core.search

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.LocationEntry

/**
 * A single search result with favorite metadata, produced by
 * [FavoriteSearchMerger]. Consumed by both the phone search dialog and the
 * Android Auto search template so the two variants cannot drift.
 *
 * @property entry the underlying location entry (a favorite hit converted to
 *   [LocationEntry], or a native search result)
 * @property isFavorite true when the entry is a favorite hit or a native
 *   result whose coordinates match an existing favorite
 * @property isFavoriteHit true when the entry came from the favorites list
 *   (rendered in the prioritized top section)
 */
data class MergedSearchResult(
    val entry: LocationEntry,
    val isFavorite: Boolean,
    val isFavoriteHit: Boolean
)

/**
 * Merges favorites matching a search query with native location search
 * results (spec: favorite-search).
 *
 * Rules:
 * - Favorites are matched by case-insensitive substring of the name, only for
 *   queries of at least 2 characters.
 * - Favorite hits are converted to [LocationEntry] with
 *   `matchQuality = "favorite"` and listed above native results.
 * - A native result whose coordinates are within [toleranceDegrees] of any
 *   favorite is marked as a favorite (heart).
 * - A native result within [toleranceDegrees] of a favorite hit refers to the
 *   same object and is suppressed — the favorite hit wins.
 *
 * Pure Kotlin, no IO; safe to call on any dispatcher.
 */
class FavoriteSearchMerger(
    private val toleranceDegrees: Double = DEFAULT_TOLERANCE_DEGREES
) {

    /**
     * Merge favorite hits with native results for [query].
     *
     * @param query the search query; favorites are only searched when it has
     *   at least 2 characters
     * @param favorites all favorites (any group), flattened
     * @param nativeResults results from the native location search
     * @return favorite hits first (marked), then non-suppressed native results
     */
    fun merge(
        query: String,
        favorites: List<FavoriteLocation>,
        nativeResults: List<LocationEntry>
    ): List<MergedSearchResult> {
        val favoriteHits = if (query.length >= MIN_QUERY_LENGTH) {
            favorites.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            emptyList()
        }

        val favoriteHitResults = favoriteHits.map { fav ->
            MergedSearchResult(
                entry = LocationEntry().apply {
                    label = fav.name
                    lat = fav.lat
                    lon = fav.lon
                    matchQuality = "favorite"
                },
                isFavorite = true,
                isFavoriteHit = true
            )
        }

        val nativeResults = nativeResults.mapNotNull { entry ->
            // Same object as a favorite hit (coordinates within tolerance):
            // suppress the native duplicate, the favorite hit wins.
            if (favoriteHits.any { isNear(it, entry) }) {
                null
            } else {
                MergedSearchResult(
                    entry = entry,
                    isFavorite = favorites.any { isNear(it, entry) },
                    isFavoriteHit = false
                )
            }
        }

        return favoriteHitResults + nativeResults
    }

    private fun isNear(fav: FavoriteLocation, entry: LocationEntry): Boolean =
        kotlin.math.abs(fav.lat - entry.lat) < toleranceDegrees &&
            kotlin.math.abs(fav.lon - entry.lon) < toleranceDegrees

    private companion object {
        /** Same tolerance as the app's favorite-identity logic (findFavoriteByLocation). */
        const val DEFAULT_TOLERANCE_DEGREES = 0.0001

        /** Favorites are only searched for queries of at least this length. */
        const val MIN_QUERY_LENGTH = 2
    }
}
