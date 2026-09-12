package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
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
 * Verifies FREE_DRIVE suspension (spec: map-modes — drive suspension and
 * reset): any manual pan/zoom/rotate suspends the drive preset and sets
 * uiState.driveSuspended; resetDrivePreset restores the standard drive values
 * and clears it; the re-center button predicate follows the per-mode matrix.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelAutoZoomPauseTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
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
    fun userZoomInFreeDriveSuspendsDrivePreset() {
        viewModel.enterFreeDrive()
        assertFalse("drive starts active", viewModel.uiState.value.driveSuspended)

        viewModel.updateMagnification(6.0)

        assertTrue("user zoom must suspend the drive preset", viewModel.uiState.value.driveSuspended)
    }

    @Test
    fun buttonZoomSuspendsDrivePreset() {
        // The on-map zoom buttons (standard and routing views) go through
        // zoomIn/zoomOut, which route into updateMagnification — the same
        // suspension path as pinch zoom.
        viewModel.enterFreeDrive()
        assertFalse("drive starts active", viewModel.uiState.value.driveSuspended)

        viewModel.zoomIn()

        assertTrue("button zoom must suspend the drive preset", viewModel.uiState.value.driveSuspended)
    }

    @Test
    fun manualPanSuspendsDrivePreset() {
        viewModel.enterFreeDrive()

        viewModel.disengageFollowMode()

        assertTrue("manual pan must suspend the drive preset", viewModel.uiState.value.driveSuspended)
        assertFalse("follow must disengage on pan", viewModel.uiState.value.followMode)
    }

    @Test
    fun manualRotateSuspendsDrivePreset() {
        viewModel.enterFreeDrive()

        viewModel.onManualRotation(0.5)

        assertTrue("manual rotate must suspend the drive preset", viewModel.uiState.value.driveSuspended)
    }

    @Test
    fun zoomInBrowseMarksDriftedNotSuspended() {
        // In BROWSE a manual zoom drifts the viewport from GPS — it must NOT
        // set driveSuspended (which would flip the mode to FREE_DRIVE).
        viewModel.zoomIn()

        assertFalse("browse zoom must not suspend the drive preset", viewModel.uiState.value.driveSuspended)
        assertTrue("browse zoom must mark the viewport drifted", viewModel.uiState.value.browseDrifted)
        assertEquals("mode stays BROWSE", MapMode.BROWSE, viewModel.mode)
    }

    @Test
    fun resetDrivePresetRestoresStandardValues() {
        viewModel.enterFreeDrive()
        viewModel.updateMagnification(6.0)
        assertTrue(viewModel.uiState.value.driveSuspended)

        viewModel.resetDrivePreset()

        val state = viewModel.uiState.value
        assertFalse("reset must clear suspension", state.driveSuspended)
        assertTrue("reset must re-enable follow", state.followMode)
        assertTrue("reset must re-enable auto-zoom", state.autoZoomEnabled)
        assertFalse("reset must restore heading-up", state.navNorthUp)
    }

    @Test
    fun exitFreeDriveClearsSuspensionAndReturnsToBrowse() {
        viewModel.enterFreeDrive()
        viewModel.updateMagnification(6.0)
        assertTrue(viewModel.uiState.value.driveSuspended)

        viewModel.exitFreeDrive()

        val state = viewModel.uiState.value
        assertFalse(state.driveSuspended)
        assertFalse(state.followMode)
        assertTrue("browse is north-up", state.freeFormNorthUp)
        assertEquals("mode returns to BROWSE", MapMode.BROWSE, viewModel.mode)
    }

    @Test
    fun reCenterButtonPredicateMatrix() {
        val p = MapCanvasViewModel::shouldShowReCenterButton
        // FREE_DRIVE active: no button.
        assertFalse(p(MapMode.FREE_DRIVE, false, false))
        // FREE_DRIVE suspended: button.
        assertTrue(p(MapMode.FREE_DRIVE, true, false))
        // BROWSE not drifted: no button (clean start screen).
        assertFalse(p(MapMode.BROWSE, false, false))
        // BROWSE drifted: button.
        assertTrue(p(MapMode.BROWSE, false, true))
        // NAVIGATION with auto-zoom suspended: button (existing behavior).
        assertTrue(p(MapMode.NAVIGATION, true, false))
        // NAVIGATION active: no button.
        assertFalse(p(MapMode.NAVIGATION, false, false))
    }
}
