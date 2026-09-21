package com.naviveylin.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import com.naviveylin.ui.route.RoutePanelViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The stop path of the phone [NavigationViewModel] (spec:
 * navigation-ongoing-notification — "Stop action for navigation").
 *
 * The notification's stop action ends navigation without passing the Compose
 * stop buttons, so two things must hold here rather than in the screen:
 *  - the ViewModel collects the process-wide stop request and stops itself, and
 *  - `stopNavigation()` clears the route panel the same way the in-app button
 *    does (panel non-navigating, route hidden) — otherwise the phone keeps
 *    showing the route after a stop from the shade (`TODO.md` §46).
 *
 * Runs under Robolectric with the default sandbox (AGENTS.md classloader rule
 * for the JNI stub `.so`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NavigationViewModelStopPathTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient()
    }

    private fun routePanel(): RoutePanelViewModel =
        RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            context = context
        ).apply { defaultDispatcher = mainDispatcherRule.dispatcher }

    private fun navigationViewModel(provider: NavigationStateProvider): NavigationViewModel =
        NavigationViewModel(client, provider, LocationService(context), context)

    @Test
    fun stopNavigationClearsTheRoutePanel() = runTest(mainDispatcherRule.dispatcher) {
        val provider = NavigationStateProvider()
        val viewModel = navigationViewModel(provider)
        val panel = routePanel()
        viewModel.setRoutePanelViewModel(panel)
        panel.showRouteOnMap()
        panel.setNavigating(true)
        advanceUntilIdle()

        viewModel.stopNavigation()
        advanceUntilIdle()

        assertFalse("the panel is no longer navigating", panel.uiState.value.isNavigating)
        assertFalse("the route is hidden again", panel.routeVisible.value)
    }

    @Test
    fun stopRequestStopsThisViewModel() = runTest(mainDispatcherRule.dispatcher) {
        val provider = NavigationStateProvider()
        val viewModel = navigationViewModel(provider)
        val followModeChanges = mutableListOf<Boolean>()
        viewModel.setFollowModeCallback { followModeChanges += it }
        advanceUntilIdle()

        // The notification action / car-host path: only stopNavigation() invokes
        // the follow-mode callback, so the recorded value proves the collector ran.
        provider.requestStop()
        advanceUntilIdle()

        assertEquals(listOf(false), followModeChanges)
    }
}
