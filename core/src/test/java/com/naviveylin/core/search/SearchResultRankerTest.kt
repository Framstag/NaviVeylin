package com.naviveylin.core.search

import com.framstag.libosmscout.client.LocationEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the search result classification and ordering (spec:
 * search-result-ranking, location-search, search-free-text).
 */
class SearchResultRankerTest {

    private val waltrop = SearchQueryParser.criteriaOf("Waltrop")

    private fun entry(
        label: String = "Ort",
        matchedName: String? = label,
        matchedComponent: String? = "location",
        locationQuality: String? = "none",
        adminRegionQuality: String? = "none",
        postalAreaQuality: String? = "none",
        addressQuality: String? = "none",
        poiQuality: String? = "none",
        hasHouseNumber: Boolean = false,
        postalArea: String? = null,
        region: Array<String>? = null,
        adminRegionHierarchy: String? = null,
        lat: Double = 51.5,
        lon: Double = 7.4,
        objectFileOffset: Long = 0L,
        type: String = "object"
    ): LocationEntry = LocationEntry().apply {
        this.label = label
        this.type = type
        this.lat = lat
        this.lon = lon
        this.region = region
        this.postalArea = postalArea
        this.adminRegionHierarchy = adminRegionHierarchy
        this.objectFileOffset = objectFileOffset
        this.matchedName = matchedName
        this.matchedComponent = matchedComponent
        this.locationMatchQuality = locationQuality
        this.adminRegionMatchQuality = adminRegionQuality
        this.postalAreaMatchQuality = postalAreaQuality
        this.addressMatchQuality = addressQuality
        this.poiMatchQuality = poiQuality
        this.hasHouseNumber = hasHouseNumber
    }

    /** The town as libosmscout reports it: the admin region entry itself. */
    private fun townWaltrop(lat: Double = 51.6) = entry(
        label = "Waltrop",
        matchedName = "Waltrop",
        matchedComponent = "adminRegion",
        adminRegionQuality = "match",
        region = arrayOf("Waltrop"),
        lat = lat
    )

    /** A street in the town, matched only as a name prefix of the query. */
    private fun waltroperStrasse(lat: Double = 51.55) = entry(
        label = "Waltroper Straße",
        matchedName = "Waltroper Straße",
        locationQuality = "candidate",
        adminRegionQuality = "match",
        region = arrayOf("Waltrop"),
        lat = lat
    )

    // --- Perfect match classification ---

    @Test
    fun `city query makes the town itself a perfect match and the street a close one`() {
        assertTrue(SearchResultRanker.isPerfectMatch(townWaltrop(), waltrop))
        assertFalse(SearchResultRanker.isPerfectMatch(waltroperStrasse(), waltrop))
    }

    @Test
    fun `town outranks the nearer street because it is the exact answer`() {
        val street = waltroperStrasse(lat = 51.5001)
        val town = townWaltrop(lat = 51.9)

        val ranked = SearchResultRanker.rank(listOf(street, town), waltrop, null)

        assertEquals(listOf("Waltrop", "Waltroper Straße"), ranked.map { it.label })
    }

    @Test
    fun `house-level entry carrying an unnamed house number is not perfect`() {
        val streetQuery = SearchQueryParser.criteriaOf("Erbstollenstraße")
        val street = entry(label = "Erbstollenstraße", locationQuality = "match")
        val house = entry(
            label = "Erbstollenstraße 10",
            matchedName = "Erbstollenstraße",
            locationQuality = "match",
            hasHouseNumber = true,
            addressQuality = "none"
        )

        assertTrue(SearchResultRanker.isPerfectMatch(street, streetQuery))
        assertFalse(SearchResultRanker.isPerfectMatch(house, streetQuery))
        assertEquals(
            listOf("Erbstollenstraße", "Erbstollenstraße 10"),
            SearchResultRanker.rank(listOf(house, street), streetQuery, null).map { it.label }
        )
    }

    @Test
    fun `house-level entry is perfect when the query names the house number`() {
        val criteria = SearchQueryParser.criteriaOf("Erbstollenstraße 10")
        val house = entry(
            label = "Erbstollenstraße 10",
            matchedName = "Erbstollenstraße",
            locationQuality = "match",
            hasHouseNumber = true,
            addressQuality = "match"
        )

        assertTrue(SearchResultRanker.isPerfectMatch(house, criteria))
    }

    @Test
    fun `city and street named together are perfect`() {
        val criteria = SearchQueryParser.criteriaOf("Hauptstraße Dortmund")
        val streetInDortmund = entry(
            label = "Hauptstraße",
            locationQuality = "match",
            adminRegionQuality = "match",
            region = arrayOf("Dortmund")
        )

        assertTrue(SearchResultRanker.isPerfectMatch(streetInDortmund, criteria))
    }

