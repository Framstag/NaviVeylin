package com.naviveylin.auto

import android.graphics.Bitmap
import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Row
import androidx.core.graphics.drawable.IconCompat
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.ResultMarkings
import com.naviveylin.core.formatDistanceKm
import com.naviveylin.core.search.carRowDescription
import com.naviveylin.core.search.rowTitle
import com.naviveylin.core.search.MergedSearchResult
import com.naviveylin.core.search.ResultMarking
import com.naviveylin.core.search.SearchReference
import com.naviveylin.core.search.SearchResultRanker
import com.naviveylin.core.search.resultMarkingOf

/**
 * Pure functions for building [SearchScreen] content.
 * Extracted for testability.
 */
object SearchScreenMapper {

    const val MAX_RESULTS = 20
    const val SEARCH_DEBOUNCE_MS = 300L

    /**
     * Build a result row for a [MergedSearchResult]: title, description, the distance from
     * [reference], and the row's single image (spec: `search-result-row-icon` — "One leading slot
     * carries icon and marking together"). [onClick] is invoked on row tap.
     *
     * [typeIcon] is the entry's type artwork, already resolved by the screen from the shared cache:
     * resolving it here would be a native call inside a host-thread template build, which is what
     * `guidelines/Design.md` §4 forbids. A row whose type has no artwork passes null and shows its
     * marking alone, exactly as before.
     *
     * @param reference point the row distances are measured from — the same one
     *   the list was ranked against; null shows no distance
     */
    fun buildResultRow(
        carContext: CarContext,
        result: MergedSearchResult,
        reference: SearchReference? = null,
        typeIcon: Bitmap? = null,
        onClick: () -> Unit
    ): Row {
        val entry = result.entry
        val builder = Row.Builder()
            .setTitle(rowTitle(entry))

        val description = carRowDescription(entry)
        if (description.isNotEmpty()) {
            builder.addText(description)
        }
        // Second text line (a Row allows two): the distance, same wording as the
        // phone result list.
        SearchResultRanker.distanceMeters(entry, reference)?.let { meters ->
            builder.addText(
                carContext.getString(R.string.distance_unit_km, formatDistanceKm(meters))
            )
        }

        val marking = resultMarkingOf(result.isFavorite, result.isPerfectMatch)
        if (marking != ResultMarking.NONE || typeIcon != null) {
            // One image slot on a car Row, so the icon and the marking are drawn as a single
            // composite glyph rather than stacking two icons.
            builder.setImage(
                CarIcon.Builder(IconCompat.createWithBitmap(ResultMarkings.bitmapFor(marking, typeIcon))).build()
            )
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
