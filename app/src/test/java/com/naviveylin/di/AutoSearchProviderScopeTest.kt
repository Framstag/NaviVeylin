package com.naviveylin.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.AutoSearchProvider
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import javax.inject.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies that the car search is scoped with the admin region containing the
 * car's position, through the shared rule (spec: `auto-search` — Search scoped
 * by the car position's admin region; Car region scope follows the car's
 * movement).
 *
 * Robolectric with the default sandbox config, because `FakeOSMScoutClient`
 * triggers `OSMScoutClient`'s static `System.loadLibrary` (see AGENTS.md).
 */
@RunWith(RobolectricTestRunner::class)
class AutoSearchProviderScopeTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var provider: AutoSearchProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        locationService = LocationService(context)
        val regionScope = CarSearchRegionSource(Provider { client }, locationService)
        provider = AutoServiceModule.provideAutoSearchProvider(Provider { client }, regionScope)
    }

    private fun fix(lat: Double, lon: Double, accuracy: Double = 10.0): GpsFix =
        GpsFix(
            lat = lat,
            lon = lon,
            accuracy = accuracy,
            speedKmH = Double.NaN,
            smoothedBearing = Double.NaN,
            markerBearing = Double.NaN,
            time = System.currentTimeMillis()
        )

    private fun entry(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            matchedName = label
            matchedComponent = "location"
            locationMatchQuality = "match"
            this.lat = lat
            this.lon = lon
        }

    @Test
    fun carSearchPassesTheResolvedRegionHandle() {
        client.nextAdminRegionHandle = 7L
        locationService.setGpsFixForTest(fix(51.5136, 7.4653))

        provider.searchLocations("Hilpert Theater", 20, null)

        // Not OSMScoutClient.NO_ADMIN_REGION (0): the search is scoped to the
        // region containing the car.
        assertEquals(listOf(7L), client.searchAdminRegionHandles)
        assertEquals(listOf(7L), client.adminRegionHandles)
    }

    @Test
    fun carSearchWithoutAFixRunsUnconstrainedAndStillReturnsResults() {
        client.nextSearchResults = arrayOf(entry("Hilpert Theater", 51.5136, 7.4653))
        locationService.setGpsFixForTest(null)

        val results = provider.searchLocations("Hilpert Theater", 20, null)

        assertEquals(listOf(0L), client.searchAdminRegionHandles)
        assertTrue(client.adminRegionHandles.isEmpty())
        assertEquals(listOf("Hilpert Theater"), results.map { it.label })
    }

    @Test
    fun carSearchWithACoarseFixRunsUnconstrained() {
        locationService.setGpsFixForTest(fix(51.5136, 7.4653, accuracy = 80.0))

        provider.searchLocations("Hilpert Theater", 20, null)

        assertEquals(listOf(0L), client.searchAdminRegionHandles)
        assertTrue(client.adminRegionHandles.isEmpty())
    }

    @Test
    fun carSearchReusesTheRegionWithinTheMovementThreshold() {
        client.nextAdminRegionHandle = 7L
        locationService.setGpsFixForTest(fix(51.5136, 7.4653))
        provider.searchLocations("Hilpert", 20, null)

        // ~200 m north — below the 500 m movement threshold: the region is reused.
        locationService.setGpsFixForTest(fix(51.5154, 7.4653))
        provider.searchLocations("Hilpert Theater", 20, null)

        assertEquals(listOf(7L, 7L), client.searchAdminRegionHandles)
        assertEquals(listOf(7L), client.adminRegionHandles)
    }

    @Test
    fun carSearchReresolvesTheRegionAfterSignificantMovement() {
        client.nextAdminRegionHandle = 7L
        locationService.setGpsFixForTest(fix(51.5136, 7.4653))
        provider.searchLocations("Hilpert", 20, null)

        client.nextAdminRegionHandle = 8L
        // ~1.1 km north — beyond the movement threshold.
        locationService.setGpsFixForTest(fix(51.5236, 7.4653))
        provider.searchLocations("Hilpert", 20, null)

        assertEquals(listOf(7L, 8L), client.adminRegionHandles)
        assertEquals(listOf(7L), client.releasedAdminRegionHandles)
        assertEquals(listOf(7L, 8L), client.searchAdminRegionHandles)
    }

    @Test
    fun carSearchReleasesTheRegionWhenTheFixBecomesUnusable() {
        client.nextAdminRegionHandle = 7L
        locationService.setGpsFixForTest(fix(51.5136, 7.4653))
        provider.searchLocations("Hilpert", 20, null)

        locationService.setGpsFixForTest(null)
        provider.searchLocations("Hilpert", 20, null)

        assertEquals(listOf(7L), client.releasedAdminRegionHandles)
        assertEquals(listOf(7L, 0L), client.searchAdminRegionHandles)
    }
}
