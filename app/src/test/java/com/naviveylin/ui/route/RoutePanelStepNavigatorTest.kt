package com.naviveylin.ui.route

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Compose tests for the phone's MIN overlay (owner directive, 2026-10-03): after selecting a
 * step the card collapses to a small overlay carrying the analysed step's position, its name
 * and the two step controls. The controls move the analysed step (camera and highlight
 * follow), the ends are no-ops, tapping the name returns to the list, and the selection
 * survives a mode change.
 */
@RunWith(RobolectricTestRunner::class)
class RoutePanelStepNavigatorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun grantPreciseLocation() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        RuntimeEnvironment.setQualifiers("w411dp-h891dp")
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    /** MIN overlay showing a two-step route. */
    private fun launchMinWithRoute(): RoutePanelViewModel {
        val client = FakeOSMScoutClient()
        val viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context()),
            locationService = LocationService(context()),
            context = context()
        )
        viewModel.openSession()
        runBlocking {
            viewModel.updateLocationsForReroute(
                LocationEntry().apply { label = "Start"; lat = 48.0; lon = 2.0; matchQuality = "coordinate" },
                LocationEntry().apply { label = "Destination"; lat = 49.0; lon = 2.6; matchQuality = "coordinate" }
            )
            client.routeToDeliver = RouteEntry().apply {
                routeHandle = 1L
                latitudes = doubleArrayOf(48.0, 48.5, 49.0)
                longitudes = doubleArrayOf(2.0, 2.3, 2.6)
                distance = 120_000.0
                duration = 5_400.0
                // The native start line owns a zero-length leg, so it carries no values: the route
                // starts on the first step that has one (spec: route-analysis — Step values describe
                // the step's own leg).
                descriptions = arrayOf(
                    "Start: A  []",
                    "Right onto B  [120.0 km]",
                    "Left onto C  [5.0 km]"
                )
                instructionLats = doubleArrayOf(48.0, 48.5, 49.0)
                instructionLons = doubleArrayOf(2.0, 2.3, 2.6)
            }
            viewModel.calculateRoute()
        }
        // MIN (the session lands in MAX while editing and the selection collapses to MIN).
        viewModel.setOverlayAnchor(RouteOverlayAnchor.COMPACT)
        composeRule.setContent {
            RoutePanel(
                viewModel = viewModel,
                onOpenFavoritePicker = {},
                centerLat = 48.5,
                centerLon = 2.3
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.routeEntry != null
        }
        // The adoption lands the card in MAX (the list is the result); MIN is what the tests
        // here exercise, so move it there after the route has arrived.
        viewModel.setOverlayAnchor(RouteOverlayAnchor.COMPACT)
        composeRule.waitForIdle()
        return viewModel
    }

    @Test
    fun nextMovesTheAnalysedStepAndShowsWhereItIs() {
        val viewModel = launchMinWithRoute()

        // A calculated route starts on its first step that carries a leg (owner finding, 2026-10-03).
        assertEquals(1, viewModel.uiState.value.analysedStepIndex)
        composeRule.onNodeWithTag("analysedStepName").assertTextContains("Right onto B")
        composeRule.onNodeWithTag("analysedStepName").assertTextContains("2 / 3")

        composeRule.onNodeWithTag("routeStepNext").performClick()
        composeRule.waitForIdle()
        assertEquals(2, viewModel.uiState.value.analysedStepIndex)
        composeRule.onNodeWithTag("analysedStepName").assertTextContains("Left onto C")
        composeRule.onNodeWithTag("analysedStepName").assertTextContains("3 / 3")
    }

    @Test
    fun theEndsAreNoOpsAndTheControlsSaySo() {
        val viewModel = launchMinWithRoute()

        // Back from the current step reaches the start line, which owns no leg.
        composeRule.onNodeWithTag("routeStepPrevious").performClick()
        composeRule.waitForIdle()
        assertEquals(0, viewModel.uiState.value.analysedStepIndex)
        // On the first step: back has nothing to step to.
        composeRule.onNodeWithTag("routeStepPrevious").assertIsNotEnabled()

        composeRule.onNodeWithTag("routeStepNext").performClick()
        composeRule.waitForIdle()
        assertEquals(1, viewModel.uiState.value.analysedStepIndex)

        composeRule.onNodeWithTag("routeStepNext").performClick()
        composeRule.waitForIdle()
        assertEquals(2, viewModel.uiState.value.analysedStepIndex)

        // Last step reached: further "next" changes nothing.
        composeRule.onNodeWithTag("routeStepNext").performClick()
        composeRule.waitForIdle()
        assertEquals(2, viewModel.uiState.value.analysedStepIndex)
        composeRule.onNodeWithTag("routeStepNext").assertIsNotEnabled()

        // And back walks the same steps down again.
        composeRule.onNodeWithTag("routeStepPrevious").performClick()
        composeRule.waitForIdle()
        assertEquals(1, viewModel.uiState.value.analysedStepIndex)
    }

    @Test
    fun theStepNameShowsTheListAndTheSelectionSurvivesTheModeChange() {
        val viewModel = launchMinWithRoute()
        composeRule.onNodeWithTag("routeStepNext").performClick()
        composeRule.waitForIdle()
        assertEquals(2, viewModel.uiState.value.analysedStepIndex)

        // Tapping the step name in MIN brings the list (MAX) back…
        composeRule.onNodeWithTag("analysedStepName").performClick()
        composeRule.waitForIdle()
        assertEquals(RouteOverlayAnchor.EXPANDED, viewModel.uiState.value.overlayAnchor)
        composeRule.onNodeWithTag("routeStep1").assertExists()

        // …and the analysed step survives it, and MIN keeps showing it.
        viewModel.setOverlayAnchor(RouteOverlayAnchor.COMPACT)
        composeRule.waitForIdle()
        assertEquals(2, viewModel.uiState.value.analysedStepIndex)
        composeRule.onNodeWithTag("analysedStepName").assertTextContains("3 / 3")
    }

    @Test
    fun theMinOverlayHasNoListAndNoNavigatorWithoutARoute() {
        val client = FakeOSMScoutClient()
        val viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context()),
            locationService = LocationService(context()),
            context = context()
        )
        viewModel.openSession()
        viewModel.setOverlayAnchor(RouteOverlayAnchor.COMPACT)
        composeRule.setContent {
            RoutePanel(
                viewModel = viewModel,
                onOpenFavoritePicker = {},
                centerLat = 48.5,
                centerLon = 2.3
            )
        }
        composeRule.waitForIdle()

        // Without a route there is nothing to minimise to: the card stays in MAX (editing).
        composeRule.onNodeWithTag("analysedStepName").assertDoesNotExist()
        composeRule.onNodeWithTag("routeStep0").assertDoesNotExist()
        composeRule.onNodeWithText("Calculate").assertExists()
    }
}
