package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies [MapRenderer.setGpsMarker] field setting and haversine distance.
 */
@RunWith(RobolectricTestRunner::class)
class MapRendererGpsMarkerTest {

    private lateinit var renderer: MapRenderer

    @Before
    fun setUp() {
        renderer = MapRenderer(
            client = FakeOSMScoutClient(),
            dpi = 320.0,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            gpsRenderMinIntervalMs = 0 // disable GPS render throttle in tests
        )
    }

    @Test
    fun setGpsMarkerSetsFields() {
        renderer.setGpsMarker(48.8566, 2.3522, 45.0, 10.0)
        val state = renderer.getGpsMarkerState()
        assertEquals(48.8566, state.lat, 1e-10)
        assertEquals(2.3522, state.lon, 1e-10)
        assertEquals(45.0, state.bearing, 1e-10)
        assertEquals(10.0, state.accuracy, 1e-10)
        assertTrue(state.visible)
    }

    @Test
    fun setGpsMarkerNanHidesMarker() {
        renderer.setGpsMarker(48.8566, 2.3522, 45.0, 10.0)
        assertTrue(renderer.getGpsMarkerState().visible)

        renderer.setGpsMarker(Double.NaN, Double.NaN, -1.0, 0.0)
        assertFalse(renderer.getGpsMarkerState().visible)
    }

    @Test
    fun clearGpsMarkerHidesMarker() {
        renderer.setGpsMarker(48.8566, 2.3522, 45.0, 10.0)
        assertTrue(renderer.getGpsMarkerState().visible)

        renderer.clearGpsMarker()
        assertFalse(renderer.getGpsMarkerState().visible)
    }

    @Test
    fun haversineZeroDistance() {
        val dist = renderer.haversineDistance(48.8566, 2.3522, 48.8566, 2.3522)
        assertEquals(0.0, dist, 1e-6)
    }

    @Test
    fun haversineKnownDistance() {
        // Paris to Versailles ~17.9 km
        val dist = renderer.haversineDistance(48.8566, 2.3522, 48.8049, 2.1204)
        assertEquals(17914.0, dist, 500.0)
    }

    @Test
    fun haversineNanInputReturnsInfinity() {
        val dist = renderer.haversineDistance(Double.NaN, 2.3522, 48.8566, 2.3522)
        assertTrue(dist.isInfinite())
    }

    @Test
    fun setGpsMarkerSamePositionDoesNotTriggerRender() {
        // First call: position changes from NaN → sets fields, triggers render
        val triggeredFirst = renderer.setGpsMarker(48.8566, 2.3522, 45.0, 10.0)
        assertTrue("First GPS fix should trigger a render", triggeredFirst)

        // Second call: same position → should NOT trigger render
        val triggeredSecond = renderer.setGpsMarker(48.8566, 2.3522, 45.0, 10.0)
        assertFalse("Same position should not trigger another render", triggeredSecond)
    }

    @Test
    fun setGpsMarkerDifferentPositionTriggersRender() {
        renderer.setGpsMarker(48.8566, 2.3522, 45.0, 10.0)
        // Position change >5m → should trigger render
        val triggered = renderer.setGpsMarker(48.8570, 2.3522, 45.0, 10.0)
        assertTrue("Position change should trigger a render", triggered)
    }
}
