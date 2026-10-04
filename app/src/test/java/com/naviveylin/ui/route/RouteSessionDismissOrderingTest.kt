package com.naviveylin.ui.route

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/**
 * The session's dismissal (spec: `route-planning-session` — the session's only exits;
 * `map-canvas-screen` — back dismisses the topmost overlay).
 *
 * After the 2026-10-03 revision the phone has exactly **one** surface (design D10): the
 * summary dialog is gone, so there is no order left to keep. The card is composed inside
 * the map's own window — dismissal is the screen's `BackHandler`, and the card's own
 * cancel exit is [RoutePanelViewModel.endSession], which the session-state tests cover.
 * What is asserted here is the structural half: no second window exists.
 */
@RunWith(RobolectricTestRunner::class)
class RouteSessionDismissOrderingTest {

    @get:Rule
    // createAndroidComposeRule so the test can ask the activity how many windows exist.
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun grantPreciseLocation() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        RuntimeEnvironment.setQualifiers("w411dp-h891dp")
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun panelViewModel(client: FakeOSMScoutClient) = RoutePanelViewModel(
        client = client,
        favoriteRepository = FavoriteRepository(client),
        searchHistoryRepository = SearchHistoryRepository(context()),
        locationService = LocationService(context()),
        context = context()
    )

    @Test
    fun theSessionOverlayIsNotASecondWindow() {
        val viewModel = panelViewModel(FakeOSMScoutClient())
        viewModel.openSession()
        composeRule.setContent {
            RoutePanel(
                viewModel = viewModel,
                onOpenFavoritePicker = {},
                centerLat = 48.5,
                centerLon = 2.3
            )
        }
        composeRule.waitForIdle()

        // The panel is composed inside the map's own window (design D10 — one session
        // surface): no dialog, so back reaches the activity's handler — the screen's
        // BackHandler — instead of a second dispatcher whose ordering the session would
        // have to keep. The deleted summary dialog was that second window.
        assertNull(ShadowDialog.getLatestDialog())
        assertFalse(composeRule.activity.isFinishing)
        assertEquals(RouteSessionState.EDITING, viewModel.sessionState.value)
    }
}
