package com.naviveylin.ui.map

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import com.naviveylin.navigation.CarSessionPresenceImpl
import com.naviveylin.navigation.NavigationViewModel
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import com.naviveylin.test.engineUnderTest
import com.naviveylin.ui.route.RoutePanelViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The phone chrome band's overlay-width probe reaches the map screen's right inset in **every**
 * mode the band composes the right-side widget column in (spec: `smooth-follow` — Right anchor
 * stays clear of the widget column only when covered, and the navigation overlays are measured).
 *
 * `MapRightWidgetColumn` publishes its measured width through `LocalOverlayWidthProbe`, the chrome
 * band forwards it as `overlayRightInset`, and the screen pushes it with
 * `setMapOverlayInsets(right = …)` so `resolveAnchorFraction` moves a right-edge preset out of the
 * column (`MapCanvasViewModel.setMapOverlayInsets` → `publishResolvedAnchor` → `uiState.resolvedAnchor`).
 * The probe must be provided for the **whole chrome band**: the band composes the column in its
 * browse branch *and* in its navigation branch, and only one of them is composed at a time.
 *
 * **Why the case drives the Compose clock by hand.** `MapCanvasScreen` runs a `while (isActive)`
 * `withFrameNanos` loop (`MapCanvasScreen.kt:578`) feeding the follow display, so the composition
 * never becomes idle and `createComposeRule().waitForIdle()` cannot be used; the screen is therefore not
 * hostable the way the band harnesses are. With
 * `mainClock.autoAdvance = false` and explicit `advanceTimeByFrame()` calls the composition, its
 * effects and the screen's callbacks all run, and the observable is the ViewModel's state — the
 * screen's own wiring, exercised whole, with no harness reproducing it.
 *
 * The measured value is the resolved anchor fraction, the number the render target, the marker and
 * the blit offset consume: `0.9` is the raw `MIDDLE_FAR_RIGHT` preset (nothing measured) and
 * `~0.78` is the same preset moved left of the column. Both cases print it, so the JUnit XML's
 * `system-out` carries the numbers they assert.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapNavColumnWidthProbeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var presence: CarSessionPresenceImpl
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var routePanelViewModel: RoutePanelViewModel
    private lateinit var navigationViewModel: NavigationViewModel

    private val rawPresetFx = VehicleAnchorPosition.MIDDLE_FAR_RIGHT.fx

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        RuntimeEnvironment.setQualifiers("w411dp-h891dp")
        client = FakeOSMScoutClient()
        presence = CarSessionPresenceImpl()
        val settings = SettingsStorage(context)
        runBlocking {
            settings.save(
                settings.load().copy(
                    routingAnchorId = VehicleAnchorPosition.MIDDLE_FAR_RIGHT.id,
                    freeDrivingAnchorId = VehicleAnchorPosition.MIDDLE_FAR_RIGHT.id
                )
            )
        }
        locationService = LocationService(context)
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = settings,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = locationService,
            darkModeController = DarkModeController(settings),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            carSessionPresence = presence,
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        routePanelViewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = locationService,
            context = context
        )
        routePanelViewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        navigationViewModel = NavigationViewModel(
            engineUnderTest({ client }, LocationService(context), context)
        )
        viewModel.setNavigationViewModel(navigationViewModel)
        viewModel.setRoutePanelViewModel(routePanelViewModel)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        if (this::viewModel.isInitialized) viewModel.cancelScopeForTest()
    }

    /**
     * Compose the real screen — the chrome band, its browse and navigation branches and the screen's
     * own inset wiring — with the Compose clock under the case's control.
     */
    private fun composeScreen() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MapCanvasScreen(
                mapPath = "/data/maps/testmap",
                viewModel = viewModel,
                routePanelViewModel = routePanelViewModel,
                navigationViewModel = navigationViewModel,
                carSessionPresence = presence
            )
        }
    }

    /** One composition/frame pass: the screen's effects and the ViewModel share one scheduler. */
    private fun frames(count: Int = 20) {
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        repeat(count) { composeRule.mainClock.advanceTimeByFrame() }
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    private fun emitFix() {
        locationService.setGpsFixForTest(
            GpsFix(
                lat = 51.5136, lon = 7.4653, accuracy = 5.0, speedKmH = 40.0,
                smoothedBearing = 45.0, markerBearing = 45.0,
                time = System.currentTimeMillis()
            )
        )
    }

    private fun anchorFx(): Double = viewModel.uiState.value.resolvedAnchor.fx

    /**
     * The covered band's width as a fraction of the window, read back from the resolved anchor of a
     * covered preset: `resolveAnchorFraction` moves a covered preset to the covered edge plus
     * [VehicleAnchorPosition.MIN_FRACTION] of the visible extent, i.e. `fx = (W - band)(1 - m)/W` for a
     * preset covered on the trailing (right) edge only. Printed, not asserted: it is how the two
     * columns' widths are compared across rows without a second source for the number.
     */
    private fun bandWidthFraction(fx: Double): Double =
        1.0 - fx / (1.0 - VehicleAnchorPosition.MIN_FRACTION)

    private fun route(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        distance = 12_400.0
        duration = 1_500.0
        latitudes = doubleArrayOf(51.5136, 51.5226)
        longitudes = doubleArrayOf(7.4653, 7.4653)
        descriptions = arrayOf("Start: A  [0.0 km]", "Turn left into Main Street  [1.2 km, 5 min]")
    }

    @Test
    fun theNavigationWidgetColumnPublishesItsWidthSoTheRightAnchorStaysClearOfIt() {
        // The reachable path: the phone surface hands the map over to the car, the driver starts
        // navigation on the car, and asks for the map back — the phone composes its chrome band
        // from scratch while navigation is already active (spec: map-canvas-screen — The phone map
        // canvas is suspended while a car session is active; guidelines/MapRendering.md §18: no
        // remembered frame survives the suspension).
        composeScreen()
        frames()
        emitFix()
        frames()
        assertEquals(
            "premise: the browsing band keeps the vehicle at the configured anchor",
            VehicleAnchorPosition.MIDDLE_FAR_RIGHT,
            viewModel.uiState.value.activeFollowAnchor
        )
        val browsingFx = anchorFx()
        assertTrue(
            "premise: the browsing right-side column reports its width, so the preset is already " +
                "clear of it (fx=$browsingFx)",
            browsingFx < rawPresetFx
        )

        presence.setActive(true)
        frames()
        assertTrue(
            "premise: the car session suspends the phone map canvas",
            viewModel.uiState.value.phoneMapSuspended
        )

        navigationViewModel.start(route(), Vehicle.CAR)
        frames()
        assertTrue(
            "premise: the driver navigates while the phone map is suspended",
            navigationViewModel.state.value.isNavigating
        )

        viewModel.showMapDuringCarSession()
        frames(count = 30)
        assertTrue(
            "premise: the map comes back for the rest of the car session",
            !viewModel.uiState.value.phoneMapSuspended
        )

        val navigatingFx = anchorFx()
        println(
            "NavColumnProbe anchor=${viewModel.uiState.value.activeFollowAnchor} " +
                "browseFx=$browsingFx navigatingFx=$navigatingFx rawPresetFx=$rawPresetFx"
        )
        println(
            "NavColumnProbe bandWidthFraction browse=${bandWidthFraction(browsingFx)} " +
                "navigating=${bandWidthFraction(navigatingFx)}"
        )
        assertTrue(
            "the navigation-time widget column must publish its width like the browsing one, so " +
                "the right-edge preset stays clear of the column " +
                "(navigatingFx=$navigatingFx rawPresetFx=$rawPresetFx)",
            navigatingFx < rawPresetFx
        )
    }

    @Test
    fun theBrowsingWidgetColumnStillPublishesItsWidth() {
        composeScreen()
        frames()
        emitFix()
        frames()
        val browsingFx = anchorFx()
        println(
            "NavColumnProbe (browse) anchor=${viewModel.uiState.value.activeFollowAnchor} " +
                "browseFx=$browsingFx rawPresetFx=$rawPresetFx " +
                "bandWidthFraction=${bandWidthFraction(browsingFx)}"
        )
        assertTrue(
            "the browsing right-side column must keep publishing its measured width " +
                "(browseFx=$browsingFx rawPresetFx=$rawPresetFx)",
            browsingFx < rawPresetFx
        )
    }
}
