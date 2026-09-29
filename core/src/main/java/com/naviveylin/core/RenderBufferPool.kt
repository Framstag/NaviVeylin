package com.naviveylin.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.IdentityHashMap

/**
 * Shared pool of direct pixel buffers for renders that write into caller-owned storage
 * (spec: `render-performance` — A render writes into caller-owned pixel storage; `osmscout-jni`
 * — Render entry point writing into a caller-supplied pixel buffer).
 *
 * A render into caller-supplied storage needs somewhere for those pixels to live between the
 * native call and the bitmap that displays them. Allocating that per render is what this pool
 * removes: the buffers are retained per size class and handed out again, exactly like
 * [RenderBitmapPool] does for the render targets themselves, and the JNI path writes the frame
 * straight into them (no `int[]`, no intermediate pixel vector — measured 2026-09-27: three
 * frame-sized buffers per render at the car's overrun size, ~37 MB transient).
 *
 * ## Contract (design D2)
 *
 * - [acquire] hands a buffer out, positioned at 0 with its full capacity readable; the caller
 *   owns it until [release].
 * - [release] returns it to the free list, or drops it when the pool already retains the
 *   maximum for that size class (a direct buffer cannot be recycled explicitly; the JVM's
 *   cleaner frees it once nothing references it).
 * - Releasing a buffer the pool did not hand out (or releasing twice) is refused and reported
 *   through [DiagnosticsLog] under the `RENDER` tag, so the same storage can never be handed to
 *   two renders by accident.
 * - Every buffer is 4 bytes per pixel, `width * 4` bytes per row, no padding, and holds the frame in
 *   the layout `Bitmap.copyPixelsFromBuffer` reads: the bytes `R,G,B,A` per pixel, i.e. an
 *   `ARGB_8888` bitmap's own byte order (`0xAABBGGRR` read as a little-endian word). That is **not**
 *   the `0xAARRGGBB` word `Bitmap.setPixels(int[])` takes, which the *allocating* render entry point
 *   returns — the same frame reaches the two destinations in two different layouts, and writing one
 *   into the other is a red/blue channel swap (the 2026-09-29 defect: design D1b of
 *   `reduce-render-peak-memory`, spec `osmscout-jni` — The buffer's layout is the consumer's, not the
 *   allocating path's). The buffers are allocated with [ByteOrder.nativeOrder] for the JVM-side reads
 *   in tests; that order decides nothing for `copyPixelsFromBuffer`, which copies bytes.
 *
 * ## Bound (mirrors design D2 of the render-target pool)
 *
 * At most [MAX_FREE_PER_SIZE] free buffers per size class and at most [MAX_SIZE_CLASSES] size
 * classes are retained, so a resize cannot accumulate historical sizes.
 */
object RenderBufferPool {

    private const val TAG = "RENDER"

    /** Free buffers retained per size class. */
    private const val MAX_FREE_PER_SIZE = 2

    /** Size classes whose free buffers are retained. */
    private const val MAX_SIZE_CLASSES = 2

    /** Bytes per pixel of the frame format the bridge writes (ARGB, one int per pixel). */
    const val BYTES_PER_PIXEL = 4

    private val lock = Any()

    /** Free buffers per size class, in insertion order so the oldest class can be evicted. */
    private val free = LinkedHashMap<Long, ArrayDeque<ByteBuffer>>()

    /** Buffers currently handed out; a release is accepted only for a member. */
    private val handedOut = IdentityHashMap<ByteBuffer, Boolean>()

    /**
     * Number of buffers this pool has allocated since the process started (test-only
     * observable). Always counted; production behaviour does not read it.
     */
    @Volatile
    var allocatedCount: Int = 0
        private set

    /**
     * Bytes one frame of [width] x [height] occupies: exactly what the bridge writes and what
     * [Bitmap.copyPixelsFromBuffer] reads.
     */
    fun frameBytes(width: Int, height: Int): Int {
        require(width > 0 && height > 0) { "render buffer size must be positive: ${width}x$height" }
        val bytes = width.toLong() * height.toLong() * BYTES_PER_PIXEL
        require(bytes <= Int.MAX_VALUE) { "render buffer too large: ${width}x$height" }
        return bytes.toInt()
    }

    /**
     * Returns a direct buffer able to hold one [width] x [height] frame, reusing a free one when
     * available and allocating otherwise. The buffer comes back positioned at 0 with its full
     * capacity readable, so a native write and the following `copyPixelsFromBuffer` both use the
     * whole frame.
     */
    fun acquire(width: Int, height: Int): ByteBuffer {
        val bytes = frameBytes(width, height)
        synchronized(lock) {
            val sizeKey = key(width, height)
            val queue = free[sizeKey]
            val reusable = queue?.removeLastOrNull()
            if (queue != null && queue.isEmpty()) {
                free.remove(sizeKey)
            }
            val buffer = reusable ?: ByteBuffer.allocateDirect(bytes)
                .order(ByteOrder.nativeOrder())
                .also { allocatedCount++ }
            buffer.clear()
            handedOut[buffer] = true
            return buffer
        }
    }

    /**
     * Returns a buffer to the pool. Drops it when the pool already retains the maximum for its
     * size class. A buffer that was not handed out (or was already released) is refused: the
     * caller's bug is reported, and no storage is aliased.
     */
    fun release(buffer: ByteBuffer) {
        synchronized(lock) {
            if (handedOut.remove(buffer) == null) {
                DiagnosticsLog.log(
                    TAG,
                    "refused release of a ${buffer.capacity()}-byte buffer " +
                        "the pool did not hand out (double release or foreign buffer)"
                )
                return
            }
            val sizeKey = key(buffer.capacity())
            val queue = free[sizeKey]
            if (queue != null && queue.size >= MAX_FREE_PER_SIZE) {
                return
            }
            free.getOrPut(sizeKey) { ArrayDeque() }.addLast(buffer)
            while (free.size > MAX_SIZE_CLASSES) {
                val oldestKey = free.keys.firstOrNull() ?: break
                free.remove(oldestKey)
            }
        }
    }

    /** Buffers currently handed out (test-only observable). */
    val inUseCount: Int
        get() = synchronized(lock) { handedOut.size }

    /** Free buffers retained for one buffer size in bytes (test-only observable). */
    fun freeCountForBytes(bytes: Int): Int {
        synchronized(lock) {
            return free[key(bytes)]?.size ?: 0
        }
    }

    /**
     * Clears the pool for a test: forgets every free and handed-out buffer and resets
     * [allocatedCount]. Never call this while a render holds a buffer — its later release would
     * be refused.
     */
    fun resetForTest() {
        synchronized(lock) {
            free.clear()
            handedOut.clear()
            allocatedCount = 0
        }
    }

    private fun key(width: Int, height: Int): Long = key(frameBytes(width, height))

    /** Size key: the buffer capacity in bytes, which is what a size class is. */
    private fun key(bytes: Int): Long = bytes.toLong()
}
