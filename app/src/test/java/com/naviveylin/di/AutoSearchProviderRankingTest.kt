package com.naviveylin.di

import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.search.SearchReference
import com.naviveylin.core.search.SearchResultRanker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the Android Auto search pipeline (spec: auto-search,
 * search-result-ranking): the same candidate set size and match-tier ranking as
 * the phone, so a perfect match the backend's own order pushed past the page
 * still reaches the car's result list.
 */
@RunWith(RobolectricTestRunner::class)
class AutoSearchProviderRankingTest {

    private lateinit var client: FakeOSMScoutClient
    private lateinit var provider: com.naviveylin.core.AutoSearchProvider

    @Before
    fun setUp() {
        client = FakeOSMScoutClient()
        provider = AutoServiceModule.provideAutoSearchProvider(client)
    }

    private fun exactEntry(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            matchedName = label
            matchedComponent = "location"
            locationMatchQuality = "match"
            this.lat = lat
            this.lon = lon
        }

    private fun prefixEntry(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            matchedName = label
            matchedComponent = "location"
            locationMatchQuality = "candidate"
            this.lat = lat
            this.lon = lon
        }

    @Test
    fun fetchesMoreCandidatesThanTheDisplayedLimit() {
        client.nextSearchResults = arrayOf(exactEntry("Waltrop", 51.6, 7.5))

        provider.searchLocations("Waltrop", SearchResultRanker.DISPLAY_LIMIT, null)

        assertEquals(listOf(SearchResultRanker.CANDIDATE_LIMIT), client.searchLimits)
    }

    @Test
    fun perfectMatchBeyondTheBackendPageIsFirst() {
        val close = (1..25).map { prefixEntry("Waltroper Straße $it", 51.5, 7.4) }
        client.nextSearchResults = (close + exactEntry("Waltrop", 52.0, 8.0)).toTypedArray()

        val results = provider.searchLocations(
            "Waltrop",
            SearchResultRanker.DISPLAY_LIMIT,
            SearchReference(51.5136, 7.4653)
        )

        assertEquals("Waltrop", results.first().label)
        assertEquals(SearchResultRanker.DISPLAY_LIMIT, results.size)
    }

    @Test
    fun searchRunsUnconstrainedLikeThePhoneRoutePicker() {
        client.nextSearchResults = arrayOf(exactEntry("Waltrop", 51.6, 7.5))

        provider.searchLocations("Waltrop", SearchResultRanker.DISPLAY_LIMIT, null)

        assertEquals(listOf(0L), client.searchAdminRegionHandles)
    }

    @Test
    fun rankedResultListIsCappedAtTheRequestedLimit() {
        client.nextSearchResults = (1..10).map { exactEntry("Waltrop $it", 51.5, 7.4) }.toTypedArray()

        val results = provider.searchLocations("Waltrop", 5, null)

        assertEquals(5, results.size)
        assertTrue(results.all { it.label.startsWith("Waltrop") })
    }
}
