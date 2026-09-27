package com.naviveylin.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [RenderBufferPool] — the caller-owned pixel storage of the render path
 * (spec: `render-performance` — A render writes into caller-owned pixel storage; Per-render
 * transient allocation is bounded; `osmscout-jni` — Render entry point writing into a
 * caller-supplied pixel buffer).
 */
@RunWith(RobolectricTestRunner::class)
class RenderBufferPoolTest {

    @Before
    fun setUp() {
        RenderBufferPool.resetForTest()
    }

    @Test
    fun aBufferIsArgbShapedAndNativelyOrdered() {
        val buffer = RenderBufferPool.acquire(64, 48)

        assertEquals("4 bytes per pixel", 64 * 48 * 4, buffer.capacity())
        assertEquals(64 * 48 * 4, RenderBufferPool.frameBytes(64, 48))
        assertEquals(
            "the pixel ints are read in the platform's byte order, so a native 0xAARRGGBB write " +
                "is the pixel Bitmap.copyPixelsFromBuffer reads",
            ByteOrder.nativeOrder(),
            buffer.order()
        )
        assertEquals("a handed-out buffer is positioned at 0", 0, buffer.position())
        assertEquals(buffer.capacity(), buffer.limit())

        // Write one ARGB pixel and read it back as the int the bitmap would see.
        buffer.putInt(0, 0xFF123456.toInt())
        assertEquals(0xFF123456.toInt(), buffer.getInt(0))
        RenderBufferPool.release(buffer)
    }

    @Test
    fun consecutiveSameSizeRendersReuseOneBufferAndAllocateOnce() {
        val first = RenderBufferPool.acquire(64, 48)
        RenderBufferPool.release(first)
        val second = RenderBufferPool.acquire(64, 48)

        assertSame("the second render reuses the released buffer", first, second)
        assertEquals("exactly one allocation for the sequence", 1, RenderBufferPool.allocatedCount)
        RenderBufferPool.release(second)
    }

    @Test
    fun aSecondSizeClassAllocatesItsOwnBuffer() {
        val small = RenderBufferPool.acquire(64, 48)
        val large = RenderBufferPool.acquire(128, 96)

        assertNotSame(small, large)
        assertEquals(2, RenderBufferPool.allocatedCount)
        assertEquals(2, RenderBufferPool.inUseCount)

        RenderBufferPool.release(small)
        RenderBufferPool.release(large)
        assertEquals(1, RenderBufferPool.freeCountForBytes(64 * 48 * 4))
        assertEquals(1, RenderBufferPool.freeCountForBytes(128 * 96 * 4))
    }

    @Test
    fun surplusBuffersBeyondTheBoundAreDropped() {
        val buffers = (1..3).map { RenderBufferPool.acquire(64, 48) }
        assertEquals(3, RenderBufferPool.allocatedCount)
        buffers.forEach { RenderBufferPool.release(it) }

        assertEquals(
            "no more than the bound is retained per size class",
            2,
            RenderBufferPool.freeCountForBytes(64 * 48 * 4)
        )
    }

    @Test
    fun aThirdSizeClassEvictsTheOldest() {
        for ((w, h) in listOf(10 to 10, 20 to 20, 30 to 30)) {
            val buffer = RenderBufferPool.acquire(w, h)
            RenderBufferPool.release(buffer)
        }

        assertEquals("the oldest size class is evicted", 0, RenderBufferPool.freeCountForBytes(10 * 10 * 4))
        assertEquals(1, RenderBufferPool.freeCountForBytes(20 * 20 * 4))
        assertEquals(1, RenderBufferPool.freeCountForBytes(30 * 30 * 4))
    }

    @Test
    fun doubleReleaseIsRefusedAndDoesNotAliasStorage() {
        val buffer = RenderBufferPool.acquire(64, 48)
        RenderBufferPool.release(buffer)
        RenderBufferPool.release(buffer)

        assertEquals("the double release is not retained twice", 1, RenderBufferPool.freeCountForBytes(64 * 48 * 4))
        assertEquals(0, RenderBufferPool.inUseCount)
        val again = RenderBufferPool.acquire(64, 48)
        assertSame("the storage is only ever handed out once", buffer, again)
        RenderBufferPool.release(again)
    }

    @Test
    fun aForeignBufferIsRefused() {
        val foreign = ByteBuffer.allocateDirect(64 * 48 * 4)

        RenderBufferPool.release(foreign)

        assertEquals("a buffer the pool did not hand out is not retained", 0, RenderBufferPool.freeCountForBytes(64 * 48 * 4))
        assertEquals(0, RenderBufferPool.allocatedCount)
    }

    @Test
    fun anAcquireAfterAReleaseHandsOutTheSameStorageAgain() {
        val buffer = RenderBufferPool.acquire(100, 100)
        buffer.putInt(0, 0x11223344)
        RenderBufferPool.release(buffer)

        val reused = RenderBufferPool.acquire(100, 100)

        assertSame(buffer, reused)
        assertEquals(
            "the pool does not clear the caller's pixels for it (the render overwrites them)",
            0x11223344,
            reused.getInt(0)
        )
        assertEquals("reuse keeps one allocation", 1, RenderBufferPool.allocatedCount)
        RenderBufferPool.release(reused)
    }

    @Test
    fun aNonPositiveSizeIsAProgrammingError() {
        val failed = runCatching { RenderBufferPool.acquire(0, 10) }.isFailure
        assertTrue("a zero-width frame is refused, not silently rendered", failed)
    }
}
