package com.naviveylin.auto

import com.framstag.libosmscout.client.RoadInfo
import com.naviveylin.core.FreeDrivingStatusProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The car free-driving screen's publication into the shared status (spec:
 * `current-road-info` — Car publishes its own resolution into the same status) and
 * its clear on screen destroy (spec — no road from an earlier session).
 *
 * The [FreeDrivingScreen] object itself needs a live `CarContext` and a Hilt entry
 * point (see [FreeDrivingScreenTest]); the publish shape is pinned through the
 * companion seam the screen calls from its own road lookup.
 */
@RunWith(RobolectricTestRunner::class)
class FreeDrivingScreenStatusTest {

    @Test
    fun carFreeDrivingPublishesItsRoad() {
        val provider = FreeDrivingStatusProvider()

        FreeDrivingScreen.publishCarRoadStatus(
            provider,
            RoadInfo("Hauptstrasse", "B 1", "highway_primary", 50.0),
            42.0
        )

        assertEquals("the car's surface key", "car", FreeDrivingStatusProvider.SURFACE_CAR)
        assertEquals("B 1", provider.status.value?.roadRef)
        assertEquals("Hauptstrasse", provider.status.value?.roadName)
        assertEquals("B 1 Hauptstrasse", provider.status.value?.roadText)
        assertEquals(42.0, provider.status.value?.speedKmH ?: Double.NaN, 1e-9)
    }

    @Test
    fun carPublishesNoRoadAsARealStatus() {
        val provider = FreeDrivingStatusProvider()

        FreeDrivingScreen.publishCarRoadStatus(provider, null, 42.0)

        assertEquals("a known fix with no road is published", false, provider.status.value?.hasRoad ?: true)
    }

    @Test
    fun clearOnScreenDestroyLeavesNoStaleRoad() {
        val provider = FreeDrivingStatusProvider()
        FreeDrivingScreen.publishCarRoadStatus(
            provider,
            RoadInfo("Hauptstrasse", "B 1", "highway_primary", 50.0),
            42.0
        )

        // The screen's onDestroy body.
        FreeDrivingScreen.clearCarRoadStatus(provider)

        assertNull("no road from an earlier session survives", provider.status.value)
    }
}
