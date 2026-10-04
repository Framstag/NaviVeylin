package com.naviveylin.ui.route

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Before
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Compose UI tests for the route panel search flow:
 * convenience-entry gating, swap button placement, and tap-to-open popup.
 */
@RunWith(RobolectricTestRunner::class)
class RoutePanelComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun grantPreciseLocation() {
        // The navigation gate requires the precise grant (spec: `location-permissions` —
        // Starting navigation requires precise location); the route cases below need it.
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        // Phone-sized window: the card is a share of the screen height, so the default
        // 320x470 dp window cannot show its content (runtime, not @Config — the JNI stub's
        // classloader rule forbids a sandbox change here).
        RuntimeEnvironment.setQualifiers("w411dp-h891dp")
    }

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

        // No GPS fix in test → Current Location hidden, Select Favorite shown. Existence,
        // not display: the phone card is a fixed-height surface with scrolling content, so a
        // host test's small window can put the popup below the fold.
        composeRule.onNodeWithText("Current Location").assertDoesNotExist()
        composeRule.onNodeWithText("Select Favorite").assertExists()
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

        composeRule.onNodeWithText("Select Favorite").assertExists()
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
                centerLat = 51.5136,
                centerLon = 7.4653
            )
        }
        viewModel.calculateRoute()
        // MAX is where the phone shows the route list (owner directive, 2026-10-03).
        viewModel.setOverlayAnchor(com.naviveylin.ui.route.RouteOverlayAnchor.EXPANDED)
        // Route calc lands on a background dispatcher; pump the main looper
        // until the Done state renders (spec: move-routing-summary).
        composeRule.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithText("Start Navigation").fetchSemanticsNodes().isNotEmpty()
        }
        // MAX carries the route list (with the headline distance/duration and the steps), the
        // pinned actions — and, instead of a redundant recalculation, the action that opens the
        // start/target fields (owner finding, 2026-10-03).
        composeRule.onNodeWithText("Change start & target").assertExists()
        composeRule.onNodeWithText("Start Navigation").assertExists()
        // The header carries the two statistics now (owner choice, 2026-10-03), as one line with
        // the destination above it.
        composeRule.onNodeWithTag("routeHeaderStats").assertTextContains("12.4 km", substring = true)
        composeRule.onNodeWithTag("routeHeaderStats").assertTextContains("25 min", substring = true)
        composeRule.onNodeWithText("Route · ", substring = true).assertExists()
        // The header is two lines: the destination on top, the numbers smaller below it (owner
        // request, 2026-10-03: one dense line was hard to read).
        val titleY = composeRule.onNodeWithTag("routeHeaderTitle").getBoundsInRoot().top.value
        val statsY = composeRule.onNodeWithTag("routeHeaderStats").getBoundsInRoot().top.value
        assertTrue("the statistics must sit below the destination ($titleY < $statsY)", statsY > titleY)
        composeRule.onNodeWithText("Turn left into Main Street").assertExists()

        // Selecting a step analyses it and collapses the card to MIN, so the map is next.
        composeRule.onNodeWithTag("routeStep0").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("analysedStepName").assertTextContains("1 / 2")
    }

    /**
     * The owner's finding (2026-10-03): with a route on screen the card only offered "Change
     * start & target" and "Start Navigation", and the header's close merely minimised — there
     * was no way to *leave* the analysis. The spec demands an explicit End action
     * (`route-planning-session`: "the user starts navigation or ends the session"), in both
     * card states.
     */
    @Test
    fun theCardOffersAWayOutOfTheAnalysisInBothStates() {
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
            descriptions = arrayOf("Start: A  [0.0 km]", "Turn left into Main Street  [1.2 km, 5 min]")
        }
        composeRule.setContent {
            RoutePanel(viewModel = viewModel, onOpenFavoritePicker = {}, centerLat = 51.5136, centerLon = 7.4653)
        }
        viewModel.openSession()
        viewModel.calculateRoute()
        viewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithText("Start Navigation").fetchSemanticsNodes().isNotEmpty()
        }
        // MAX: the labelled End action sits with the other actions.
        composeRule.onNodeWithTag("routeEndSession").assertExists()
        composeRule.onNodeWithText("End analysis").assertExists()
        // MIN: the analysis view has no header, so its exit is the close control in the step row.
        viewModel.setOverlayAnchor(RouteOverlayAnchor.COMPACT)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("routeEndSessionCompact").assertExists()

        composeRule.onNodeWithTag("routeEndSessionCompact").performClick()
        composeRule.waitForIdle()
        assertEquals(null, viewModel.uiState.value.routeEntry)
        assertEquals(RouteOverlayAnchor.HIDDEN, viewModel.uiState.value.overlayAnchor)
        composeRule.onNodeWithText("Start Navigation").assertDoesNotExist()
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
