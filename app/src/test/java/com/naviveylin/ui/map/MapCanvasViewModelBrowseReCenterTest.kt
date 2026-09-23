package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Verifies the derived BROWSE re-center rule (spec: map-modes — Browse
 * re-center): the button follows the measured offset between the map center and
 * the vehicle — not a remembered interaction — so a viewport that is not
 * centered on the vehicle (a persisted start viewport), vehicle movement, a pan
 * and a zoom all count, while rotation does not, GPS noise does not toggle it,
 * and a missing fix hides it. Re-centering centers the vehicle and hides the
 * button without applying the vehicle anchor presets.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapCanvasViewModelBrowseReCenterTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var settingsStorage: SettingsStorage
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        locationService = LocationService(context)
        settingsStorage = SettingsStorage(context)
        settingsStorage.ioDispatcher = mainDispatcherRule.dispatcher
        createViewModel()
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private fun createViewModel() {
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = settingsStorage,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = locationService,
            darkModeController = DarkModeController(settingsStorage),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel.setScreenSize(SCREEN_W, SCREEN_H)
        viewModel.updateMagnification(MAG)
    }

    private val dpi: Double
        get() = context.resources.displayMetrics.densityDpi.toDouble()

    /**
     * The viewport center that shows [VEHICLE_LAT]/[VEHICLE_LON] [dxPx] right and
     * [dyPx] down from the center of the canvas.
     */
    private fun centerForVehicleOffset(
        dxPx: Double,
        dyPx: Double,
        mag: Double = MAG,
        angle: Double = 0.0
    ): Pair<Double, Double> = ProjectionUtils
        .viewport(VEHICLE_LAT, VEHICLE_LON, mag, SCREEN_W, SCREEN_H, dpi, angle)
        .screenToGeoRotated(SCREEN_W / 2.0 - dxPx, SCREEN_H / 2.0 - dyPx)

    /** Move the committed viewport so the vehicle sits [dxPx]/[dyPx] off center. */
    private fun moveViewportBy(dxPx: Double, dyPx: Double, mag: Double = MAG) {
        val (lat, lon) = centerForVehicleOffset(dxPx, dyPx, mag)
        viewModel.updateMagnification(mag)
        viewModel.updateCenter(lat, lon)
    }

    private fun emitFix(lat: Double = VEHICLE_LAT, lon: Double = VEHICLE_LON) {
        locationService.setGpsFixForTest(
            GpsFix(
                lat = lat, lon = lon,
                accuracy = 5.0, speedKmH = 30.0,
                smoothedBearing = 45.0, markerBearing = 45.0,
                time = System.currentTimeMillis()
            )
        )
    }

    /** The committed offset of the vehicle from the canvas center, in screen px. */
    private fun committedOffsetPx(): Double {
        val vp = viewModel.uiState.value.viewport
        return MapCanvasViewModel.browseCenterOffsetPx(
            vp.centerLat, vp.centerLon, vp.magnification, vp.angle,
            SCREEN_W, SCREEN_H, dpi, VEHICLE_LAT, VEHICLE_LON
        )
    }

    /**
     * Wait out the appear dwell, then deliver a fix so the rule re-evaluates.
     * The fix repeats the position the case is about — in BROWSE the map does not
     * follow, so a test that moved the *vehicle* must repeat the moved position.
     */
    private fun awaitDwellAndRefresh(
        lat: Double = VEHICLE_LAT,
        lon: Double = VEHICLE_LON
    ) {
        Thread.sleep(MapCanvasViewModel.RECENTER_DWELL_MS + 150L)
        emitFix(lat, lon)
    }

    /**
     * Put the map center on the vehicle and deliver a fix, waited out past the
     * dwell — the baseline the movement/pan/zoom cases start from.
     */
    private fun startCenteredOnVehicle() {
        moveViewportBy(0.0, 0.0)
        emitFix()
        awaitDwellAndRefresh()
    }

    // --- projection helper (task 2.2) ---

    @Test
    fun offsetHelperMeasuresTheDistanceFromTheCanvasCenter() = runTest(mainDispatcherRule.dispatcher) {
        val (lat, lon) = centerForVehicleOffset(120.0, 0.0)
        val offset = MapCanvasViewModel.browseCenterOffsetPx(
            lat, lon, MAG, 0.0, SCREEN_W, SCREEN_H, dpi, VEHICLE_LAT, VEHICLE_LON
        )
        assertEquals("120 px to the right of the center", 120.0, offset, 1.0)

        // A rotation about the center preserves the distance (spec: map-modes —
        // Browse re-center, "Rotating the map does not change visibility").
        val (rLat, rLon) = centerForVehicleOffset(0.0, 200.0, angle = 0.0)
        val rotated = MapCanvasViewModel.browseCenterOffsetPx(
            rLat, rLon, MAG, ROTATION_RAD, SCREEN_W, SCREEN_H, dpi, VEHICLE_LAT, VEHICLE_LON
        )
        assertEquals("rotation preserves the offset", 200.0, rotated, 1.0)
    }

    @Test
    fun offsetHelperReportsUnknownInputsAsNaN() {
        assertTrue(
            "unknown position",
            MapCanvasViewModel.browseCenterOffsetPx(
                52.51, 13.40, MAG, 0.0, SCREEN_W, SCREEN_H, dpi, Double.NaN, 13.40
            ).isNaN()
        )
        assertTrue(
            "unknown canvas",
            MapCanvasViewModel.browseCenterOffsetPx(
                52.51, 13.40, MAG, 0.0, 0, 0, dpi, 52.52, 13.41
            ).isNaN()
        )
    }

    // --- the rule (spec scenarios) ---

    @Test
    fun offCenterViewportShowsTheButtonWithoutAnyMapInteraction() =
        runTest(mainDispatcherRule.dispatcher) {
            // Stands in for the persisted start viewport: the viewport is set
            // before the first fix ever arrives, and no pan/zoom happens at all.
            moveViewportBy(400.0, 0.0)
            advanceUntilIdle()
            assertFalse("no fix yet", viewModel.uiState.value.browseReCenterVisible)

            emitFix()
            advanceUntilIdle()
            assertFalse("dwell not elapsed yet", viewModel.uiState.value.browseReCenterVisible)

            awaitDwellAndRefresh()
            advanceUntilIdle()

            assertTrue(
                "a fix arriving on a viewport that is not centered on the vehicle must show the button",
                viewModel.uiState.value.browseReCenterVisible
            )
            assertEquals("mode stays BROWSE", MapMode.BROWSE, viewModel.mode)
        }

    @Test
    fun vehicleMovingAwayFromTheCenterShowsTheButton() = runTest(mainDispatcherRule.dispatcher) {
        // Start centered on the vehicle — the map does not follow in BROWSE, so
        // it stays put while the vehicle drives.
        startCenteredOnVehicle()
        advanceUntilIdle()
        assertFalse(
            "a centered viewport stays hidden past the dwell",
            viewModel.uiState.value.browseReCenterVisible
        )

        val (movedLat, movedLon) = ProjectionUtils
            .viewport(VEHICLE_LAT, VEHICLE_LON, MAG, SCREEN_W, SCREEN_H, dpi, 0.0)
            .screenToGeoRotated(SCREEN_W / 2.0 + 400.0, SCREEN_H / 2.0)
        emitFix(movedLat, movedLon)
        advanceUntilIdle()
        assertFalse("dwell not elapsed yet", viewModel.uiState.value.browseReCenterVisible)

        awaitDwellAndRefresh(movedLat, movedLon)
        advanceUntilIdle()

        assertTrue(
            "vehicle movement away from the center must show the button",
            viewModel.uiState.value.browseReCenterVisible
        )
    }

    @Test
    fun panningAwayFromTheVehicleShowsTheButton() = runTest(mainDispatcherRule.dispatcher) {
        startCenteredOnVehicle()
        advanceUntilIdle()
        assertFalse(
            "a centered viewport stays hidden past the dwell",
            viewModel.uiState.value.browseReCenterVisible
        )

        moveViewportBy(-500.0, 0.0)
        advanceUntilIdle()

        awaitDwellAndRefresh()
        advanceUntilIdle()

        assertTrue(
            "panning away from the vehicle must show the button",
            viewModel.uiState.value.browseReCenterVisible
        )
    }

    @Test
    fun zoomingAboutAPointOtherThanTheCenterShowsTheButton() =
        runTest(mainDispatcherRule.dispatcher) {
            startCenteredOnVehicle()
            advanceUntilIdle()
            assertFalse(
                "a centered viewport stays hidden past the dwell",
                viewModel.uiState.value.browseReCenterVisible
            )

            // A pinch zooms about the gesture centroid: the center moves.
            moveViewportBy(300.0, 150.0, mag = MAG + 1.0)
            advanceUntilIdle()

            awaitDwellAndRefresh()
            advanceUntilIdle()

            assertTrue(
                "a zoom that moves the center away from the vehicle must show the button",
                viewModel.uiState.value.browseReCenterVisible
            )
        }

    @Test
    fun rotatingTheMapDoesNotChangeVisibility() = runTest(mainDispatcherRule.dispatcher) {
        // Far off center: visible.
        moveViewportBy(400.0, 0.0)
        emitFix()
        advanceUntilIdle()
        awaitDwellAndRefresh()
        advanceUntilIdle()
        assertTrue("off-center viewport", viewModel.uiState.value.browseReCenterVisible)
        assertEquals("the vehicle starts 400 px off center", 400.0, committedOffsetPx(), 1.0)

        val offsetBefore = committedOffsetPx()
        viewModel.onManualRotation(ROTATION_RAD)
        advanceUntilIdle()

        assertTrue(
            "rotation about the center keeps the offset, so the button stays visible",
            viewModel.uiState.value.browseReCenterVisible
        )
        assertEquals(
            "the offset from the center is unchanged by the rotation",
            offsetBefore, committedOffsetPx(), 1.0
        )
    }

    @Test
    fun centeredVehicleHidesTheButton() = runTest(mainDispatcherRule.dispatcher) {
        startCenteredOnVehicle()
        advanceUntilIdle()

        assertFalse(
            "a centered viewport never shows the button",
            viewModel.uiState.value.browseReCenterVisible
        )
        assertTrue("the vehicle is centered", committedOffsetPx() < 1.0)
    }

    @Test
    fun noisyPositionAroundTheCenterDoesNotToggleTheButton() =
        runTest(mainDispatcherRule.dispatcher) {
            // High magnification is where the pixel offset is dominated by GPS
            // noise (one screen pixel is ~0.03 m at magnif 20), so the noise band
            // straddles the appear threshold. The dwell must keep it hidden.
            val noisyMag = 19.0
            moveViewportBy(0.0, 0.0, mag = noisyMag)
            advanceUntilIdle()

            val offsets = listOf(
                0.0,
                MapCanvasViewModel.RECENTER_SHOW_OFFSET_PX * 1.1,
                MapCanvasViewModel.RECENTER_SHOW_OFFSET_PX * 0.8,
                0.0,
                MapCanvasViewModel.RECENTER_SHOW_OFFSET_PX * 1.3,
                MapCanvasViewModel.RECENTER_SHOW_OFFSET_PX * 0.9
            )
            for (dx in offsets) {
                val (lat, lon) = centerForVehicleOffset(dx, 0.0, mag = noisyMag)
                emitFix(lat, lon)
                advanceUntilIdle()
                assertFalse(
                    "noise at dx=$dx px must not reveal the button",
                    viewModel.uiState.value.browseReCenterVisible
                )
            }

            // A sustained offset does reveal it — the dwell must not deadlock.
            val (sLat, sLon) = centerForVehicleOffset(200.0, 0.0, mag = noisyMag)
            emitFix(sLat, sLon)
            advanceUntilIdle()
            Thread.sleep(MapCanvasViewModel.RECENTER_DWELL_MS + 150L)
            emitFix(sLat, sLon)
            advanceUntilIdle()

            assertTrue(
                "a sustained off-center position must show the button",
                viewModel.uiState.value.browseReCenterVisible
            )
        }

    @Test
    fun hysteresisHoldsTheButtonUntilTheHideThreshold() = runTest(mainDispatcherRule.dispatcher) {
        moveViewportBy(400.0, 0.0)
        emitFix()
        advanceUntilIdle()
        awaitDwellAndRefresh()
        advanceUntilIdle()
        assertTrue("off-center viewport", viewModel.uiState.value.browseReCenterVisible)

        // Inside the hysteresis band (below the appear threshold, above the hide
        // threshold): an already visible button must not be restarted.
        moveViewportBy(MapCanvasViewModel.RECENTER_HIDE_OFFSET_PX + 4.0, 0.0)
        advanceUntilIdle()
        assertTrue(
            "inside the band a shown button stays visible",
            viewModel.uiState.value.browseReCenterVisible
        )

        // Below the hide threshold: hidden immediately, no dwell.
        moveViewportBy(MapCanvasViewModel.RECENTER_HIDE_OFFSET_PX / 2.0, 0.0)
        advanceUntilIdle()
        assertFalse(
            "below the hide threshold the button hides immediately",
            viewModel.uiState.value.browseReCenterVisible
        )
    }

    @Test
    fun noGpsFixHidesTheButton() = runTest(mainDispatcherRule.dispatcher) {
        moveViewportBy(400.0, 0.0)
        emitFix()
        advanceUntilIdle()
        awaitDwellAndRefresh()
        advanceUntilIdle()
        assertTrue("off-center viewport with a fix", viewModel.uiState.value.browseReCenterVisible)

        locationService.setGpsFixForTest(null)
        advanceUntilIdle()
        assertFalse(
            "without a fix the button must be hidden",
            viewModel.uiState.value.browseReCenterVisible
        )
        assertEquals("mode stays BROWSE", MapMode.BROWSE, viewModel.mode)
    }

    // --- the action and the anchor presets ---

    @Test
    fun reCenterCentersTheVehicleAndHidesTheButton() = runTest(mainDispatcherRule.dispatcher) {
        moveViewportBy(400.0, 0.0)
        emitFix()
        advanceUntilIdle()
        awaitDwellAndRefresh()
        advanceUntilIdle()
        assertTrue("off-center viewport", viewModel.uiState.value.browseReCenterVisible)

        viewModel.recenterInBrowse()
        advanceUntilIdle()

        assertTrue("the vehicle must be centered", committedOffsetPx() < 1.0)
        assertEquals("mode stays BROWSE", MapMode.BROWSE, viewModel.mode)
        assertFalse(
            "re-centering hides the button",
            viewModel.uiState.value.browseReCenterVisible
        )
    }

    @Test
    fun reCenterIgnoresANonCenterAnchorPreset() = runTest(mainDispatcherRule.dispatcher) {
        val current = settingsStorage.load()
        settingsStorage.save(
            current.copy(freeDrivingAnchorId = VehicleAnchorPosition.BOTTOM_CENTER.id)
        )
        createViewModel()
        advanceUntilIdle()

        moveViewportBy(400.0, 0.0)
        emitFix()
        advanceUntilIdle()
        awaitDwellAndRefresh()
        advanceUntilIdle()
        assertTrue("off-center viewport", viewModel.uiState.value.browseReCenterVisible)

        viewModel.recenterInBrowse()
        advanceUntilIdle()

        val vp = viewModel.uiState.value.viewport
        val (x, y) = ProjectionUtils
            .viewport(vp.centerLat, vp.centerLon, vp.magnification, SCREEN_W, SCREEN_H, dpi, vp.angle)
            .geoToScreenRotated(VEHICLE_LAT, VEHICLE_LON)
        assertEquals("vehicle at the canvas center x", SCREEN_W / 2.0, x, 1.0)
        assertEquals(
            "vehicle at the canvas center y — the BOTTOM_CENTER preset is not applied",
            SCREEN_H / 2.0, y, 1.0
        )
        assertFalse(
            "an off-center anchor target would re-show the button",
            viewModel.uiState.value.browseReCenterVisible
        )
    }

    // --- driving modes are untouched by the browse rule ---

    @Test
    fun freeDriveSuspensionStillDrivesTheButton() = runTest(mainDispatcherRule.dispatcher) {
        emitFix()
        advanceUntilIdle()
        viewModel.enterFreeDrive()
        advanceUntilIdle()

        assertFalse(
            "following free drive hides the button",
            MapCanvasViewModel.shouldShowReCenterButton(
                viewModel.mode,
                viewModel.uiState.value.driveSuspended,
                viewModel.uiState.value.browseReCenterVisible
            )
        )

        viewModel.updateMagnification(6.0)
        advanceUntilIdle()

        assertTrue(
            "a zoom in free drive suspends the preset and shows the button",
            MapCanvasViewModel.shouldShowReCenterButton(
                viewModel.mode,
                viewModel.uiState.value.driveSuspended,
                viewModel.uiState.value.browseReCenterVisible
            )
        )
    }

    private companion object {
        const val SCREEN_W = 1080
        const val SCREEN_H = 1920
        const val MAG = 15.0
        const val ROTATION_RAD = 0.7
        const val VEHICLE_LAT = 52.51
        const val VEHICLE_LON = 13.40
    }
}
