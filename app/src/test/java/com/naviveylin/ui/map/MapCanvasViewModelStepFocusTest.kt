package com.naviveylin.ui.map

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.navigation.NavigationEngine
import com.naviveylin.navigation.NavigationViewModel
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import com.naviveylin.ui.route.RoutePanelViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * Verifies the analysed-step camera move (spec: `route-analysis` — "Selecting a step
 * SHALL move the map camera to that manoeuvre", at a magnification close enough to see
 * the junction). A magnification already closer than the focus level is kept, clearing
 * the selection does not move the camera, and a step without a position does not move it.
 *
 * Harness mirrors [MapCanvasViewModelRouteFitTest]: real [MapRenderer] on the test
 * scheduler, `FakeOSMScoutClient` recording the route, a real [RoutePanelViewModel]
 * wired into the map, Robolectric with the default sandbox (AGENTS.md classloader rule
 * for the stub .so).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelStepFocusTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var routePanelViewModel: RoutePanelViewModel
    private lateinit var navigationViewModel: NavigationViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        File(context.filesDir, "maps/search_history.json").delete()
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
        routePanelViewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            context = context
        )
        routePanelViewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        navigationViewModel = NavigationViewModel(
            NavigationEngine({ client }, LocationService(context), context)
        )
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    /** Route whose three instruction lines sit on its three vertices (legs of ~110 m). */
    private fun routeEntry(withPositions: Boolean = true): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        latitudes = doubleArrayOf(48.0, 48.0002, 48.0004)
        longitudes = doubleArrayOf(2.0, 2.0002, 2.0004)
        distance = 60.0
        descriptions = arrayOf(
            "Start: A  []",
            "Right onto B  [50 m]",
            "Destination reached  []"
        )
        if (withPositions) {
            instructionLats = doubleArrayOf(48.0, 48.0002, 48.0004)
            instructionLons = doubleArrayOf(2.0, 2.0002, 2.0004)
        }
    }

    private fun entry(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.lat = lat
            this.lon = lon
            matchQuality = "coordinate"
        }

    private fun TestScope.wireRendererAndPanel() {
        val renderer = MapRenderer(client, dpi = 160.0, scope = backgroundScope)
        renderer.screenWidth = 400
        renderer.screenHeight = 800
        viewModel.setScreenSize(400, 800)
        viewModel.setMapRendererForTest(renderer)
        viewModel.setNavigationViewModel(navigationViewModel)
        viewModel.setRoutePanelViewModel(routePanelViewModel)
        advanceUntilIdle()
    }

    /** Calculate a route and let the one-shot overview fit settle first. */
    private fun TestScope.calculateAndAwaitRender(route: RouteEntry = routeEntry()) {
        routePanelViewModel.updateLocationsForReroute(
            entry("start", 48.0, 2.0), entry("dest", 48.0004, 2.0004)
        )
        client.routeToDeliver = route
        routePanelViewModel.calculateRoute()
        advanceUntilIdle()
        advanceTimeBy(1_000)
        advanceUntilIdle()
    }

    @Test
    fun `analysing a step fits that segment into the free area`() =
        runTest(mainDispatcherRule.dispatcher) {
            wireRendererAndPanel()
            calculateAndAwaitRender()
            val magBefore = viewModel.uiState.value.viewport.magnification
            assertNotEquals(MapCanvasViewModel.MANOEUVRE_FOCUS_MAG, magBefore)

            routePanelViewModel.analyseStep(1)
            advanceUntilIdle()

            val vp = viewModel.uiState.value.viewport
            val range = routePanelViewModel.analysedSegmentRange.value
            assertNotNull("the analysed step must own a polyline range", range)
            // A step owns the leg that *leads to* its manoeuvre (spec: route-analysis) — step 1's
            // manoeuvre sits on vertex 1, so its leg is vertices 0..1. The earlier mapping gave it
            // the leg *after* the manoeuvre, which is what the owner saw as "the segment shown is
            // the wrong one" (2026-10-03).
            assertEquals(IntRange(0, 1), range)
            val bbox = doubleArrayOf(48.0, 48.0002, 2.0, 2.0002)
            assertTrue(
                "the analysed segment must fit (range=$range)",
                fitsVisibleArea(bbox, vp.centerLat, vp.centerLon, vp.magnification, 400, 800, 160.0)
            )
            assertEquals(
                "the segment midpoint is centred (no overlay reported)",
                48.0001, vp.centerLat, 0.00005
            )
            // The zoom fits the segment: a ~20 m leg still needs a level or two past the readable
            // cap on this canvas, so the cap is a *floor* of readability, not the answer.
            assertTrue(
                "the zoom fits the segment instead of a fixed level (mag=${vp.magnification})",
                vp.magnification >= MapCanvasViewModel.MANOEUVRE_FOCUS_MAG - 1e-9
            )
        }

    @Test
    fun `the analysed manoeuvre stays in the free band above the overlay`() =
        runTest(mainDispatcherRule.dispatcher) {
            wireRendererAndPanel()
            calculateAndAwaitRender()
            viewModel.setOverlayCoveredPx(480)

            routePanelViewModel.analyseStep(1)
            advanceUntilIdle()

            val vp = viewModel.uiState.value.viewport
            val projected = ProjectionUtils.viewport(
                vp.centerLat, vp.centerLon, vp.magnification, 400, 800,
                context.resources.displayMetrics.densityDpi.toDouble(), vp.angle
            )
            val y = projected.geoToScreenRotated(48.0001, 2.0001).second
            // The free band is the canvas minus the 480 px overlay: [0, 320], its centre at 160.
            // The segment's midpoint goes there, not the canvas centre (400) — the owner's
            // "step not completely visible" (2026-10-03).
            val range = routePanelViewModel.analysedSegmentRange.value
            assertTrue(
                "the analysed segment must fit in the free band (range=$range)",
                fitsVisibleArea(
                    doubleArrayOf(48.0, 48.0002, 2.0, 2.0002), 48.0001, 2.0001,
                    vp.magnification, 400, 800,
                    context.resources.displayMetrics.densityDpi.toDouble(), vp.angle,
                    coveredPx = 480, marginPx = (MapCanvasViewModel.MARKER_MARGIN_DP *
                        context.resources.displayMetrics.densityDpi / 160.0).toInt()
                )
            )
            assertEquals("segment midpoint in the free band's centre", 160.0, y, 40.0)
        }

    @Test
    fun `a standing manoeuvre focus survives the overlay growing`() =
        runTest(mainDispatcherRule.dispatcher) {
            wireRendererAndPanel()
            calculateAndAwaitRender()
            viewModel.setOverlayCoveredPx(480)
            routePanelViewModel.analyseStep(1)
            advanceUntilIdle()
            val focused = viewModel.uiState.value.viewport

            // The min card grows with a longer instruction line: that growth must not pull the
            // camera back to the overview (owner finding, 2026-10-03: "suddenly without action
            // zoom away from the visible step"). It must, however, re-place the step so the taller
            // card does not cover its lower part (owner finding, 2026-10-03: "segment still cut
            // off"), and it may only widen, never zoom in.
            viewModel.setOverlayCoveredPx(600)
            advanceUntilIdle()

            val after = viewModel.uiState.value.viewport
            assertTrue(
                "the step is still the subject: the zoom never tightens on a layout change " +
                    "(${focused.magnification} -> ${after.magnification})",
                after.magnification <= focused.magnification + 1e-9
            )
            assertTrue(
                "the step is re-placed above the taller card (${focused.centerLat} -> ${after.centerLat})",
                after.centerLat < focused.centerLat
            )
        }

    @Test
    fun `a closer user zoom is kept when a step is analysed`() =
        runTest(mainDispatcherRule.dispatcher) {
            wireRendererAndPanel()
            calculateAndAwaitRender()
            val closer = MapCanvasViewModel.MANOEUVRE_FOCUS_MAG + 1.5
            viewModel.updateMagnification(closer, walk = false)
            advanceUntilIdle()

            routePanelViewModel.analyseStep(1)
            advanceUntilIdle()

            // Short leg: the readable cap holds the zoom (the fixture's legs are ~110 m), so the
            // user's closer zoom is kept — the rule only zooms *out* when a segment needs it.
            assertEquals(
                "the user's closer zoom is kept (was $closer, now ${viewModel.uiState.value.viewport.magnification})",
                closer, viewModel.uiState.value.viewport.magnification, 1e-9
            )
        }

    @Test
    fun `clearing the analysed step leaves the camera where it is`() =
        runTest(mainDispatcherRule.dispatcher) {
            wireRendererAndPanel()
            calculateAndAwaitRender()
            routePanelViewModel.analyseStep(1)
            advanceUntilIdle()
            val centered = viewModel.uiState.value.viewport

            routePanelViewModel.clearAnalysedStep()
            advanceUntilIdle()

            assertEquals(centered, viewModel.uiState.value.viewport)
        }

    @Test
    fun `a step without a position does not move the camera`() =
        runTest(mainDispatcherRule.dispatcher) {
            wireRendererAndPanel()
            calculateAndAwaitRender(routeEntry(withPositions = false))
            val before = viewModel.uiState.value.viewport

            routePanelViewModel.analyseStep(1)
            advanceUntilIdle()

            assertEquals(before, viewModel.uiState.value.viewport)
        }

    /**
     * Owner finding (2026-10-03): "segment still cut off". The min card grows with the analysed
     * instruction (a longer step wraps to two lines) *after* the focus ran, and the growth was
     * ignored wholesale — the lower part of the segment slid behind the card. The fix keeps the
     * zoom and re-places the segment on the new band centre, widening only if it no longer fits.
     */
    @Test
    fun `a card growing under the focused step keeps the segment in the band`() =
        runTest(mainDispatcherRule.dispatcher) {
            wireRendererAndPanel()
            calculateAndAwaitRender()
            routePanelViewModel.analyseStep(1)
            advanceUntilIdle()
            val mag = viewModel.uiState.value.viewport.magnification
            val before = viewModel.uiState.value.viewport.centerLat

            // The card grows (236 px -> 400 px of the 800 px canvas).
            viewModel.setOverlayCoveredPx(400)
            advanceUntilIdle()

            val vp = viewModel.uiState.value.viewport
            assertTrue(
                "the user's zoom is kept, only widened if the segment no longer fits " +
                    "($mag -> ${vp.magnification})",
                vp.magnification <= mag + 1e-9
            )
            assertTrue(
                "the camera re-placed the step ($before -> ${vp.centerLat})",
                vp.centerLat < before
            )
        }

    @Test
    fun `clearing the route does not move the camera after an analysis`() =
        runTest(mainDispatcherRule.dispatcher) {
            wireRendererAndPanel()
            calculateAndAwaitRender()
            routePanelViewModel.analyseStep(1)
            advanceUntilIdle()
            val afterAnalysis = viewModel.uiState.value.viewport

            routePanelViewModel.clearRoute()
            advanceUntilIdle()

            assertEquals(afterAnalysis, viewModel.uiState.value.viewport)
        }
}
