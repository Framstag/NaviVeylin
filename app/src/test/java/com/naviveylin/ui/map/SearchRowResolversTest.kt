package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The per-row address lookup of the result rows (change `search-result-row-content`, design D8;
 * spec: `location-search` — Result item display).
 *
 * No `@Config`: the fake extends `OSMScoutClient`, so this class stays in the default Robolectric
 * sandbox (AGENTS.md).
 */
@RunWith(RobolectricTestRunner::class)
class SearchRowResolversTest {

    private fun poi(label: String, matchedComponent: String = "poi"): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.matchedComponent = matchedComponent
            lat = 51.5136
            lon = 7.4653
        }

    @Test
    fun `A name hit shows the address resolved at its position`() = runTest {
        val client = FakeOSMScoutClient()
        client.addressAt = arrayOf("Hauptstraße", "20", "Dortmund", "44139")

        assertEquals("Hauptstraße 20", resolveRowAddress(client, poi("Aldi")))
        assertEquals(listOf(51.5136 to 7.4653), client.addressAtRequests)
    }

    @Test
    fun `A name hit without an indexed address stays readable`() = runTest {
        val client = FakeOSMScoutClient()
        client.addressAt = null

        assertNull(resolveRowAddress(client, poi("Aldi")))
        // The lookup did happen; it just found nothing, which the row renders as no address.
        assertEquals(1, client.addressAtRequests.size)
    }

    @Test
    fun `Address and street hits never ask for their own address`() = runTest {
        val client = FakeOSMScoutClient()
        client.addressAt = arrayOf("Rosenweg", "20", "Dortmund", "44139")

        assertNull(resolveRowAddress(client, poi("Rosenweg 20", matchedComponent = "address")))
        assertNull(resolveRowAddress(client, poi("Rosenweg", matchedComponent = "location")))
        assertNull(resolveRowAddress(client, poi("Dortmund", matchedComponent = "adminRegion")))
        assertNull(resolveRowAddress(client, poi("51.5136, 7.4653", matchedComponent = "coordinate")))
        // The predicate is what keeps the lookup count at zero: the label already carries the
        // street and the house number, and a lookup would only risk contradicting it.
        assertTrue(client.addressAtRequests.isEmpty())
    }

    @Test
    fun `A failing lookup is an absent address, not a broken row`() = runTest {
        val client = FakeOSMScoutClient()
        client.addressAtFailure = IllegalStateException("no database open")

        assertNull(resolveRowAddress(client, poi("Aldi")))
        assertEquals(1, client.addressAtRequests.size)
    }

    @Test
    fun `The cache answers a repeated position without a second lookup`() {
        val cache = RowAddressCache()
        assertEquals(false, cache.contains(51.5136, 7.4653))
        cache.put(51.5136, 7.4653, "Hauptstraße 20")
        assertEquals(true, cache.contains(51.5136, 7.4653))
        assertEquals("Hauptstraße 20", cache.get(51.5136, 7.4653))

        // A position the index knows nothing about is remembered as such, so a recycled row does
        // not ask again.
        cache.put(50.0, 8.0, null)
        assertEquals(true, cache.contains(50.0, 8.0))
        assertNull(cache.get(50.0, 8.0))
        assertNull(cache.get(49.0, 9.0))
    }
}
