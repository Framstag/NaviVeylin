package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import android.location.Location
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies follow mode state transitions and re-center button visibility condition.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapCanvasViewModelFollowModeTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        locationService = LocationService(context)
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
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    @Test
    fun searchResultSelectionDisablesFollowModeAndCenters() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onToggleFollowMode(true)
        assertTrue(viewModel.uiState.first { it.followMode }.followMode)

        val entry = LocationEntry().apply {
            label = "Test Place"
            lat = 51.5136
            lon = 7.4653
        }
        viewModel.onSearchResultSelected(entry)

        // Follow mode must be deactivated before centering so GPS updates do
        // not re-center the viewport away from the selected result.
        assertFalse(viewModel.uiState.first { !it.followMode }.followMode)
        val state = viewModel.uiState.first { it.viewport.centerLat == 51.5136 }
        assertEquals(7.4653, state.viewport.centerLon, 1e-9)
    }

    @Test
    fun followModeDefaultsToFalse() {
        assertFalse(viewModel.uiState.value.followMode)
    }

    @Test
    fun onToggleFollowModeSetsFollowMode() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onToggleFollowMode(true)
        assertTrue(viewModel.uiState.first { it.followMode }.followMode)
    }

    @Test
    fun onToggleFollowModeFalseClearsFollowMode() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onToggleFollowMode(true)
        assertTrue(viewModel.uiState.first { it.followMode }.followMode)

        viewModel.onToggleFollowMode(false)
        assertFalse(viewModel.uiState.first { !it.followMode }.followMode)
    }

    @Test
    fun disengageFollowModeClearsFollowMode() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onToggleFollowMode(true)
        assertTrue(viewModel.uiState.first { it.followMode }.followMode)

        viewModel.disengageFollowMode()
        assertFalse(viewModel.uiState.first { !it.followMode }.followMode)
    }

    @Test
    fun onManualRotationDisengagesFollowModeAndNorthUp() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onToggleFollowMode(true)
        viewModel.onSetNavOrientation(true) // driving north-up
        assertTrue(viewModel.uiState.first { it.followMode }.followMode)
        assertTrue(viewModel.uiState.value.navNorthUp)

        viewModel.onManualRotation(0.5)

        val state = viewModel.uiState.value
        assertFalse("follow mode must be disengaged", state.followMode)
        assertTrue("drive suspension must be set", state.driveSuspended)
        assertFalse("driving always-north must be cleared", state.navNorthUp)
        assertEquals("rotation delta must be applied", 0.5, state.viewport.angle, 1e-9)
    }

    @Test
    fun onManualRotationAccumulatesAngle() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onManualRotation(0.3)
        viewModel.onManualRotation(0.2)
        assertEquals(0.5, viewModel.uiState.value.viewport.angle, 1e-9)
    }

    @Test
    fun reengagingFollowModeKeepsManualAngleAndNorthUpCleared() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onManualRotation(0.7)
        viewModel.onToggleFollowMode(true)
        val state = viewModel.uiState.value
        assertTrue(state.followMode)
        assertFalse("north-up must stay cleared after re-engage", state.freeFormNorthUp)
        assertEquals("manual angle must survive re-engage", 0.7, state.viewport.angle, 1e-9)
    }

    @Test
    fun recenterButtonVisibleWhenBrowseDriftedAndGpsGood() {
        // BROWSE with a drifted viewport and a good GPS fix: button visible.
        val visible = MapCanvasViewModel.shouldShowReCenterButton(
            MapMode.BROWSE, driveSuspended = false, browseDrifted = true
        ) && GpsFixQuality.GOOD != GpsFixQuality.NONE
        assertTrue("Re-center button should be visible when browse drifted", visible)
    }

    @Test
    fun recenterButtonHiddenWhenDriveActive() {
        // FREE_DRIVE active (no suspension): button hidden.
        val visible = MapCanvasViewModel.shouldShowReCenterButton(
            MapMode.FREE_DRIVE, driveSuspended = false, browseDrifted = false
        )
        assertFalse("Re-center button should be hidden when drive active", visible)
    }

    @Test
    fun recenterButtonHiddenWhenNoGpsFix() {
        // BROWSE drifted but no GPS fix: button hidden (GPS gate).
        val visible = MapCanvasViewModel.shouldShowReCenterButton(
            MapMode.BROWSE, driveSuspended = false, browseDrifted = true
        ) && GpsFixQuality.NONE != GpsFixQuality.NONE
        assertFalse("Re-center button should be hidden when no GPS fix", visible)
    }

    @Test
    fun recenterButtonHiddenWhenDriveActiveAndNoGps() {
        val visible = MapCanvasViewModel.shouldShowReCenterButton(
            MapMode.FREE_DRIVE, driveSuspended = false, browseDrifted = false
        ) && GpsFixQuality.NONE != GpsFixQuality.NONE
        assertFalse("Re-center button should be hidden", visible)
    }

    // --- GPS location state (Compose overlay input comes from renderer marker snapshot) ---

    @Test
    fun gpsLocationTracksRawFixInFollowMode() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onToggleFollowMode(true)
        assertTrue(viewModel.uiState.first { it.followMode }.followMode)

        val loc = Location("gps").apply {
            latitude = 51.5136
            longitude = 7.4653
            accuracy = 8f
            time = 1_000L
        }
        locationService.setLocationForTest(loc)

        val state = viewModel.uiState.first { it.gpsLocation != null }
        assertEquals("raw fix stored", 51.5136, state.gpsLocation!!.lat, 1e-9)
        assertEquals(7.4653, state.gpsLocation!!.lon, 1e-9)
    }

    @Test
    fun gpsLocationTracksRawFixWithoutFollowMode() = runTest(mainDispatcherRule.dispatcher) {
        val loc = Location("gps").apply {
            latitude = 51.5
            longitude = 7.4
            bearing = 45f
            accuracy = 5f
            time = 2_000L
        }
        locationService.setLocationForTest(loc)

        val state = viewModel.uiState.first { it.gpsLocation != null }
        assertFalse("follow mode must stay off", state.followMode)
        assertEquals(51.5, state.gpsLocation!!.lat, 1e-9)
        assertEquals(7.4, state.gpsLocation!!.lon, 1e-9)
    }

    @Test
    fun gpsLocationClearedOnNullLocation() = runTest(mainDispatcherRule.dispatcher) {
        val loc = Location("gps").apply {
            latitude = 51.5136
            longitude = 7.4653
            accuracy = 8f
            time = 3_000L
        }
        locationService.setLocationForTest(loc)
        viewModel.uiState.first { it.gpsLocation != null }

        locationService.setLocationForTest(null)

        val state = viewModel.uiState.first { it.gpsLocation == null }
        assertEquals(null, state.gpsLocation)
    }

    // --- Bearing smoothing (spec: gps-bearing-smoothing) ---

    /**
     * Enable follow mode with follow-direction (not north-up). The init
     * settings load overwrites the state with persisted values when the test
     * scheduler advances — flush it, then re-apply so the test state sticks.
     * FREE_DRIVE orientation is the driving orientation (navNorthUp).
     */
    private fun TestScope.enableFollowDirectionMode() {
        viewModel.onToggleFollowMode(true)
        viewModel.onSetNavOrientation(false)
        advanceUntilIdle()
        viewModel.onToggleFollowMode(true)
        viewModel.onSetNavOrientation(false)
    }

    private fun gpsFix(
        lat: Double, lon: Double,
        smoothed: Double, marker: Double,
        time: Long
    ) = GpsFix(
        lat = lat, lon = lon,
        accuracy = 8.0,
        speedKmH = Double.NaN,
        smoothedBearing = smoothed,
        markerBearing = marker,
        time = time
    )

    @Test
    fun smallSmoothedBearingChangeWithinDeadbandDoesNotRotateMap() = runTest(mainDispatcherRule.dispatcher) {
        enableFollowDirectionMode()
        viewModel.uiState.first { it.followMode && !it.navNorthUp }

        // First fix establishes the rendered angle (north-up).
        locationService.setGpsFixForTest(gpsFix(51.5136, 7.4653, smoothed = 0.0, marker = 0.0, time = 1_000L))
        viewModel.uiState.first { it.gpsLocation?.time == 1_000L }
        assertEquals(0.0, viewModel.uiState.value.viewport.angle, 1e-9)

        // 1° change — inside the 2° deadband → the map must not rotate.
        locationService.setGpsFixForTest(gpsFix(51.5136, 7.4653, smoothed = 1.0, marker = 1.0, time = 2_000L))
        viewModel.uiState.first { it.gpsLocation?.time == 2_000L }
        assertEquals("small bearing change must not rotate the map", 0.0, viewModel.uiState.value.viewport.angle, 1e-9)
    }

    @Test
    fun largeSmoothedBearingChangeRotatesMap() = runTest(mainDispatcherRule.dispatcher) {
        enableFollowDirectionMode()
        viewModel.uiState.first { it.followMode && !it.navNorthUp }

        locationService.setGpsFixForTest(gpsFix(51.5136, 7.4653, smoothed = 0.0, marker = 0.0, time = 1_000L))
        viewModel.uiState.first { it.gpsLocation?.time == 1_000L }
        // The follow-mode render throttle uses System.currentTimeMillis() (real
        // clock in Robolectric) — sleep past the 200 ms interval so the second
        // fix is not coalesced away.
        Thread.sleep(250)

        // 45° change — beyond the deadband → the map rotates toward it (45° < 90° rate clamp).
        locationService.setGpsFixForTest(gpsFix(51.5136, 7.4653, smoothed = 45.0, marker = 45.0, time = 2_000L))
        viewModel.uiState.first { it.gpsLocation?.time == 2_000L }
        assertEquals("map must rotate toward the smoothed bearing", -Math.toRadians(45.0), viewModel.uiState.value.viewport.angle, 1e-6)
    }

    @Test
    fun mapRotationFollowsSmoothedBearingNotMarkerBearing() = runTest(mainDispatcherRule.dispatcher) {
        enableFollowDirectionMode()
        viewModel.uiState.first { it.followMode && !it.navNorthUp }

        // markerBearing = 90 (east), smoothedBearing = 0 (north): the marker arrow
        // would point east while the map stays north-up — decoupled.
        locationService.setGpsFixForTest(gpsFix(51.5136, 7.4653, smoothed = 0.0, marker = 90.0, time = 1_000L))
        viewModel.uiState.first { it.gpsLocation?.time == 1_000L }

        locationService.setGpsFixForTest(gpsFix(51.5136, 7.4653, smoothed = 0.0, marker = 90.0, time = 2_000L))
        viewModel.uiState.first { it.gpsLocation?.time == 2_000L }
        assertEquals("map rotation must follow smoothed bearing, not marker bearing", 0.0, viewModel.uiState.value.viewport.angle, 1e-9)
    }

    @Test
    fun markerBearingUpdatesOnEveryFixWithoutRender() = runTest(mainDispatcherRule.dispatcher) {
        enableFollowDirectionMode()
        viewModel.uiState.first { it.followMode && !it.navNorthUp }

        // First fix: marker bearing 45°.
        locationService.setGpsFixForTest(gpsFix(51.5136, 7.4653, smoothed = 0.0, marker = 45.0, time = 1_000L))
        viewModel.uiState.first { it.gpsLocation?.time == 1_000L }
        assertEquals(45.0, viewModel.uiState.value.gpsMarkerBearing, 1e-9)

        // Second fix: same position (no render would be triggered — position < 5 m,
        // smoothed bearing unchanged) but marker bearing 90°. The marker must update
        // immediately, independent of the render cadence.
        locationService.setGpsFixForTest(gpsFix(51.5136, 7.4653, smoothed = 0.0, marker = 90.0, time = 2_000L))
        viewModel.uiState.first { it.gpsLocation?.time == 2_000L }
        assertEquals("marker bearing must update without a render", 90.0, viewModel.uiState.value.gpsMarkerBearing, 1e-9)
    }
}
