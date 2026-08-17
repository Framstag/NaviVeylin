package com.naviveylin.ui.map

import android.graphics.Bitmap
import android.util.Log
import java.util.LinkedHashMap

/**
 * LRU tile cache for rendered map tiles.
 *
 * Stores rendered geographic tiles keyed by (zoomLevel, tileX, tileY).
 * The renderer renders missing tiles natively one per tile and reuses cached
 * tiles when composing the visible viewport (`MapRenderer.renderFromTiles`).
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

    data class TileKey(val zoomLevel: Int, val tileX: Int, val tileY: Int)

    data class CachedTile(val bitmap: Bitmap, val epoch: Long)

    companion object {
        private const val TAG = "TileCache"
        const val DEFAULT_MAX_SIZE = 200
    }
}
