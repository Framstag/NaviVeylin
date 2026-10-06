package com.naviveylin.ui.map

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.navigation.NavigationViewModel
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import com.naviveylin.ui.route.RouteOverlayAnchor
import com.naviveylin.ui.route.RoutePanel
import com.naviveylin.ui.route.RoutePanelViewModel
import com.naviveylin.ui.route.RouteSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File
import com.naviveylin.test.engineUnderTest

/**
 * The session's exit from its own card (spec: `route-planning-session` — Ending the session
 * removes its surface; `route-panel-ui` — the close control dismisses without collapsing).
 *
 * This is the assertion whose absence let the phone trap ship: the card ended the session
 * through `RoutePanelViewModel.endSession()` alone, which clears the route but leaves
 * `MapCanvasUiState.showRoutePanel` true, so the surface outlived its session — the header's
 * close minimised onto a route-ready pill that had no exit of its own (owner finding,
 * 2026-10-05: "I cannot leave the stateful navigation without starting a route").
 *
 * The card is composed with the same wiring the screen uses
 * (`onEndSession = { viewModel.dismissRoutePanel() }`, `MapCanvasScreen.kt`), so a card that
 * ends the session without asking the host to close fails here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RouteSessionCardExitTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var routePanelViewModel: RoutePanelViewModel
    private lateinit var navigationViewModel: NavigationViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Compose needs a real rule instance; the window size is set per class so the card's
    // pinned action band is on screen (runtime, not @Config — the JNI stub's classloader
    // rule forbids a sandbox change on a class that loads the stub).
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        RuntimeEnvironment.setQualifiers("w411dp-h891dp")
        File(context.filesDir, "maps/search_history.json").delete()
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
        viewModel.cancelScopeForTest()
    }

    private fun entry(label: String): LocationEntry = LocationEntry().apply {
        this.label = label
        lat = 51.5136
        lon = 7.4653
        matchQuality = "coordinate"
    }

    /** A two-point route: the published result is the geometry the overview fit consumes. The
     *  fit itself is guarded on the renderer (`mapRenderer?.`), which this test does not build.
     *  A route *without* a polyline is covered by
     *  `RoutePanelViewModelSessionTest.adopting a route without geometry adopts the session and
     *  publishes no geometry` and `RouteGeometryTest` (change `fix-adopt-route-null-polyline`). */
    private fun route(): RouteEntry = RouteEntry().apply {
        distance = 12_400.0
        duration = 1_500.0
        latitudes = doubleArrayOf(51.5136, 51.5226)
        longitudes = doubleArrayOf(7.4653, 7.4653)
        descriptions = arrayOf("Start: A  [0.0 km]", "Turn left into Main Street  [1.2 km, 5 min]")
    }

    @Test
    fun `ending the session from the card closes the surface and returns to browse`() {
        // Endpoints, then the route, then the session: `openRoutePanelWithStart(entry)`
        // sets the destination first, and a changed endpoint invalidates a calculated route
        // (`clearRouteIfNeeded`), so a session opened that way starts in editing. Passing no
        // entry opens the session on the route that already exists — the review state this
        // test needs (spec: `route-planning-session` — Reviewing a route during navigation
        // is read-only is the analogous path).
        routePanelViewModel.setStartLocation(entry("Start"))
        routePanelViewModel.setDestLocation(entry("Destination"))
        routePanelViewModel.adoptRoute(route(), Vehicle.CAR)
        viewModel.openRoutePanelWithStart(null)
        routePanelViewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertTrue("the session is open", viewModel.uiState.value.showRoutePanel)
        assertEquals(RouteSessionState.REVIEWING, routePanelViewModel.sessionState.value)

        composeRule.setContent {
            RoutePanel(
                viewModel = routePanelViewModel,
                onOpenFavoritePicker = {},
                // The screen's wiring, verbatim (MapCanvasScreen.kt).
                onEndSession = { viewModel.dismissRoutePanel() },
                centerLat = 51.5136,
                centerLon = 7.4653
            )
        }
        composeRule.waitForIdle()

        // MAX carries the labelled End action; MIN its close control, which
        // RoutePanelComposeTest covers. Either way the card asks the host to close.
        composeRule.onNodeWithTag("routeEndSession").performClick()
        composeRule.waitForIdle()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertFalse(
            "the session's surface must not outlive its session",
            viewModel.uiState.value.showRoutePanel
        )
        assertNull(
            "ending without navigation clears the route",
            routePanelViewModel.uiState.value.routeEntry
        )
        assertEquals(RouteSessionState.INACTIVE, routePanelViewModel.sessionState.value)
        assertFalse("the camera lease is released", viewModel.sessionHoldsCamera())

        // Re-opening starts from the empty state, in editing (spec: No route outlives its
        // session / The header close ends the session).
        viewModel.openRoutePanelWithStart(entry("Destination"))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(RouteSessionState.EDITING, routePanelViewModel.sessionState.value)
        assertNull(routePanelViewModel.uiState.value.routeEntry)
    }
}
