package com.naviveylin.ui.route

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
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
 * Compose tests for the session's stopped state (spec: `route-planning-session` — Grace period
 * after navigation is stopped): while the grace runs the surface offers Restart and End instead of
 * the planning actions, in the phone card's action band. The device measured the state being
 * reached with no surface of its own — the card kept showing the planning content (`TODO.md` §140).
 */
@RunWith(RobolectricTestRunner::class)
class RoutePanelStoppedStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun grantPreciseLocation() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        RuntimeEnvironment.setQualifiers("w411dp-h891dp")
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun entry(label: String) = LocationEntry().apply {
        this.label = label
        lat = 48.5
        lon = 2.3
        matchQuality = "coordinate"
    }

    /**
     * A panel whose session is in the stopped state: a route was adopted, the session was opened on
     * it, navigation started, and a session opened during navigation was then stopped.
     *
     * The fixture adopts the route synchronously instead of calculating it: a pending
     * `calculateRoute()` completes on a real dispatcher *after* the assertions (and during
     * composition), and its completion moves the session out of the stopped state again.
     */
    private fun launchStoppedPanel(
        onRestart: () -> Unit = {},
        onEnd: () -> Unit = {}
    ): RoutePanelViewModel {
        val client = FakeOSMScoutClient()
        val viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context()),
            locationService = LocationService(context()),
            context = context()
        )
        runBlocking {
            viewModel.updateLocationsForReroute(entry("Start"), entry("Destination"))
        }
        viewModel.adoptRoute(
            RouteEntry().apply {
                routeHandle = 1L
                latitudes = doubleArrayOf(48.0, 48.5, 49.0)
                longitudes = doubleArrayOf(2.0, 2.3, 2.6)
                distance = 120_000.0
                descriptions = arrayOf("Start: A  [0.0 km]", "Right onto B  [120.0 km]")
                instructionLats = doubleArrayOf(48.0, 48.5)
                instructionLons = doubleArrayOf(2.0, 2.3)
            },
            Vehicle.CAR
        )
        viewModel.openSession()
        viewModel.setNavigating(true)
        viewModel.openSession()
        viewModel.setNavigating(false)
        assertEquals(RouteSessionState.STOPPED, viewModel.sessionState.value)
        // MAX is where the session's actions live on the phone.
        viewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)

        composeRule.setContent {
            RoutePanel(
                viewModel = viewModel,
                onOpenFavoritePicker = {},
                onStopNavigation = {},
                onRestartNavigation = onRestart,
                onEndSession = onEnd,
                centerLat = 48.5,
                centerLon = 2.3
            )
        }
        composeRule.waitForIdle()
        return viewModel
    }

    @Test
    fun stoppedStateOffersRestartAndEnd() {
        launchStoppedPanel()

        composeRule.onNodeWithText("Restart navigation").assertExists()
        composeRule.onNodeWithTag("routeEndSessionStopped").assertExists()
    }

    @Test
    fun stoppedStateOffersNoStartOrCalculate() {
        launchStoppedPanel()

        composeRule.onNodeWithText("Start Navigation").assertDoesNotExist()
        composeRule.onNodeWithText("Calculate").assertDoesNotExist()
        composeRule.onNodeWithText("Clear route").assertDoesNotExist()
    }

    @Test
    fun stoppedStateRestartInvokesTheRestartAction() {
        var restarts = 0
        var ends = 0
        launchStoppedPanel(onRestart = { restarts++ }, onEnd = { ends++ })

        composeRule.onNodeWithTag("restartNavigation").performClick()
        composeRule.waitForIdle()

        assertEquals("Restart is the action the stopped state invokes", 1, restarts)
        assertEquals("End must not be triggered by the Restart action", 0, ends)
    }

    @Test
    fun stoppedStateEndInvokesTheEndAction() {
        var restarts = 0
        var ends = 0
        launchStoppedPanel(onRestart = { restarts++ }, onEnd = { ends++ })

        composeRule.onNodeWithTag("routeEndSessionStopped").performClick()
        composeRule.waitForIdle()

        assertEquals("End is the action the stopped state invokes", 1, ends)
        assertEquals("Restart must not be triggered by the End action", 0, restarts)
    }
}
