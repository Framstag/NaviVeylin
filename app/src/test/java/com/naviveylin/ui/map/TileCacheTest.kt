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
    fun `tiles at different zoom levels do not collide`() {
        val keyLow = TileCache.TileKey(8, 0, 0)
        val keyHigh = TileCache.TileKey(14, 0, 0)
        val bmp = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
        cache.put(keyLow, bmp, 1L)
        assertNotNull(cache.get(keyLow, 1L))
        assertNull(cache.get(keyHigh, 1L))
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
}
