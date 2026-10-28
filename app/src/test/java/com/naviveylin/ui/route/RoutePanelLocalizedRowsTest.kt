package com.naviveylin.ui.route

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * The route panel's row list, built from the route's structured steps and localized from the
 * manoeuvre's own values (specs: `route-panel-ui` — Turn-by-turn instruction list;
 * `turn-instruction-localization` — Route analysis rows are localized from the step model).
 *
 * The rows are read from the panel's state, which is what the list renders — a Compose assertion
 * would only repeat the string the state already carries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoutePanelLocalizedRowsTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: RoutePanelViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient()
        viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context)
                .also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
            locationService = LocationService(context),
            context = context,
            settingsStorage = SettingsStorage(context)
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    private fun entry(label: String) = LocationEntry().apply {
        this.label = label
        lat = 52.52
        lon = 13.40
        matchQuality = "coordinate"
    }

    /** Start, a left turn into a street, a roundabout exit at 3 and a motorway entry onto the A42. */
    private fun roundaboutAndMotorwayRoute() = routeWithSteps(
        startStep(lat = 52.5200, lon = 13.4050),
        turnStep(
            TurnType.LEFT,
            lat = 52.5210, lon = 13.4060,
            legDistance = 400.0, legTime = 60.0,
            name = "Hauptstraße"
        ),
        step(
            RouteStep.Kind.ROUNDABOUT_LEAVE,
            lat = 52.5220, lon = 13.4065,
            legDistance = 300.0, legTime = 40.0,
            roundaboutExit = 3
        ),
        step(
            RouteStep.Kind.MOTORWAY_ENTER,
            lat = 52.5260, lon = 13.4090,
            legDistance = 900.0, legTime = 70.0,
            motorwayTo = "A42"
        ),
        targetStep(lat = 52.5300, lon = 13.4100, legDistance = 200.0, legTime = 20.0)
    )

    private fun TestScope.calculateAndAwaitSuccess(route: com.framstag.libosmscout.client.RouteEntry) {
        viewModel.updateLocationsForReroute(entry("start"), entry("dest"))
        client.routeToDeliver = route
        viewModel.calculateRoute()
        advanceUntilIdle()
        assertEquals(RouteState.Done, viewModel.uiState.value.routeState)
    }

    @Test
    @Config(qualifiers = "de")
    fun `a German left turn row is localized`() = runTest(mainDispatcherRule.dispatcher) {
        calculateAndAwaitSuccess(
            routeWithSteps(
                startStep(),
                turnStep(TurnType.LEFT, legDistance = 400.0, legTime = 60.0, name = "Hauptstraße"),
                targetStep(legDistance = 200.0, legTime = 20.0)
            )
        )

        val rows = viewModel.uiState.value.routeSteps
        assertEquals("Ankunft", rows.last().instruction)
        assertTrue(rows[1].instruction, rows[1].instruction.contains("Links abbiegen"))
        assertTrue(rows[1].instruction, rows[1].instruction.contains("Hauptstraße"))
    }

    @Test
    @Config(qualifiers = "de")
    fun `a German route lists a German roundabout row`() = runTest(mainDispatcherRule.dispatcher) {
        calculateAndAwaitSuccess(roundaboutAndMotorwayRoute())

        val roundabout = viewModel.uiState.value.routeSteps
            .first { it.kind == RouteStep.Kind.ROUNDABOUT_LEAVE }

        assertTrue(roundabout.instruction, roundabout.instruction.startsWith("Ausfahrt 3"))
        assertNotEquals("Leave roundabout", roundabout.instruction)
        assertEquals(3, roundabout.roundaboutExit)
    }

    @Test
    fun `roundabout and motorway manoeuvres appear in the list`() = runTest(mainDispatcherRule.dispatcher) {
        calculateAndAwaitSuccess(roundaboutAndMotorwayRoute())

        val rows = viewModel.uiState.value.routeSteps
        val kinds = rows.map { it.kind }

        assertTrue("the roundabout exit is a row: $kinds", kinds.contains(RouteStep.Kind.ROUNDABOUT_LEAVE))
        assertTrue("the motorway entry is a row: $kinds", kinds.contains(RouteStep.Kind.MOTORWAY_ENTER))
        // The text names what the old prose path never published in the list.
        assertTrue(rows.any { it.instruction.contains("Exit 3") })
        assertTrue(rows.any { it.instruction.contains("A42") })
    }

    @Test
    fun `the row icon follows the step kind`() = runTest(mainDispatcherRule.dispatcher) {
        calculateAndAwaitSuccess(roundaboutAndMotorwayRoute())

        val rows = viewModel.uiState.value.routeSteps
        assertEquals(TurnType.START, rows[0].turnType)
        assertEquals(TurnType.LEFT, rows[1].turnType)
        assertEquals(TurnType.ROUNDABOUT_LEAVE, rows[2].turnType)
        assertEquals(TurnType.MOTORWAY_ENTER, rows[3].turnType)
        assertEquals(TurnType.TARGET_REACHED, rows[4].turnType)
    }

    @Test
    fun `a motorway change row is not a plain turn`() = runTest(mainDispatcherRule.dispatcher) {
        calculateAndAwaitSuccess(
            routeWithSteps(
                startStep(),
                step(
                    RouteStep.Kind.MOTORWAY_CHANGE,
                    lat = 52.5250, lon = 13.4080,
                    legDistance = 900.0, legTime = 70.0,
                    turn = TurnType.LEFT,
                    motorwayFrom = "A40",
                    motorwayTo = "A42"
                ),
                targetStep(legDistance = 200.0, legTime = 20.0)
            )
        )

        val row = viewModel.uiState.value.routeSteps
            .first { it.kind == RouteStep.Kind.MOTORWAY_CHANGE }

        assertNotEquals(
            "the motorway glyph must not be drawn as a plain left turn",
            TurnType.LEFT,
            row.turnType
        )
        assertEquals(TurnType.MOTORWAY_ENTER, row.turnType)
        assertTrue(row.instruction, row.instruction.contains("Keep left"))
        assertTrue(row.instruction, row.instruction.contains("A42"))
    }
}
