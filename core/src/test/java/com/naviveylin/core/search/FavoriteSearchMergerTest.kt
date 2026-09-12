package com.naviveylin.core.search

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.LocationEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteSearchMergerTest {

    private val merger = FavoriteSearchMerger()

    private fun fav(name: String, lat: Double = 51.0, lon: Double = 7.0) =
        FavoriteLocation(name, lat, lon)

    private fun native(label: String, lat: Double = 51.0, lon: Double = 7.0) =
        LocationEntry().apply {
            this.label = label
            this.lat = lat
            this.lon = lon
            matchQuality = "match"
        }

    // --- Matching ---

    @Test
    fun `substring match finds favorite`() {
        val favorites = listOf(fav("Home Sweet Home"))
        val result = merger.merge("sweet", favorites, emptyList())

        assertEquals(1, result.size)
        assertTrue(result[0].isFavoriteHit)
        assertEquals("Home Sweet Home", result[0].entry.label)
        assertEquals("favorite", result[0].entry.matchQuality)
    }

    @Test
    fun `case-insensitive match finds favorite`() {
        val favorites = listOf(fav("Home"))
        val result = merger.merge("hOmE", favorites, emptyList())

        assertEquals(1, result.size)
        assertTrue(result[0].isFavoriteHit)
    }

    @Test
    fun `non-matching query finds no favorite`() {
        val favorites = listOf(fav("Home"))
        val result = merger.merge("xyz", favorites, emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `short query does not search favorites`() {
        val favorites = listOf(fav("Home"))
        val result = merger.merge("h", favorites, emptyList())

        assertTrue(result.isEmpty())
    }

    // --- Ordering ---

    @Test
    fun `favorite hit ordered above native results`() {
        val favorites = listOf(fav("Home", 51.0, 7.0))
        val nativeResults = listOf(native("Home Street", 51.5, 7.5))

        val result = merger.merge("home", favorites, nativeResults)

        assertEquals(2, result.size)
        assertTrue(result[0].isFavoriteHit)
        assertFalse(result[1].isFavoriteHit)
        assertEquals("Home", result[0].entry.label)
        assertEquals("Home Street", result[1].entry.label)
    }

    // --- Heart marking of native results ---

    @Test
    fun `native result near a favorite is marked as favorite`() {
        val favorites = listOf(fav("Home", 51.0, 7.0), fav("Work", 51.5, 7.5))
        // "Home" is a hit for query "home"; the native result at Work's
        // coordinates is near a favorite that is not a hit → kept and marked.
        val nativeResults = listOf(native("Work", 51.5, 7.5))

        val result = merger.merge("home", favorites, nativeResults)

        assertEquals(2, result.size)
        assertTrue(result[0].isFavoriteHit) // Home hit on top
        assertTrue(result[1].isFavorite) // Work native marked
        assertFalse(result[1].isFavoriteHit)
    }

    @Test
    fun `native result far from any favorite is not marked`() {
        val favorites = listOf(fav("Home", 51.0, 7.0))
        val nativeResults = listOf(native("Other", 52.0, 8.0))

        val result = merger.merge("other", favorites, nativeResults)

        assertEquals(1, result.size)
        assertFalse(result[0].isFavorite)
    }

    // --- Deduplication ---

    @Test
    fun `identical favorite and native result deduped to favorite only`() {
        val favorites = listOf(fav("Home", 51.0, 7.0))
        val nativeResults = listOf(native("Home", 51.0, 7.0))

        val result = merger.merge("home", favorites, nativeResults)

        assertEquals(1, result.size)
        assertTrue(result[0].isFavoriteHit)
        assertEquals("Home", result[0].entry.label)
    }

    @Test
    fun `different objects both shown`() {
        val favorites = listOf(fav("Home", 51.0, 7.0))
        val nativeResults = listOf(native("Home", 52.0, 8.0))

        val result = merger.merge("home", favorites, nativeResults)

        assertEquals(2, result.size)
        assertTrue(result[0].isFavoriteHit)
        assertFalse(result[1].isFavoriteHit)
    }

    // --- Edge cases ---

    @Test
    fun `empty favorites passes native results through unmarked`() {
        val nativeResults = listOf(native("Street", 51.0, 7.0))

        val result = merger.merge("street", emptyList(), nativeResults)

        assertEquals(1, result.size)
        assertFalse(result[0].isFavorite)
        assertFalse(result[0].isFavoriteHit)
    }

    @Test
    fun `empty native results returns favorite hits only`() {
        val favorites = listOf(fav("Home"))

        val result = merger.merge("home", favorites, emptyList())

        assertEquals(1, result.size)
        assertTrue(result[0].isFavoriteHit)
    }

    @Test
    fun `both empty returns empty list`() {
        val result = merger.merge("home", emptyList(), emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `native result near favorite but not a hit is kept and marked`() {
        // Favorite "Home" does not match query "street", but native result is
        // at the favorite's coordinates — kept (not a hit) and heart-marked.
        val favorites = listOf(fav("Home", 51.0, 7.0))
        val nativeResults = listOf(native("Street", 51.0, 7.0))

        val result = merger.merge("street", favorites, nativeResults)

        assertEquals(1, result.size)
        assertFalse(result[0].isFavoriteHit)
        assertTrue(result[0].isFavorite)
    }
}
