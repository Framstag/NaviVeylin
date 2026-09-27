package com.naviveylin.ui.map

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.NativeTileDataCache
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.navigation.CarSessionPresenceImpl
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import java.io.File
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
 * The phone's side of a live car session (spec: `map-canvas-screen` — The phone map canvas is
 * suspended while a car session is active / The car-session surface is informative and offers
 * the map back): a presence edge suspends the map, no render is requested while suspended, the
 * phone-owned storage is released once per session, the override brings the map back for the
 * rest of the session and is cleared at its end, and the shared native tile-data cache is
 * never touched — the car renders from it.
 *
 * A real [MapRenderer] is injected instead of running `initMap` (no database in a unit test),
 * so the render requests the ViewModel issues are counted on the client.
 *
 * Default Robolectric sandbox (no `@Config`): [FakeOSMScoutClient] loads the JNI stub, per the
 * AGENTS.md classloader rule.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapCanvasViewModelCarSessionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var presence: CarSessionPresenceImpl
    private lateinit var renderer: MapRenderer
    private lateinit var rendererScope: CoroutineScope
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var logFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        presence = CarSessionPresenceImpl()
        val settings = SettingsStorage(context)
        settings.ioDispatcher = mainDispatcherRule.dispatcher
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = settings,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            darkModeController = DarkModeController(settings),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            carSessionPresence = presence,
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel.setScreenSize(SCREEN_W, SCREEN_H)
        viewModel.updateMagnification(MAG)

        // The renderer the screen's initMap would build, wired without a database: the
        // ViewModel's render requests are counted on the client (renderCount).
        rendererScope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher)
        renderer = MapRenderer(client, 320.0, rendererScope)
        renderer.screenWidth = SCREEN_W
        renderer.screenHeight = SCREEN_H
        viewModel.setMapRendererForTest(renderer)

        logFile = File(context.filesDir, "diagnostics/car-session-${System.nanoTime()}.log")
        DiagnosticsLog.reset()
        DiagnosticsLog.initForTest(logFile)
        NativeTileDataCache.resetForTest()
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
        renderer.shutdown()
        rendererScope.cancel()
        DiagnosticsLog.reset()
        NativeTileDataCache.resetForTest()
        logFile.parentFile?.deleteRecursively()
    }

    private fun FakeOSMScoutClient.renderCountTotal() =
        renderCount.get() + renderWithRouteAndPoisCount.get()

    private fun TestScope.edge(active: Boolean) {
        presence.setActive(active)
        advanceUntilIdle()
    }

    private fun diagnosticsLines(needle: String): List<String> =
        DiagnosticsLog.readEntries().filter { it.contains(needle) }

    /** One frame as the renderer would emit it, for the frame-application paths. */
    private fun frame(mag: Double = MAG) = MapRenderer.FrameState(
        bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888),
        viewport = MapRenderer.RenderViewport(VEHICLE_LAT, VEHICLE_LON, mag, 0.0),
        marker = MapRenderer.MarkerSnapshot(Double.NaN, Double.NaN, Double.NaN, 0.0)
    )

    @Test
    fun aRisingEdgeSuspendsThePhoneMapAndRecordsTheReleaseOnce() =
        runTest(mainDispatcherRule.dispatcher) {
            edge(active = true)

            val state = viewModel.uiState.value
            assertTrue("the phone map is suspended", state.phoneMapSuspended)
            assertFalse("no override was requested yet", state.phoneMapOverridden)

            val recorded = diagnosticsLines("phone map suspended")
            assertEquals("one record per session, not per emission", 1, recorded.size)
            assertTrue(
                "the record names the release and the untouched shared cache: $recorded",
                recorded.single().contains("released tiles=") &&
                    recorded.single().contains("shared native tile-data cache untouched")
            )

            // A repeated value is not an edge: no second release.
            presence.setActive(true)
            advanceUntilIdle()
            assertEquals(1, diagnosticsLines("phone map suspended").size)
        }

    @Test
    fun noRenderIsRequestedWhileSuspended() = runTest(mainDispatcherRule.dispatcher) {
        // Control: without a car session the request reaches the renderer.
        viewModel.renderMap()
        advanceUntilIdle()
        val rendersWithoutSession = client.renderCountTotal()
        assertTrue("the harness renders without a car session", rendersWithoutSession > 0)

        edge(active = true)
        viewModel.renderMap()
        advanceUntilIdle()

        assertEquals(
            "a suspended phone map requests no render",
            rendersWithoutSession,
            client.renderCountTotal()
        )
    }

    @Test
    fun aFrameArrivingWhileSuspendedIsDiscarded() = runTest(mainDispatcherRule.dispatcher) {
        // Control: a frame is published while no car session is live.
        viewModel.applyRenderedFrame(frame())
        assertNotNull("a frame is published without a car session", viewModel.uiState.value.renderedBitmap)

        edge(active = true)
        assertNull("the suspension drops the published frame", viewModel.uiState.value.renderedBitmap)

        viewModel.applyRenderedFrame(frame())
        assertNull(
            "a frame from a render in flight at the edge is discarded, not published",
            viewModel.uiState.value.renderedBitmap
        )
    }

    @Test
    fun theOverrideReturnsTheMapForTheRestOfTheSession() = runTest(mainDispatcherRule.dispatcher) {
        edge(active = true)
        val rendersWhileSuspended = client.renderCountTotal()

        viewModel.showMapDuringCarSession()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("the override lifts the suspension", state.phoneMapSuspended)
        assertTrue("the override is remembered for the session", state.phoneMapOverridden)
        assertEquals("the override is recorded once", 1, diagnosticsLines("phone map override").size)
        assertTrue(
            "the override renders the map (spec: the phone renders as without a car session)",
            client.renderCountTotal() > rendersWhileSuspended
        )

        // And rendering stays enabled: a camera move renders. An UNCHANGED viewport can be
        // served by the frame in hand — that is the overrun rule, not the suspension gate.
        val afterOverride = client.renderCountTotal()
        viewModel.updateCenter(MOVED_LAT, MOVED_LON)
        viewModel.renderMap()
        advanceUntilIdle()
        assertTrue("rendering stays enabled after the override", client.renderCountTotal() > afterOverride)
    }

    @Test
    fun theOverrideIsClearedAtSessionEndAndTheNextSessionSuspendsAgain() =
        runTest(mainDispatcherRule.dispatcher) {
            // A rendered map, then a real suspension: the release drops the frame, so the
            // resume cannot reuse anything and has to render.
            viewModel.renderMap()
            advanceUntilIdle()
            val rendersBeforeSuspension = client.renderCountTotal()

            edge(active = true)
            assertNull("the suspension drops the frame", renderer.frameFlow.value.bitmap)

            edge(active = false)

            val resumed = viewModel.uiState.value
            assertFalse("the session end lifts the suspension", resumed.phoneMapSuspended)
            assertFalse("the override does not outlive the session", resumed.phoneMapOverridden)
            assertTrue(
                "the session end renders a fresh frame instead of reusing the released one",
                client.renderCountTotal() > rendersBeforeSuspension
            )
            assertEquals(1, diagnosticsLines("phone map resumed").size)

            // The override path: taken, then cleared by the session that follows it.
            edge(active = true)
            viewModel.showMapDuringCarSession()
            advanceUntilIdle()
            assertTrue("the override is active", viewModel.uiState.value.phoneMapOverridden)

            edge(active = false)
            assertFalse(
                "the override does not outlive its own session",
                viewModel.uiState.value.phoneMapOverridden
            )
            assertEquals(
                "resumed records: ${diagnosticsLines("phone map resumed")}",
                2,
                diagnosticsLines("phone map resumed").size
            )

            // A later session suspends again, by default and with its own release: one
            // release per session (three here: the first, the overridden one, and this one).
            edge(active = true)
            assertTrue("the next session suspends again", viewModel.uiState.value.phoneMapSuspended)
            assertFalse(viewModel.uiState.value.phoneMapOverridden)
            assertEquals(3, diagnosticsLines("phone map suspended").size)
            assertEquals(2, diagnosticsLines("phone map resumed").size)
            assertEquals(1, diagnosticsLines("phone map override").size)
        }

    @Test
    fun theResumedMapRendersAtTheCurrentViewportNotTheFrozenOne() =
        runTest(mainDispatcherRule.dispatcher) {
            edge(active = true)
            // The engine/UI keeps working while the phone is suspended: the viewport moves.
            viewModel.updateCenter(MOVED_LAT, MOVED_LON)
            advanceUntilIdle()

            edge(active = false)
            advanceUntilIdle()

            assertEquals(
                "the resume renders at the viewport the map holds now",
                MOVED_LAT,
                renderer.currentLat,
                1e-9
            )
            assertEquals(MOVED_LON, renderer.currentLon, 1e-9)
        }

    @Test
    fun theSuspensionNeverTouchesTheSharedNativeTileDataCache() =
        runTest(mainDispatcherRule.dispatcher) {
            NativeTileDataCache.apply(client, NativeTileDataCache.PHONE_TILES)
            assertEquals(
                "the phone configures the shared cache once",
                listOf(NativeTileDataCache.PHONE_TILES),
                client.nativeDataCacheSizes
            )

            edge(active = true)
            viewModel.showMapDuringCarSession()
            advanceUntilIdle()
            edge(active = false)
            advanceUntilIdle()

            assertEquals(
                "the shared cache is configured once and never re-sized by a suspension",
                listOf(NativeTileDataCache.PHONE_TILES),
                client.nativeDataCacheSizes
            )
            assertEquals(
                "the capacity the car renders with is unchanged",
                NativeTileDataCache.PHONE_TILES,
                NativeTileDataCache.currentCapacity(client)
            )
        }

    @Test
    fun theSuspensionReleasesTheRenderersStorage() = runTest(mainDispatcherRule.dispatcher) {
        // Warm the renderer itself; the ViewModel's own request path is covered above.
        renderer.requestRender(VEHICLE_LAT, VEHICLE_LON, MAG, 0.0)
        advanceUntilIdle()
        assertNotNull("the renderer holds a frame before the edge", renderer.frameFlow.value.bitmap)

        edge(active = true)

        assertNull("the release drops the held frame", renderer.frameFlow.value.bitmap)
        assertEquals("the released tile cache is empty", 0, renderer.tileCacheSize())
    }

    private companion object {
        const val SCREEN_W = 1080
        const val SCREEN_H = 2400
        const val MAG = 14.0
        const val VEHICLE_LAT = 51.5
        const val VEHICLE_LON = 7.5
        const val MOVED_LAT = 48.1372
        const val MOVED_LON = 11.5756
    }
}
