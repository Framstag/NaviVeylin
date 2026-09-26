package com.naviveylin.core

import android.graphics.Bitmap
import java.util.IdentityHashMap

/**
 * Shared pool of reusable ARGB_8888 render targets.
 *
 * The render path draws a map frame into a target obtained from this pool instead of
 * allocating a bitmap per render (`TODO.md` §49 — a full car render used to walk four
 * full-size buffers, one of them this bitmap). Both surfaces that render through the
 * shared JNI entry point use it: the phone map canvas and the car map surface
 * (spec: `render-performance` — Reusable render target for map frames).
 *
 * ## Contract (design D3)
 *
 * - [acquire] hands a target out; the caller owns it until it calls [release].
 * - A caller **never** calls [Bitmap.recycle] on a pooled target — the pool is the only
 *   recycler, and it recycles surplus or evicted targets itself.
 * - [release] returns the target to the free list, or recycles it when the pool already
 *   retains the maximum for that size class.
 * - Releasing a target the pool did not hand out (or releasing twice) is refused and
 *   reported through [DiagnosticsLog] under the `RENDER` tag; the storage is never handed
 *   to a second holder by accident, which is the aliasing failure
 *   `guidelines/MapRendering.md` §2 documents.
 *
 * ## Bound (design D2)
 *
 * At most [MAX_FREE_PER_SIZE] free targets per size class and at most [MAX_SIZE_CLASSES]
 * size classes are retained, so a resize (overrun multiplier change, rotation, car surface
 * change) cannot accumulate historical sizes. Two free slots cover the car's case, where
 * one frame stays displayed while the next render is in flight.
 *
 * The pool changes allocation *churn*, not the peak: a target retained here is the same
 * target the previous design allocated and dropped per render.
 */
object RenderBitmapPool {

    private const val TAG = "RENDER"

    /** Free targets retained per size class (design D2). */
    private const val MAX_FREE_PER_SIZE = 2

    /** Size classes whose free targets are retained (design D2). */
    private const val MAX_SIZE_CLASSES = 2

    private val lock = Any()

    /**
     * Free targets per size class, in size-class insertion order so the oldest class can
     * be evicted when a new one arrives. A class with no free target is not held here.
     */
    private val free = LinkedHashMap<Long, ArrayDeque<Bitmap>>()

    /** Targets currently handed out; a release is accepted only for a member. */
    private val handedOut = IdentityHashMap<Bitmap, Boolean>()

    /**
     * Number of targets this pool has allocated since the process started (test-only
     * observable, design D10). Always counted; production behaviour does not read it.
     */
    @Volatile
    var allocatedCount: Int = 0
        private set

    /**
     * Returns a target of the requested size, reusing a free one when available and
     * allocating otherwise. ARGB_8888, same configuration the render path always used.
     */
    fun acquire(width: Int, height: Int): Bitmap {
        require(width > 0 && height > 0) { "render target size must be positive: ${width}x$height" }
        synchronized(lock) {
            val sizeKey = key(width, height)
            val queue = free[sizeKey]
            var reusable: Bitmap? = null
            while (queue != null && queue.isNotEmpty() && reusable == null) {
                val candidate = queue.removeLast()
                if (!candidate.isRecycled) {
                    reusable = candidate
                }
            }
            if (queue != null && queue.isEmpty()) {
                free.remove(sizeKey)
            }
            val target = reusable ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                allocatedCount++
            }
            handedOut[target] = true
            return target
        }
    }

    /**
     * Returns a target to the pool. Recycles it when the pool already retains the maximum
     * for its size class. A target that was not handed out (or was already released) is
     * refused: the caller's bug is reported, and no storage is aliased.
     */
    fun release(bitmap: Bitmap) {
        synchronized(lock) {
            if (handedOut.remove(bitmap) == null) {
                DiagnosticsLog.log(
                    TAG,
                    "refused release of a ${bitmap.width}x${bitmap.height} target " +
                        "the pool did not hand out (double release or foreign bitmap)"
                )
                return
            }
            if (bitmap.isRecycled) {
                return
            }
            val sizeKey = key(bitmap.width, bitmap.height)
            val queue = free[sizeKey]
            if (queue != null && queue.size >= MAX_FREE_PER_SIZE) {
                bitmap.recycle()
                return
            }
            free.getOrPut(sizeKey) { ArrayDeque() }.addLast(bitmap)
            while (free.size > MAX_SIZE_CLASSES) {
                val oldestKey = free.keys.firstOrNull() ?: break
                free.remove(oldestKey)?.forEach { it.recycle() }
            }
        }
    }

    /** Free targets retained for one size class (test-only observable, design D10). */
    fun freeCount(width: Int, height: Int): Int {
        synchronized(lock) {
            return free[key(width, height)]?.size ?: 0
        }
    }

    /** Targets currently handed out (test-only observable, design D10). */
    val inUseCount: Int
        get() = synchronized(lock) { handedOut.size }

    /**
     * Clears the pool for a test (test-only): recycles everything retained, forgets the
     * handed-out set and resets [allocatedCount]. Never call this while a renderer holds
     * a target — the later release would be refused.
     */
    fun resetForTest() {
        synchronized(lock) {
            free.values.forEach { queue -> queue.forEach { it.recycle() } }
            free.clear()
            handedOut.clear()
            allocatedCount = 0
        }
    }

    private fun key(width: Int, height: Int): Long =
        (width.toLong() shl 32) or (height.toLong() and 0xFFFFFFFFL)
}
