package com.naviveylin.ui.route

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.data.FavoriteRepository
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
            locationService = LocationService(context())
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
            locationService = LocationService(context())
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
