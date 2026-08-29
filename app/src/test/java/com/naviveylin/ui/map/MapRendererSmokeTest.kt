package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Smoke test for [MapRenderer] construction and pure helpers.
 *
 * The GPS marker was moved out of the render pipeline into the Compose overlay
 * ([LocationMarkerOverlay]); marker state/throttle coverage moved to
 * [LocationMarkerOverlayTest] and the ViewModel follow-mode tests.
 *
 * Render work runs on the shared test dispatcher (virtual time via
 * [advanceUntilIdle]) — no real-time polling.
 */
@RunWith(RobolectricTestRunner::class)
class MapRendererSmokeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var renderer: MapRenderer
    private lateinit var client: FakeOSMScoutClient
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        client = FakeOSMScoutClient()
        scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher)
        renderer = MapRenderer(
            client = client,
            dpi = 320.0,
            scope = scope
        )
    }

    @After
    fun tearDown() {
        renderer.shutdown()
        scope.cancel()
    }

    private suspend fun TestScope.awaitVisibleMarker() {
        advanceUntilIdle()
        check(renderer.frameFlow.value.marker.visible) { "no frame with visible marker emitted" }
    }

    private suspend fun TestScope.awaitFrame() {
        advanceUntilIdle()
        check(renderer.frameFlow.value.bitmap != null) { "no frame bitmap emitted" }
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
    fun markerSnapshotRidesWithEmittedFrame() = runTest(mainDispatcherRule.dispatcher) {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        renderer.setGpsMarkerState(48.8566, 2.3522, 45.0, 10.0)
        renderer.requestRender(48.8566, 2.3522, 14.0, 0.0)

        awaitVisibleMarker()
        val snap = renderer.frameFlow.value.marker
        assertEquals(48.8566, snap.lat, 1e-9)
        assertEquals(2.3522, snap.lon, 1e-9)
        assertEquals(45.0, snap.bearing, 1e-9)
        assertEquals(10.0, snap.accuracy, 1e-9)
    }

    @Test
    fun clearGpsMarkerStateEmitsHiddenSnapshot() = runTest(mainDispatcherRule.dispatcher) {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        renderer.setGpsMarkerState(48.8566, 2.3522, 45.0, 10.0)
        renderer.requestRender(48.8566, 2.3522, 14.0, 0.0)

        awaitVisibleMarker()
        renderer.clearGpsMarkerState()
        assertFalse(renderer.frameFlow.value.marker.visible)
    }

    @Test
    fun setSearchSelectedForwardsMarkerToNativeRender() = runTest(mainDispatcherRule.dispatcher) {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        renderer.requestRender(48.8566, 2.3522, 14.0, 0.0)
        // Wait until the initial render emitted a frame, then set the marker
        awaitFrame()
        client.lastSearchSelLat = Double.NaN

        renderer.setSearchSelected(48.8566, 2.3522)
        advanceUntilIdle()

        assertEquals(48.8566, client.lastSearchSelLat, 1e-9)
        assertEquals(2.3522, client.lastSearchSelLon, 1e-9)
    }

    @Test
    fun clearSearchSelectedResetsMarker() = runTest(mainDispatcherRule.dispatcher) {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        renderer.requestRender(48.8566, 2.3522, 14.0, 0.0)
        awaitFrame()
        renderer.setSearchSelected(48.8566, 2.3522)
        advanceUntilIdle()
        assertEquals(48.8566, client.lastSearchSelLat, 1e-9)

        renderer.clearSearchSelected()
        advanceUntilIdle()

        assertTrue(client.lastSearchSelLat.isNaN())
        assertTrue(client.lastSearchSelLon.isNaN())
    }
}
