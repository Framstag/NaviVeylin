package com.naviveylin.data

import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.addressbook.ContactPostalAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies address resolution (spec: address-book-search — address
 * resolution): query building from address components, progressive fallback
 * when structured search finds nothing, and token-overlap ranking.
 * Runs under Robolectric because it instantiates [FakeOSMScoutClient]
 * (native stub classloader rule).
 */
@RunWith(RobolectricTestRunner::class)
class AddressBookResolverTest {

    private val address = ContactPostalAddress(
        street = "Main Street 1",
        postalCode = "10115",
        city = "Berlin",
        region = "",
        country = ""
    )

    @Test
    fun `form search used first with structured fields`() {
        val client = FakeOSMScoutClient()
        client.nextFormResults = arrayOf(entry("Main Street 1", 52.5, 13.4))
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(address)

        assertEquals(1, results.size)
        // (adminRegion=city, postalArea, location=street WITHOUT house number,
        // address=house number)
        assertEquals(listOf("Berlin", "10115", "Main Street", "1"), client.formSearchArgs[0])
        // Form search succeeded — no string search fallback.
        assertTrue(client.searchQueries.isEmpty())
    }

    @Test
    fun `form search retried without postal area when postal blocks`() {
        val client = FakeOSMScoutClient()
        client.nextFormResults = emptyArray()
        val resolver = AddressBookResolver(client)

        // First form call (with postal) empty -> retry without postal -> also
        // empty -> string search fallback.
        assertTrue(resolver.resolveAddress(address).isEmpty())

        assertEquals(2, client.formSearchArgs.size)
        assertEquals(listOf("Berlin", "10115", "Main Street", "1"), client.formSearchArgs[0])
        assertEquals(listOf("Berlin", "", "Main Street", "1"), client.formSearchArgs[1])
        // full (= street+city) -> street+city without house number -> street -> city
        assertEquals(
            listOf(
                "Main Street 1 Berlin",
                "Main Street Berlin",
                "Main Street 1",
                "Berlin"
            ),
            client.searchQueries
        )
    }

    @Test
    fun `form search skipped without city`() {
        val client = FakeOSMScoutClient()
        client.nextSearchResults = arrayOf(entry("Main Street 1", 1.0, 2.0))
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(
            ContactPostalAddress(street = "Main Street 1", postalCode = "10115")
        )

        assertEquals(1, results.size)
        assertTrue(client.formSearchArgs.isEmpty())
        assertEquals(listOf("Main Street 1"), client.searchQueries)
    }

    @Test
    fun `form search error falls back to string search`() {
        val client = FakeOSMScoutClient()
        client.formSearchError = RuntimeException("boom")
        client.nextSearchResults = arrayOf(entry("Main Street 1 Berlin", 1.0, 2.0))
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(address)

        assertEquals(1, results.size)
        assertEquals("Main Street 1 Berlin", results[0].label)
    }

    @Test
    fun `falls back through looser queries when nothing found`() {
        val client = FakeOSMScoutClient()
        client.nextFormResults = emptyArray()
        client.nextSearchResults = emptyArray()
        val resolver = AddressBookResolver(client)

        assertTrue(resolver.resolveAddress(address).isEmpty())

        // full (= street+city) -> street+city without house number -> street -> city
        assertEquals(
            listOf(
                "Main Street 1 Berlin",
                "Main Street Berlin",
                "Main Street 1",
                "Berlin"
            ),
            client.searchQueries
        )
    }

    @Test
    fun `search exception returns empty without crashing`() {
        val client = FakeOSMScoutClient()
        client.searchLocationsError = RuntimeException("boom")
        val resolver = AddressBookResolver(client)

        assertTrue(resolver.resolveAddress(address).isEmpty())
    }

    @Test
    fun `rank puts results matching street and city tokens first`() {
        val client = FakeOSMScoutClient()
        client.nextSearchResults = arrayOf(
            entry("Somewhere Else", 9.0, 9.0),
            entry("Main Street 1, Berlin", 52.5, 13.4),
            entry("Main Street 9, Berlin", 52.6, 13.5)
        )
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(address)

        assertEquals("Main Street 1, Berlin", results[0].label)
        assertEquals("Main Street 9, Berlin", results[1].label)
        assertEquals("Somewhere Else", results[2].label)
    }

    @Test
    fun `house-level address outranks street`() {
        val client = FakeOSMScoutClient()
        client.nextSearchResults = arrayOf(
            entry("Main Street", 52.5, 13.4).apply {
                objectType = "place"
                matchQuality = "candidate"
                region = arrayOf("Berlin")
            },
            entry("Main Street 1", 52.51, 13.41).apply {
                objectType = "address"
                matchQuality = "match"
                region = arrayOf("Berlin")
            }
        )
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(address)

        assertEquals("Main Street 1", results[0].label)
        assertEquals("Main Street", results[1].label)
    }

    @Test
    fun `wrong-city poi loses to right-city street`() {
        val client = FakeOSMScoutClient()
        client.nextSearchResults = arrayOf(
            entry("Main Street 1", 53.5, 10.0).apply {
                objectType = "poi"
                matchQuality = "match"
                region = arrayOf("Hamburg")
            },
            entry("Main Street", 52.5, 13.4).apply {
                objectType = "place"
                matchQuality = "candidate"
                region = arrayOf("Berlin")
            }
        )
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(address)

        assertEquals("Main Street", results[0].label)
        assertEquals("Main Street 1", results[1].label)
    }

    @Test
    fun `house number in label breaks street ties`() {
        val client = FakeOSMScoutClient()
        client.nextSearchResults = arrayOf(
            entry("Main Street 9", 52.6, 13.5).apply {
                objectType = "place"
                region = arrayOf("Berlin")
            },
            entry("Main Street 1", 52.5, 13.4).apply {
                objectType = "place"
                region = arrayOf("Berlin")
            }
        )
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(address)

        assertEquals("Main Street 1", results[0].label)
        assertEquals("Main Street 9", results[1].label)
    }

    @Test
    fun `postal code match outranks other postal area`() {
        val client = FakeOSMScoutClient()
        client.nextSearchResults = arrayOf(
            entry("Main Street", 52.6, 13.5).apply {
                objectType = "place"
                region = arrayOf("Berlin")
                postalArea = "20095"
            },
            entry("Main Street", 52.5, 13.4).apply {
                objectType = "place"
                region = arrayOf("Berlin")
                postalArea = "10115"
            }
        )
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(address)

        assertEquals("Main Street", results[0].label)
        assertEquals("10115", results[0].postalArea)
    }

    @Test
    fun `blank address resolves to nothing without search`() {
        val client = FakeOSMScoutClient()
        val resolver = AddressBookResolver(client)

        assertTrue(resolver.resolveAddress(ContactPostalAddress()).isEmpty())
        assertTrue(client.searchQueries.isEmpty())
    }

    private fun entry(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.lat = lat
            this.lon = lon
        }
}
