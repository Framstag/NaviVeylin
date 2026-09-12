package com.naviveylin.navigation

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.CurrentRoadInfo
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.NavigationPosition
import com.framstag.libosmscout.client.NavigationState
import com.framstag.libosmscout.client.RoadInfo
import com.naviveylin.location.LocationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Tests for the phone VM's road-info source (spec: current-road-info): on
 * route the street comes from the engine's resolved way (no area search),
 * off route it falls back to the bearing-aware `getRoadAt` lookup.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationViewModelRoadInfoTest {

    private fun buildViewModel(
        client: FakeOSMScoutClient = FakeOSMScoutClient()
    ): NavigationViewModel {
        return NavigationViewModel(
            client,
            NavigationStateProvider(),
            LocationService(ApplicationProvider.getApplicationContext()),
            ApplicationProvider.getApplicationContext()
        )
    }

    /** Pump Robolectric's paused main looper until [condition] holds or timeout. */
    private fun awaitState(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("State condition not met within 5s")
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
}
