package com.naviveylin.auto

import com.framstag.libosmscout.client.LocationEntry

/**
 * Pure functions for building [SearchScreen] content.
 * Extracted for testability.
 */
object SearchScreenMapper {

    const val MAX_RESULTS = 20
    const val SEARCH_DEBOUNCE_MS = 300L

    /**
     * Build a human-readable description from a [LocationEntry].
     * Combines postal area and region hierarchy.
     */
    fun buildDescription(entry: LocationEntry): String {
        val parts = mutableListOf<String>()
        if (entry.postalArea.isNotEmpty()) {
            parts.add(entry.postalArea)
        }
        if (entry.region.isNotEmpty()) {
            parts.add(entry.region.joinToString(", "))
        }
        return parts.joinToString(" — ")
    }
}
