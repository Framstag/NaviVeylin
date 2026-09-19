package com.naviveylin.auto

import android.graphics.Bitmap
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.TravelEstimate
import androidx.core.graphics.drawable.IconCompat
import com.framstag.libosmscout.client.NavigationPosition
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.NavigationState
import com.naviveylin.core.StringResolver
import com.naviveylin.core.stringResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

/**
 * Tests for the host trip metadata of the car session (spec:
 * auto-navigation-hints — "Trip metadata for cluster and heads-up display",
 * "Trip publishing cadence").
 */
@RunWith(RobolectricTestRunner::class)
class NavigationTripTest {

    private val testIcon: CarIcon by lazy {
        CarIcon.Builder(
            IconCompat.createWithBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        ).build()
    }

    private val resolver: StringResolver by lazy { testCarContext().stringResolver() }

    private val timeZone: TimeZone = TimeZone.getTimeZone("UTC")

    /** Fixed wall clock so arrival times and remaining times are deterministic. */
    private val now = 1_700_000_000_000L

    private fun instr(
        distance: Double,
        type: TurnType = TurnType.LEFT,
        street: String = "Hauptstrasse",
        timeTo: Double = 30.0
    ) = RouteInstruction(
        distance,
        timeTo,
        type,
        street,
        "Turn left into Hauptstrasse",
        "Turn left",
        0.0,
        TurnType.STRAIGHT_ON,
        "",
        ""
    )

    private fun navigatingState(instruction: RouteInstruction? = instr(250.0)) = NavigationState(
        isNavigating = true,
        nextInstruction = instruction,
        destinationName = "Home",
        remainingDistance = 12_400.0,
        etaMillis = now + 1_800_000L
    )

    private fun tripFrom(state: NavigationState) = NavigationTemplateMapper.tripFromState(
        state = state,
        iconForTurn = { testIcon },
        resolver = resolver,
        nowMillis = now,
        timeZone = timeZone
    )

    @Test
    fun tripCarriesStepEstimateAndDestination() {
        val trip = tripFrom(navigatingState())

        assertNotNull(trip)
        assertEquals(1, trip!!.steps.size)
        assertEquals("Turn left", trip.steps[0].cue.toString())
        assertEquals("Hauptstrasse", trip.steps[0].road.toString())

        val stepEstimate = trip.stepTravelEstimates[0]
        assertEquals(250.0, stepEstimate.remainingDistance?.displayDistance)
        assertEquals(Distance.UNIT_METERS, stepEstimate.remainingDistance?.displayUnit)
        assertEquals(30L, stepEstimate.remainingTimeSeconds)
        assertEquals(
            "arrival at the step is now plus the segment time",
            now + 30_000L,
            stepEstimate.arrivalTimeAtDestination?.timeSinceEpochMillis
        )

        assertEquals("Home", trip.destinations[0].name.toString())
        val destinationEstimate = trip.destinationTravelEstimates[0]
        assertEquals(12.4, destinationEstimate.remainingDistance?.displayDistance)
        assertEquals(Distance.UNIT_KILOMETERS, destinationEstimate.remainingDistance?.displayUnit)
        assertEquals(1_800L, destinationEstimate.remainingTimeSeconds)
        assertEquals(now + 1_800_000L, destinationEstimate.arrivalTimeAtDestination?.timeSinceEpochMillis)
    }

    @Test
    fun tripFallsBackToRouteListInstruction() {
        val state = navigatingState(instruction = null).copy(
            instructions = listOf(instr(400.0, TurnType.RIGHT))
        )

        val trip = tripFrom(state)

        assertEquals(1, trip?.steps?.size)
        assertEquals(400.0, trip?.stepTravelEstimates?.get(0)?.remainingDistance?.displayDistance)
    }

    @Test
    fun loadingTripWhileRerouting() {
        val trip = tripFrom(navigatingState().copy(isRerouting = true))

        assertTrue("rerouting is a transient host state", trip?.isLoading == true)
        assertTrue(trip?.steps?.isEmpty() == true)
        assertTrue(trip?.destinations?.isEmpty() == true)
    }

    @Test
    fun loadingTripWhenArrivalTimeIsUnknown() {
        val trip = tripFrom(navigatingState().copy(etaMillis = 0L))

        assertTrue("no arrival time, no step data", trip?.isLoading == true)
        assertTrue(trip?.steps?.isEmpty() == true)
    }

    @Test
    fun noTripWhenNotNavigating() {
        assertNull(
            "free driving has no car surface",
            tripFrom(NavigationState(isNavigating = false, currentSpeedKmH = 54.0))
        )
        assertNull(tripFrom(navigatingState().copy(isNavigating = false)))
    }

