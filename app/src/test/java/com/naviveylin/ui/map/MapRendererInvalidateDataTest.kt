package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies [MapRenderer.invalidateData]: a basemap data change (download,
 * update, or delete while the app runs) bumps the epoch, clears the tile
 * cache, and forces a full re-render without any camera movement (spec:
 * basemap-loading — current view re-renders with the basemap overlay active).
 *
 * Runs under Robolectric with the default sandbox: FakeOSMScoutClient triggers
 * OSMScoutClient's static System.loadLibrary (stubbed .so), which requires the
 * default classloader (AGENTS.md classloader rule).
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapRendererInvalidateDataTest {

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
    fun invalidateDataClearsTileCacheAndForcesReRender() = runTest(mainDispatcherRule.dispatcher) {
        // Warm the tile cache in TILES mode.
        renderer.requestRender(51.5, 7.5, 14.0, 0.0)
        awaitFrame()
        val tilesAfterWarmup = client.renderWithRouteAndPoisCount.get()
        assertTrue("tile path must render tiles", tilesAfterWarmup >= 1)

        // Basemap changed: invalidateData at the SAME viewport (no gesture).
        renderer.invalidateData()
        awaitFrame()

        assertTrue(
            "invalidateData must re-render the cleared tiles at the same viewport",
            client.renderWithRouteAndPoisCount.get() > tilesAfterWarmup
        )
    }

    @Test
    fun invalidateDataForcedRenderSurvivesCoveringBlit() = runTest(mainDispatcherRule.dispatcher) {
        renderer.requestRender(51.5, 7.5, 14.0, 0.0)
        awaitFrame()
        val totalBefore = client.renderCountTotal()

        // Data change, then a covering non-force request at the same viewport:
        // the pending forced render must NOT be discarded (same contract as
        // setRoute in MapRendererBlitTest).
        renderer.invalidateData()
        renderer.requestRender(51.5, 7.5, 14.0, 0.0)
        advanceUntilIdle()

        assertTrue(
            "forced data-change render must execute despite the covering blit",
            client.renderCountTotal() > totalBefore
        )
    }

    private fun FakeOSMScoutClient.renderCountTotal() = renderCount.get() + renderWithRouteAndPoisCount.get()
}
