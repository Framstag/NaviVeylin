package com.naviveylin.core

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The row-icon cache (change `search-result-row-content`, design D7; spec:
 * `search-result-row-icon` — Resolved artwork is reused and follows the style).
 *
 * Robolectric in the default sandbox, because the resolved artwork is a `Bitmap`; the
 * cache's content is asserted by identity and by how often the source was asked, not by
 * pixels.
 */
@RunWith(RobolectricTestRunner::class)
class SearchRowIconsTest {

    /** Records every request and answers with a freshly created opaque bitmap. */
    private class RecordingSource : TypeIconSource {
        val requests = mutableListOf<Pair<String, Int>>()
        override fun artwork(objectType: String, sizePx: Int): IntArray? {
            requests.add(objectType to sizePx)
            return IntArray(sizePx * sizePx) { 0xFF00FF00.toInt() }
        }
    }

    /** Records the request, then blocks until released — a native call in progress. */
    private class BlockingSource : TypeIconSource {
        val entered = CountDownLatch(1)
        private val gate = CountDownLatch(1)
        var calls = 0
        override fun artwork(objectType: String, sizePx: Int): IntArray? {
            calls++
            entered.countDown()
            gate.await(5, TimeUnit.SECONDS)
            return IntArray(sizePx * sizePx) { 0xFF00FF00.toInt() }
        }

        fun release() = gate.countDown()
    }

    @Before
    fun clearCaches() {
        SearchRowIcons.clear()
        ResultMarkings.clear()
    }

    @Test
    fun `Several rows of one type share one resolution`() = runBlocking {
        val source = BlockingSource()
        val first = async(Dispatchers.Default) { SearchRowIcons.resolve(source, "amenity_fuel", 60) }
        assertTrue("the first resolution entered the source", source.entered.await(5, TimeUnit.SECONDS))
        val second = async(Dispatchers.Default) { SearchRowIcons.resolve(source, "amenity_fuel", 60) }
        delay(50)
        // The second caller waits for the first instead of starting a second native call.
        assertEquals(1, source.calls)
        source.release()
        val a = first.await()
        val b = second.await()
        assertSame(a, b)
        assertEquals(1, source.calls)
        assertEquals(60, a?.width)
    }

    @Test
    fun `Style switch replaces the artwork`() = runBlocking {
        val first = RecordingSource()
        val resolved = SearchRowIcons.resolve(first, "amenity_fuel", 60)
        assertSame(resolved, SearchRowIcons.resolve(first, "amenity_fuel", 60))
        assertEquals(1, first.requests.size)

        // The style finished loading (or the icon-versus-symbol preference changed).
        SearchRowIcons.clear()

        val second = RecordingSource()
        val afterSwitch = SearchRowIcons.resolve(second, "amenity_fuel", 60)
        assertEquals(1, second.requests.size)
        assertNotSame(resolved, afterSwitch)
    }

    @Test
    fun `Artwork failure leaves the row readable`() = runBlocking {
        val absent = object : TypeIconSource {
            var calls = 0
            override fun artwork(objectType: String, sizePx: Int): IntArray? {
                calls++
                return null
            }
        }
        assertNull(SearchRowIcons.resolve(absent, "address", 60))
        // Remembered as "no artwork", not retried on every recomposition.
        assertNull(SearchRowIcons.resolve(absent, "address", 60))
        assertNull(SearchRowIcons.peek("address", 60))
        assertEquals(1, absent.calls)

        // A truncated answer is treated as no artwork rather than as a broken bitmap.
        val truncated = object : TypeIconSource {
            override fun artwork(objectType: String, sizePx: Int): IntArray? = IntArray(3)
        }
        assertNull(SearchRowIcons.resolve(truncated, "shop", 60))

        // An unusable request never reaches the source.
        val unusable = RecordingSource()
        assertNull(SearchRowIcons.resolve(unusable, "shop", 0))
        assertNull(SearchRowIcons.resolve(unusable, "", 60))
        assertTrue(unusable.requests.isEmpty())
    }

    @Test
    fun `No native artwork request on a host-facing build`() = runBlocking {
        val source = RecordingSource()
        // What a car template build does: read only.
        assertNull(SearchRowIcons.peek("amenity_fuel", 60))
        assertTrue(source.requests.isEmpty())

        val resolved = SearchRowIcons.resolve(source, "amenity_fuel", 60)
        assertEquals(resolved?.width, SearchRowIcons.peek("amenity_fuel", 60)?.width)
        assertSame(resolved, SearchRowIcons.peek("amenity_fuel", 60))
        // A different size is a different key, and is not resolved by peeking.
        assertNull(SearchRowIcons.peek("amenity_fuel", 48))
    }

    @Test
    fun `Resolved artwork is a square bitmap of the requested size`() = runBlocking {
        val source = RecordingSource()
        val bitmap = SearchRowIcons.resolve(source, "amenity_fuel", 60)
        assertEquals(60, bitmap?.width)
        assertEquals(60, bitmap?.height)
        assertEquals(Bitmap.Config.ARGB_8888, bitmap?.config)
    }
}