    @Test
    fun destinationFallsBackToCoordinates() {
        val state = navigatingState().copy(
            destinationName = null,
            destLat = 52.516275,
            destLon = 13.377704
        )

        val trip = tripFrom(state)

        assertEquals("52.51628, 13.37770", trip?.destinations?.get(0)?.name?.toString())
    }

    @Test
    fun destinationOmittedWithoutNameAndCoordinates() {
        val state = navigatingState().copy(destinationName = null)

        val trip = tripFrom(state)

        assertTrue(trip?.destinations?.isEmpty() == true)
        assertEquals(1, trip?.steps?.size)
    }

    @Test
    fun stepEstimateUsesUnknownRemainingTimeWithoutSegmentTime() {
        val trip = tripFrom(navigatingState(instr(250.0, timeTo = 0.0)))

        assertEquals(
            TravelEstimate.REMAINING_TIME_UNKNOWN,
            trip?.stepTravelEstimates?.get(0)?.remainingTimeSeconds
        )
        assertEquals(
            "arrival falls back to the destination ETA",
            now + 1_800_000L,
            trip?.stepTravelEstimates?.get(0)?.arrivalTimeAtDestination?.timeSinceEpochMillis
        )
    }

    @Test
    fun destinationEstimateNeverReportsNegativeTime() {
        val state = navigatingState().copy(etaMillis = now - 60_000L)

        val trip = tripFrom(state)

        assertEquals(
            TravelEstimate.REMAINING_TIME_UNKNOWN,
            trip?.destinationTravelEstimates?.get(0)?.remainingTimeSeconds
        )
    }

    @Test
    fun carTripFactoryWiresTemplateIconsAndCarStrings() {
        val trip = carTripFor(navigatingState(), resolver)

        assertEquals("Turn left", trip?.steps?.first()?.cue.toString())
        assertEquals(Maneuver.TYPE_TURN_NORMAL_LEFT, trip?.steps?.first()?.maneuver?.type)
        assertNotNull("maneuver artwork comes from the shared glyph renderer", trip?.steps?.first()?.maneuver?.icon)
    }

    @Test
    fun carTripFactoryPublishesNothingWithoutNavigation() {
        assertNull(carTripFor(NavigationState(isNavigating = false), resolver))
    }

    // --- cadence predicate ---

    @Test
    fun cadencePublishesWhenNoPreviousState() {
        assertTrue(NavigationTemplateMapper.hasTripChanged(null, navigatingState()))
    }

    @Test
    fun cadenceIgnoresSpeedAndPositionOnlyUpdates() {
        val previous = navigatingState().copy(currentSpeedKmH = 50.0)
        val current = previous.copy(
            currentSpeedKmH = 51.0,
            position = NavigationPosition(
                com.framstag.libosmscout.client.NavigationState.OnRoute,
                52.5,
                13.4,
                90.0,
                5.0
            )
        )

        assertFalse(NavigationTemplateMapper.hasTripChanged(previous, current))
    }

    @Test
    fun cadenceIgnoresLaneAndRoadNameUpdates() {
        val previous = navigatingState().copy(laneCount = 3)
        val current = previous.copy(laneCount = 4, laneSuggested = true)

        assertFalse(NavigationTemplateMapper.hasTripChanged(previous, current))
    }

    @Test
    fun cadencePublishesOnRoundedDistanceChange() {
        val previous = navigatingState(instr(250.0))
        val current = navigatingState(instr(300.0))

        assertTrue(NavigationTemplateMapper.hasTripChanged(previous, current))
    }

    @Test
    fun cadencePublishesOnRemainingTimeChange() {
        val previous = navigatingState()
        val current = previous.copy(etaMillis = previous.etaMillis + 60_000L)

        assertTrue(NavigationTemplateMapper.hasTripChanged(previous, current))
    }

    @Test
    fun cadencePublishesOnManeuverChange() {
        val previous = navigatingState(instr(250.0, TurnType.LEFT))
        val current = navigatingState(instr(250.0, TurnType.RIGHT))

        assertTrue(NavigationTemplateMapper.hasTripChanged(previous, current))
    }

    @Test
    fun cadencePublishesOnReroutingToggle() {
        val previous = navigatingState()
        val current = previous.copy(isRerouting = true)

        assertTrue(NavigationTemplateMapper.hasTripChanged(previous, current))
    }

    @Test
    fun cadencePublishesOnNavigationEnd() {
        val previous = navigatingState()
        val current = previous.copy(isNavigating = false)

        assertTrue(NavigationTemplateMapper.hasTripChanged(previous, current))
    }
}
