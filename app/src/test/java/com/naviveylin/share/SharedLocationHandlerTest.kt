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
    private val outcome = SharedLocationOutcome.Request(request)

    @Test
    fun submit_exposesRequest() = runTest {
        val handler = SharedLocationHandler()
        handler.submit(outcome)
        assertEquals(outcome, handler.outcome.first())
    }

    @Test
    fun consume_returnsAndClears() = runTest {
        val handler = SharedLocationHandler()
        handler.submit(outcome)
        assertEquals(outcome, handler.consume())
        assertNull(handler.consume())
        assertNull(handler.outcome.first())
    }

    @Test
    fun consume_withoutSubmit_returnsNull() = runTest {
        val handler = SharedLocationHandler()
        assertNull(handler.consume())
    }

    @Test
    fun submit_replacesPrevious() = runTest {
        val handler = SharedLocationHandler()
        handler.submit(outcome)
        val second = SharedLocationOutcome.Request(SharedLocationRequest(query = "Brandenburger Tor"))
        handler.submit(second)
        assertEquals(second, handler.consume())
    }

    /** An unreadable link is carried like a request: it is an outcome of its own (spec:
     * share/location-receiving — An unreadable shared map link is reported, never searched). */
    @Test
    fun submit_exposesUnusableLink() = runTest {
        val handler = SharedLocationHandler()
        handler.submit(SharedLocationOutcome.UnusableLink)
        assertEquals(SharedLocationOutcome.UnusableLink, handler.consume())
    }
}
