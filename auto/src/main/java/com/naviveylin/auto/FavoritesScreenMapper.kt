package com.naviveylin.auto

import com.framstag.libosmscout.client.FavoriteLocation

/**
 * Pure functions for building [FavoritesScreen] content.
 * Extracted for testability.
 */
object FavoritesScreenMapper {

    /**
     * Build a display description for a [FavoriteLocation].
     * Uses the address attribute if available, otherwise empty string.
     */
    fun buildDescription(fav: FavoriteLocation): String {
        return fav.attributes["address"] ?: ""
    }

    /**
     * One page of an ordered row sequence: the rows the page shows and how many items its level
     * holds after it.
     *
     * [remaining] is what tells the screen whether to add the page row and what count the row
     * carries (spec: `car-list-row-limit` — The final row opens the next page / Items beyond the
     * capacity stay reachable). `remaining == 0` on the last page, so the last page carries no page
     * row (spec: "The last page offers no further row").
     */
    data class Page<T>(val rows: List<T>, val remaining: Int) {
        /** Whether the level holds a further page (equivalently: whether a page row is needed). */
        val hasFurtherPage: Boolean get() = remaining > 0
    }

    /**
     * The `page`-th slice of `rows` at `capacity` rows per page (spec: `car-list-row-limit` — A car
     * list never presents more rows than the host displays; design D4).
     *
     * The arithmetic is a pure function of the ordered sequence, the capacity and the page index, so
     * the pages **partition** the sequence: page `n` starts at the first item page `n-1` did not show,
     * no item appears on two pages, and following the page rows to the end presents every item exactly
     * once (spec: "Every item is reachable exactly once"). The screen adds the page row from
     * [Page.remaining]; the mapper never hides an item.
     *
     * A capacity below 1 would make the pages divide by zero or loop for ever, so it is clamped to a
     * single row — a host that answers with something unusable still gets a traversal that terminates.
     *
     * @param capacity rows per page the host reports; clamped to at least 1
     * @param page zero-based page index; negative values read page 0
     */
    fun <T> page(rows: List<T>, capacity: Int, page: Int = 0): Page<T> {
        val perPage = if (capacity < 1) 1 else capacity
        val index = if (page < 0) 0 else page
        val from = minOf(index.toLong() * perPage, rows.size.toLong()).toInt()
        val to = minOf(from + perPage, rows.size)
        return Page(rows = rows.subList(from, to).toList(), remaining = rows.size - to)
    }
}
