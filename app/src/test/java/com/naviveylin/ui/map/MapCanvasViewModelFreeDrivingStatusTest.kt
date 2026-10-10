package com.naviveylin.ui.map

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RoadInfo
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.FreeDrivingStatus
import com.naviveylin.core.FreeDrivingStatusProvider
import com.naviveylin.core.NavigationState
import com.naviveylin.core.stringResolver
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.navigation.DrivingModeProviderImpl
import com.naviveylin.service.NavigationNotificationContentFormatter
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Phone publication into the shared free-driving status and the label's data
 * source (spec: `current-road-info` — One free-driving road/speed status feeds every
 * surface and the notification; `navigation-ongoing-notification` — Free-driving
 * content comes from the free-driving status). The notification path only reads the
 * published status, so it triggers no road lookup of its own.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapCanvasViewModelFreeDrivingStatusTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var statusProvider: FreeDrivingStatusProvider
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        locationService = LocationService(context)
        statusProvider = FreeDrivingStatusProvider()
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = locationService,
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            drivingModeProvider = DrivingModeProviderImpl(),
            freeDrivingStatusProvider = statusProvider,
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private fun pushFix(lat: Double, lon: Double, time: Long, speedKmh: Float = 50f) {
        val loc = Location("gps").apply {
            latitude = lat
            longitude = lon
            speed = speedKmh / 3.6f
            accuracy = 8f
            bearing = 90f
            this.time = time
        }
        locationService.setLocationForTest(loc)
    }

    @Test
    fun noStatusBeforeFirstFix() = runTest(mainDispatcherRule.dispatcher) {
        advanceUntilIdle()
        assertNull("no fix yet -> no published status", viewModel.freeDrivingStatus.value)
        assertNull(statusProvider.status.value)
    }

    @Test
    fun publishesRoadAndSpeedOnFix() = runTest(mainDispatcherRule.dispatcher) {
        client.roadAt = RoadInfo("Hauptstrasse", "B 1", "highway_primary", 50.0)
        pushFix(51.5136, 7.4653, time = 1_000L, speedKmh = 54f)

        val status = viewModel.freeDrivingStatus.first { it?.hasRoad == true }
        assertEquals("B 1", status?.roadRef)
        assertEquals("Hauptstrasse", status?.roadName)
        assertEquals("the fresh fix's speed rides with the road", 54.0, status?.speedKmH ?: Double.NaN, 0.5)
        assertEquals(
            "the same status the notification reads",
            status,
            statusProvider.status.value
        )
    }

    @Test
    fun streetChangePublishesNewRoad() = runTest(mainDispatcherRule.dispatcher) {
        client.roadAt = RoadInfo("Hauptstrasse", "B 1", "highway_primary", 50.0)
        pushFix(51.5136, 7.4653, time = 1_000L)
        viewModel.freeDrivingStatus.first { it?.roadName == "Hauptstrasse" }

        // ~1.1 km away -> beyond the 50 m movement threshold -> re-resolve.
        client.roadAt = RoadInfo("Nebenstrasse", "", "highway_residential", Double.NaN)
        pushFix(51.52, 7.4653, time = 2_000L)

        val status = viewModel.freeDrivingStatus.first { it?.roadName == "Nebenstrasse" }
        assertEquals("Nebenstrasse", status?.roadName)
    }

    @Test
    fun clearsStatusOnFreeDriveExit() = runTest(mainDispatcherRule.dispatcher) {
        client.roadAt = RoadInfo("Hauptstrasse", "B 1", "highway_primary", 50.0)
        viewModel.onToggleFollowMode(true)
        pushFix(51.5136, 7.4653, time = 1_000L)
        viewModel.freeDrivingStatus.first { it?.hasRoad == true }

        viewModel.exitFreeDrive()
        advanceUntilIdle()

        assertNull(
            "leaving free driving drops the published road",
            viewModel.freeDrivingStatus.value
        )
    }

    @Test
    fun labelShowsPublishedRoad() = runTest(mainDispatcherRule.dispatcher) {
        // The label reads the shared status, not a private copy: a status published
        // by the phone (or the car) is what the label's source carries.
        statusProvider.publish(
            FreeDrivingStatusProvider.SURFACE_PHONE,
            RoadInfo("Hauptstrasse", "B 1", "highway_primary", 50.0),
            50.0
        )

        assertEquals("B 1 Hauptstrasse", viewModel.freeDrivingStatus.value?.roadText)
    }

    @Test
    fun notificationDoesNotTriggerRoadLookup() = runTest(mainDispatcherRule.dispatcher) {
        client.roadAt = RoadInfo("Hauptstrasse", "B 1", "highway_primary", 50.0)
        pushFix(51.5136, 7.4653, time = 1_000L)
        val status = viewModel.freeDrivingStatus.first { it?.hasRoad == true }
        val lookupsAfterFix = client.roadAtLookupCalls.size

        // Render the notification content from the very status the surface published
        // (spec: `current-road-info` — One lookup per fix, not one per consumer): the
        // notification must not resolve the road again.
        val content = NavigationNotificationContentFormatter.format(
            NavigationState(isNavigating = false),
            resolver = context.stringResolver(),
            freeDrivingActive = true,
            freeDrivingStatus = status as FreeDrivingStatus
        )

        assertTrue(content.contentText.contains("B 1 Hauptstrasse"))
        assertEquals(
            "the notification path performs no road lookup of its own",
            lookupsAfterFix,
            client.roadAtLookupCalls.size
        )
    }
}
