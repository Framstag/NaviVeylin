package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.RenderBitmapPool
import com.naviveylin.data.RenderMode
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
@OptIn(ExperimentalCoroutinesApi::class)
class MapRendererSmokeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var renderer: MapRenderer
    private lateinit var client: FakeOSMScoutClient
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        RenderBitmapPool.resetForTest()
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

    // ---- Pooled render target (spec: render-performance — Reusable render target for map
    // frames; design D6/D7): the render path releases its target on every exit, later renders
    // reuse it, and the frame handed to the display layer stays an independent copy. ----

    @Test
    fun renderPathReleasesItsPooledTargetAndReusesIt() = runTest(mainDispatcherRule.dispatcher) {
        renderer.screenWidth = 200
        renderer.screenHeight = 300

        renderer.requestRender(48.8566, 2.3522, 14.0, 0.0)
        awaitFrame()
        val afterFirst = RenderBitmapPool.allocatedCount
        assertTrue("the render took its target from the pool", afterFirst >= 1)
        assertEquals(
            "no target stays handed out once the frame is emitted",
            0,
            RenderBitmapPool.inUseCount
        )

        renderer.requestRender(48.8566, 2.3522, 14.5, 0.0)
        advanceUntilIdle()

        assertEquals("the released target is reused, not re-allocated", afterFirst, RenderBitmapPool.allocatedCount)
        assertEquals(0, RenderBitmapPool.inUseCount)
    }

    @Test
    fun emittedFrameIsAnIndependentCopyAndSurvivesLaterRenders() = runTest(mainDispatcherRule.dispatcher) {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        renderer.requestRender(48.8566, 2.3522, 14.0, 0.0)
        awaitFrame()

        val emitted = renderer.frameFlow.value.bitmap!!
        val pixelsBefore = emitted.getPixel(0, 0)
        assertTrue("the emitted frame is a live bitmap", !emitted.isRecycled)

        renderer.requestRender(48.8566, 2.3522, 16.0, 0.0)
        advanceUntilIdle()

        assertTrue(
            "a displayed frame must never be recycled or overwritten by a later render",
            !emitted.isRecycled
        )
        assertEquals(pixelsBefore, emitted.getPixel(0, 0))
        assertTrue("the emitted frame is not the pooled target", RenderBitmapPool.inUseCount == 0)
    }

    @Test
    fun shutdownLeavesNoTargetHeld() = runTest(mainDispatcherRule.dispatcher) {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        renderer.requestRender(48.8566, 2.3522, 14.0, 0.0)
        awaitFrame()
        val allocated = RenderBitmapPool.allocatedCount
        val frame = renderer.frameFlow.value.bitmap!!

        renderer.shutdown()

        assertEquals("shutdown strands no pooled target", 0, RenderBitmapPool.inUseCount)
        // The render target is overrun-sized, i.e. the front buffer's own size.
        val target = RenderBitmapPool.acquire(frame.width, frame.height)
        assertEquals(
            "the renderer's target went back to the pool and is reused",
            allocated,
            RenderBitmapPool.allocatedCount
        )
        RenderBitmapPool.release(target)
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

    // ---- Projection DPI is part of the render request (spec: render-projection-dpi) ----

    /**
     * The tile path renders one native viewport per missing tile; each request carries the
     * renderer's own DPI — the same value a full-frame render uses, so the composed tiles and
     * the overlay projection agree (spec: render-projection-dpi — "Per-tile render carries the
     * same DPI as the frame"; spec: map-render — "Tile render carries the display DPI").
     */
    @Test
    fun tilePathRendersEveryTileAtTheRenderersDpi() = runTest(mainDispatcherRule.dispatcher) {
        renderer.renderMode = RenderMode.TILES
        renderer.screenWidth = 400
        renderer.screenHeight = 400

        renderer.requestRender(48.8566, 2.3522, 14.0, 0.0)
        awaitFrame()

        assertTrue("the tile path rendered tiles natively", client.renderWithRouteAndPoisCount.get() > 0)
        assertTrue(
            "every tile request carries the renderer's DPI (320.0), never a literal",
            client.renderDpis.isNotEmpty() && client.renderDpis.all { it == 320.0 }
        )
    }

    /**
     * The reported defect at renderer level: an Android Auto session and the phone UI share one
     * process and one client, so a frame must be projected at its own surface's DPI no matter
     * which surface rendered last (spec: render-projection-dpi — "No process-global projection
     * DPI", "Surface switch leaves the other surface's scale intact").
     */
    @Test
    fun twoRenderersShareTheClientAndEachFrameCarriesItsOwnDpi() = runTest(mainDispatcherRule.dispatcher) {
        val carScope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher)
        val carRenderer = MapRenderer(client = client, dpi = 236.0, scope = carScope)
        try {
            renderer.renderMode = RenderMode.DIRECT
            carRenderer.renderMode = RenderMode.DIRECT
            renderer.screenWidth = 200
            renderer.screenHeight = 200
            carRenderer.screenWidth = 200
            carRenderer.screenHeight = 200

            renderer.requestRender(48.8566, 2.3522, 14.0, 0.0)
            awaitFrame()
            assertEquals("the phone's frame is projected at the phone DPI", 320.0, client.renderDpis.last(), 0.0)

            val afterPhone = client.renderDpis.size
            carRenderer.requestRender(48.8566, 2.3522, 14.0, 0.0)
            advanceUntilIdle()
            assertTrue("the car surface rendered", client.renderDpis.size > afterPhone)
            assertEquals("the car's frame is projected at the car surface DPI", 236.0, client.renderDpis.last(), 0.0)

            renderer.requestRender(48.8566, 2.3522, 14.0, 0.0)
            advanceUntilIdle()
            assertEquals(
                "the phone's next frame is projected at its own DPI again, with no re-init",
                320.0,
                client.renderDpis.last(),
                0.0
            )
        } finally {
            carRenderer.shutdown()
            carScope.cancel()
        }
    }
}
