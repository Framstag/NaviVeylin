package com.naviveylin.navigation

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.CurrentRoadInfo
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.NavigationPosition
import com.framstag.libosmscout.client.NavigationState
import com.framstag.libosmscout.client.RoadInfo
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.core.SurfaceOrigin
import com.naviveylin.location.LocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import com.naviveylin.test.engineUnderTest

/**
 * Tests for the phone VM's road-info source (spec: current-road-info): on
 * route the street comes from the engine's resolved way (no area search),
 * off route it falls back to the bearing-aware `getRoadAt` lookup.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationEngineRoadInfoTest {

    /**
     * The engine's off-main work runs on this pair (production uses real pools), so a case drains
     * it instead of polling (spec: `unit-test-suite-runtime` — Awaiting state, not a deadline).
     */
    private val computeDispatcher = StandardTestDispatcher()
    private val ioDispatcher = StandardTestDispatcher()

    private fun buildViewModel(
        client: FakeOSMScoutClient = FakeOSMScoutClient()
    ): NavigationEngine {
        return engineUnderTest(
            { client },
            LocationService(ApplicationProvider.getApplicationContext()),
            ApplicationProvider.getApplicationContext(),
            dispatchers = EngineDispatchers(computeDispatcher, ioDispatcher)
        )
    }

    /** Drain the injected schedulers and the looper, then assert — no wall-clock deadline. */
    private fun awaitState(condition: () -> Boolean) {
        repeat(2) {
            computeDispatcher.scheduler.advanceUntilIdle()
            ioDispatcher.scheduler.advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
        }
        if (!condition()) {
            throw AssertionError("State condition not met after draining the test scheduler")
        }
    }

    @Test
    fun onRouteWithWayInfo_setsRoadInfoFromWay_withoutLookup() {
        val client = FakeOSMScoutClient()
        val vm = buildViewModel(client)
        val position = NavigationPosition(
            NavigationState.OnRoute, 51.0, 7.0, 90.0, 5.0,
            "Hauptstrasse", "B 1", "highway_primary"
        )

        vm.updateRoadInfoFromPosition(position)

        val info = vm.state.value.currentRoadInfo
        assertEquals("B 1", info?.ref)
        assertEquals("highway_primary", info?.typeName)
        assertEquals("Hauptstrasse", info?.name)
        // No area search / bearing-aware lookup ran.
        assertTrue(client.roadAtLookupCalls.isEmpty())
    }

    @Test
    fun onRouteWithRefOnly_setsRoadInfoFromWay() {
        val client = FakeOSMScoutClient()
        val vm = buildViewModel(client)
        // Unnamed road with a ref: the way path still fires (ref present).
        val position = NavigationPosition(
            NavigationState.OnRoute, 51.0, 7.0, Double.NaN, 5.0,
            "", "A 44", "highway_motorway"
        )

        vm.updateRoadInfoFromPosition(position)

        val info = vm.state.value.currentRoadInfo
        assertEquals("A 44", info?.ref)
        assertEquals("highway_motorway", info?.typeName)
        assertEquals("", info?.name)
        assertTrue(client.roadAtLookupCalls.isEmpty())
    }

    @Test
    fun offRoute_triggersBearingAwareLookup() {
        val client = FakeOSMScoutClient().apply {
            roadAt = RoadInfo("Nebenstrasse", "", "highway_residential", Double.NaN)
        }
        val vm = buildViewModel(client)
        val position = NavigationPosition(
            NavigationState.OffRoute, 51.0, 7.0, 180.0, 5.0, "", "", ""
        )

        vm.updateRoadInfoFromPosition(position)

        awaitState { vm.state.value.currentRoadInfo != null }
        assertEquals(1, client.roadAtLookupCalls.size)
        assertEquals(51.0, client.roadAtLookupCalls[0].first, 1e-9)
        assertEquals(7.0, client.roadAtLookupCalls[0].second, 1e-9)
        assertEquals(180.0, client.roadAtLookupCalls[0].third, 1e-9)
        val info = vm.state.value.currentRoadInfo
        assertEquals("", info?.ref)
        assertEquals("highway_residential", info?.typeName)
        assertEquals("Nebenstrasse", info?.name)
    }

    @Test
    fun onRouteWithoutWayInfo_fallsBackToLookup() {
        val client = FakeOSMScoutClient().apply {
            roadAt = RoadInfo("Hauptstrasse", "B 1", "highway_primary", 50.0)
        }
        val vm = buildViewModel(client)
        // On route but the engine resolved no way identity — fall back.
        val position = NavigationPosition(
            NavigationState.OnRoute, 51.0, 7.0, 90.0, 5.0, "", "", ""
        )

        vm.updateRoadInfoFromPosition(position)

        awaitState { vm.state.value.currentRoadInfo != null }
        assertEquals(1, client.roadAtLookupCalls.size)
        val info = vm.state.value.currentRoadInfo
        assertEquals("B 1", info?.ref)
        assertEquals("highway_primary", info?.typeName)
        assertEquals("Hauptstrasse", info?.name)
    }

    @Test
    fun offRouteNoRoadFound_clearsRoadInfo() {
        val client = FakeOSMScoutClient().apply {
            roadAt = null
        }
        val vm = buildViewModel(client)
        val position = NavigationPosition(
            NavigationState.OffRoute, 51.0, 7.0, Double.NaN, 5.0, "", "", ""
        )

        vm.updateRoadInfoFromPosition(position)

        awaitState { client.roadAtLookupCalls.isNotEmpty() }
        // The lookup ran and returned null — road info cleared.
        assertNull(vm.state.value.currentRoadInfo)
    }

    /**
     * The lookup answers off the main thread, but its result is published on the main dispatcher
     * (spec: `navigation-engine` — A native lookup result is published on the main thread). The
     * injected io dispatcher has its own scheduler, so the case can prove *which* scheduler the
     * publication waits on instead of guessing from timing.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun lookupResultIsPublishedOnTheMainThread() {
        val client = FakeOSMScoutClient().apply {
            roadAt = RoadInfo("Nebenstrasse", "", "highway_residential", Double.NaN)
        }
        // Its own scheduler: advancing the lookup must not advance the main dispatcher's queue.
        val ioDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
        val vm = engineUnderTest(
            { client },
            LocationService(ApplicationProvider.getApplicationContext()),
            ApplicationProvider.getApplicationContext(),
            dispatchers = EngineDispatchers(compute = Dispatchers.Default, io = ioDispatcher)
        )
        // Another publisher writes while the lookup is in flight.
        client.onGetRoadAt = { vm.reportError("concurrent write", SurfaceOrigin.CAR) }

        vm.updateRoadInfoFromPosition(
            NavigationPosition(NavigationState.OffRoute, 51.0, 7.0, 180.0, 5.0, "", "", "")
        )

        ioDispatcher.scheduler.advanceUntilIdle()
        assertTrue("the lookup ran", client.roadAtLookupCalls.isNotEmpty())
        assertNull(
            "the lookup's result waits for the main dispatcher",
            vm.state.value.currentRoadInfo
        )

        awaitState { vm.state.value.currentRoadInfo != null }

        assertEquals("Nebenstrasse", vm.state.value.currentRoadInfo?.name)
        assertEquals(
            "the publish preserves what another writer set meanwhile",
            "concurrent write",
            vm.state.value.errorMessage
        )
    }
}
