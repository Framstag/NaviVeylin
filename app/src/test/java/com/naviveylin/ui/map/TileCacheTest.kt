package com.naviveylin.ui.map

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TileCacheTest {

    private lateinit var cache: TileCache

    @Before
    fun setUp() {
        cache = TileCache(maxSize = 10)
    }

    @Test
    fun `put and get tile`() {
        val key = TileCache.TileKey(8, 0, 0)
        val bitmap = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
        cache.put(key, bitmap, 1L)
        val retrieved = cache.get(key, 1L)
        assertNotNull(retrieved)
        assertSame(bitmap, retrieved)
    }

    @Test
    fun `get returns null for missing key`() {
        val key = TileCache.TileKey(8, 0, 0)
        assertNull(cache.get(key, 1L))
    }

    @Test
    fun `get returns null for epoch mismatch`() {
        val key = TileCache.TileKey(8, 0, 0)
        val bitmap = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
        cache.put(key, bitmap, 1L)
        assertNull(cache.get(key, 2L))
    }

    @Test
    fun `contains returns true for cached tile`() {
        val key = TileCache.TileKey(8, 0, 0)
        val bitmap = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
        cache.put(key, bitmap, 1L)
        assertTrue(cache.contains(key, 1L))
    }

    @Test
    fun `contains returns false for missing tile`() {
        val key = TileCache.TileKey(8, 0, 0)
        assertFalse(cache.contains(key, 1L))
    }

    @Test
    fun `LRU eviction removes oldest entry`() {
        val maxSize = 3
        val smallCache = TileCache(maxSize = 3)

        // Fill cache
        for (i in 0 until maxSize) {
            val key = TileCache.TileKey(8, i, 0)
            val bitmap = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
            smallCache.put(key, bitmap, 1L)
        }

        // All 3 should be present
        assertEquals(maxSize, smallCache.size())

        // Add one more — should evict the oldest (0,0)
        val newKey = TileCache.TileKey(8, 3, 0)
        val newBitmap = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
        smallCache.put(newKey, newBitmap, 1L)

        assertEquals(maxSize, smallCache.size())
        assertNull(smallCache.get(TileCache.TileKey(8, 0, 0), 1L))
        assertNotNull(smallCache.get(TileCache.TileKey(8, 1, 0), 1L))
        assertNotNull(smallCache.get(TileCache.TileKey(8, 2, 0), 1L))
        assertNotNull(smallCache.get(TileCache.TileKey(8, 3, 0), 1L))
    }

    @Test
    fun `retainEpoch removes stale entries`() {
        val key1 = TileCache.TileKey(8, 0, 0)
        val key2 = TileCache.TileKey(8, 0, 1)
        val bmp1 = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
        val bmp2 = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
        cache.put(key1, bmp1, 1L)
        cache.put(key2, bmp2, 2L)

        cache.retainEpoch(2L)

        assertNull(cache.get(key1, 1L))
        assertNotNull(cache.get(key2, 2L))
        assertEquals(1, cache.size())
    }

    @Test
    fun `clear removes all tiles`() {
        val key = TileCache.TileKey(8, 0, 0)
        val bitmap = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
        cache.put(key, bitmap, 1L)
        cache.clear()
        assertEquals(0, cache.size())
    }

    @Test
    fun `computeTileGrid covers full area`() {
        val keys = cache.computeTileGrid(512, 512, 8)
        // 512/256 = 2 cols, 2 rows = 4 tiles
        assertEquals(4, keys.size)
        val expectedKeys = setOf(
            TileCache.TileKey(8, 0, 0),
            TileCache.TileKey(8, 1, 0),
            TileCache.TileKey(8, 0, 1),
            TileCache.TileKey(8, 1, 1)
        )
        assertEquals(expectedKeys, keys.toSet())
    }

    @Test
    fun `computeTileGrid handles partial tiles`() {
        val keys = cache.computeTileGrid(300, 300, 8)
        // 300/256 = 2 cols (ceil), 2 rows = 4 tiles
        assertEquals(4, keys.size)
    }

    @Test
    fun `storeTiles and compose round-trip`() {
        val width = 512
        val height = 512
        val pixels = IntArray(width * height)
        // Fill with a recognizable pattern: red in top-left tile, blue in bottom-right
        for (y in 0 until 256) {
            for (x in 0 until 256) {
                pixels[y * width + x] = 0xFFFF0000.toInt() // red
            }
        }
        for (y in 256 until 512) {
            for (x in 256 until 512) {
                pixels[y * width + x] = 0xFF0000FF.toInt() // blue
            }
        }

        cache.storeTiles(pixels, width, height, 8, 1L)

        val result = cache.compose(width, height, 8, 1L)
        assertNotNull(result)
        assertEquals(0, result!!.missingTiles)
        assertTrue(result.isComplete)

        // Verify pixel at top-left is red
        val checkPixels = IntArray(1)
        result.bitmap.getPixels(checkPixels, 0, 1, 0, 0, 1, 1)
        assertEquals(0xFFFF0000.toInt(), checkPixels[0])
    }

    @Test
    fun `compose with partial cache returns missing count`() {
        val width = 512
        val height = 512
        val pixels = IntArray(width * height)

        // Store only one tile
        val singleTile = IntArray(256 * 256)
        val singleBmp = android.graphics.Bitmap.createBitmap(singleTile, 256, 256, android.graphics.Bitmap.Config.ARGB_8888)
        cache.put(TileCache.TileKey(8, 0, 0), singleBmp, 1L)

        val result = cache.compose(width, height, 8, 1L)
        assertNotNull(result)
        assertEquals(3, result!!.missingTiles)
        assertFalse(result.isComplete)
    }

    @Test
    fun `compose with no cached tiles returns null`() {
        val result = cache.compose(512, 512, 8, 1L)
        assertNull(result)
    }
}
