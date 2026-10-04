package com.naviveylin.ui.route

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * Verifies the route-analysis state contract (spec: `route-analysis`): a step can be
 * analysed, the analysed step owns the polyline segment between its manoeuvre and the
 * next one, and the selection is dropped when the route changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoutePanelViewModelStepAnalysisTest {

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
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    /**
     * Three-point route whose three instruction lines sit exactly on the polyline's
     * three vertices, so the expected vertex ranges are readable.
     */
    private fun routeEntry(withPositions: Boolean = true): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
        longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
        distance = 5000.0
        descriptions = arrayOf(
            "Start: Hauptbahnhof  [0.0 km]",
            "Left onto Main Street  [1.2 km]",
            "Destination reached  [0.0 km]"
        )
        if (withPositions) {
            instructionLats = doubleArrayOf(52.5200, 52.5230, 52.5300)
            instructionLons = doubleArrayOf(13.4050, 13.4080, 13.4100)
        }
    }

    private fun entry(label: String, lat: Double = 51.5, lon: Double = 7.4): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.lat = lat
            this.lon = lon
            matchQuality = "coordinate"
        }

    private fun TestScope.calculateAndAwaitSuccess(route: RouteEntry = routeEntry()) {
        viewModel.updateLocationsForReroute(entry("start"), entry("dest"))
        client.routeToDeliver = route
        viewModel.calculateRoute()
        advanceUntilIdle()
        assertEquals(RouteState.Done, viewModel.uiState.value.routeState)
    }

    @Test
    fun `toggling the analysed step selects it and toggling again clears it`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()

            viewModel.toggleAnalysedStep(1)
            assertEquals(1, viewModel.uiState.value.analysedStepIndex)
            assertEquals(0..1, viewModel.uiState.value.analysedSegment)
            assertNotNull(viewModel.analysedAnchor.value)

            viewModel.toggleAnalysedStep(1)
            assertNull(viewModel.uiState.value.analysedStepIndex)
            assertNull(viewModel.uiState.value.analysedSegment)
            assertNull(viewModel.analysedAnchor.value)
        }

    @Test
    fun `toggling another step swaps the analysed step`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()

            viewModel.toggleAnalysedStep(0)
            viewModel.toggleAnalysedStep(2)

            assertEquals(2, viewModel.uiState.value.analysedStepIndex)
            assertEquals(1..2, viewModel.uiState.value.analysedSegment)
        }

    @Test
    fun `the analysed anchor follows the analysed step`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()

            viewModel.analyseStep(2)
            assertEquals(StepAnchor(52.5300, 13.4100), viewModel.analysedAnchor.value)
            assertEquals(2, viewModel.uiState.value.analysedStepIndex)

            viewModel.clearAnalysedStep()
            assertNull(viewModel.analysedAnchor.value)
        }

    @Test
    fun `the analysed anchor is dropped with the route`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            viewModel.analyseStep(1)

            viewModel.clearRoute()

            assertNull(viewModel.analysedAnchor.value)
        }

    @Test
    fun `a step without a position analyses without an anchor`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess(routeEntry(withPositions = false))

            viewModel.analyseStep(1)

            assertEquals(1, viewModel.uiState.value.analysedStepIndex)
            assertNull(viewModel.analysedAnchor.value)
        }

    @Test
    fun `clearing the analysed step leaves the route drawn`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            viewModel.analyseStep(1)

            viewModel.clearAnalysedStep()

            // Only the highlight goes: the route itself is still the active one.
            assertNull(viewModel.uiState.value.analysedSegment)
            assertNotNull(viewModel.routeResultFlow.value)
            assertNotNull(viewModel.uiState.value.routeEntry)
        }

    @Test
    fun `ending the session drops the analysed step with the route`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.openSession()
            calculateAndAwaitSuccess()
            viewModel.analyseStep(1)

            viewModel.endSession()

            assertNull(viewModel.uiState.value.analysedStepIndex)
            assertNull(viewModel.uiState.value.analysedSegment)
            assertNull(viewModel.analysedAnchor.value)
            assertNull(viewModel.routeResultFlow.value)
            assertNull(viewModel.uiState.value.routeEntry)
        }

    @Test
    fun `analysing a step selects it and resolves its segment`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            assertEquals(3, viewModel.uiState.value.stepAnchors.size)

            viewModel.analyseStep(1)

            assertEquals(1, viewModel.uiState.value.analysedStepIndex)
            assertEquals(0..1, viewModel.uiState.value.analysedSegment)
        }

    @Test
    fun `analysing the first step resolves the leading segment`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()

            viewModel.analyseStep(0)

            assertEquals(0, viewModel.uiState.value.analysedStepIndex)
            // The start line sits on the route's first vertex, so it owns no leg.
            assertNull(viewModel.uiState.value.analysedSegment)
        }

    @Test
    fun `analysing another step replaces the selection`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()

            viewModel.analyseStep(0)
            viewModel.analyseStep(2)

            assertEquals(2, viewModel.uiState.value.analysedStepIndex)
            assertEquals(1..2, viewModel.uiState.value.analysedSegment)
        }

    @Test
    fun `analysing an out-of-range step changes nothing`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            viewModel.analyseStep(1)

            viewModel.analyseStep(7)
            viewModel.analyseStep(-1)

            assertEquals(1, viewModel.uiState.value.analysedStepIndex)
            assertEquals(0..1, viewModel.uiState.value.analysedSegment)
        }

    @Test
    fun `clearing the analysed step drops the segment`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            viewModel.analyseStep(1)

            viewModel.clearAnalysedStep()

            assertNull(viewModel.uiState.value.analysedStepIndex)
            assertNull(viewModel.uiState.value.analysedSegment)
        }

    @Test
    fun `a step without a position is analysed without a segment`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess(routeEntry(withPositions = false))

            viewModel.analyseStep(1)

            assertEquals(1, viewModel.uiState.value.analysedStepIndex)
            assertNull(viewModel.uiState.value.analysedSegment)
            assertEquals(0, viewModel.uiState.value.stepAnchors.size)
        }

    @Test
    fun `a new route calculation starts at its first step`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            assertEquals(0, viewModel.uiState.value.analysedStepIndex)
            viewModel.analyseStep(1)
            assertEquals(1, viewModel.uiState.value.analysedStepIndex)

            calculateAndAwaitSuccess()

            // The new route's current step is its first one (owner finding, 2026-10-03: the
            // min overlay had no usable entry before the first paging).
            assertEquals(0, viewModel.uiState.value.analysedStepIndex)
            // Step 0 is the start line and owns the leg *leading to* its manoeuvre — the route's
            // first vertex, so no leg (spec: route-analysis).
            assertNull(viewModel.uiState.value.analysedSegment)
            assertEquals(3, viewModel.uiState.value.stepAnchors.size)
        }

    /**
     * The anchor is the trigger a consumer reacts to (the map focuses the analysed leg when it
     * changes), so the step's segment must already be published when the anchor arrives. Device
     * measurement (2026-10-03): with the opposite order the fit logged `range=9..28` while the
     * highlight drew `range=28..32` — the camera moved onto the *previous* step's leg while card
     * and highlight showed the right one ("the current segment is not centred … sometimes reaches
     * out over the bottom or to the left"). An unconfined collector resumes inline on the
     * emission, so it observes exactly what a consumer would.
     */
    @Test
    fun `the analysed segment is published before the anchor a consumer reacts to`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            val seen = mutableListOf<IntRange?>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.analysedAnchor.collect { anchor ->
                    if (anchor != null) seen += viewModel.analysedSegmentRange.value
                }
            }

            viewModel.analyseStep(1)
            advanceUntilIdle()
            job.cancel()

            assertEquals(listOf<IntRange?>(0..1), seen)
        }

    @Test
    fun `clearing the route drops the analysed step and the anchors`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            viewModel.analyseStep(1)

            viewModel.clearRoute()

            assertNull(viewModel.uiState.value.analysedStepIndex)
            assertNull(viewModel.uiState.value.analysedSegment)
            assertEquals(0, viewModel.uiState.value.stepAnchors.size)
        }

    @Test
    fun `an adopted reroute starts at its first step`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            viewModel.analyseStep(1)

            viewModel.adoptRoute(routeEntry(), Vehicle.CAR)

            assertEquals(0, viewModel.uiState.value.analysedStepIndex)
            assertNull(viewModel.uiState.value.analysedSegment)
            assertEquals(3, viewModel.uiState.value.stepAnchors.size)
        }

    @Test
    fun `a route starts on its first step that carries a distance or time`() =
        runTest(mainDispatcherRule.dispatcher) {
            val route = RouteEntry().apply {
                routeHandle = 7L
                latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
                longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
                distance = 5000.0
                // The native start line carries an empty bracket — no distance, no time —
                // and presenting it left the min overlay's detail empty (owner finding,
                // 2026-10-03).
                descriptions = arrayOf(
                    "--- Route ---",
                    "Start: Hauptbahnhof  []",
                    "Left onto Main Street  [1.2 km, 45 s]"
                )
                // One instruction position per instruction line (the header is not an
                // instruction), so two here: the start line and the turn.
                instructionLats = doubleArrayOf(52.5200, 52.5230)
                instructionLons = doubleArrayOf(13.4050, 13.4080)
            }

            calculateAndAwaitSuccess(route)

            assertEquals(1, viewModel.uiState.value.analysedStepIndex)
            assertNotNull(viewModel.uiState.value.analysedSegment)
            assertEquals("1.2 km", viewModel.uiState.value.routeSteps[1].distanceText)
            assertEquals("45 s", viewModel.uiState.value.routeSteps[1].timeText)
        }

    @Test
    fun `the navigator steps forward to the last step and stops there`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            // The route starts at its first step, so forward moves to the second.
            assertEquals(0, viewModel.uiState.value.analysedStepIndex)

            viewModel.analyseNextStep()
            assertEquals(1, viewModel.uiState.value.analysedStepIndex)
            assertNotNull(viewModel.uiState.value.analysedSegment)

            viewModel.analyseNextStep()
            assertEquals(2, viewModel.uiState.value.analysedStepIndex)

            // At the last step forward is a no-op, not a wrap-around.
            viewModel.analyseNextStep()
            assertEquals(2, viewModel.uiState.value.analysedStepIndex)
        }

    @Test
    fun `the navigator steps back and stops on the first step`() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()

            // Back on the current first step changes nothing.
            assertEquals(0, viewModel.uiState.value.analysedStepIndex)
            viewModel.analysePreviousStep()
            assertEquals(0, viewModel.uiState.value.analysedStepIndex)

            viewModel.analyseStep(2)
            viewModel.analysePreviousStep()
            assertEquals(1, viewModel.uiState.value.analysedStepIndex)
            viewModel.analysePreviousStep()
            assertEquals(0, viewModel.uiState.value.analysedStepIndex)

            viewModel.analysePreviousStep()
            assertEquals(0, viewModel.uiState.value.analysedStepIndex)
        }

    @Test
    fun `the navigator does nothing without a route`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.analyseNextStep()

            assertNull(viewModel.uiState.value.analysedStepIndex)
            assertNull(viewModel.uiState.value.analysedSegment)
        }
}
