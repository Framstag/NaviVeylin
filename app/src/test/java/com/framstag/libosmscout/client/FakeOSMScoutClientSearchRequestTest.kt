package com.framstag.libosmscout.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The search request lifecycle as `FakeOSMScoutClient` mirrors it (change `fix-search-cancellation`,
 * spec: `search-request-lifecycle`).
 *
 * The double is what the app's own search cases observe, so these cases pin the double itself: a
 * fresh generation per request, one live generation per slot, and a cancellation that reports
 * whether the request it named was still running. `search_breaker.h` is the normative home of the
 * rule; the host case `SearchBreakerTest` pins the native side of it.
 *
 * Robolectric with the default sandbox: instantiating the double triggers the guarded
 * `System.loadLibrary` that `app/src/test/jniLibs` stubs (`guidelines/Build.md` §6).
 */
@RunWith(RobolectricTestRunner::class)
class FakeOSMScoutClientSearchRequestTest {

    private val client = FakeOSMScoutClient()

    /** Spec: `search-request-lifecycle` — one search slot per surface, a request carries a generation. */
    @Test
    fun beginReturnsAFreshGenerationPerSlot() {
        val placesFirst = client.beginSearch("places")
        val placesSecond = client.beginSearch("places")
        val car = client.beginSearch("car")

        assertEquals(listOf("places", "places", "car"), client.begunSearchSlots)
        assertTrue("a request must carry a generation", placesFirst > 0L)
        assertTrue("a newer request of the slot gets a new generation", placesSecond != placesFirst)
        assertEquals("the slot's current generation is the newest one", placesSecond, client.liveSearchGenerations["places"])
        assertEquals(car, client.liveSearchGenerations["car"])
    }

    /** Spec: `search-request-lifecycle` — supersede stays inside the slot. */
    @Test
    fun oneSlotsRequestDoesNotTouchAnotherSlots() {
        val places = client.beginSearch("places")
        val car = client.beginSearch("car")

        assertTrue(client.cancelSearch(places))
        assertTrue("the car request must survive", client.liveSearchGenerations.values.contains(car))
    }

    /** Spec: `osmscout-jni` — a cancellation names a generation and reports whether it was live. */
    @Test
    fun cancelRecordsTheGenerationAndReportsLiveness() {
        val superseded = client.beginSearch("places")
        val current = client.beginSearch("places")

        assertFalse("a replaced generation aborts nothing", client.cancelSearch(superseded))
        assertTrue("the current generation is aborted", client.cancelSearch(current))

        assertEquals(listOf(superseded, current), client.cancelledSearchGenerations)
        assertFalse("a cancelled generation is no longer live", client.liveSearchGenerations.values.contains(current))
    }

    /** Spec: `search-request-lifecycle` — an aborted search publishes no result (the fail-closed rule). */
    @Test
    fun searchWithAnAbortedGenerationReturnsNoEntries() {
        client.nextSearchResults = arrayOf(LocationEntry().apply { label = "Bochum"; lat = 51.48; lon = 7.21 })

        // The second begin supersedes the first, so the first is the aborted request.
        val aborted = client.beginSearch("places")
        val live = client.beginSearch("places")

        assertEquals("a live request returns its results", 1, client.searchLocations("Bochum", 20, 0L, live)?.size)
        assertEquals("the aborted request publishes nothing", 0, client.searchLocations("Bochum", 20, 0L, aborted)?.size)
        assertEquals("both calls are recorded in call order", listOf(live, aborted), client.searchGenerations)
    }
}
