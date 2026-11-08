package com.naviveylin.ui.route

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.location.LocationService
import com.naviveylin.test.ComposeSandboxRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Compose tests for the docked wide panel (spec: `route-planning-session` — Overlay docks when
 * width allows): the panel carries the session's content and its actions, and offers no control
 * that only sizes the phone card.
 *
 * The wide surface is a *constraint* the test sets on the panel, not a window change: the panel
 * decides its frame from its own `BoxWithConstraints` (`useDockedPanel(maxWidth, maxHeight)`), so
 * a parent that hands it a surface wider than it is tall renders the docked frame in the phone
 * sized Robolectric window (`RoutePanelOverlayAnchorTest` sets the same window). A
 * `@Config`/`@GraphicsMode` change would break the JNI stub's classloader rule
 * (`AGENTS.md`, "Test constraints").
 */
@RunWith(RobolectricTestRunner::class)
class RoutePanelDockedPanelTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()

    /** A wide surface: 1280x600 dp (a landscape tablet or a foldable's inner screen). */
    private val wideWidth = 1280.dp
    private val wideHeight = 600.dp

    @Before
    fun grantPreciseLocation() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        RuntimeEnvironment.setQualifiers("w411dp-h891dp")
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun launchDockedWithRoute(): RoutePanelViewModel {
        val client = FakeOSMScoutClient()
        val viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context()).also { it.defaultDispatcher = Dispatchers.Unconfined },
            locationService = LocationService(context()),
            context = context(),
            settingsStorage = SettingsStorage(context())
        )
        viewModel.openSession()
        runBlocking {
            viewModel.updateLocationsForReroute(
                LocationEntry().apply { label = "Start"; lat = 48.0; lon = 2.0; matchQuality = "coordinate" },
                LocationEntry().apply { label = "Destination"; lat = 49.0; lon = 2.6; matchQuality = "coordinate" }
            )
            client.routeToDeliver = routeWithSteps(
                startStep(lat = 48.0, lon = 2.0),
                turnStep(TurnType.RIGHT, lat = 48.5, lon = 2.3, legDistance = 120_000.0, legTime = 5_400.0, name = "B"),
                distance = 120_000.0,
                duration = 5_400.0
            )
            viewModel.calculateRoute()
        }
        composeRule.setContent {
            // The panel reads its frame from its own constraints, so the test hands it a surface
            // wider than it is tall instead of resizing the window.
            Box(modifier = Modifier.requiredSize(width = wideWidth, height = wideHeight)) {
                RoutePanel(
                    viewModel = viewModel,
                    onOpenFavoritePicker = {},
                    onEndSession = { viewModel.endSession() },
                    centerLat = 48.5,
                    centerLon = 2.3
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.routeEntry != null
        }
        composeRule.waitForIdle()
        return viewModel
    }

    @Test
    fun theDockedPanelOffersNoAnchorToggle() {
        val viewModel = launchDockedWithRoute()

        // The docked frame is what is on screen — not the phone card — so the assertions below
        // are about the wide layout and cannot pass on a portrait one.
        composeRule.onNodeWithTag(ROUTE_PANEL_CARD_TAG).assertDoesNotExist()

        // The `^`/`v` toggle sizes the phone card only (`phoneCardHeightDp`), which the docked
        // frame never reads: it is offered in neither anchor.
        viewModel.setOverlayAnchor(RouteOverlayAnchor.COMPACT)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("routeOverlayExpand").assertDoesNotExist()

        viewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("routeOverlayCollapse").assertDoesNotExist()

        // The session's own actions stay — the same content and the same session actions as the
        // expanded phone anchor: the review's Start action is on the panel, and the header's `X`
        // ends the session from the docked layout (spec: `route-planning-session` — Session
        // lifetime and its only exits).
        composeRule.onNodeWithText("Start Navigation").assertExists()
        composeRule.onNodeWithTag("routeEndSessionHeader").performClick()
        composeRule.waitForIdle()
        assertNull(viewModel.uiState.value.routeEntry)
        assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
    }
}
