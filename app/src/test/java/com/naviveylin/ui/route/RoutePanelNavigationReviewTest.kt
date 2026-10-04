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
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Compose tests for reviewing a route during navigation (spec: `route-planning-session`
 * — "Reviewing a route during navigation is read-only"): the location fields accept no
 * input and calculate/clear are not offered, while the step list, the route summary and
 * Stop Navigation stay available.
 */
@RunWith(RobolectricTestRunner::class)
class RoutePanelNavigationReviewTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun grantPreciseLocation() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        RuntimeEnvironment.setQualifiers("w411dp-h891dp")
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    /** A panel showing a calculated route, with navigation active. */
    private fun launchNavigatingPanel(): RoutePanelViewModel {
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
                com.framstag.libosmscout.client.LocationEntry().apply {
                    label = "Start"; lat = 48.0; lon = 2.0; matchQuality = "coordinate"
                },
                com.framstag.libosmscout.client.LocationEntry().apply {
                    label = "Destination"; lat = 49.0; lon = 2.6; matchQuality = "coordinate"
                }
            )
            client.routeToDeliver = RouteEntry().apply {
                routeHandle = 1L
                latitudes = doubleArrayOf(48.0, 48.5, 49.0)
                longitudes = doubleArrayOf(2.0, 2.3, 2.6)
                distance = 120_000.0
                descriptions = arrayOf("Start: A  [0.0 km]", "Right onto B  [120.0 km]")
                instructionLats = doubleArrayOf(48.0, 48.5)
                instructionLons = doubleArrayOf(2.0, 2.3)
            }
            viewModel.calculateRoute()
        }
        viewModel.setNavigating(true)
        // MAX is where the navigation review shows the list and the actions.
        viewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)

        composeRule.setContent {
            RoutePanel(
                viewModel = viewModel,
                onOpenFavoritePicker = {},
                onStopNavigation = {},
                isNavigating = true,
                centerLat = 48.5,
                centerLon = 2.3
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.routeEntry != null
        }
        composeRule.waitForIdle()
        return viewModel
    }

    @Test
    fun locationFieldsAreNotEditableDuringNavigation() {
        launchNavigatingPanel()

        // Read-only review: the editable rows are replaced by the read-only route line, so
        // there is no field to edit at all during navigation (spec: route-planning-session —
        // Reviewing a route during navigation is read-only).
        composeRule.onNodeWithTag("routeBar").assertExists()
        composeRule.onNodeWithText("Start").assertDoesNotExist()
        composeRule.onNodeWithText("Destination").assertDoesNotExist()
    }

    @Test
    fun calculateAndClearAreNotOfferedDuringNavigation() {
        launchNavigatingPanel()

        composeRule.onNodeWithText("Calculate").assertDoesNotExist()
        composeRule.onNodeWithText("Clear route").assertDoesNotExist()
    }

    @Test
    fun stopNavigationStaysAvailableAndStepsRemainAnalysable() {
        launchNavigatingPanel()

        composeRule.onNodeWithText("Stop Navigation").assertExists()

        // The read-only review keeps the list (owner directive: MAX shows it), and a tap on an
        // entry analyses that step and collapses to MIN — analysis stays available while
        // navigating.
        composeRule.onNodeWithTag("routeStep0").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("analysedStepName").assertTextContains("1 / 2")
        composeRule.onNodeWithTag("routeStep0").assertDoesNotExist()
    }
}
