package com.naviveylin.navigation

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
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
 * Tests for the AA controller's road-info source (spec: current-road-info):
 * the car surface now populates `currentRoadInfo` — on route from the
 * engine's resolved way, off route via the bearing-aware `getRoadAt` — with
 * the same rules as the phone VM.
 */
@RunWith(RobolectricTestRunner::class)
class AANavigationControllerRoadInfoTest {

    private fun buildController(
        client: FakeOSMScoutClient = FakeOSMScoutClient()
    ): AANavigationController {
        return AANavigationController(
            client,
            NavigationStateProvider(),
            LocationService(ApplicationProvider.getApplicationContext())
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
        val controller = buildController(client)
        val position = NavigationPosition(
            NavigationState.OnRoute, 51.0, 7.0, 90.0, 5.0,
            "Hauptstrasse", "B 1", "highway_primary"
        )

        controller.updateRoadInfoFromPosition(position)

        val info = controller.state.value.currentRoadInfo
        assertEquals("B 1", info?.ref)
        assertEquals("highway_primary", info?.typeName)
        assertEquals("Hauptstrasse", info?.name)
        assertTrue(client.roadAtLookupCalls.isEmpty())
    }

    @Test
    fun offRoute_triggersBearingAwareLookup() {
        val client = FakeOSMScoutClient().apply {
            roadAt = RoadInfo("Nebenstrasse", "", "highway_residential", Double.NaN)
        }
        val controller = buildController(client)
        val position = NavigationPosition(
            NavigationState.OffRoute, 51.0, 7.0, 180.0, 5.0, "", "", ""
        )

        controller.updateRoadInfoFromPosition(position)

        awaitState { controller.state.value.currentRoadInfo != null }
        assertEquals(1, client.roadAtLookupCalls.size)
        assertEquals(180.0, client.roadAtLookupCalls[0].third, 1e-9)
        val info = controller.state.value.currentRoadInfo
        assertEquals("", info?.ref)
        assertEquals("highway_residential", info?.typeName)
        assertEquals("Nebenstrasse", info?.name)
    }

    @Test
    fun offRouteNoRoadFound_clearsRoadInfo() {
        val client = FakeOSMScoutClient().apply {
            roadAt = null
        }
        val controller = buildController(client)
        val position = NavigationPosition(
            NavigationState.OffRoute, 51.0, 7.0, Double.NaN, 5.0, "", "", ""
        )

        controller.updateRoadInfoFromPosition(position)

        awaitState { client.roadAtLookupCalls.isNotEmpty() }
        assertNull(controller.state.value.currentRoadInfo)
    }
}
