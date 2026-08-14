package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies follow mode state transitions and re-center button visibility condition.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelFollowModeTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            locationService = LocationService(context),
            darkModeController = DarkModeController(SettingsStorage(context)),
            context = context
        )
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
        Dispatchers.resetMain()
    }

    @Test
    fun searchResultSelectionDisablesFollowModeAndCenters() = runTest {
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
    fun onToggleFollowModeSetsFollowMode() = runTest {
        viewModel.onToggleFollowMode(true)
        assertTrue(viewModel.uiState.first { it.followMode }.followMode)
    }

    @Test
    fun onToggleFollowModeFalseClearsFollowMode() = runTest {
        viewModel.onToggleFollowMode(true)
        assertTrue(viewModel.uiState.first { it.followMode }.followMode)

        viewModel.onToggleFollowMode(false)
        assertFalse(viewModel.uiState.first { !it.followMode }.followMode)
    }

    @Test
    fun disengageFollowModeClearsFollowMode() = runTest {
        viewModel.onToggleFollowMode(true)
        assertTrue(viewModel.uiState.first { it.followMode }.followMode)

        viewModel.disengageFollowMode()
        assertFalse(viewModel.uiState.first { !it.followMode }.followMode)
    }

    @Test
    fun onManualRotationDisengagesFollowModeAndNorthUp() = runTest {
        viewModel.onToggleFollowMode(true)
        assertTrue(viewModel.uiState.first { it.followMode }.followMode)
        assertTrue(viewModel.uiState.value.freeFormNorthUp)

        viewModel.onManualRotation(0.5)

        val state = viewModel.uiState.value
        assertFalse("follow mode must be disengaged", state.followMode)
        assertFalse("always-north must be cleared", state.freeFormNorthUp)
        assertEquals("rotation delta must be applied", 0.5, state.viewport.angle, 1e-9)
    }

    @Test
    fun onManualRotationAccumulatesAngle() = runTest {
        viewModel.onManualRotation(0.3)
        viewModel.onManualRotation(0.2)
        assertEquals(0.5, viewModel.uiState.value.viewport.angle, 1e-9)
    }

    @Test
    fun reengagingFollowModeKeepsManualAngleAndNorthUpCleared() = runTest {
        viewModel.onManualRotation(0.7)
        viewModel.onToggleFollowMode(true)
        val state = viewModel.uiState.value
        assertTrue(state.followMode)
        assertFalse("north-up must stay cleared after re-engage", state.freeFormNorthUp)
        assertEquals("manual angle must survive re-engage", 0.7, state.viewport.angle, 1e-9)
    }

    @Test
    fun recenterButtonVisibleWhenFollowOffAndGpsGood() {
        // Simulate state: followMode=false, gpsFixQuality=GOOD
        val state = MapCanvasUiState(
            followMode = false,
            gpsFixQuality = GpsFixQuality.GOOD
        )
        val buttonVisible = !state.followMode && state.gpsFixQuality != GpsFixQuality.NONE
        assertTrue("Re-center button should be visible", buttonVisible)
    }

    @Test
    fun recenterButtonHiddenWhenFollowActive() {
        val state = MapCanvasUiState(
            followMode = true,
            gpsFixQuality = GpsFixQuality.GOOD
        )
        val buttonVisible = !state.followMode && state.gpsFixQuality != GpsFixQuality.NONE
        assertFalse("Re-center button should be hidden when follow mode active", buttonVisible)
    }

    @Test
    fun recenterButtonHiddenWhenNoGpsFix() {
        val state = MapCanvasUiState(
            followMode = false,
            gpsFixQuality = GpsFixQuality.NONE
        )
        val buttonVisible = !state.followMode && state.gpsFixQuality != GpsFixQuality.NONE
        assertFalse("Re-center button should be hidden when no GPS fix", buttonVisible)
    }

    @Test
    fun recenterButtonHiddenWhenFollowActiveAndNoGps() {
        val state = MapCanvasUiState(
            followMode = true,
            gpsFixQuality = GpsFixQuality.NONE
        )
        val buttonVisible = !state.followMode && state.gpsFixQuality != GpsFixQuality.NONE
        assertFalse("Re-center button should be hidden", buttonVisible)
    }
}