    @Test
    fun `city named that the entry does not belong to is not perfect`() {
        val criteria = SearchQueryParser.criteriaOf("Hauptstraße Witten")
        val streetInDortmund = entry(
            label = "Hauptstraße",
            locationQuality = "match",
            adminRegionQuality = "none",
            region = arrayOf("Dortmund")
        )

        assertFalse(SearchResultRanker.isPerfectMatch(streetInDortmund, criteria))
    }

    @Test
    fun `named postal code that does not match the entry is not perfect`() {
        val criteria = SearchQueryParser.criteriaOf("Hauptstraße 12 58454 Witten")
        val entry = entry(
            label = "Hauptstraße 12",
            matchedName = "Hauptstraße",
            locationQuality = "match",
            adminRegionQuality = "match",
            addressQuality = "match",
            hasHouseNumber = true,
            postalArea = "44581",
            postalAreaQuality = "none",
            region = arrayOf("Witten")
        )

        assertFalse(SearchResultRanker.isPerfectMatch(entry, criteria))
    }

    @Test
    fun `postal area reported as matched but holding another code is not perfect`() {
        // The GPS search scope stamps region and postal quality "match" for
        // every entry inside it, so the value itself must agree with the query.
        val criteria = SearchQueryParser.criteriaOf("58454 Witten")
        val entry = entry(
            label = "Witten",
            matchedName = "Witten",
            matchedComponent = "adminRegion",
            adminRegionQuality = "match",
            postalArea = "44581",
            postalAreaQuality = "match",
            region = arrayOf("Witten")
        )

        assertFalse(SearchResultRanker.isPerfectMatch(entry, criteria))
    }

    @Test
    fun `postal area the query did not name does not prevent a perfect match`() {
        val criteria = SearchQueryParser.criteriaOf("Hauptstraße Dortmund")
        val street = entry(
            label = "Hauptstraße",
            locationQuality = "match",
            adminRegionQuality = "match",
            postalArea = "44581",
            postalAreaQuality = "match",
            region = arrayOf("Dortmund")
        )

        assertTrue(SearchResultRanker.isPerfectMatch(street, criteria))
    }

    @Test
    fun `admin region the query did not name does not prevent a perfect match`() {
        // The scope-derived region match is a lie about the query: it must not
        // influence the classification either way.
        val street = entry(
            label = "Hauptstraße",
            locationQuality = "match",
            adminRegionQuality = "match",
            region = arrayOf("Dortmund")
        )

        assertTrue(SearchResultRanker.isPerfectMatch(street, waltrop.copy(nameTokens = listOf("hauptstrasse"))))
    }

    @Test
    fun `transliterated spelling counts as the same name`() {
        val criteria = SearchQueryParser.criteriaOf("Erbstollenstrasse")
        val entry = entry(label = "Erbstollenstraße", locationQuality = "match")

        assertTrue(SearchResultRanker.isPerfectMatch(entry, criteria))
    }

    @Test
    fun `case difference counts as the same name`() {
        val criteria = SearchQueryParser.criteriaOf("ERBSTOLLENSTRASSE")
        val entry = entry(label = "Erbstollenstraße", locationQuality = "match")

        assertTrue(SearchResultRanker.isPerfectMatch(entry, criteria))
    }

    @Test
    fun `partially matched name is not perfect`() {
        val criteria = SearchQueryParser.criteriaOf("Waltrop")
        val entry = entry(
            label = "Waltrop",
            matchedName = "Waltrop",
            locationQuality = "candidate"
        )

        assertFalse(SearchResultRanker.isPerfectMatch(entry, criteria))
    }

    @Test
    fun `entry without the queried attribute is not perfect`() {
        val criteria = SearchQueryParser.criteriaOf("Hauptstraße")
        val entry = entry(label = "Nebenstraße", matchedName = "Nebenstraße", locationQuality = "none")

        assertFalse(SearchResultRanker.isPerfectMatch(entry, criteria))
    }

    @Test
    fun `free-text hit is never perfect even with an equal name`() {
        val entry = entry(
            label = "Waltrop",
            matchedName = "Waltrop",
            matchedComponent = "freeText",
            locationQuality = "none"
        )

        assertFalse(SearchResultRanker.isPerfectMatch(entry, waltrop))
    }

    @Test
    fun `coordinate result is the exact answer to a coordinate query`() {
        val criteria = SearchQueryParser.criteriaOf("51.5136, 7.4653")
        val entry = entry(label = "51.5136, 7.4653", matchedName = null, matchedComponent = "coordinate", type = "coordinate")

        assertTrue(SearchResultRanker.isPerfectMatch(entry, criteria))
    }

