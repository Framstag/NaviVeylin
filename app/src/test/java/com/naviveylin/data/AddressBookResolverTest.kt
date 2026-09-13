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
 * resolution): component merging with the formatted address, the
 * evidence-driven candidate chain (a street-less result set is a miss, not a
 * hit), progressive fallback, and token-overlap ranking.
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
        // Full formatted address (street+house+postal+city) -> the components
        // without postal code -> street+house+postal -> street+postal ->
        // street+city -> street+house. Street-less queries are never issued
        // (city-only free-text results auto-resolve kilometers away — spec:
        // address-book-search "Wrong-location free-text result not selected").
        assertEquals(
            listOf(
                "Main Street 1 10115 Berlin",
                "Main Street 1 Berlin",
                "Main Street 1 10115",
                "Main Street 10115",
                "Main Street Berlin",
                "Main Street 1"
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
        // The full address query hits; the looser city-less variants would
        // follow only if it found nothing.
        assertEquals(listOf("Main Street 1 10115"), client.searchQueries)
    }

    @Test
    fun `city-less address never issues a bare street query`() {
        val client = FakeOSMScoutClient()
        client.nextSearchResults = emptyArray()
        val resolver = AddressBookResolver(client)

        assertTrue(
            resolver.resolveAddress(
                ContactPostalAddress(street = "Main Street 1", postalCode = "10115")
            ).isEmpty()
        )

        assertEquals(
            listOf("Main Street 1 10115", "Main Street 1", "Main Street 10115"),
            client.searchQueries
        )
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

        // The chain runs to exhaustion: full formatted address -> components
        // without postal code -> street+house+postal -> street+postal ->
        // street+city -> street+house.
        assertEquals(
            listOf(
                "Main Street 1 10115 Berlin",
                "Main Street 1 Berlin",
                "Main Street 1 10115",
                "Main Street 10115",
                "Main Street Berlin",
                "Main Street 1"
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

        // "Somewhere Else" carries no street-token evidence and is dropped by
        // the resolution gate (spec: address-book-search "Wrong-location
        // free-text result not selected").
        assertEquals(2, results.size)
        assertEquals("Main Street 1, Berlin", results[0].label)
        assertEquals("Main Street 9, Berlin", results[1].label)
    }

    @Test
    fun `postal code in street field is not taken as house number`() {
        val client = FakeOSMScoutClient()
        client.nextFormResults = arrayOf(entry("Erbstollenstraße 10", 51.45, 7.41).apply {
            objectType = "address"
            matchQuality = "match"
        })
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(
            ContactPostalAddress(
                street = "Erbstollenstraße 10 58454",
                postalCode = "58454",
                city = "Witten"
            )
        )

        assertEquals(1, results.size)
        // Street WITHOUT the postal code, house number 10, postal area set.
        assertEquals(listOf("Witten", "58454", "Erbstollenstraße", "10"), client.formSearchArgs[0])
    }

    @Test
    fun `house missing from index falls back to street level`() {
        val client = FakeOSMScoutClient()
        // The form search returns only the street-level candidate (native
        // partialMatch=true fallback): the house number is not in the index.
        client.nextFormResults = arrayOf(entry("Erbstollenstraße", 51.4501, 7.4113).apply {
            objectType = "place"
            matchQuality = "candidate"
            region = arrayOf("Witten")
        })
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(
            ContactPostalAddress(street = "Erbstollenstraße 10", postalCode = "58454", city = "Witten")
        )

        assertEquals(1, results.size)
        assertEquals("Erbstollenstraße", results[0].label)
    }

    @Test
    fun `city-only free-text noise is never auto-selected`() {
        val client = FakeOSMScoutClient()
        client.nextFormResults = emptyArray()
        client.nextSearchResults = arrayOf(
            entry("Witten-Bommern Bf", 51.4253, 7.3365).apply {
                objectType = "poi"
                matchQuality = "match"
            }
        )
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(
            ContactPostalAddress(street = "Erbstollenstraße 10", city = "Witten")
        )

        // No result carries the street token "erbstollenstrasse" — the bus
        // stop kilometers away must not resolve the contact.
        assertTrue(results.isEmpty())
    }

    @Test
    fun `admin region outranks poi for city-only ranking`() {
        val client = FakeOSMScoutClient()
        client.nextSearchResults = arrayOf(
            entry("Witten-Bommern Bf", 51.4253, 7.3365).apply {
                objectType = "poi"
                matchQuality = "match"
            },
            entry("Witten", 51.4431, 7.3414).apply {
                objectType = "boundary_administrative"
                matchQuality = "match"
                region = arrayOf("Witten")
            }
        )
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(
            ContactPostalAddress(street = "Witten X", postalCode = "58454", city = "Witten")
        )

        assertTrue(results.isNotEmpty())
        assertEquals("Witten", results[0].label)
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

    // --- evidence-driven candidate chain (spec: address-book-search
    // "Street-less result does not abort the candidate chain",
    // "Not found requires the whole chain to fail")

    @Test
    fun `street-less form result does not abort the chain`() {
        val client = FakeOSMScoutClient()
        // Native partialMatch=true: the form search returns an admin-region
        // fallback entry instead of nothing.
        client.nextFormResults = arrayOf(regionEntry("Witten"))
        client.searchResultsByQuery["Main Street 1 10115 Berlin"] = arrayOf(regionEntry("Berlin"))
        client.nextSearchResults = arrayOf(entry("Main Street 1", 52.5, 13.4))
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(address)

        assertEquals(1, results.size)
        assertEquals("Main Street 1", results[0].label)
        // Both form attempts were street-less, the first string query too —
        // the chain kept going and hit on the second string query.
        assertEquals(2, client.formSearchArgs.size)
        assertEquals(
            listOf("Main Street 1 10115 Berlin", "Main Street 1 Berlin"),
            client.searchQueries
        )
    }

    @Test
    fun `street-less string result does not abort the chain`() {
        val client = FakeOSMScoutClient()
        client.nextFormResults = emptyArray()
        client.searchResultsByQuery["Main Street 1 10115 Berlin"] = arrayOf(regionEntry("Berlin"))
        client.searchResultsByQuery["Main Street 1 Berlin"] = arrayOf(entry("Main Street 1", 52.5, 13.4))
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(address)

        assertEquals(1, results.size)
        assertEquals("Main Street 1", results[0].label)
        assertEquals(
            listOf("Main Street 1 10115 Berlin", "Main Street 1 Berlin"),
            client.searchQueries
        )
    }

    @Test
    fun `not found only after every candidate was street-less`() {
        val client = FakeOSMScoutClient()
        client.nextFormResults = arrayOf(regionEntry("Berlin"))
        // Every candidate answers with a street-less fallback entry.
        client.nextSearchResults = arrayOf(regionEntry("Berlin"))
        val resolver = AddressBookResolver(client)

        assertTrue(resolver.resolveAddress(address).isEmpty())

        // The whole chain ran — no candidate short-circuited it.
        assertEquals(
            listOf(
                "Main Street 1 10115 Berlin",
                "Main Street 1 Berlin",
                "Main Street 1 10115",
                "Main Street 10115",
                "Main Street Berlin",
                "Main Street 1"
            ),
            client.searchQueries
        )
    }

    @Test
    fun `formatted-only contact resolves through the form search`() {
        val client = FakeOSMScoutClient()
        client.nextFormResults = arrayOf(entry("Erbstollenstraße 10", 51.45, 7.41).apply {
            objectType = "address"
            matchQuality = "match"
        })
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(
            ContactPostalAddress(formatted = "Erbstollenstraße 10, 58454 Witten")
        )

        assertEquals(1, results.size)
        assertEquals("Erbstollenstraße 10", results[0].label)
        assertEquals(
            listOf("Witten", "58454", "Erbstollenstraße", "10"),
            client.formSearchArgs[0]
        )
    }

    @Test
    fun `formatted address completes a missing city for the form search`() {
        val client = FakeOSMScoutClient()
        client.nextFormResults = arrayOf(entry("Erbstollenstraße 10", 51.45, 7.41))
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(
            ContactPostalAddress(
                street = "Erbstollenstraße 10",
                formatted = "Erbstollenstraße 10, 58454 Witten"
            )
        )

        assertEquals(1, results.size)
        assertEquals(
            listOf("Witten", "58454", "Erbstollenstraße", "10"),
            client.formSearchArgs[0]
        )
    }

    @Test
    fun `formatted address is a candidate of its own`() {
        val client = FakeOSMScoutClient()
        client.nextFormResults = emptyArray()
        client.nextSearchResults = emptyArray()
        val resolver = AddressBookResolver(client)

        assertTrue(
            resolver.resolveAddress(
                ContactPostalAddress(
                    street = "Erbstollenstraße 10",
                    city = "Witten",
                    formatted = "Erbstollenstraße 10, 58454 Witten"
                )
            ).isEmpty()
        )

        // The raw provider text is tried verbatim, like the typed query in the
        // unified search dialog would be.
        assertTrue(client.searchQueries.contains("Erbstollenstraße 10, 58454 Witten"))
    }

    @Test
    fun `address without a street only tries the formatted text`() {
        val client = FakeOSMScoutClient()
        client.nextSearchResults = arrayOf(regionEntry("Witten"))
        val resolver = AddressBookResolver(client)

        assertTrue(
            resolver.resolveAddress(
                ContactPostalAddress(postalCode = "58454", city = "Witten")
            ).isEmpty()
        )

        // No form search without a street, and no city-only string query:
        // with no street and no formatted text there is nothing to search.
        assertTrue(client.formSearchArgs.isEmpty())
        assertTrue(client.searchQueries.isEmpty())
    }

    @Test
    fun `address without a street searches the formatted text when present`() {
        val client = FakeOSMScoutClient()
        client.nextSearchResults = arrayOf(regionEntry("Witten"))
        val resolver = AddressBookResolver(client)

        assertTrue(
            resolver.resolveAddress(
                ContactPostalAddress(
                    postalCode = "58454",
                    city = "Witten",
                    formatted = "58454 Witten"
                )
            ).isEmpty()
        )

        // Exactly one candidate — the provider text — and its street-less
        // result cannot resolve the contact.
        assertEquals(listOf("58454 Witten"), client.searchQueries)
    }

    @Test
    fun `house number absent from index resolves to the street with partial matching`() {
        val client = FakeOSMScoutClient()
        client.nextFormResults = arrayOf(
            entry("Erbstollenstraße 10", 51.45, 7.41).apply { objectType = "address" },
            entry("Erbstollenstraße", 51.4501, 7.4113).apply { objectType = "place" }
        )
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(
            ContactPostalAddress(street = "Erbstollenstraße 99", postalCode = "58454", city = "Witten")
        )

        // Both entries carry the street token; the house-number match of the
        // requested number is absent, so ranking decides.
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.label!!.contains("Erbstollenstraße") })
    }

    @Test
    fun `house-prefix street with ss spelling resolves like the form search`() {
        val client = FakeOSMScoutClient()
        // Native result label carries the index spelling (ß); the contact's
        // street field holds the sync-transliterated form (ss). The evidence
        // gate folds ß->ss on both sides, so the entry is accepted — the
        // Kotlin mirror of the native transliterate matcher (spec:
        // address-book-search — resolution from formatted address only).
        client.nextFormResults = arrayOf(entry("Erbstollenstraße 10", 51.45, 7.41).apply {
            objectType = "address"
            matchQuality = "match"
        })
        val resolver = AddressBookResolver(client)

        val results = resolver.resolveAddress(
            ContactPostalAddress(
                street = "10 Erbstollenstrasse",
                postalCode = "58454",
                city = "Witten"
            )
        )

        assertEquals(1, results.size)
        assertEquals("Erbstollenstraße 10", results[0].label)
        // Prefix house number stripped, postal/city kept: the form search
        // sees the street without the house and the house as the address
        // field (same call the ß-spelled twin in the other tests makes).
        assertEquals(
            listOf("Witten", "58454", "Erbstollenstrasse", "10"),
            client.formSearchArgs[0]
        )
        // Form search succeeded — no string search fallback.
        assertTrue(client.searchQueries.isEmpty())
    }

    private fun regionEntry(label: String): LocationEntry =
        entry(label, 51.44, 7.34).apply {
            objectType = "boundary_administrative"
            matchQuality = "match"
            region = arrayOf(label)
        }

    private fun entry(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.lat = lat
            this.lon = lon
        }
}
