package com.naviveylin.ui.route

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Compose and unit tests for the session's overlay anchors (spec:
 * `route-planning-session` — Session overlay anchors (max and min)): the two card heights are
 * fixed shares of the screen (so the map always keeps its area), the compact card keeps the
 * fields, the stats and the session actions while hiding the vehicle selector, the expand
 * control brings the vehicle selector and the step navigator back, and the header's close
 * control ends the session instead of hiding the card.
 */
@RunWith(RobolectricTestRunner::class)
class RoutePanelOverlayAnchorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun grantPreciseLocation() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        // A phone-sized window: the card's heights are shares of the *screen*, so the default
        // Robolectric window (320x470 dp) would leave a 164 dp card whose content cannot be
        // seen at all. Set at runtime on purpose — a @Config/@GraphicsMode sandbox change is
        // forbidden for classes that load the JNI stub (AGENTS.md classloader rule).
        RuntimeEnvironment.setQualifiers("w411dp-h891dp")
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the overlay docks when the surface is wider than it is tall`() {
        // A wide surface (tablet, foldable, landscape): the panel docks so the map keeps
        // the rest. A phone in portrait keeps the bottom sheet.
        assertEquals(true, useDockedPanel(widthPx = 1200f, heightPx = 800f))
        assertEquals(true, useDockedPanel(widthPx = 800f, heightPx = 799f))
        assertEquals(false, useDockedPanel(widthPx = 800f, heightPx = 1200f))
        assertEquals(false, useDockedPanel(widthPx = 800f, heightPx = 800f))
    }

    @Test
    fun `the session anchors are the two overlay sizes`() {
        assertEquals(2, RouteOverlayAnchor.entries.size)
        assertEquals(RouteOverlayAnchor.EXPANDED, RouteOverlayAnchor.valueOf("EXPANDED"))
        assertEquals(RouteOverlayAnchor.COMPACT, RouteOverlayAnchor.valueOf("COMPACT"))
    }

    /**
     * Panel showing a calculated route, starting at the compact anchor.
     *
     * [onOverlayHeightChanged] is the screen's probe: `MapCanvasScreen` hands the card's reported
     * height to the map's overview fit and to the right-side control column's inset.
     */
    private fun launchWithRoute(
        onOverlayHeightChanged: (Int) -> Unit = {}
    ): RoutePanelViewModel {
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
                descriptions = arrayOf("Start: A  []", "Right onto B  [120.0 km]")
                instructionLats = doubleArrayOf(48.0, 48.5)
                instructionLons = doubleArrayOf(2.0, 2.3)
            }
            viewModel.calculateRoute()
        }
        composeRule.setContent {
            RoutePanel(
                viewModel = viewModel,
                onOpenFavoritePicker = {},
                // The host's half of the exit: the session ends (the screen additionally
                // closes the surface, `RouteSessionCardExitTest`). Without it these tests
                // would only prove the click landed somewhere.
                onEndSession = { viewModel.endSession() },
                onOverlayHeightChanged = onOverlayHeightChanged,
                centerLat = 48.5,
                centerLon = 2.3
            )
        }
        // The route result lands through the view model's dispatcher, so wait for the adoption
        // instead of a single idle pass (the mode depends on it).
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.routeEntry != null
        }
        composeRule.waitForIdle()
        return viewModel
    }

    @Test
    fun `the phone card heights are fixed shares of the screen`() {
        // A Pixel-8-sized portrait screen in dp; the exact number does not matter, only that
        // the expanded card leaves the map at least 55 % (spec: anchors; design D8/D10).
        val screen = 914f
        val expanded = phoneCardHeightDp(RouteOverlayAnchor.EXPANDED, screen)
        assertEquals(screen * 0.45f, expanded, 1e-4f)
        assertTrue("the expanded card must leave 55 % of the screen to the map", expanded <= screen * 0.55f)

        val compact = phoneCardHeightDp(RouteOverlayAnchor.COMPACT, screen)
        assertTrue("the min card stays one line's height", compact <= 160f)
        assertTrue("the min card must leave 65 % of the screen to the map", compact <= screen * 0.65f)
    }

    @Test
    fun `a short screen scales the compact card instead of the cap`() {
        // 18 % of a 500 dp screen is 90 dp, well below the cap: the share wins, so the map
        // keeps its area on a small device too.
        assertEquals(90f, phoneCardHeightDp(RouteOverlayAnchor.COMPACT, 500f), 1e-4f)
    }

    @Test
    fun `the compact card sits at the bottom edge, not at the top`() {
        launchWithRoute()

        // Found on the device (2026-10-03): `BoxWithConstraints` without `fillMaxSize()` sizes
        // itself to its content, so `align(BottomCenter)` aligned the card inside its own box
        // and the card rendered at the TOP of the screen while the fit happily used the height
        // it reported. The card's bottom edge must be the root's bottom edge.
        val card = composeRule.onNodeWithTag(ROUTE_PANEL_CARD_TAG).getBoundsInRoot()
        val root = composeRule.onRoot().getBoundsInRoot()
        assertEquals(root.bottom.value, card.bottom.value, 2f)
        assertTrue(
            "the card must leave map area above it (card top=${card.top.value})",
            card.top.value > 0f
        )
    }

    @Test
    fun `the card reports the covered height it occupies`() {
        val reportedPx = mutableListOf<Int>()
        val viewModel = launchWithRoute(onOverlayHeightChanged = { reportedPx += it })

        // The card reports the height it covers to the map screen, which is what the overview fit
        // and the right-side control column's inset are given (spec: `route-planning-session` —
        // Planning card content and its pinned actions, "the card reports the height it has";
        // `MapCanvasScreen` passes the number to `setOverlayCoveredPx` and to `bottomInset`). The
        // card is bottom-anchored, so the band it covers is its own height.
        viewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)
        composeRule.waitForIdle()
        val root = composeRule.onRoot().getBoundsInRoot()
        val maxCard = composeRule.onNodeWithTag(ROUTE_PANEL_CARD_TAG).getBoundsInRoot()
        val coveredInMax = with(composeRule.density) { reportedPx.last().toDp().value }
        assertEquals(
            "the max card must report the height it covers (card top=${maxCard.top.value}, " +
                "root bottom=${root.bottom.value})",
            root.bottom.value - maxCard.top.value,
            coveredInMax,
            1f
        )

        // The report follows the anchor: min covers a smaller band, so the map's fit and the
        // column's inset move with it.
        viewModel.setOverlayAnchor(RouteOverlayAnchor.COMPACT)
        composeRule.waitForIdle()
        val minCard = composeRule.onNodeWithTag(ROUTE_PANEL_CARD_TAG).getBoundsInRoot()
        val coveredInMin = with(composeRule.density) { reportedPx.last().toDp().value }
        assertEquals(
            "the min card must report the height it covers (card top=${minCard.top.value}, " +
                "root bottom=${root.bottom.value})",
            root.bottom.value - minCard.top.value,
            coveredInMin,
            1f
        )
        assertTrue(
            "min must cover less than max ($coveredInMin vs $coveredInMax)",
            coveredInMin < coveredInMax
        )
    }

    @Test
    fun maxModeHugsItsContentAndStaysWithinItsShare() {
        val viewModel = launchWithRoute()
        viewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)
        composeRule.waitForIdle()

        val card = composeRule.onNodeWithTag(ROUTE_PANEL_CARD_TAG).getBoundsInRoot()
        val root = composeRule.onRoot().getBoundsInRoot()
        val cardHeight = card.bottom.value - card.top.value
        val rootHeight = root.bottom.value - root.top.value
        // Bottom-anchored…
        assertEquals(root.bottom.value, card.bottom.value, 2f)
        // …and never more than its share of the screen (it hugs its content, so this is an
        // upper bound rather than the height it always takes).
        assertTrue(
            "max card height=$cardHeight must stay within its share of $rootHeight",
            cardHeight <= rootHeight * EXPANDED_CARD_FRACTION + 2f
        )
    }

    @Test
    fun calculatingLandsInMaxWithTheList() {
        val viewModel = launchWithRoute()

        // The result of a calculation is the route's step list, so the card is in MAX right
        // after it (owner directive, 2026-10-03).
        assertEquals(RouteOverlayAnchor.EXPANDED, viewModel.uiState.value.overlayAnchor)
        composeRule.onNodeWithText("Steps").assertExists()
        composeRule.onNodeWithTag("routeStep0").assertExists()
    }

    @Test
    fun minModeShowsOnlyTheAnalysedStep() {
        val viewModel = launchWithRoute()
        // Minimise: MIN is the small overlay (a selection from the list lands here by itself).
        viewModel.setOverlayAnchor(RouteOverlayAnchor.COMPACT)
        composeRule.waitForIdle()

        // MIN is the small overlay: the analysed step's position, its name and the two step
        // controls — nothing else (owner directive, 2026-10-03).
        assertEquals(RouteOverlayAnchor.COMPACT, viewModel.uiState.value.overlayAnchor)
        assertNotNull("route must be adopted", viewModel.uiState.value.routeEntry)
        assertEquals(ActiveField.NONE, viewModel.uiState.value.activeField)
        // The name node merges its texts (it is the tap target), so the position indicator is
        // read from it: the route starts on its first step that carries a leg, the start line above
        // it owning none (spec: route-analysis — Step values describe the step's own leg).
        composeRule.onNodeWithTag("analysedStepName").assertTextContains("2 / 2")
        composeRule.onNodeWithText("Vehicle").assertDoesNotExist()
        composeRule.onNodeWithText("Steps").assertDoesNotExist()
        composeRule.onNodeWithTag("routeStep0").assertDoesNotExist()
        composeRule.onNodeWithText("Start Navigation").assertDoesNotExist()
    }

    @Test
    fun maxModeShowsTheStepListAndItsActions() {
        val viewModel = launchWithRoute()
        viewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)
        composeRule.waitForIdle()

        // MAX is the full card: the read-only route line (the fields collapse into it while a
        // route is on screen, so the list starts on screen), the route list (the phone has it in
        // MAX, owner directive 2026-10-03) and the pinned actions.
        composeRule.onNodeWithTag("routeBar").assertExists()
        composeRule.onNodeWithText("Vehicle").assertDoesNotExist()
        composeRule.onNodeWithText("Steps").assertExists()
        composeRule.onNodeWithTag("routeStep0").assertExists()
        composeRule.onNodeWithText("Start Navigation").assertIsDisplayed()
        // The navigator belongs to MIN.
        composeRule.onNodeWithTag("analysedStepName").assertDoesNotExist()
    }

    @Test
    fun tappingTheRouteBarOpensTheFieldsForEditing() {
        val viewModel = launchWithRoute()
        assertEquals(ActiveField.NONE, viewModel.uiState.value.activeField)
        composeRule.onNodeWithTag("routeBar").assertExists()

        composeRule.onNodeWithTag("routeBar").performClick()
        composeRule.waitForIdle()

        // The tap opens the destination field for editing…
        assertEquals(ActiveField.DEST, viewModel.uiState.value.activeField)
        composeRule.onNodeWithText("Destination").assertExists()
        composeRule.onNodeWithTag("routeBar").assertDoesNotExist()
        // …and it stays open: the field takes focus itself, so it does not report an immediate
        // focus loss and close the edit state again (the reason a programmatic focus request
        // is needed at all).
        composeRule.waitForIdle()
        assertEquals(ActiveField.DEST, viewModel.uiState.value.activeField)
    }

    @Test
    fun theRouteEditActionOpensTheFields() {
        val viewModel = launchWithRoute()
        assertEquals(ActiveField.NONE, viewModel.uiState.value.activeField)

        composeRule.onNodeWithText("Change start & target").performClick()
        composeRule.waitForIdle()

        // Same target as tapping the route line: the destination field, focused.
        assertEquals(ActiveField.DEST, viewModel.uiState.value.activeField)
        composeRule.onNodeWithText("Destination").assertExists()
        // Editing brings the calculation back into that slot (a changed destination must stay
        // recalculable).
        composeRule.onNodeWithText("Calculate").assertExists()
        composeRule.onNodeWithText("Change start & target").assertDoesNotExist()
    }

    @Test
    fun editingKeepsTheVehicleSelector() {
        val client = FakeOSMScoutClient()
        val viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context()),
            locationService = LocationService(context()),
            context = context()
        )
        viewModel.openSession()
        viewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)
        composeRule.setContent {
            RoutePanel(
                viewModel = viewModel,
                onOpenFavoritePicker = {},
                centerLat = 48.5,
                centerLon = 2.3
            )
        }
        composeRule.waitForIdle()

        // Before a route exists (editing) the profile choice is on screen.
        composeRule.onNodeWithText("Vehicle").assertExists()
        composeRule.onNodeWithText("Calculate").assertExists()
    }

    @Test
    fun selectingAStepInTheListCollapsesToMin() {
        val viewModel = launchWithRoute()
        viewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)
        composeRule.waitForIdle()

        // The list scrolls inside the card: scroll to the row and tap it, as a user would.
        composeRule.onNodeWithTag("routeStep0").performScrollTo().performClick()
        composeRule.waitForIdle()

        // A tap on a list entry analyses that step and switches to MIN, so what the user looks
        // at next is the map with that manoeuvre and its highlighted segment.
        assertEquals(0, viewModel.uiState.value.analysedStepIndex)
        assertEquals(RouteOverlayAnchor.COMPACT, viewModel.uiState.value.overlayAnchor)
        composeRule.onNodeWithTag("analysedStepName").assertTextContains("1 / 2")
        composeRule.onNodeWithTag("routeStep0").assertDoesNotExist()
    }

    @Test
    fun tappingTheStepNameInMinShowsTheListAgain() {
        val viewModel = launchWithRoute()
        viewModel.setOverlayAnchor(RouteOverlayAnchor.COMPACT)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("analysedStepName").performClick()
        composeRule.waitForIdle()

        assertEquals(RouteOverlayAnchor.EXPANDED, viewModel.uiState.value.overlayAnchor)
        composeRule.onNodeWithText("Steps").assertExists()
    }

    @Test
    fun theHeaderCloseEndsTheSession() {
        val viewModel = launchWithRoute()

        // The close control lives in MAX's title row (MIN has no chrome of its own) and is
        // the session's exit there: one glyph, one meaning — while the toggle next to it is
        // what collapses the card (owner finding, 2026-10-05; spec:
        // `route-planning-session` — Session lifetime and its only exits,
        // "Ending the session removes its surface").
        viewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("routeEndSessionHeader").performClick()
        composeRule.waitForIdle()

        assertNull(viewModel.uiState.value.routeEntry)
        assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
    }

    @Test
    fun theCollapseControlOnlyChangesTheAnchor() {
        val viewModel = launchWithRoute()
        viewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Collapse route panel").performClick()
        composeRule.waitForIdle()

        assertEquals(RouteOverlayAnchor.COMPACT, viewModel.uiState.value.overlayAnchor)
        assertNotNull("collapsing keeps the session", viewModel.uiState.value.routeEntry)
        // A calculated route moves an open session into the review state (onSuccess); the
        // point here is that the collapse did not end it.
        assertEquals(RouteSessionState.REVIEWING, viewModel.sessionState.value)
    }
}
