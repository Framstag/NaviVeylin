package com.naviveylin.ui.route

import android.content.Context
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import java.time.Duration
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Compose UI tests for the route panel search flow:
 * convenience-entry gating, swap button placement, and tap-to-open popup.
 */
@RunWith(RobolectricTestRunner::class)
class RoutePanelComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun launchPanel() {
        val client = FakeOSMScoutClient()
        val viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context()),
            locationService = LocationService(context()),
            context = context()
        )
        composeRule.setContent {
            RoutePanel(
                viewModel = viewModel,
                onOpenFavoritePicker = {},
                onDismiss = {},
                centerLat = 51.5136,
                centerLon = 7.4653
            )
        }
    }

    @Test
    fun emptyQueryShowsSelectFavoriteEntry() {
        launchPanel()
        // Popup opens when the field gets focus (activeField != NONE)
        composeRule.onNodeWithText("Start location").performClick()

        // No GPS fix in test → Current Location hidden, Select Favorite shown
        composeRule.onNodeWithText("Current Location").assertDoesNotExist()
        composeRule.onNodeWithText("Select Favorite").assertIsDisplayed()
    }

    @Test
    fun typingHidesConvenienceEntries() {
        launchPanel()
        composeRule.onNodeWithText("Start location").performClick()
        composeRule.onNodeWithText("Start location").performTextInput("Dort")

        composeRule.onNodeWithText("Select Favorite").assertDoesNotExist()
        composeRule.onNodeWithText("Current Location").assertDoesNotExist()
    }

    @Test
    fun tappingFieldOpensResultsPopup() {
        launchPanel()
        composeRule.onNodeWithText("Start location").performClick()

        composeRule.onNodeWithText("Select Favorite").assertIsDisplayed()
    }

    @Test
    fun swapButtonIsRightOfFieldsAndVerticallyCentered() {
        launchPanel()

        val startBounds = composeRule.onNodeWithText("Start location").getBoundsInRoot()
        val destBounds = composeRule.onNodeWithText("Destination").getBoundsInRoot()
        val swapBounds = composeRule
            .onNodeWithContentDescription("Swap start and destination")
            .getBoundsInRoot()

        // Button to the right of both fields
        assertTrue(
            "swap button must be right of start field (left=${swapBounds.left.value}, field right=${startBounds.right.value})",
            swapBounds.left.value >= startBounds.right.value
        )
        assertTrue(
            "swap button must be right of destination field (left=${swapBounds.left.value}, field right=${destBounds.right.value})",
            swapBounds.left.value >= destBounds.right.value
        )

        // Vertically centered between the two fields
        val startMidY = (startBounds.top.value + startBounds.bottom.value) / 2f
        val destMidY = (destBounds.top.value + destBounds.bottom.value) / 2f
        val swapMidY = (swapBounds.top.value + swapBounds.bottom.value) / 2f
        val midY = (startMidY + destMidY) / 2f
        val tolerance = 24f
        assertTrue(
            "swap button must be vertically centered between fields (buttonY=$swapMidY, midY=$midY)",
            kotlin.math.abs(swapMidY - midY) < tolerance
        )
    }

    @Test
    fun doneStateShowsSummaryInline() {
        val client = FakeOSMScoutClient()
        val viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context()),
            locationService = LocationService(context()),
            context = context()
        )
        viewModel.setStartLocation(LocationEntry().apply { label = "Start"; lat = 51.0; lon = 7.0 })
        viewModel.setDestLocation(LocationEntry().apply { label = "Dest"; lat = 52.0; lon = 8.0 })
        client.routeToDeliver = RouteEntry().apply {
            distance = 12400.0
            duration = 1500.0
            latitudes = doubleArrayOf(51.0, 52.0)
            longitudes = doubleArrayOf(7.0, 8.0)
            descriptions = arrayOf(
                "--- Route ---",
                "Start: A  [0.0 km]",
                "Turn left into Main Street  [1.2 km, 5 min]"
            )
        }
        composeRule.setContent {
            RoutePanel(
                viewModel = viewModel,
                onOpenFavoritePicker = {},
                onDismiss = {},
                centerLat = 51.5136,
                centerLon = 7.4653
            )
        }
        viewModel.calculateRoute()
        // Route calc lands on a background dispatcher; pump the main looper
        // until the Done state renders (spec: move-routing-summary).
        composeRule.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithText("Start Navigation").fetchSemanticsNodes().isNotEmpty()
        }
        // Panel stays open: Calculate + Start Navigation + inline summary.
        // (Composition order in the panel Column is the layout order:
        // Calculate → Start Navigation → summary.)
        composeRule.onNodeWithText("Calculate").assertExists()
        composeRule.onNodeWithText("Start Navigation").assertExists()
        composeRule.onNodeWithText("12.4 km").assertExists()
        composeRule.onNodeWithText("25 min").assertExists()
        composeRule.onNodeWithText("Turn left into Main Street").assertExists()
    }

    @Test
    fun showRouteOpensSummaryDialog() {
        val client = FakeOSMScoutClient()
        val viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context()),
            locationService = LocationService(context()),
            context = context()
        )
        viewModel.setStartLocation(LocationEntry().apply { label = "Start"; lat = 51.0; lon = 7.0 })
        viewModel.setDestLocation(LocationEntry().apply { label = "Dest"; lat = 52.0; lon = 8.0 })
        client.routeToDeliver = RouteEntry().apply {
            distance = 12400.0
            duration = 1500.0
            latitudes = doubleArrayOf(51.0, 52.0)
            longitudes = doubleArrayOf(7.0, 8.0)
            descriptions = arrayOf("--- Route ---", "Start: A  [0.0 km]")
        }
        composeRule.setContent {
            RoutePanel(
                viewModel = viewModel,
                onOpenFavoritePicker = {},
                onDismiss = {},
                centerLat = 51.5136,
                centerLon = 7.4653
            )
        }
        viewModel.calculateRoute()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithText("Show Route").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Show Route").performSemanticsAction(SemanticsActions.OnClick)
        // The panel signals the summary dialog; MapCanvasScreen renders it and
        // hides the panel (spec: route-summary-dialog — Show Route action).
        assertTrue("Show Route must set showSummaryDialog", viewModel.uiState.value.showSummaryDialog)
    }

    /** Entry ~1.0 km north of the test center (0.009° latitude). */
    private fun distanceEntry(): LocationEntry = LocationEntry().apply {
        label = "Test Place"
        lat = 51.5226
        lon = 7.4653
    }

    @Test
    fun searchResultShowsDistanceFromMapCenter() {
        val client = FakeOSMScoutClient()
        val viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context()),
            locationService = LocationService(context()),
            context = context()
        )
        client.nextSearchResults = arrayOf(distanceEntry())
        composeRule.setContent {
            RoutePanel(
                viewModel = viewModel,
                onOpenFavoritePicker = {},
                onDismiss = {},
                centerLat = 51.5136,
                centerLon = 7.4653
            )
        }
        composeRule.onNodeWithText("Start location").performClick()
        composeRule.onNodeWithText("Start location").performTextInput("Dort")

        // Debounce (300 ms) runs on the main looper; advance it, then wait for
        // the background search result to land and render.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithText("1.0 km").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
