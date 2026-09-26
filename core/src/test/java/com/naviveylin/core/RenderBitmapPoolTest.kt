package com.naviveylin.core

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for [RenderBitmapPool] — the reuse contract of the render path
 * (spec: `render-performance` — Reusable render target for map frames; design D2/D3/D10).
 */
@RunWith(RobolectricTestRunner::class)
class RenderBitmapPoolTest {

    @Before
    fun setUp() {
        RenderBitmapPool.resetForTest()
    }

    @Test
    fun consecutiveSameSizeRendersReuseOneTargetAndAllocateOnce() {
        val first = RenderBitmapPool.acquire(64, 48)
        RenderBitmapPool.release(first)
        val second = RenderBitmapPool.acquire(64, 48)

        assertSame("the second render must reuse the released target", first, second)
        assertEquals("exactly one allocation for the sequence", 1, RenderBitmapPool.allocatedCount)
        assertEquals(64, second.width)
        assertEquals(48, second.height)
        assertEquals(Bitmap.Config.ARGB_8888, second.config)
        RenderBitmapPool.release(second)
    }

    @Test
    fun aSecondSizeClassAllocatesItsOwnTarget() {
        val small = RenderBitmapPool.acquire(64, 48)
        val large = RenderBitmapPool.acquire(128, 96)

        assertNotSame(small, large)
        assertEquals(2, RenderBitmapPool.allocatedCount)
        assertEquals(2, RenderBitmapPool.inUseCount)

        RenderBitmapPool.release(small)
        RenderBitmapPool.release(large)
        assertEquals(1, RenderBitmapPool.freeCount(64, 48))
        assertEquals(1, RenderBitmapPool.freeCount(128, 96))
    }

    @Test
    fun surplusTargetsBeyondTheBoundAreRecycled() {
        val targets = (1..3).map { RenderBitmapPool.acquire(64, 48) }
        assertEquals(3, RenderBitmapPool.allocatedCount)

        targets.forEach { RenderBitmapPool.release(it) }

        assertEquals("the free list keeps only the bound", 2, RenderBitmapPool.freeCount(64, 48))
        assertEquals("exactly one surplus target is recycled", 1, targets.count { it.isRecycled })
        assertEquals(0, RenderBitmapPool.inUseCount)

        val reused = RenderBitmapPool.acquire(64, 48)
        assertTrue("a retained target is reused instead of allocating", targets.contains(reused))
        assertEquals(3, RenderBitmapPool.allocatedCount)
        RenderBitmapPool.release(reused)
    }

    @Test
    fun aThirdSizeClassEvictsTheOldest() {
        val oldest = RenderBitmapPool.acquire(10, 10)
        RenderBitmapPool.release(oldest)
        val middle = RenderBitmapPool.acquire(20, 20)
        RenderBitmapPool.release(middle)
        val newest = RenderBitmapPool.acquire(30, 30)
        RenderBitmapPool.release(newest)

        assertEquals("the oldest size class is evicted", 0, RenderBitmapPool.freeCount(10, 10))
        assertEquals(1, RenderBitmapPool.freeCount(20, 20))
        assertEquals(1, RenderBitmapPool.freeCount(30, 30))
        assertTrue("the evicted target is recycled", oldest.isRecycled)
    }

    @Test
    fun doubleReleaseIsRefusedAndDoesNotAliasStorage() {
        val first = RenderBitmapPool.acquire(64, 48)
        RenderBitmapPool.release(first)
        assertEquals(1, RenderBitmapPool.freeCount(64, 48))

        // Second release of the same target: refused. The target stays on the free list
        // exactly once — no double insert, and the refusing path does not recycle it.
        RenderBitmapPool.release(first)
        assertEquals(1, RenderBitmapPool.freeCount(64, 48))
        assertFalse(first.isRecycled)

        val reused = RenderBitmapPool.acquire(64, 48)
        val other = RenderBitmapPool.acquire(64, 48)
        assertSame("the refused release must not alias storage", first, reused)
        assertNotSame(reused, other)
        assertEquals("only the targets the pool handed out are counted", 2, RenderBitmapPool.allocatedCount)
        RenderBitmapPool.release(reused)
        RenderBitmapPool.release(other)
    }

    @Test
    fun aForeignBitmapIsRefused() {
        val foreign = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        RenderBitmapPool.release(foreign)

        assertEquals(0, RenderBitmapPool.freeCount(64, 48))
        assertFalse("a foreign bitmap is never retained", foreign.isRecycled)
    }

    @Test
    fun concurrentAcquireReleaseNeverAliasesOneTarget() {
        val held = ConcurrentHashMap.newKeySet<Bitmap>()
        val overlaps = AtomicInteger(0)
        val threads = (1..4).map {
            Thread {
                repeat(200) {
                    val target = RenderBitmapPool.acquire(64, 48)
                    if (!held.add(target)) {
                        overlaps.incrementAndGet()
                    }
                    held.remove(target)
                    RenderBitmapPool.release(target)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals("no target is ever held by two threads at once", 0, overlaps.get())
        assertEquals("every handed-out target was released", 0, RenderBitmapPool.inUseCount)
        assertTrue(
            "the pool retained no more than its bound under concurrency",
            RenderBitmapPool.freeCount(64, 48) <= 2
        )
    }
}
