package com.naviveylin.auto

import android.graphics.Bitmap
import androidx.car.app.HostException
import androidx.car.app.model.CarIcon
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.model.Trip
import androidx.core.graphics.drawable.IconCompat
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.NavigationState
import com.naviveylin.core.StringResolver
import com.naviveylin.core.stringResolver
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for trip metadata publication (spec: auto-navigation-hints — "Trip
 * metadata for cluster and heads-up display", "Trip publishing cadence", "Hint
 * teardown without host connection").
 */
@RunWith(RobolectricTestRunner::class)
class NavigationManagerControllerTripTest {

    private val now = 1_700_000_000_000L

    private val testIcon: CarIcon by lazy {
        CarIcon.Builder(
            IconCompat.createWithBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        ).build()
    }

    private val resolver: StringResolver by lazy { testCarContext().stringResolver() }

    private fun instruction(distanceTo: Double = 250.0) = RouteInstruction(
        distanceTo,
        30.0,
        TurnType.LEFT,
        "Hauptstrasse",
        "Turn left into Hauptstrasse",
        "Turn left",
        0.0,
        TurnType.STRAIGHT_ON,
        "",
        ""
    )

    private fun navigatingState(distanceTo: Double = 250.0) = NavigationState(
        isNavigating = true,
        nextInstruction = instruction(distanceTo),
        destinationName = "Home",
        remainingDistance = 12_400.0,
        etaMillis = now + 1_800_000L
    )

    private fun tripFor(state: NavigationState): Trip? = NavigationTemplateMapper.tripFromState(
        state = state,
        iconForTurn = { testIcon },
        resolver = resolver,
        nowMillis = now,
        timeZone = TimeZone.getTimeZone("UTC")
    )

    private fun startedManager(): NavigationManager = mockk<NavigationManager>().apply {
        every { setNavigationManagerCallback(any()) } just runs
        every { navigationStarted() } just runs
        every { navigationEnded() } just runs
        every { clearNavigationManagerCallback() } just runs
        every { updateTrip(any()) } just runs
    }

    private fun controllerWith(navigationManager: NavigationManager) =
        NavigationManagerController(navigationManager) {}

    @Test
    fun tripIsPublishedAfterNavigationStarts() {
        val navigationManager = startedManager()
        val controller = controllerWith(navigationManager)

        controller.onNavigationStarted()
        controller.publishTrip(navigatingState(), ::tripFor)

        verify(exactly = 1) { navigationManager.updateTrip(any()) }
    }

    @Test
    fun tripIsNotPublishedBeforeNavigationStarts() {
        val navigationManager = startedManager()
        val controller = controllerWith(navigationManager)

        controller.publishTrip(navigatingState(), ::tripFor)

        verify(exactly = 0) { navigationManager.updateTrip(any()) }
    }

    @Test
    fun unchangedTripContentIsNotRepublished() {
        val navigationManager = startedManager()
        val controller = controllerWith(navigationManager)
        val state = navigatingState()

        controller.onNavigationStarted()
        controller.publishTrip(state, ::tripFor)
        controller.publishTrip(state.copy(currentSpeedKmH = 51.0), ::tripFor)

        verify(exactly = 1) { navigationManager.updateTrip(any()) }
    }

    @Test
    fun changedRoundedDistanceIsRepublished() {
        val navigationManager = startedManager()
        val controller = controllerWith(navigationManager)

        controller.onNavigationStarted()
        controller.publishTrip(navigatingState(distanceTo = 250.0), ::tripFor)
        controller.publishTrip(navigatingState(distanceTo = 300.0), ::tripFor)

        verify(exactly = 2) { navigationManager.updateTrip(any()) }
    }

    @Test
    fun tripIsNotPublishedAfterNavigationEnds() {
        val navigationManager = startedManager()
        val controller = controllerWith(navigationManager)

        controller.onNavigationStarted()
        controller.publishTrip(navigatingState(), ::tripFor)
        controller.onNavigationEnded()
        controller.publishTrip(navigatingState(distanceTo = 300.0), ::tripFor)

        verify(exactly = 1) { navigationManager.updateTrip(any()) }
    }

    @Test
    fun publishingResumesWithFreshThrottleAfterRestart() {
        val navigationManager = startedManager()
        val controller = controllerWith(navigationManager)
        val state = navigatingState()

        controller.onNavigationStarted()
        controller.publishTrip(state, ::tripFor)
        controller.onNavigationEnded()
        controller.onNavigationStarted()
        controller.publishTrip(state, ::tripFor)

        verify(exactly = 2) { navigationManager.updateTrip(any()) }
    }

    @Test
    fun hostFailureIsSwallowed() {
        val navigationManager = mockk<NavigationManager>().apply {
            every { setNavigationManagerCallback(any()) } just runs
            every { navigationStarted() } just runs
            every { updateTrip(any()) } throws HostException("host gone")
        }
        val controller = controllerWith(navigationManager)

        controller.onNavigationStarted()
        controller.publishTrip(navigatingState(), ::tripFor)
    }

    @Test
    fun sessionDestroyStopsPublishing() {
        val navigationManager = startedManager()
        val controller = controllerWith(navigationManager)

        controller.onNavigationStarted()
        controller.onDestroy()
        controller.publishTrip(navigatingState(), ::tripFor)

        verify(exactly = 0) { navigationManager.updateTrip(any()) }
    }

    @Test
    fun publishedTripCarriesTheCurrentStep() {
        val navigationManager = startedManager()
        val trips = mutableListOf<Trip>()
        every { navigationManager.updateTrip(capture(trips)) } just runs
        val controller = controllerWith(navigationManager)

        controller.onNavigationStarted()
        controller.publishTrip(navigatingState(), ::tripFor)

        assertEquals(1, trips.size)
        assertEquals("Turn left", trips.first().steps.first().cue.toString())
        assertEquals("Hauptstrasse", trips.first().steps.first().road.toString())
    }
}
