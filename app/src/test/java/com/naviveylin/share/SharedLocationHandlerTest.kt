package com.naviveylin.share

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedLocationHandlerTest {

    private val request = SharedLocationRequest(lat = 48.8566, lon = 2.3522, label = "Eiffel Tower")

    @Test
    fun submit_exposesRequest() = runTest {
        val handler = SharedLocationHandler()
        handler.submit(request)
        assertEquals(request, handler.request.first())
    }

    @Test
    fun consume_returnsAndClears() = runTest {
        val handler = SharedLocationHandler()
        handler.submit(request)
        assertEquals(request, handler.consume())
        assertNull(handler.consume())
        assertNull(handler.request.first())
    }

    @Test
    fun consume_withoutSubmit_returnsNull() = runTest {
        val handler = SharedLocationHandler()
        assertNull(handler.consume())
    }

    @Test
    fun submit_replacesPrevious() = runTest {
        val handler = SharedLocationHandler()
        handler.submit(request)
        val second = SharedLocationRequest(query = "Brandenburger Tor")
        handler.submit(second)
        assertEquals(second, handler.consume())
    }
}
