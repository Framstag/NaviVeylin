package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.model.Row
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.search.MergedSearchResult

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
        // JNI leaves these fields null when the location index has no data.
        if (!entry.postalArea.isNullOrEmpty()) {
            parts.add(entry.postalArea)
        }
        if (!entry.region.isNullOrEmpty()) {
            parts.add(entry.region.joinToString(", "))
        }
        return parts.joinToString(" — ")
    }

    /**
     * Build a result row for a [MergedSearchResult]: title + description, with
     * a heart icon when the result is a favorite (spec: favorite-search).
     * [onClick] is invoked on row tap.
     */
    fun buildResultRow(
        carContext: CarContext,
        result: MergedSearchResult,
        onClick: () -> Unit
    ): Row {
        val entry = result.entry
        val builder = Row.Builder()
            .setTitle(entry.label ?: "Unknown")
            .addText(buildDescription(entry))
        if (result.isFavorite) {
            builder.setImage(CarGlyphs.favorite)
        }
        return builder
            .setOnClickListener(onClick)
            .build()
    }

    /**
     * Mode rows shown on an empty query (and after a no-results state):
     * "Search POIs near me" and, while [showContactsRow], "Search contacts".
     * [onPoiSearch] / [onContactsSearch] are invoked on row tap.
     *
     * Spec: auto-search-suggestions — mode rows on empty query; the contacts
     * row is shown only while `READ_CONTACTS` is granted.
     */
    fun buildModeRows(
        carContext: CarContext,
        showContactsRow: Boolean,
        onPoiSearch: () -> Unit,
        onContactsSearch: () -> Unit
    ): List<Row> {
        val rows = mutableListOf(
            Row.Builder()
                .setTitle(carContext.getString(R.string.search_pois_near_me))
                .setOnClickListener { onPoiSearch() }
                .build()
        )
        if (showContactsRow) {
            rows += Row.Builder()
                .setTitle(carContext.getString(R.string.search_contacts_action))
                .setOnClickListener { onContactsSearch() }
                .build()
        }
        return rows
    }

    /**
     * Recent-search rows shown on an empty query: a "Recent searches" header
     * row followed by one row per history entry (shared search history store,
     * which also records phone searches). [onHistorySelected] is invoked with
     * the query on row tap.
     *
     * Spec: auto-search-suggestions — recent searches on empty query.
     */
    fun buildHistoryRows(
        carContext: CarContext,
        history: List<String>,
        onHistorySelected: (String) -> Unit
    ): List<Row> {
        val rows = mutableListOf(
            Row.Builder()
                .setTitle(carContext.getString(R.string.recent_searches))
                .build()
        )
        for (query in history) {
            rows += Row.Builder()
                .setTitle(query)
                .setOnClickListener { onHistorySelected(query) }
                .build()
        }
        return rows
    }

    /**
     * Rows shown when a query returns no places results: the "No results
     * found" row followed by the mode rows, so the driver can pivot to POI
     * or contacts search without clearing the field.
     *
     * Spec: auto-search-suggestions — no-results state keeps mode rows.
     */
    fun buildNoResultsRows(
        carContext: CarContext,
        showContactsRow: Boolean,
        onPoiSearch: () -> Unit,
        onContactsSearch: () -> Unit
    ): List<Row> {
        return listOf(
            Row.Builder()
                .setTitle(carContext.getString(R.string.no_results_found))
                .build()
        ) + buildModeRows(carContext, showContactsRow, onPoiSearch, onContactsSearch)
    }
}
