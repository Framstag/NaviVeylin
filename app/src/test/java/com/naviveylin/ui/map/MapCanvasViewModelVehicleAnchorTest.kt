package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.anchorCenter
import com.naviveylin.core.resolveAnchorFraction
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import com.naviveylin.navigation.NavigationStateProvider
import com.naviveylin.navigation.NavigationViewModel
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the phone follow-mode vehicle anchor selection (spec:
 * smooth-follow — Vehicle position anchor in follow mode): the routing
 * anchor applies while route guidance is active, the free-driving anchor
 * otherwise, and the persisted ids resolve to presets.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapCanvasViewModelVehicleAnchorTest {

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
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private suspend fun persistAnchors(routing: String, freeDriving: String) {
        val current = settingsStorage.load()
        settingsStorage.save(current.copy(routingAnchorId = routing, freeDrivingAnchorId = freeDriving))
    }

    private fun emitFix(lat: Double, lon: Double) {
        locationService.setGpsFixForTest(
            GpsFix(
                lat = lat, lon = lon,
                accuracy = 5.0, speedKmH = 40.0,
                smoothedBearing = 45.0, markerBearing = 45.0,
                time = System.currentTimeMillis()
            )
        )
    }

    @Test
    fun freeDrivingAnchorAppliedWithoutGuidance() = runTest(mainDispatcherRule.dispatcher) {
        persistAnchors(routing = VehicleAnchorPosition.BOTTOM_RIGHT.id, freeDriving = VehicleAnchorPosition.TOP_CENTER.id)
        // Reload the view model so the persisted settings are picked up.
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
        advanceUntilIdle()
        emitFix(52.51, 13.40)
        advanceUntilIdle()
        assertEquals(
            VehicleAnchorPosition.TOP_CENTER,
            viewModel.uiState.value.activeFollowAnchor
        )
    }

    @Test
    fun routingAnchorAppliedWhileNavigating() = runTest(mainDispatcherRule.dispatcher) {
        val navVm = buildNavigationViewModel()
        viewModel.setNavigationViewModel(navVm)
        advanceUntilIdle()

        startNavigating(navVm)
        // The picker path: the user chooses the routing anchor while guidance
        // is active — the active anchor flips immediately.
        viewModel.setRoutingAnchor(VehicleAnchorPosition.BOTTOM_RIGHT)
        advanceUntilIdle()
        assertEquals(
            VehicleAnchorPosition.BOTTOM_RIGHT,
            viewModel.uiState.value.activeFollowAnchor
        )
        // And the choice persists to the shared storage.
        assertEquals(
            VehicleAnchorPosition.BOTTOM_RIGHT.id,
            settingsStorage.load().routingAnchorId
        )

        // Free-driving anchor chosen before/independent of guidance.
        viewModel.setFreeDrivingAnchor(VehicleAnchorPosition.TOP_CENTER)
        assertEquals(
            VehicleAnchorPosition.BOTTOM_RIGHT,
            viewModel.uiState.value.activeFollowAnchor
        )

        // End guidance: the free-driving anchor takes over (spec:
        // "free-driving anchor otherwise").
        navVm.stopNavigation()
        advanceUntilIdle()
        viewModel.setFreeDrivingAnchor(VehicleAnchorPosition.TOP_CENTER)
        assertEquals(
            VehicleAnchorPosition.TOP_CENTER,
            viewModel.uiState.value.activeFollowAnchor
        )
    }

    @Test
    fun defaultAnchorsAreCenter() = runTest(mainDispatcherRule.dispatcher) {
        // Fresh settings (no anchor keys): the app defaults to center/center,
        // reproducing the pre-feature framing.
        val current = settingsStorage.load()
        settingsStorage.save(current.copy(routingAnchorId = "", freeDrivingAnchorId = ""))
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
        advanceUntilIdle()
        emitFix(52.51, 13.40)
        advanceUntilIdle()
        assertEquals(VehicleAnchorPosition.DEFAULT, viewModel.uiState.value.activeFollowAnchor)
    }

    // --- Render target / anchor-centered follow framing ---------------------
    // (spec: smooth-follow — Anchor-centered follow framing; tasks 1.1-1.4,
    // 4.1-4.3 of change fix-phone-vehicle-anchor-framing)

    private fun recreateViewModel() {
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
    }

    private val dpi: Double
        get() = context.resources.displayMetrics.densityDpi.toDouble()

    /** Project [lat]/[lon] against the committed viewport, in screen pixels. */
    private fun projectCommitted(lat: Double, lon: Double): Pair<Double, Double> {
        val vp = viewModel.uiState.value.viewport
        return ProjectionUtils.viewport(
            vp.centerLat, vp.centerLon, vp.magnification, SCREEN_W, SCREEN_H, dpi, vp.angle
        ).geoToScreenRotated(lat, lon)
    }

    private fun assertVehicleAtAnchor(
        anchor: VehicleAnchorPosition,
        lat: Double,
        lon: Double,
        tolerancePx: Double = 1.0
    ) {
        val (x, y) = projectCommitted(lat, lon)
        assertEquals("anchor ${anchor.id} x", anchor.fx * SCREEN_W, x, tolerancePx)
        assertEquals("anchor ${anchor.id} y", anchor.fy * SCREEN_H, y, tolerancePx)
    }

    @Test
    fun renderTargetHelperKeepsCenterAnchorIdentical() = runTest(mainDispatcherRule.dispatcher) {
        recreateViewModel()
        advanceUntilIdle()
        val (lat, lon) = viewModel.followRenderTarget(52.51, 13.40, 15.0, 0.0)
        assertEquals(52.51, lat, 1e-12)
        assertEquals(13.40, lon, 1e-12)
    }

    @Test
    fun renderTargetHelperShiftsForOffCenterAnchor() = runTest(mainDispatcherRule.dispatcher) {
        val current = settingsStorage.load()
        settingsStorage.save(
            current.copy(freeDrivingAnchorId = VehicleAnchorPosition.BOTTOM_CENTER.id)
        )
        recreateViewModel()
        advanceUntilIdle()
        emitFix(52.51, 13.40)
        advanceUntilIdle()
        assertEquals(VehicleAnchorPosition.BOTTOM_CENTER, viewModel.uiState.value.activeFollowAnchor)

        val mag = 15.0
        val (targetLat, targetLon) = viewModel.followRenderTarget(52.51, 13.40, mag, 0.0)
        val expected = anchorCenter(
            52.51, 13.40, VehicleAnchorPosition.BOTTOM_CENTER, mag, SCREEN_W, SCREEN_H, dpi, 0.0
        )
        assertEquals(expected.first, targetLat, 1e-9)
        assertEquals(expected.second, targetLon, 1e-9)
        // The target projects the vehicle to the anchor, not to the center.
        val (x, y) = ProjectionUtils
            .viewport(targetLat, targetLon, mag, SCREEN_W, SCREEN_H, dpi, 0.0)
            .geoToScreenRotated(52.51, 13.40)
        assertEquals(0.5 * SCREEN_W, x, 1.0)
        assertEquals(0.9 * SCREEN_H, y, 1.0)
    }

    @Test
    fun followCommitUsesTheAnchorCenteredRenderTarget() = runTest(mainDispatcherRule.dispatcher) {
        val current = settingsStorage.load()
        settingsStorage.save(
            current.copy(freeDrivingAnchorId = VehicleAnchorPosition.MIDDLE_FAR_RIGHT.id)
        )
        recreateViewModel()
        advanceUntilIdle()
        emitFix(52.51, 13.40)
        advanceUntilIdle()
        viewModel.onToggleFollowMode(true)
        advanceUntilIdle()
        awaitFollowThrottle()
        emitFix(52.5105, 13.4005)
        advanceUntilIdle()
        assertVehicleAtAnchor(VehicleAnchorPosition.MIDDLE_FAR_RIGHT, 52.5105, 13.4005)
        val vp = viewModel.uiState.value.viewport
        val (expectedLat, expectedLon) = anchorCenter(
            52.5105, 13.4005, VehicleAnchorPosition.MIDDLE_FAR_RIGHT,
            vp.magnification, SCREEN_W, SCREEN_H, dpi, vp.angle
        )
        assertEquals(expectedLat, vp.centerLat, 1e-9)
        assertEquals(expectedLon, vp.centerLon, 1e-9)
    }

    @Test
    fun guidanceStateSelectsTheAnchorUsedForTheRenderTarget() =
        runTest(mainDispatcherRule.dispatcher) {
            val current = settingsStorage.load()
            settingsStorage.save(
                current.copy(
                    routingAnchorId = VehicleAnchorPosition.BOTTOM_RIGHT.id,
                    freeDrivingAnchorId = VehicleAnchorPosition.TOP_LEFT.id
                )
            )
            recreateViewModel()
            val navVm = buildNavigationViewModel()
            viewModel.setNavigationViewModel(navVm)
            advanceUntilIdle()

            // Free driving (no guidance): the free-driving anchor frames the map.
            emitFix(52.51, 13.40)
            viewModel.onToggleFollowMode(true)
            advanceUntilIdle()
            awaitFollowThrottle()
            emitFix(52.5105, 13.4005)
            advanceUntilIdle()
            assertEquals(VehicleAnchorPosition.TOP_LEFT, viewModel.uiState.value.activeFollowAnchor)
            assertVehicleAtAnchor(VehicleAnchorPosition.TOP_LEFT, 52.5105, 13.4005)

            // Guidance active: the routing anchor takes over on the next update.
            startNavigating(navVm)
            advanceUntilIdle()
            awaitFollowThrottle()
            emitFix(52.5110, 13.4010)
            advanceUntilIdle()
            assertEquals(VehicleAnchorPosition.BOTTOM_RIGHT, viewModel.uiState.value.activeFollowAnchor)
            assertVehicleAtAnchor(VehicleAnchorPosition.BOTTOM_RIGHT, 52.5110, 13.4010)
        }

    @Test
    fun headingUpFollowKeepsTheVehicleAtTheAnchor() = runTest(mainDispatcherRule.dispatcher) {
        val current = settingsStorage.load()
        settingsStorage.save(
            current.copy(
                freeDrivingAnchorId = VehicleAnchorPosition.BOTTOM_LEFT.id,
                navNorthUp = false
            )
        )
        recreateViewModel()
        advanceUntilIdle()
        // Northbound fix (bearing 0) then an eastbound one (bearing 90): the map
        // rotates heading-up and the vehicle must stay on the anchor.
        locationService.setGpsFixForTest(
            GpsFix(
                lat = 52.51, lon = 13.40, accuracy = 5.0, speedKmH = 40.0,
                smoothedBearing = 0.0, markerBearing = 0.0, time = System.currentTimeMillis()
            )
        )
        advanceUntilIdle()
        viewModel.onToggleFollowMode(true)
        advanceUntilIdle()
        awaitFollowThrottle()
        locationService.setGpsFixForTest(
            GpsFix(
                lat = 52.5105, lon = 13.4015, accuracy = 5.0, speedKmH = 40.0,
                smoothedBearing = 90.0, markerBearing = 90.0, time = System.currentTimeMillis() + 1000
            )
        )
        advanceUntilIdle()

        val vp = viewModel.uiState.value.viewport
        assertEquals("heading-up angle", -Math.PI / 2, vp.angle, 1e-9)
        assertVehicleAtAnchor(VehicleAnchorPosition.BOTTOM_LEFT, 52.5105, 13.4015, tolerancePx = 2.0)
    }

    @Test
    fun browseRecenterCentersTheVehicleIgnoringTheAnchorPreset() =
        runTest(mainDispatcherRule.dispatcher) {
            // A non-center free-driving anchor must NOT frame the browse re-center:
            // an off-center target would immediately re-show the button the user
            // just dismissed (spec: map-modes — Browse re-center).
            val current = settingsStorage.load()
            settingsStorage.save(
                current.copy(freeDrivingAnchorId = VehicleAnchorPosition.MIDDLE_FAR_RIGHT.id)
            )
            recreateViewModel()
            advanceUntilIdle()
            emitFix(52.51, 13.40)
            advanceUntilIdle()
            assertEquals(MapMode.BROWSE, viewModel.mode)

            viewModel.recenterInBrowse()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.browseReCenterVisible)
            assertVehicleAtAnchor(VehicleAnchorPosition.CENTER, 52.51, 13.40)
        }

    @Test
    fun reEngagingFollowCommitsTheAnchorCenteredFrame() = runTest(mainDispatcherRule.dispatcher) {
        val current = settingsStorage.load()
        settingsStorage.save(
            current.copy(freeDrivingAnchorId = VehicleAnchorPosition.BOTTOM_FAR_RIGHT.id)
        )
        recreateViewModel()
        advanceUntilIdle()
        emitFix(52.51, 13.40)
        advanceUntilIdle()
        viewModel.onToggleFollowMode(true)
        advanceUntilIdle()

        // Pan away (disengage), then re-engage from the compass/re-center.
        viewModel.disengageFollowMode()
        assertFalse(viewModel.uiState.value.followMode)
        viewModel.onToggleFollowMode(true)
        advanceUntilIdle()

        assertVehicleAtAnchor(VehicleAnchorPosition.BOTTOM_FAR_RIGHT, 52.51, 13.40)
    }

    @Test
    fun gestureAfterFollowKeepsTheCommittedFrameWithoutAnchorJump() =
        runTest(mainDispatcherRule.dispatcher) {
            val current = settingsStorage.load()
            settingsStorage.save(
                current.copy(freeDrivingAnchorId = VehicleAnchorPosition.BOTTOM_CENTER.id)
            )
            recreateViewModel()
            advanceUntilIdle()
            emitFix(52.51, 13.40)
            advanceUntilIdle()
            viewModel.onToggleFollowMode(true)
            advanceUntilIdle()
            val anchored = viewModel.uiState.value.viewport.centerLat to
                viewModel.uiState.value.viewport.centerLon

            viewModel.disengageFollowMode()
            // The committed viewport is the geo position at the screen center, so a
            // gesture commit continues from the frame on screen — no hidden anchor.
            assertEquals(anchored.first, viewModel.uiState.value.viewport.centerLat, 1e-12)
            assertEquals(anchored.second, viewModel.uiState.value.viewport.centerLon, 1e-12)

            viewModel.updateCenter(52.53, 13.42)
            assertEquals(52.53, viewModel.uiState.value.viewport.centerLat, 1e-12)
            assertEquals(13.42, viewModel.uiState.value.viewport.centerLon, 1e-12)
        }

    @Test
    fun overlayInsetsResolveTheAnchorIntoTheVisibleArea() = runTest(mainDispatcherRule.dispatcher) {
        val current = settingsStorage.load()
        settingsStorage.save(current.copy(freeDrivingAnchorId = VehicleAnchorPosition.BOTTOM_CENTER.id))
        recreateViewModel()
        advanceUntilIdle()
        emitFix(52.51, 13.40)
        advanceUntilIdle()
        assertEquals(
            "with no overlay measured the preset applies unchanged",
            0.9, viewModel.uiState.value.resolvedAnchor.fy, 1e-9
        )

        // The phone navigation overlays cover the top and the bottom of the canvas.
        viewModel.setMapOverlayInsets(top = 400, bottom = 400, right = 80)
        advanceUntilIdle()
        val expected = resolveAnchorFraction(
            VehicleAnchorPosition.BOTTOM_CENTER, 0, 400, 80, 400, SCREEN_W, SCREEN_H
        )
        val resolved = viewModel.uiState.value.resolvedAnchor
        assertEquals(expected.fx, resolved.fx, 1e-9)
        assertEquals(expected.fy, resolved.fy, 1e-9)
        assertTrue(
            "bottom anchor must move up into the visible area",
            resolved.fy < VehicleAnchorPosition.BOTTOM_CENTER.fy
        )

        // The follow render target uses the resolved fraction, so the vehicle projects
        // to it (not to the raw preset fraction).
        val (targetLat, targetLon) = viewModel.followRenderTarget(52.51, 13.40, 15.0, 0.0)
        val (expLat, expLon) = anchorCenter(
            52.51, 13.40, expected.fx, expected.fy, 15.0, SCREEN_W, SCREEN_H, dpi, 0.0
        )
        assertEquals(expLat, targetLat, 1e-9)
        assertEquals(expLon, targetLon, 1e-9)
        val (x, y) = ProjectionUtils
            .viewport(targetLat, targetLon, 15.0, SCREEN_W, SCREEN_H, dpi, 0.0)
            .geoToScreenRotated(52.51, 13.40)
        assertEquals("vehicle at the resolved anchor x", expected.fx * SCREEN_W, x, 1.0)
        assertEquals("vehicle at the resolved anchor y", expected.fy * SCREEN_H, y, 1.0)
    }

    @Test
    fun defaultCenterStaysExactlyCenteredUnderNavigationInsets() =
        runTest(mainDispatcherRule.dispatcher) {
            recreateViewModel()
            advanceUntilIdle()
            emitFix(52.51, 13.40)
            advanceUntilIdle()
            // Full phone navigation overlay set (spec smooth-follow — "Default
            // anchors reproduce today's framing"; device regression 2026-09-16): the
            // default center/center must resolve to the exact canvas center, never
            // the center of the reduced visible rect the old rescale resolved to.
            viewModel.setMapOverlayInsets(top = 400, bottom = 400, right = 80)
            advanceUntilIdle()
            val resolved = viewModel.uiState.value.resolvedAnchor
            assertEquals(0.5, resolved.fx, 1e-9)
            assertEquals(0.5, resolved.fy, 1e-9)

            // The follow render target projects the vehicle to the exact center.
            val (targetLat, targetLon) = viewModel.followRenderTarget(52.51, 13.40, 15.0, 0.0)
            val (expLat, expLon) = anchorCenter(
                52.51, 13.40, 0.5, 0.5, 15.0, SCREEN_W, SCREEN_H, dpi, 0.0
            )
            assertEquals(expLat, targetLat, 1e-9)
            assertEquals(expLon, targetLon, 1e-9)
        }

    @Test
    fun unchangedOverlayInsetsDoNotEmitState() = runTest(mainDispatcherRule.dispatcher) {
        recreateViewModel()
        advanceUntilIdle()
        emitFix(52.51, 13.40)
        advanceUntilIdle()
        viewModel.setMapOverlayInsets(top = 100, bottom = 200, right = 50)
        advanceUntilIdle()
        val before = viewModel.uiState.value
        viewModel.setMapOverlayInsets(top = 100, bottom = 200, right = 50)
        assertSame(
            "identical insets must not re-emit state (no recomposition thrash)",
            before, viewModel.uiState.value
        )
    }

    @Test
    fun routingAndFreeDrivingResolveTheirOwnAnchorsWithInsets() =
        runTest(mainDispatcherRule.dispatcher) {
            val current = settingsStorage.load()
            settingsStorage.save(
                current.copy(
                    routingAnchorId = VehicleAnchorPosition.BOTTOM_RIGHT.id,
                    freeDrivingAnchorId = VehicleAnchorPosition.TOP_LEFT.id
                )
            )
            recreateViewModel()
            advanceUntilIdle()
            emitFix(52.51, 13.40)
            advanceUntilIdle()
            viewModel.setMapOverlayInsets(top = 400, bottom = 400, right = 80)
            advanceUntilIdle()

            val freeDriving = viewModel.uiState.value.resolvedAnchor
            assertEquals(
                resolveAnchorFraction(
                    VehicleAnchorPosition.TOP_LEFT, 0, 400, 80, 400, SCREEN_W, SCREEN_H
                ).fy,
                freeDriving.fy, 1e-9
            )

            val navVm = buildNavigationViewModel()
            viewModel.setNavigationViewModel(navVm)
            advanceUntilIdle()
            startNavigating(navVm)
            advanceUntilIdle()
            awaitFollowThrottle()
            emitFix(52.5105, 13.4005)
            advanceUntilIdle()

            val routing = viewModel.uiState.value.resolvedAnchor
            assertEquals(
                resolveAnchorFraction(
                    VehicleAnchorPosition.BOTTOM_RIGHT, 0, 400, 80, 400, SCREEN_W, SCREEN_H
                ).fx,
                routing.fx, 1e-9
            )
            assertTrue("routing and free-driving resolutions must differ", routing.fx != freeDriving.fx)
        }

    private companion object {
        const val SCREEN_W = 1080
        const val SCREEN_H = 1920

        /**
         * Follow renders are throttled by real time (GPS_FOLLOW_RENDER_INTERVAL_MS
         * = 200 ms), so a second fix in the same (virtual) test instant does not
         * commit a new viewport. Real-time sleep mirrors the existing
         * `startNavigating` helper's use of Thread.sleep.
         */
        fun awaitFollowThrottle() = Thread.sleep(250)
    }

    private fun buildNavigationViewModel(): NavigationViewModel {
        val routeClient = FakeOSMScoutClient().apply {
            routeToDeliver = com.framstag.libosmscout.client.RouteEntry().apply {
                routeHandle = 1L
                latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
                longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
                distance = 5000.0
                descriptions = arrayOf(
                    "Start navigation  [0.0 km, 0 min]",
                    "Destination reached  [0.0 km, 0 min]"
                )
            }
        }
        return NavigationViewModel(
            routeClient, NavigationStateProvider(),
            LocationService(context), context
        )
    }

    /** Start a route via startDirectRoute and wait until navigation is active. */
    private suspend fun TestScope.startNavigating(navVm: NavigationViewModel) {
        navVm.startDirectRoute(52.5200, 13.4050, 52.5300, 13.4100)
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline && !navVm.state.value.isNavigating) {
            advanceUntilIdle()
            Thread.sleep(10)
        }
        assertEquals("navigation must become active", true, navVm.state.value.isNavigating)
        advanceUntilIdle()
    }
}
