package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.RenderBitmapPool
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies [MapRenderer.releaseRenderStorage] (spec: `map-canvas-screen` — Phone-owned
 * storage is released, the shared cache is not): the suspension gives up the rendered tile
 * cache and the frame buffers, leaves the renderer usable for the resume render, and never
 * recycles the frame the UI may still be drawing.
 *
 * Runs under Robolectric with the default sandbox: [FakeOSMScoutClient] triggers
 * OSMScoutClient's static System.loadLibrary (stubbed .so), which requires the default
 * classloader (AGENTS.md classloader rule).
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapRendererReleaseStorageTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var client: FakeOSMScoutClient
    private lateinit var renderer: MapRenderer
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        RenderBitmapPool.resetForTest()
        client = FakeOSMScoutClient()
        scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher)
        renderer = MapRenderer(client, 320.0, scope)
        renderer.screenWidth = 1200
        renderer.screenHeight = 1200
    }

    @After
    fun tearDown() {
        renderer.shutdown()
        scope.cancel()
        RenderBitmapPool.resetForTest()
    }

    private fun FakeOSMScoutClient.renderCountTotal() =
        renderCount.get() + renderWithRouteAndPoisCount.get()

    /** Render once at [mag]; fail if no frame bitmap was emitted. */
    private fun TestScope.renderFrame(mag: Double = 14.0) {
        renderer.requestRender(51.5, 7.5, mag, 0.0)
        advanceUntilIdle()
        check(renderer.frameFlow.value.bitmap != null) { "no frame bitmap emitted" }
    }

    @Test
    fun releaseDropsTheTileCacheAndBothFrameBuffers() = runTest(mainDispatcherRule.dispatcher) {
        renderFrame()
        val tilesBefore = renderer.tileCacheSize()
        assertTrue("the tile path must have cached tiles", tilesBefore > 0)

        val released = renderer.releaseRenderStorage()

        assertEquals("every cached tile is released", tilesBefore, released.tilesCleared)
        // A tile-path frame holds one buffer (the emitted copy is separate); a full/rotated
        // render holds the back buffer as well.
        assertTrue("at least the front buffer is released", released.frameBuffersReleased >= 1)
        assertEquals(0, renderer.tileCacheSize())
    }

    @Test
    fun releaseLeavesTheRendererUsableAndRendersAFreshFrame() =
        runTest(mainDispatcherRule.dispatcher) {
            renderFrame()
            val frameBefore = renderer.frameFlow.value.bitmap
            val rendersBefore = client.renderCountTotal()

            renderer.releaseRenderStorage()

            // The released frame is never recycled — Compose may still be drawing it — but it
            // must not stay referenced by the renderer either: the frame flow drops it.
            assertFalse("a released frame is never recycled", frameBefore!!.isRecycled)
            assertNull("the frame flow no longer holds the released frame", renderer.frameFlow.value.bitmap)

            // No shutdown: the next request renders from scratch.
            renderFrame(mag = 13.0)
            assertTrue(
                "the renderer renders again after a release",
                client.renderCountTotal() > rendersBefore
            )
            assertNotNull(renderer.frameFlow.value.bitmap)
        }

    @Test
    fun releaseIsIdempotentAndReportsNothingOnASecondCall() =
        runTest(mainDispatcherRule.dispatcher) {
            renderFrame()
            val first = renderer.releaseRenderStorage()
            assertTrue(first.tilesCleared > 0)

            val second = renderer.releaseRenderStorage()

            assertEquals("no tiles left to release", 0, second.tilesCleared)
            assertEquals("no frame buffers left to release", 0, second.frameBuffersReleased)
            assertEquals("no idle targets left to release", 0, second.idleTargetsReleased)
        }

    @Test
    fun releaseDropsThePooledTargetsTheFramesWereComposedIn() =
        runTest(mainDispatcherRule.dispatcher) {
            renderFrame()
            val pooled = RenderBitmapPool.acquire(1200, 1200)
            RenderBitmapPool.release(pooled)
            assertTrue(RenderBitmapPool.freeCount(1200, 1200) >= 1)

            val released = renderer.releaseRenderStorage()

            assertTrue("the idle pooled target is released too", released.idleTargetsReleased >= 1)
            assertEquals(0, RenderBitmapPool.freeCount(1200, 1200))
            assertEquals("no caller's target is stranded", 0, RenderBitmapPool.inUseCount)
        }

    @Test
    fun releaseDropsAQueuedRenderInsteadOfExecutingIt() = runTest(mainDispatcherRule.dispatcher) {
        renderFrame()
        val rendersBefore = client.renderCountTotal()
        // A request lands in the debounce window: it must not execute into released storage.
        renderer.requestRender(51.5, 7.5, 12.0, 0.0)
        val latBefore = renderer.currentLat
        val lonBefore = renderer.currentLon

        renderer.releaseRenderStorage()
        advanceUntilIdle()

        assertEquals(
            "the queued render is dropped, not executed",
            rendersBefore,
            client.renderCountTotal()
        )
        assertEquals("the release does not move the committed viewport", latBefore, renderer.currentLat, 0.0)
        assertEquals("the release does not move the committed viewport", lonBefore, renderer.currentLon, 0.0)
    }

    @Test
    fun anOverrunFrameIsNotServedAfterARelease() = runTest(mainDispatcherRule.dispatcher) {
        renderFrame()
        // The released renderer must report "not covered" — its frame is gone — so a pan
        // request renders instead of shifting a frame that no longer exists.
        renderer.releaseRenderStorage()

        assertFalse(
            "a released frame cannot serve an overrun window",
            renderer.overrunWindowCovers(51.5001, 7.5001, 14.0, 0.0)
        )
    }
}
