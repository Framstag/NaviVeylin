package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Smoke test for [MapRenderer] construction and pure helpers.
 *
 * The GPS marker was moved out of the render pipeline into the Compose overlay
 * ([LocationMarkerOverlay]); marker state/throttle coverage moved to
 * [LocationMarkerOverlayTest] and the ViewModel follow-mode tests.
 */
@RunWith(RobolectricTestRunner::class)
class MapRendererSmokeTest {

    private lateinit var renderer: MapRenderer
    private lateinit var client: FakeOSMScoutClient

    @Before
    fun setUp() {
        client = FakeOSMScoutClient()
        renderer = MapRenderer(
            client = client,
            dpi = 320.0,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
    }

    @Test
    fun constructsWithDefaultParameters() {
        assertNotNull(renderer)
    }

    @Test
    fun tileSizePxScalesWithDpi() {
        // 256px @ 96dpi reference → 853px @ 320dpi
        assertEquals(853, renderer.tileSizePx)
    }

    @Test
    fun markerSnapshotRidesWithEmittedFrame() = runBlocking {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        renderer.setGpsMarkerState(48.8566, 2.3522, 45.0, 10.0)
        renderer.requestRender(48.8566, 2.3522, 14, 0.0)

        withTimeout(5000) {
            while (!renderer.frameFlow.value.marker.visible) delay(10)
        }
        val snap = renderer.frameFlow.value.marker
        assertEquals(48.8566, snap.lat, 1e-9)
        assertEquals(2.3522, snap.lon, 1e-9)
        assertEquals(45.0, snap.bearing, 1e-9)
        assertEquals(10.0, snap.accuracy, 1e-9)
    }

    @Test
    fun clearGpsMarkerStateEmitsHiddenSnapshot() = runBlocking {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        renderer.setGpsMarkerState(48.8566, 2.3522, 45.0, 10.0)
        renderer.requestRender(48.8566, 2.3522, 14, 0.0)

        withTimeout(5000) {
            while (!renderer.frameFlow.value.marker.visible) delay(10)
        }
        renderer.clearGpsMarkerState()
        assertFalse(renderer.frameFlow.value.marker.visible)
    }

    @Test
    fun setSearchSelectedForwardsMarkerToNativeRender() = runBlocking {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        renderer.requestRender(48.8566, 2.3522, 14, 0.0)
        // Wait until the initial render emitted a frame, then set the marker
        withTimeout(5000) {
            while (renderer.frameFlow.value.bitmap == null) delay(10)
        }
        client.lastSearchSelLat = Double.NaN

        renderer.setSearchSelected(48.8566, 2.3522)

        withTimeout(5000) {
            while (client.lastSearchSelLat.isNaN()) delay(10)
        }
        assertEquals(48.8566, client.lastSearchSelLat, 1e-9)
        assertEquals(2.3522, client.lastSearchSelLon, 1e-9)
    }

    @Test
    fun clearSearchSelectedResetsMarker() = runBlocking {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        renderer.requestRender(48.8566, 2.3522, 14, 0.0)
        withTimeout(5000) {
            while (renderer.frameFlow.value.bitmap == null) delay(10)
        }
        renderer.setSearchSelected(48.8566, 2.3522)
        withTimeout(5000) {
            while (client.lastSearchSelLat.isNaN()) delay(10)
        }

        renderer.clearSearchSelected()

        withTimeout(5000) {
            while (!client.lastSearchSelLat.isNaN()) delay(10)
        }
        assertTrue(client.lastSearchSelLat.isNaN())
        assertTrue(client.lastSearchSelLon.isNaN())
    }
}
