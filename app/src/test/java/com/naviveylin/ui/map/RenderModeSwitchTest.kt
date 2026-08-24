package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.data.RenderMode
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the render-mode switch: TILES mode renders missing tiles natively
 * through the tile path, DIRECT mode always renders the full frame natively,
 * and switching modes invalidates the cache and re-renders from scratch.
 *
 * The renderer scope runs on the shared test dispatcher, so all debounce/
 * render work is driven by `advanceUntilIdle` (virtual time) — no real-time
 * polling, no wall-clock race.
 */
@RunWith(RobolectricTestRunner::class)
class RenderModeSwitchTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var client: FakeOSMScoutClient
    private lateinit var renderer: MapRenderer
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
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
    }

    /** Run all queued render work; fail if no frame bitmap was emitted. */
    private suspend fun TestScope.awaitFrame() {
        advanceUntilIdle()
        check(renderer.frameFlow.value.bitmap != null) { "no frame bitmap emitted" }
    }

    @Test
    fun tilesModeRendersTilesNotFullFrame() = runTest(mainDispatcherRule.dispatcher) {
        // Default mode is TILES.
        assertEquals(RenderMode.TILES, renderer.renderMode)

        renderer.requestRender(51.5, 7.5, 14, 0.0)
        awaitFrame()

        assertTrue(
            "TILES mode must render per-tile via renderWithRouteAndPois",
            client.renderWithRouteAndPoisCount.get() >= 1
        )
        assertEquals(
            "TILES mode must not issue a full native render",
            0,
            client.renderCount.get()
        )
    }

    @Test
    fun directModeRendersFullFrameWithoutTiles() = runTest(mainDispatcherRule.dispatcher) {
        renderer.renderMode = RenderMode.DIRECT
        renderer.requestRender(51.5, 7.5, 14, 0.0)
        awaitFrame()

        assertEquals(
            "DIRECT mode must issue exactly one full native render",
            1,
            client.renderCount.get()
        )
        assertEquals(
            "DIRECT mode must not render per-tile",
            0,
            client.renderWithRouteAndPoisCount.get()
        )
    }

    @Test
    fun switchingToDirectInvalidatesCacheAndForcesFullRender() = runTest(mainDispatcherRule.dispatcher) {
        // Warm the tile cache in TILES mode.
        renderer.requestRender(51.5, 7.5, 14, 0.0)
        awaitFrame()
        val tilesAfterWarmup = client.renderWithRouteAndPoisCount.get()
        assertTrue(tilesAfterWarmup >= 1)

        // Switch: cache cleared (invalidateStyle), next frame must come from
        // the direct native path.
        renderer.renderMode = RenderMode.DIRECT
        renderer.invalidateStyle()
        awaitFrame()

        assertTrue(
            "switch to DIRECT must produce a full native render",
            client.renderCount.get() >= 1
        )
        // The forced re-render must not consult the (now cleared) tile cache:
        // no new per-tile renders after the warm-up.
        assertEquals(
            "no per-tile render after switching to DIRECT",
            tilesAfterWarmup,
            client.renderWithRouteAndPoisCount.get()
        )
    }

    @Test
    fun directModeKeepsWorkingAfterPanAndZoom() = runTest(mainDispatcherRule.dispatcher) {
        renderer.renderMode = RenderMode.DIRECT
        renderer.requestRender(51.5, 7.5, 14, 0.0)
        awaitFrame()

        renderer.requestRender(51.6, 7.5, 15, 0.0)
        advanceUntilIdle()

        assertEquals(15, renderer.renderedMag)
        assertEquals(2, client.renderCount.get())
        assertEquals(0, client.renderWithRouteAndPoisCount.get())
    }

    @Test
    fun switchingModeDiscardsInFlightTileRender() = runTest(mainDispatcherRule.dispatcher) {
        // Slow tile renderer: the tile path blocks long enough to interleave a
        // mode switch while the first job is in flight.
        client.renderWithRouteAndPoisDelayMs = 400L
        val slowRenderer = MapRenderer(client, 320.0, scope)
        slowRenderer.screenWidth = 1200
        slowRenderer.screenHeight = 1200

        slowRenderer.requestRender(51.5, 7.5, 14, 0.0)
        // Switch while the tile render is in flight: epoch bumps, in-flight
        // result must be discarded, and the re-render must come from DIRECT.
        slowRenderer.renderMode = RenderMode.DIRECT
        slowRenderer.invalidateStyle()

        advanceUntilIdle()

        assertTrue(
            "final frame must be produced by the direct path",
            client.renderCount.get() >= 1
        )
        // The stale tile render may have completed, but its result must have
        // been discarded (epoch mismatch) — the front buffer reflects the new
        // mode's render, and no TILES-mode tile render can follow it.
        assertTrue(
            "no tile render may follow the mode switch",
            client.renderWithRouteAndPoisCount.get() <= 1
        )
        slowRenderer.shutdown()
    }
}
