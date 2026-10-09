package com.naviveylin.ui.route

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The phone card's pinned action band under a large system font scale (spec:
 * `route-planning-session` — Planning card content and its pinned actions, "the pinned band keeps
 * the height its content needs").
 *
 * The card capped its scrolling content at `cardCap - 120 dp`, a *fixed* reservation for the band
 * below it. At font scale 2.0 the band's own content is taller than that reservation, so the band
 * was handed too little room and the labelled End action lost part of its height — on the device
 * (1080x2400, font scale 2.0) it left the card entirely and disappeared from the UI dump
 * (`TODO.md` §138). This class measures the geometry the host can measure: the labelled End
 * action keeps its tap target and stays inside the card.
 *
 * The window is the AVD's shape *and* density (`411 x 891 dp` at `420 dpi`): `sp` only reaches a
 * realistic pixel size at a phone density, so the band's font-scale growth is invisible at the
 * Robolectric default (mdpi). The font scale itself is set through `LocalDensity` — the same value
 * `AndroidComposeView` reads from the configuration — because Robolectric has no font-scale
 * qualifier, and a `@Config` sandbox change is forbidden for a class that loads the JNI stub.
 */
@RunWith(RobolectricTestRunner::class)
class RoutePanelActionBandScaleTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun grantPreciseLocation() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-420dpi")
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    /** The panel in max, on a calculated route, at the system font scale [fontScale]. */
    private fun launchWithRoute(fontScale: Float) {
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
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale)
            ) {
                RoutePanel(
                    viewModel = viewModel,
                    onOpenFavoritePicker = {},
                    onEndSession = { viewModel.endSession() },
                    centerLat = 48.5,
                    centerLon = 2.3
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.routeEntry != null }
        viewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)
        composeRule.waitForIdle()
    }

    @Test
    fun `the labelled End action keeps its tap target inside the card at font scale 2`() {
        launchWithRoute(fontScale = 2.0f)

        val card = composeRule.onNodeWithTag(ROUTE_PANEL_CARD_TAG).getBoundsInRoot()
        val end = composeRule.onNodeWithTag("routeEndSession").getBoundsInRoot()
        val root = composeRule.onRoot().getBoundsInRoot()
        val cardHeight = card.bottom.value - card.top.value
        val rootHeight = root.bottom.value - root.top.value

        // The card stays within its share of the screen: the band must not be rescued by growing
        // the card over the map (spec: Session overlay anchors (max and min) — max at most 45 %).
        assertTrue(
            "max card height=$cardHeight must stay within its share of $rootHeight " +
                "(card=$card top=${card.top.value} bottom=${card.bottom.value})",
            cardHeight <= rootHeight * EXPANDED_CARD_FRACTION + 2f
        )
        // …and the labelled End action is drawn inside it — on the device at this scale it sat
        // below the card's bottom edge and reached no dump at all (TODO.md §138).
        assertTrue(
            "the labelled End action (top=${end.top.value} bottom=${end.bottom.value}) must lie " +
                "inside the card (top=${card.top.value} bottom=${card.bottom.value})",
            end.top.value >= card.top.value && end.bottom.value <= card.bottom.value
        )
        // The band is given the height its own content needs, so the action keeps its tap target
        // (guidelines/UI.md §8: at least 48 dp per dimension). A fixed reservation for the band
        // squeezes the last child of the band by the shortfall — 42.7 dp measured on HEAD.
        composeRule.onNodeWithTag("routeEndSession").assertHeightIsAtLeast(48.dp)
    }
}
