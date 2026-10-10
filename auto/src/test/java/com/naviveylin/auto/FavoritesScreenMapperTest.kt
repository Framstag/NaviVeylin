package com.naviveylin.auto

import com.framstag.libosmscout.client.FavoriteLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesScreenMapperTest {

    @Test
    fun buildDescription_withAddress() {
        val fav = FavoriteLocation("Home", 51.5, 7.5)
        fav.attributes["address"] = "123 Main St"
        assertEquals("123 Main St", FavoritesScreenMapper.buildDescription(fav))
    }

    @Test
    fun buildDescription_withoutAddress() {
        val fav = FavoriteLocation("Home", 51.5, 7.5)
        assertEquals("", FavoritesScreenMapper.buildDescription(fav))
    }

    @Test
    fun buildDescription_emptyAddress() {
        val fav = FavoriteLocation("Home", 51.5, 7.5)
        fav.attributes["address"] = ""
        assertEquals("", FavoritesScreenMapper.buildDescription(fav))
    }

    // --- The page arithmetic (spec: car-list-row-limit — A car list never presents more rows than the
    // host displays; Items beyond the capacity stay reachable; design D4) ---

    private val rows = (1..7).map { "row$it" }

    @Test
    fun aListAtTheCapacityCarriesNoPageRow() {
        // Spec: "The list fits the host's capacity" — at the capacity there is nothing left over, so

        // no page row and no hidden item.
        val page = FavoritesScreenMapper.page(rows.take(3), capacity = 3, page = 0)

        assertEquals(listOf("row1", "row2", "row3"), page.rows)
        assertEquals(0, page.remaining)
        assertFalse(page.hasFurtherPage)
    }

    /**
     * The scenario's THEN clause, stated on the mapper: a page never carries more rows than the
     * capacity, however long the list is (spec: `car-list-row-limit` — "the template SHALL carry at
     * most that many rows"). This is the bound a mutation that drops the capacity from the arithmetic
     * violates, while the exactly-at-the-capacity case below cannot tell the difference.
     */
    @Test
    fun aPageNeverCarriesMoreRowsThanTheCapacity() {
        for (capacity in 1..8) {
            for (page in 0..3) {
                val slice = FavoritesScreenMapper.page(rows, capacity = capacity, page = page)
                assertTrue(
                    "page $page at capacity $capacity carried ${slice.rows.size} rows: ${slice.rows}",
                    slice.rows.size <= capacity
                )
            }
        }
    }

    @Test
    fun theLastPageCarriesNoPageRow() {
        // Spec: "The last page offers no further row".
        val last = FavoritesScreenMapper.page(rows, capacity = 3, page = 2)

        assertEquals(listOf("row7"), last.rows)
        assertEquals(0, last.remaining)
        assertFalse(last.hasFurtherPage)
    }

    @Test
    fun thePagesPartitionTheSequence() {
        // Spec: "Every item is reachable exactly once" — the pages are disjoint and their union is the
        // whole sequence, whatever the capacity.
        for (capacity in 1..8) {
            val seen = mutableListOf<String>()
            var page = 0
            while (true) {
                val slice = FavoritesScreenMapper.page(rows, capacity = capacity, page = page)
                assertTrue(
                    "page $page at capacity $capacity repeated an item: ${slice.rows}",
                    seen.none { it in slice.rows }
                )
                seen += slice.rows
                assertEquals(
                    "the level's items left was wrong on page $page at capacity $capacity",
                    rows.size - seen.size,
                    slice.remaining
                )
                if (!slice.hasFurtherPage) break
                page++
                assertTrue("paging did not terminate at capacity $capacity", page <= rows.size)
            }
            assertEquals("the pages missed an item at capacity $capacity", rows, seen)
        }
    }

    @Test
    fun anEmptyListIsOneEmptyPage() {
        val page = FavoritesScreenMapper.page(emptyList<String>(), capacity = 6, page = 0)

        assertEquals(emptyList<String>(), page.rows)
        assertEquals(0, page.remaining)
        assertFalse(page.hasFurtherPage)
    }

    @Test
    fun anUnusableCapacityStillTerminates() {
        // A host answering 0 (or a caller passing a negative page) must not make the traversal
        // non-terminating or drop the items: the clamp keeps one row per page.
        val zero = FavoritesScreenMapper.page(rows, capacity = 0, page = 0)
        assertEquals(listOf("row1"), zero.rows)
        assertEquals(6, zero.remaining)
        assertTrue(zero.hasFurtherPage)

        val negative = FavoritesScreenMapper.page(rows, capacity = 3, page = -1)
        assertEquals(listOf("row1", "row2", "row3"), negative.rows)
    }
}
