package com.naviveylin.ui.map

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.PoiEntry
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
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
import org.robolectric.Shadows.shadowOf

/**
 * Verifies the area-favorite fit end to end through the ViewModel (spec:
 * fav-auto-zoom — Bounding box zoom calculation, Whole-level rounding does not
 * crop the object, The area-favorites floor bounds the fit).
 *
 * The off-centre premise matters here: `onFavoriteSelected` centers the camera on
 * the **favorite coordinate** (`updateCenter(fav.lat, fav.lon)`), while
 * `computeAreaZoom` solves "fits in 80 % when the bbox midpoint is the camera
 * center" — so a favorite at a corner of its object's bbox was cropped even at an
 * exact fit. Each case asserts that premise against the unverified value first,
 * so it cannot pass vacuously.
 *
 * Deliberately a separate class from [MapCanvasViewModelRouteFitTest]: that file
 * is the regression proof for the `verifiedAreaFit` extraction (task 2.2) and has
 * to stay byte-identical, so the new fit-behaviour cases live here. Harness,
 * canvas and DPI mirror it (400x800 at Robolectric's mdpi 160, real [MapRenderer]
 * on the test scheduler). Robolectric with the default sandbox (AGENTS.md
 * classloader rule for the stub .so).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AreaFitViewModelTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var locationService: LocationService

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val canvasWidth = 400
    private val canvasHeight = 800

    /** Robolectric mdpi — the DPI the view model projects with. */
    private val dpi = 160.0

    /** The documented area-favorites floor (`MapCanvasViewModel.MIN_AREA_ZOOM`, private). */
    private val areaFloor = 14.0

    private val favLat = 51.5136
    private val favLon = 7.4653

    /** Current position used by the POI cases. */
    private val currentLat = 51.5136
    private val currentLon = 7.4653

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
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
        viewModel.setScreenSize(canvasWidth, canvasHeight)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    /** An object bbox spanning [latMeters] x [lonMeters] metres, anchored at its south-west corner. */
    private fun bboxFromSouthWest(latMeters: Double, lonMeters: Double): DoubleArray {
        val dLat = latMeters / METERS_PER_DEG_LAT
        val dLon = lonMeters / (METERS_PER_DEG_LAT * Math.cos(Math.toRadians(favLat)))
        return doubleArrayOf(favLat, favLat + dLat, favLon, favLon + dLon)
    }

    @Test
    fun areaFavoriteShowsTheWholeObjectWhenTheFavoriteIsNotItsCentre() = runTest(mainDispatcherRule.dispatcher) {
        // 195 m object, favorite on its south-west corner: the camera is at the
        // corner, not the bbox midpoint.
        val bbox = bboxFromSouthWest(195.0, 195.0)
        client.objectBoundingBox = bbox
        val unverified = MapCanvasViewModel.computeAreaZoom(bbox, canvasWidth, canvasHeight, areaFloor, dpi)
        assertFalse(
            "premise: the unverified fit crops the object around the favorite",
            fitsVisibleArea(bbox, favLat, favLon, unverified, canvasWidth, canvasHeight, dpi)
        )

        viewModel.onFavoriteSelected(FavoriteLocation("Home", favLat, favLon))
        advanceUntilIdle()

        val mag = viewModel.uiState.value.viewport.magnification
        assertTrue(
            "the whole object must be visible at the applied magnification",
            fitsVisibleArea(bbox, favLat, favLon, mag, canvasWidth, canvasHeight, dpi)
        )
        assertTrue("the fit may only step out, never in", mag < unverified)
        assertEquals("the camera stays on the favorite", favLat, viewModel.uiState.value.viewport.centerLat, 1e-9)
        assertEquals(favLon, viewModel.uiState.value.viewport.centerLon, 1e-9)
    }

    @Test
    fun areaFavoriteIsBoundedByTheAreaFavoritesFloor() = runTest(mainDispatcherRule.dispatcher) {
        // A 2 km object cannot be shown whole at the area-favorites floor: the floor
        // is where stepping stops, so the applied magnification is the floor.
        val bbox = bboxFromSouthWest(2000.0, 2000.0)
        client.objectBoundingBox = bbox

        viewModel.onFavoriteSelected(FavoriteLocation("Park", favLat, favLon))
        advanceUntilIdle()

        val mag = viewModel.uiState.value.viewport.magnification
        assertEquals("the fit must not go below the area-favorites floor", areaFloor, mag, 0.0)
        assertFalse(
            "a floor-reached fit is the floor's own bound, not a fit",
            fitsVisibleArea(bbox, favLat, favLon, mag, canvasWidth, canvasHeight, dpi)
        )
    }

    @Test
    fun distantPoiZoomsOutBelowTheAreaFavoritesFloor() = runTest(mainDispatcherRule.dispatcher) {
        // A POI 3 km north of the fix. The fit is degenerate in longitude (both
        // points share it), which the helper answers with its fixed node zoom; the
        // verified fit has to step that out until the current location is visible
        // too — below the area-favorites floor, which is what option B2 allows.
        fixAt(currentLat, currentLon)
        advanceUntilIdle()
        val poiLat = currentLat + 3000.0 / METERS_PER_DEG_LAT

        viewModel.onPoiEntryClick(poi(poiLat, currentLon, 3000.0))
        advanceUntilIdle()

        val mag = viewModel.uiState.value.viewport.magnification
        assertTrue(
            "premise: a floor-14 fit could not show a 3 km-distant POI",
            mag < areaFloor
        )
        assertTrue(
            "the POI must be visible at the applied magnification",
            isVisible(poiLat, currentLon, mag, angleRad = 0.0)
        )
        assertTrue(
            "the current location must be visible at the applied magnification",
            isVisible(currentLat, currentLon, mag, angleRad = 0.0)
        )
        assertEquals("the camera stays on the POI", poiLat, viewModel.uiState.value.viewport.centerLat, 1e-9)
    }

    @Test
    fun rotatedViewportKeepsBothPositionsVisible() = runTest(mainDispatcherRule.dispatcher) {
        // A POI 3 km north and only ~200 m east: the fitted bbox is long on the
        // latitude axis. North-up that is the roomy axis (800 px tall), but a
        // 90-degree viewport puts the long axis on the tight one (400 px wide),
        // so the rotated fit must step further out for both positions to stay
        // visible — and the north-up fit must not be enough on the rotated view.
        fixAt(currentLat, currentLon)
        advanceUntilIdle()
        val poiLat = currentLat + 3000.0 / METERS_PER_DEG_LAT
        val poiLon = currentLon + 200.0 / (METERS_PER_DEG_LAT * Math.cos(Math.toRadians(currentLat)))
        val poi = poi(poiLat, poiLon, 3006.0)

        viewModel.onPoiEntryClick(poi)
        advanceUntilIdle()
        val northUpMag = viewModel.uiState.value.viewport.magnification

        val angle = Math.toRadians(90.0)
        viewModel.updateAngle(angle)
        viewModel.onPoiEntryClick(poi)
        advanceUntilIdle()
        val rotatedMag = viewModel.uiState.value.viewport.magnification

        assertFalse(
            "premise: the north-up fit clips on the rotated viewport",
            isVisible(poiLat, poiLon, northUpMag, angle) && isVisible(currentLat, currentLon, northUpMag, angle)
        )
        assertTrue("rotation must step out further", rotatedMag < northUpMag)
        assertTrue("the POI must be visible", isVisible(poiLat, poiLon, rotatedMag, angle))
        assertTrue("the current location must be visible", isVisible(currentLat, currentLon, rotatedMag, angle))
    }

    @Test
    fun poiClickWithoutAFixKeepsTheCurrentMagnification() = runTest(mainDispatcherRule.dispatcher) {
        locationService.setLocationForTest(null)
        viewModel.updateMagnification(12.0, walk = false)
        advanceUntilIdle()

        viewModel.onPoiEntryClick(poi(currentLat + 0.05, currentLon, 5000.0))
        advanceUntilIdle()

        assertEquals(
            "without a fix the zoom level stays as it was",
            12.0,
            viewModel.uiState.value.viewport.magnification,
            1e-9
        )
        assertEquals(currentLat + 0.05, viewModel.uiState.value.viewport.centerLat, 1e-9)
    }

    /** A fix the POI fit can use as its distance reference. */
    private fun fixAt(lat: Double, lon: Double) {
        locationService.setLocationForTest(
            Location("gps").apply {
                latitude = lat
                longitude = lon
                accuracy = 8f
                time = 1_000L
            }
        )
    }

    private fun poi(lat: Double, lon: Double, distanceMeters: Double): PoiEntry = PoiEntry().apply {
        label = "POI"
        this.lat = lat
        this.lon = lon
        distance = distanceMeters
    }

    /** Whether a position projects inside the canvas at the applied viewport. */
    private fun isVisible(lat: Double, lon: Double, mag: Double, angleRad: Double): Boolean {
        val viewport = viewModel.uiState.value.viewport
        val vp = ProjectionUtils.viewport(
            viewport.centerLat, viewport.centerLon, mag, canvasWidth, canvasHeight, dpi, angleRad
        )
        val (x, y) = vp.geoToScreenRotated(lat, lon)
        return x >= 0.0 && x <= canvasWidth.toDouble() && y >= 0.0 && y <= canvasHeight.toDouble()
    }

    private companion object {
        const val METERS_PER_DEG_LAT = 111320.0
    }
}
