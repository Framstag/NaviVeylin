package com.naviveylin.ui.map

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import com.naviveylin.test.engineUnderTest
import com.naviveylin.ui.route.ROUTE_PANEL_CARD_TAG
import com.naviveylin.ui.route.RouteOverlayAnchor
import com.naviveylin.ui.route.RoutePanel
import com.naviveylin.ui.route.RoutePanelViewModel
import com.naviveylin.ui.route.RouteSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * The phone map screen's layer bands (spec: `map-canvas-screen` — Phone map overlay layer stack,
 * Every map screen element is assigned to a declared band, No surface opens between the map and its
 * chrome, The map menu is composed above the map chrome, Snackbar messages are composed above the
 * map and its in-window surfaces; spec: `route-planning-session` — The session surface is composed
 * above the phone's map chrome, and The overlay never covers the map controls).
 *
 * The screen itself cannot be hosted in this suite: `MapCanvasScreen` takes three Hilt view models
 * and no Hilt test host exists here (`RouteSessionCardExitTest` and its siblings compose the
 * individual composables instead). So each case composes the band structure with the members the
 * claim is about — a tagged control in the chrome band, a tagged surface in the modal band, the real
 * session card — and asserts the *effect* of the band: which node receives a tap injected at a point
 * both could claim, and what the members measure. The paint verdict over a rendered map is the
 * device step (`pixel-check`), not this file.
 *
 * The tap assertions inject a touch at a point in root coordinates
 * (`onRoot().performTouchInput { click(point) }`), so the node that receives it is the one the
 * hierarchy puts on top — the same question the device's tap-consumer diagnosis asks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapLayerStackComposeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val taps = mutableListOf<String>()

    /**
     * The centre of [tag]'s bounds as a pixel offset in root coordinates: `getBoundsInRoot()`
     * returns Dp, the touch injection takes pixels.
     */
    private fun centerInRoot(tag: String): Offset {
        val bounds = composeRule.onNodeWithTag(tag).getBoundsInRoot()
        return with(composeRule.density) {
            Offset(((bounds.left + bounds.right) / 2).toPx(), ((bounds.top + bounds.bottom) / 2).toPx())
        }
    }

    /** A control in the chrome band and a full-screen surface in the modal band, overlapping. */
    private fun composeChromeUnderSurface(chromeFirst: Boolean) {
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                if (chromeFirst) {
                    Box(Modifier.fillMaxSize().zIndex(MapLayer.CHROME.z)) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .align(Alignment.BottomCenter)
                                .testTag("chrome-control")
                                .clickable { taps += "chrome" }
                        )
                    }
                    Box(Modifier.fillMaxSize().zIndex(MapLayer.MODAL.z)) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .testTag("modal-surface")
                                .clickable { taps += "modal" }
                        )
                    }
                } else {
                    // Same bands, declared the other way round: the band decides, not the order.
                    Box(Modifier.fillMaxSize().zIndex(MapLayer.MODAL.z)) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .testTag("modal-surface")
                                .clickable { taps += "modal" }
                        )
                    }
                    Box(Modifier.fillMaxSize().zIndex(MapLayer.CHROME.z)) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .align(Alignment.BottomCenter)
                                .testTag("chrome-control")
                                .clickable { taps += "chrome" }
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the surface takes the tap where it overlaps the chrome`() {
        composeChromeUnderSurface(chromeFirst = true)
        composeRule.onRoot().performTouchInput { click(centerInRoot("chrome-control")) }
        composeRule.waitForIdle()
        assertEquals(listOf("modal"), taps)
    }

    @Test
    fun `declaration order does not change the stacking`() {
        composeChromeUnderSurface(chromeFirst = false)
        composeRule.onRoot().performTouchInput { click(centerInRoot("chrome-control")) }
        composeRule.waitForIdle()
        assertEquals(listOf("modal"), taps)
    }

    @Test
    fun `a control keeps its measured size inside its band`() {
        var measuredHeight = 0
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().zIndex(MapLayer.CHROME.z)) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .testTag("chrome-control")
                            .onSizeChanged { measuredHeight = it.height }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        // The band wrapper must not change what a member measures: the inset callbacks the screen
        // hangs on the turn card and the routing status card depend on this (spec: smooth-follow —
        // visible-area scenarios).
        assertEquals(with(composeRule.density) { 56.dp.roundToPx() }, measuredHeight)
    }

    @Test
    fun `chrome the surface does not reach stays untouched`() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().zIndex(MapLayer.CHROME.z)) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .align(Alignment.TopStart)
                            .testTag("chrome-control")
                            .clickable { taps += "chrome" }
                    )
                }
                // A surface that reaches only the bottom half of the screen.
                Box(Modifier.fillMaxSize().zIndex(MapLayer.MODAL.z)) {
                    Box(
                        Modifier
                            .fillMaxSize(0.5f)
                            .align(Alignment.BottomCenter)
                            .testTag("modal-surface")
                            .clickable { taps += "modal" }
                    )
                }
            }
        }
        composeRule.onRoot().performTouchInput { click(centerInRoot("chrome-control")) }
        composeRule.waitForIdle()
        assertEquals("the untouched control must keep its taps", listOf("chrome"), taps)
    }

    @Test
    fun `the menu band covers the chrome`() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().zIndex(MapLayer.CHROME.z)) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .align(Alignment.TopStart)
                            .testTag("chrome-control")
                            .clickable { taps += "chrome" }
                    )
                }
                Box(Modifier.fillMaxSize().zIndex(MapLayer.MENU.z)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag("menu-scrim")
                            .clickable { taps += "menu-dismiss" }
                    )
                }
            }
        }
        composeRule.onRoot().performTouchInput { click(centerInRoot("chrome-control")) }
        composeRule.waitForIdle()
        assertEquals(listOf("menu-dismiss"), taps)

        val control = composeRule.onNodeWithTag("chrome-control").getBoundsInRoot()
        val scrim = composeRule.onNodeWithTag("menu-scrim").getBoundsInRoot()
        assertTrue(
            "the on-map control must lie inside the menu's covering area, " +
                "control=$control scrim=$scrim",
            control.left >= scrim.left && control.right <= scrim.right &&
                control.top >= scrim.top && control.bottom <= scrim.bottom
        )
    }

    @Test
    fun `a snackbar is composed above the map band`() {
        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            LaunchedEffect(Unit) { hostState.showSnackbar("Harness message") }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().zIndex(MapLayer.MAP.z)) {
                    Box(Modifier.fillMaxSize().testTag("map-frame"))
                }
                Box(Modifier.fillMaxSize().zIndex(MapLayer.SNACKBAR.z)) {
                    SnackbarHost(
                        hostState = hostState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Harness message").fetchSemanticsNodes().isNotEmpty()
        }
        val message = composeRule.onNodeWithText("Harness message")
        message.assertIsDisplayed()
        val bounds = message.getBoundsInRoot()
        val root = composeRule.onRoot().getBoundsInRoot()
        assertTrue(
            "the message must sit inside the screen, message=$bounds root=$root",
            bounds.left >= root.left && bounds.right <= root.right &&
                bounds.top >= root.top && bounds.bottom <= root.bottom
        )
    }

    // --- the session surface over the navigation chrome (spec: route-planning-session) ---

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var routePanelViewModel: RoutePanelViewModel

    @Before
    fun setUpSession() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        RuntimeEnvironment.setQualifiers("w411dp-h891dp")
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient()
        val locationService = LocationService(context)
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
        viewModel.setNavigationViewModel(
            NavigationViewModel(engineUnderTest({ client }, LocationService(context), context))
        )
        viewModel.setRoutePanelViewModel(routePanelViewModel)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDownSession() {
        if (this::viewModel.isInitialized) viewModel.cancelScopeForTest()
    }

    private fun entry(label: String): LocationEntry = LocationEntry().apply {
        this.label = label
        lat = 51.5136
        lon = 7.4653
        matchQuality = "coordinate"
    }

    private fun route(): RouteEntry = RouteEntry().apply {
        distance = 12_400.0
        duration = 1_500.0
        latitudes = doubleArrayOf(51.5136, 51.5226)
        longitudes = doubleArrayOf(7.4653, 7.4653)
        descriptions = arrayOf("Start: A  [0.0 km]", "Turn left into Main Street  [1.2 km, 5 min]")
    }

    /** Opens the session in REVIEWING on a two-point route, as `RouteSessionCardExitTest` does. */
    private fun openSessionOnRoute() {
        routePanelViewModel.setStartLocation(entry("Start"))
        routePanelViewModel.setDestLocation(entry("Destination"))
        routePanelViewModel.adoptRoute(route(), Vehicle.CAR)
        viewModel.openRoutePanelWithStart(null)
        routePanelViewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(RouteSessionState.REVIEWING, routePanelViewModel.sessionState.value)
    }

    @Test
    fun `the session card takes the tap the navigation column would take`() {
        openSessionOnRoute()
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                // Chrome band: the bottom band the navigation view puts its right-side widget
                // column and its routing status card in (MapCanvasScreen.kt, navigation overlay
                // block) — the region the session card shares with it.
                Box(Modifier.fillMaxSize().zIndex(MapLayer.CHROME.z)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.3f)
                            .align(Alignment.BottomCenter)
                            .testTag("nav-column-control")
                            .clickable { taps += "chrome" }
                    )
                }
                Box(Modifier.fillMaxSize().zIndex(MapLayer.MODAL.z)) {
                    RoutePanel(
                        viewModel = routePanelViewModel,
                        onOpenFavoritePicker = {},
                        onEndSession = { viewModel.dismissRoutePanel() },
                        centerLat = 51.5136,
                        centerLon = 7.4653
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val card = composeRule.onNodeWithTag(ROUTE_PANEL_CARD_TAG).getBoundsInRoot()
        val column = composeRule.onNodeWithTag("nav-column-control").getBoundsInRoot()
        assertTrue(
            "the column and the card must overlap for this case to mean anything, " +
                "card=$card column=$column",
            card.top <= column.bottom && card.bottom >= column.top &&
                card.left <= column.right && card.right >= column.left
        )
        // The card does not reach the turn card's band: the driver keeps the next instruction
        // (spec: route-planning-session — The turn card stays visible).
        assertTrue("the card must not cover the turn card's band, card=$card", card.top > 96.dp)

        // A tap on the card's own action reaches the card, not the column underneath it.
        val endAction = centerInRoot("routeEndSession")
        composeRule.onRoot().performTouchInput { click(endAction) }
        composeRule.waitForIdle()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals("the chrome must not receive the card's tap", emptyList<String>(), taps)
        assertFalse(
            "the card must have handled the tap and ended the session",
            viewModel.uiState.value.showRoutePanel
        )
    }

    @Test
    fun `the right-side control column sits fully above the session card`() {
        // The card reports the height it covers; the screen hands that number to the column as its
        // inset (spec: route-planning-session — The overlay never covers the map controls; owner
        // finding 2026-10-03: the min strip covered the zoom-out button). The screen itself cannot
        // be hosted here, so the case composes the two real members in their two bands and
        // reproduces the screen's wiring one-for-one (`MapCanvasScreen.kt`: `onOverlayHeightChanged
        // = { height -> viewModel.setOverlayCoveredPx(height); overlayBottomInset = height }` and
        // `bottomInset = state.overlayCoveredPx.toDp()`): the inset is the number the real card
        // reported, not a constant — with a zero inset these controls fall to the screen's bottom
        // edge, i.e. into the card.
        openSessionOnRoute()
        var reportedCoveredPx by mutableStateOf(0)
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().zIndex(MapLayer.CHROME.z)) {
                    MapRightWidgetColumn(
                        isLandscape = true,
                        mapAngleRadians = 0.0,
                        gpsFixQuality = GpsFixQuality.GOOD,
                        isDarkPresentation = false,
                        onCenterClick = {},
                        onToggleOrientation = {},
                        speedInput = SpeedWidgetInput(50.0, 50.0),
                        canZoomIn = true,
                        canZoomOut = true,
                        currentMag = 12.0,
                        onZoomIn = {},
                        onZoomOut = {},
                        bottomInset = with(LocalDensity.current) { reportedCoveredPx.toDp() },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
                Box(Modifier.fillMaxSize().zIndex(MapLayer.MODAL.z)) {
                    RoutePanel(
                        viewModel = routePanelViewModel,
                        onOpenFavoritePicker = {},
                        onEndSession = { viewModel.dismissRoutePanel() },
                        centerLat = 51.5136,
                        centerLon = 7.4653,
                        onOverlayHeightChanged = { reportedCoveredPx = it }
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { reportedCoveredPx > 0 }
        composeRule.waitForIdle()

        val card = composeRule.onNodeWithTag(ROUTE_PANEL_CARD_TAG).getBoundsInRoot()
        val zoomOut = composeRule.onNodeWithContentDescription("Zoom out").getBoundsInRoot()
        // Premise: the inset the column received is the card's covered height — a report of 0
        // would leave the column at the screen's bottom edge.
        val coveredDp = with(composeRule.density) { reportedCoveredPx.toDp().value }
        assertEquals(
            "the column's inset must be the card's covered height, card=$card zoomOut=$zoomOut",
            card.bottom.value - card.top.value,
            coveredDp,
            1f
        )
        // The claim: the lowest control of the column lies fully above the card's top edge.
        assertTrue(
            "the column's controls must sit fully above the card (zoomOut=$zoomOut card=$card)",
            zoomOut.bottom.value <= card.top.value + 1f
        )
    }
}