    @Test
    fun `missing per-attribute quality degrades to a close match`() {
        val entry = entry(
            label = "Waltrop",
            matchedName = "Waltrop",
            matchedComponent = "location",
            locationQuality = null,
            adminRegionQuality = null,
            postalAreaQuality = null,
            addressQuality = null,
            poiQuality = null
        )

        assertFalse(SearchResultRanker.isPerfectMatch(entry, waltrop))
    }

    // --- Ordering ---

    @Test
    fun `perfect matches are ordered by distance`() {
        val near = townWaltrop(lat = 51.5001)
        val far = townWaltrop(lat = 52.0).apply { label = "Waltrop Alt" }

        val ranked = SearchResultRanker.rank(
            listOf(far, near),
            SearchQueryParser.criteriaOf("Waltrop"),
            SearchReference(51.5, 7.4)
        )

        assertEquals(listOf("Waltrop", "Waltrop Alt"), ranked.map { it.label })
    }

    @Test
    fun `close matches are ordered by quality before distance`() {
        val candidateFar = waltroperStrasse(lat = 53.0).apply { label = "Waltroper Straße (weit)" }
        val unmatchedNear = entry(
            label = "Waltrop Weg",
            matchedName = "Waltrop Weg",
            locationQuality = "none",
            lat = 51.5001
        )

        val ranked = SearchResultRanker.rank(
            listOf(unmatchedNear, candidateFar),
            waltrop,
            SearchReference(51.5, 7.4)
        )

        assertEquals(
            listOf("Waltroper Straße (weit)", "Waltrop Weg"),
            ranked.map { it.label }
        )
    }

    @Test
    fun `equal quality falls back to distance`() {
        val near = waltroperStrasse(lat = 51.5001).apply { label = "A" }
        val far = waltroperStrasse(lat = 53.0).apply { label = "B" }

        val ranked = SearchResultRanker.rank(
            listOf(far, near),
            waltrop,
            SearchReference(51.5, 7.4)
        )

        assertEquals(listOf("A", "B"), ranked.map { it.label })
    }

    @Test
    fun `order is deterministic for equal entries`() {
        val alpha = waltroperStrasse().apply { label = "Alpha"; objectFileOffset = 2L }
        val beta = waltroperStrasse().apply { label = "Beta"; objectFileOffset = 1L }

        val first = SearchResultRanker.rank(listOf(beta, alpha), waltrop, null)
        val second = SearchResultRanker.rank(listOf(beta, alpha), waltrop, null)

        assertEquals(listOf("Alpha", "Beta"), first.map { it.label })
        assertEquals(first.map { it.label }, second.map { it.label })
    }

    @Test
    fun `without a reference point ordering falls back to quality and label`() {
        val town = townWaltrop()
        val street = waltroperStrasse()

        val ranked = SearchResultRanker.rank(listOf(street, town), waltrop, null)

        assertEquals(listOf("Waltrop", "Waltroper Straße"), ranked.map { it.label })
    }

    // --- Distance ---

    @Test
    fun `distance is measured from the reference point`() {
        val meters = SearchResultRanker.distanceMeters(
            entry(lat = 51.5, lon = 7.4),
            SearchReference(51.5, 7.4)
        )

        assertEquals(0.0, meters!!, 0.001)
    }

    @Test
    fun `distance is unknown without a reference point`() {
        assertNull(SearchResultRanker.distanceMeters(entry(lat = 51.5, lon = 7.4), null))
    }

    @Test
    fun `distance is unknown for unusable coordinates`() {
        assertNull(SearchResultRanker.distanceMeters(entry(lat = Double.NaN, lon = 7.4), SearchReference(51.5, 7.4)))
    }

    // --- Display limit ---

    @Test
    fun `display list is capped while the candidate set is larger`() {
        val many = (1..25).map { townWaltrop(lat = 51.0 + it * 0.01).apply { label = "Waltrop $it" } }

        val displayed = SearchResultRanker.rankForDisplay(many, waltrop, SearchReference(51.5, 7.4))

        assertEquals(SearchResultRanker.DISPLAY_LIMIT, displayed.size)
        assertTrue(SearchResultRanker.CANDIDATE_LIMIT >= 2 * SearchResultRanker.DISPLAY_LIMIT)
    }

    @Test
    fun `a perfect match beyond the first page still reaches the display list`() {
        val close = (1..25).map {
            waltroperStrasse(lat = 51.5 + it * 0.001).apply { label = "Waltroper Straße $it" }
        }
        val exact = townWaltrop(lat = 60.0)

        val displayed = SearchResultRanker.rankForDisplay(close + exact, waltrop, SearchReference(51.5, 7.4))

        assertEquals("Waltrop", displayed.first().label)
        assertEquals(SearchResultRanker.DISPLAY_LIMIT, displayed.size)
    }
}
