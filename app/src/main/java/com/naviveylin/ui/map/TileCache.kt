package com.naviveylin.ui.map

import android.graphics.Bitmap
import android.util.Log
import java.util.LinkedHashMap

/**
 * LRU tile cache for rendered map tiles.
 *
 * Stores rendered tiles keyed by (zoomLevel, tileX, tileY).
 * After a full render completes, the result is split into tiles and cached.
 * On subsequent renders, cached tiles are reused and only missing tiles
 * are rendered individually.
 *
 * Evicts least recently used tiles when cache size exceeds limit.
 * Supports epoch-based invalidation.
 */
class TileCache(private val maxSize: Int = DEFAULT_MAX_SIZE) {

    private val cache = object : LinkedHashMap<TileKey, CachedTile>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileKey, CachedTile>?): Boolean {
            if (size > this@TileCache.maxSize) {
                val e = eldest?.key
                if (e != null) {
                    Log.d(TAG, "tile evicted z=" + e.zoomLevel + " x=" + e.tileX + " y=" + e.tileY +
                            " (size=" + size + "/" + this@TileCache.maxSize + ")")
                }
                return true
            }
            return false
        }
    }

    @Synchronized
    fun get(key: TileKey, epoch: Long): Bitmap? {
        val entry = cache[key] ?: return null
        return if (entry.epoch != epoch) null else entry.bitmap
    }

    @Synchronized
    fun getLogged(key: TileKey, epoch: Long): Bitmap? {
        val entry = cache[key]
        if (entry == null) {
            Log.d(TAG, "tile miss z=" + key.zoomLevel + " x=" + key.tileX + " y=" + key.tileY)
            return null
        } else if (entry.epoch != epoch) {
            Log.d(TAG, "tile stale z=" + key.zoomLevel + " x=" + key.tileX + " y=" + key.tileY +
                    " (epoch " + entry.epoch + " != " + epoch + ")")
            return null
        } else {
            Log.d(TAG, "tile hit z=" + key.zoomLevel + " x=" + key.tileX + " y=" + key.tileY)
            return entry.bitmap
        }
    }

    @Synchronized
    fun put(key: TileKey, bitmap: Bitmap, epoch: Long) {
        cache[key] = CachedTile(bitmap, epoch)
        Log.d(TAG, "tile added z=" + key.zoomLevel + " x=" + key.tileX + " y=" + key.tileY +
                " (size=" + cache.size + "/" + maxSize + ")")
    }

    @Synchronized
    fun contains(key: TileKey, epoch: Long): Boolean {
        val entry = cache[key] ?: return false
        return entry.epoch == epoch
    }

    @Synchronized
    fun retainEpoch(validEpoch: Long) {
        cache.values.removeIf { it.epoch != validEpoch }
    }

    @Synchronized
    fun clear() {
        val n = cache.size
        cache.clear()
        if (n > 0) {
            Log.d(TAG, "tile cache cleared ($n tiles)")
        }
    }

    @Synchronized
    fun size(): Int = cache.size

    fun computeTileGrid(width: Int, height: Int, zoomLevel: Int): Array<TileKey> {
        val cols = ceilDiv(width, TILE_SIZE)
        val rows = ceilDiv(height, TILE_SIZE)
        return Array(cols * rows) { index ->
            val col = index % cols
            val row = index / cols
            TileKey(zoomLevel, col, row)
        }
    }

    fun storeTiles(pixels: IntArray, width: Int, height: Int, zoomLevel: Int, epoch: Long) {
        val keys = computeTileGrid(width, height, zoomLevel)
        for (key in keys) {
            val tileW = minOf(TILE_SIZE, width - key.tileX * TILE_SIZE)
            val tileH = minOf(TILE_SIZE, height - key.tileY * TILE_SIZE)
            if (tileW > 0 && tileH > 0) {
                val tilePixels = IntArray(tileW * tileH)
                val srcX = key.tileX * TILE_SIZE
                val srcY = key.tileY * TILE_SIZE
                for (y in 0 until tileH) {
                    System.arraycopy(pixels, (srcY + y) * width + srcX, tilePixels, y * tileW, tileW)
                }
                val tileBitmap = Bitmap.createBitmap(tilePixels, tileW, tileH, Bitmap.Config.ARGB_8888)
                put(key, tileBitmap, epoch)
            }
        }
    }

    fun compose(width: Int, height: Int, zoomLevel: Int, epoch: Long): CompositeResult? {
        val keys = computeTileGrid(width, height, zoomLevel)
        val result = IntArray(width * height)
        var anyCached = false
        var missingCount = 0
        for (key in keys) {
            val tileW = minOf(TILE_SIZE, width - key.tileX * TILE_SIZE)
            val tileH = minOf(TILE_SIZE, height - key.tileY * TILE_SIZE)
            if (tileW > 0 && tileH > 0) {
                val tile = get(key, epoch)
                if (tile == null) {
                    missingCount++
                } else {
                    val tilePixels = IntArray(tileW * tileH)
                    tile.getPixels(tilePixels, 0, tileW, 0, 0, tileW, tileH)
                    val dstX = key.tileX * TILE_SIZE
                    val dstY = key.tileY * TILE_SIZE
                    for (y in 0 until tileH) {
                        System.arraycopy(tilePixels, y * tileW, result, (dstY + y) * width + dstX, tileW)
                    }
                    anyCached = true
                }
            }
        }
        if (!anyCached) return null
        val resultBitmap = Bitmap.createBitmap(result, width, height, Bitmap.Config.ARGB_8888)
        return CompositeResult(resultBitmap, missingCount)
    }

    data class TileKey(val zoomLevel: Int, val tileX: Int, val tileY: Int)

    data class CachedTile(val bitmap: Bitmap, val epoch: Long)

    data class CompositeResult(val bitmap: Bitmap, val missingTiles: Int) {
        val isComplete: Boolean get() = missingTiles == 0
    }

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

    companion object {
        private const val TAG = "TileCache"
        const val DEFAULT_MAX_SIZE = 200
        const val TILE_SIZE = 256
    }
}
