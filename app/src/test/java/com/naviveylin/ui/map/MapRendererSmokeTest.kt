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

    @Before
    fun setUp() {
        renderer = MapRenderer(
            client = FakeOSMScoutClient(),
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
}
