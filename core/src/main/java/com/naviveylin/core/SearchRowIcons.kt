package com.naviveylin.core

import android.graphics.Bitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Source of a map object type's stylesheet artwork: the ARGB pixels of [sizePx] × [sizePx]
 * artwork, row-major, in the layout the bridge's render entry points document — or `null`
 * when the active stylesheet gives that type neither a symbol nor icon artwork (spec:
 * `osmscout-jni` — Icon entry point returns a type's stylesheet artwork).
 *
 * The phone passes the bridge call; tests pass a recorder. It is a native call, so it runs
 * off the main thread and never inside a car host callback (`guidelines/Design.md` §4).
 */
fun interface TypeIconSource {
    fun artwork(objectType: String, sizePx: Int): IntArray?
}

/**
 * Edge length the search result rows request their type icon at, in dp. The icon artwork is
 * rendered at density-scaled pixels of this size, so the phone row and the car row ask the same
 * cache key for the same type (change `search-result-row-content`, design D6).
 */
const val SEARCH_ROW_ICON_DP = 20

/**
 * The type icon a search result row leads with, resolved once per type and reused
 * (spec: `search-result-row-icon` — "Resolved artwork is reused and follows the style";
 * change `search-result-row-content`, design D7).
 *
 * Resolution is single-flight per `(objectType, sizePx)`: a list showing twenty entries of
 * one type renders once, and a second caller for a key already being resolved waits for
 * that result instead of starting a second native call. A `null` answer is remembered as
 * "this type has no artwork" and is not retried for that key — a type without artwork is a
 * normal outcome, not a failure to retry on every recomposition.
 *
 * [peek] never calls native and never blocks, which is what lets a car template build read
 * the cache on the host thread. [clear] is called when the active stylesheet finishes
 * loading and when the icon-versus-symbol preference changes; the callers pair it with
 * [ResultMarkings.clear], so a composite cannot outlive the icon it was drawn with.
 */
object SearchRowIcons {

    private data class Key(val objectType: String, val sizePx: Int)

    private val icons = java.util.concurrent.ConcurrentHashMap<Key, Bitmap>()
    private val missing = java.util.concurrent.ConcurrentHashMap.newKeySet<Key>()

    /**
     * Serialises resolutions: the artwork call is native and short, and a query produces a
     * handful of distinct types, so one lock is enough to keep the bridge's DB thread from
     * being asked for the same key twice.
     */
    private val lock = Mutex()

    /**
     * Artwork for [objectType] at [sizePx], resolving it through [source] when it is neither
     * cached nor known to be absent. Returns `null` for a type the stylesheet gives no
     * artwork for, and for an unusable size.
     */
    suspend fun resolve(source: TypeIconSource, objectType: String, sizePx: Int): Bitmap? {
        if (objectType.isEmpty() || sizePx <= 0) return null
        val key = Key(objectType, sizePx)
        icons[key]?.let { return it }
        if (missing.contains(key)) return null
        return lock.withLock {
            icons[key]?.let { return@withLock it }
            if (missing.contains(key)) return@withLock null
            val bitmap = source.artwork(objectType, sizePx)
                ?.takeIf { it.size >= sizePx * sizePx }
                ?.let { Bitmap.createBitmap(it, sizePx, sizePx, Bitmap.Config.ARGB_8888) }
            if (bitmap == null) {
                missing.add(key)
                null
            } else {
                icons[key] = bitmap
                bitmap
            }
        }
    }

    /**
     * Cached artwork for [objectType] at [sizePx], or `null` when it is not resolved (or
     * known to be absent). Never calls native and never blocks — the car mapper reads the
     * rows' artwork through this, on the host thread.
     */
    fun peek(objectType: String, sizePx: Int): Bitmap? = icons[Key(objectType, sizePx)]

    /** Forget every resolved and absent artwork; the next [resolve] calls the source again. */
    fun clear() {
        icons.clear()
        missing.clear()
    }
}
