package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Viewport-restore race regression (change `fix-viewport-save-race`, spec
 * `viewport-persist`): a screen-size report arriving while `initMap` is still
 * restoring the persisted viewport must not submit a render with the
 * uninitialized default viewport, and the restored center must survive on
 * disk. No @Config — default Robolectric sandbox so the FakeOSMScoutClient
 * JNI stub loads correctly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelViewportRestoreTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewportStorage: ViewportStorage
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        viewportStorage = ViewportStorage(context)
        viewModel = MapCanvasViewModel(
            viewportStorage = viewportStorage,
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    @Test
    fun `screen size during viewport restore does not clobber restored center`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Seed a persisted viewport for the map initMap will open.
            val file = File(context.filesDir, "maps/viewport-testmap.json")
            file.parentFile?.mkdirs()
            file.writeText("""{"centerLat":48.2,"centerLon":16.4,"magnification":12.0,"angle":0.0}""")

            // Gate the storage dispatcher so initMap suspends at
            // viewportStorage.load and the test can interleave a setScreenSize
            // call into that window (the exact race the fix removes).
            val gated = GatedDispatcher()
            viewportStorage.ioDispatcher = gated

            // initMap runs until it suspends at the viewport load.
            viewModel.initMap("/data/maps/testmap")
            mainDispatcherRule.dispatcher.scheduler.runCurrent()

            // The composable reports its size while the restore is still in
            // progress. No renderer exists yet — no render may be submitted
            // with the uninitialized default viewport.
            viewModel.setScreenSize(100, 100)
            assertEquals(0, client.renderCount.get() + client.renderWithRouteAndPoisCount.get())

            // Let the restore complete. The real-time pause matters: a buggy
            // implementation (renderer created before the restore) would have
            // queued a default-viewport render here, which the debounce fires
            // during this window — the render-count assertion below catches it.
            Thread.sleep(300)
            gated.release()
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            // Wait for the first render — the renderer's debounce runs on a
            // real dispatcher, outside the test scheduler's control.
            val deadline = System.currentTimeMillis() + 5000
            while (client.renderCount.get() + client.renderWithRouteAndPoisCount.get() < 1 &&
                System.currentTimeMillis() < deadline
            ) {
                Thread.sleep(10)
            }
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            // Exactly one render, with the RESTORED center — no early
            // default-viewport render was submitted. The tile path renders at
            // tile centers, so compare loosely (a default-center render at
            // 51.51/7.47 would still fail this).
            assertEquals(1, client.renderCount.get() + client.renderWithRouteAndPoisCount.get())
            assertEquals(48.2, client.lastRenderLat, 0.1)
            assertEquals(16.4, client.lastRenderLon, 0.1)

            // The persisted file still holds the restored center.
            val loaded = viewportStorage.load("testmap")
            assertEquals(48.2, loaded!!.centerLat, 1e-9)
            assertEquals(16.4, loaded.centerLon, 1e-9)
            assertEquals(12.0, loaded.magnification, 1e-9)

            // The map state carries the restored viewport.
            val vp = viewModel.uiState.value.viewport
            assertEquals(48.2, vp.centerLat, 1e-9)
            assertEquals(16.4, vp.centerLon, 1e-9)
            assertEquals(12.0, vp.magnification, 1e-9)
        }

    @Test
    fun `saveViewport during restore does not clobber persisted viewport`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Seed a persisted viewport for the map initMap will open.
            val file = File(context.filesDir, "maps/viewport-testmap.json")
            file.parentFile?.mkdirs()
            file.writeText("""{"centerLat":48.2,"centerLon":16.4,"magnification":12.0,"angle":0.0}""")

            // Gate the storage dispatcher so initMap suspends at the viewport
            // load — the exact window a lifecycle save must not write into.
            val gated = GatedDispatcher()
            viewportStorage.ioDispatcher = gated

            viewModel.initMap("/data/maps/testmap")
            mainDispatcherRule.dispatcher.scheduler.runCurrent()

            // Lifecycle save fires while the restore is still in progress.
            // With the fix it no-ops (viewportRestored is false); without it,
            // the default viewport (51.5136/7.4653/8.0) is queued and written
            // on release, clobbering the seed.
            viewModel.saveViewport()
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            gated.release()
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            // The file still holds the seeded (restored) center — not the
            // default. Read before the debounced first render's view-change
            // listener can rewrite it (it would write the same values and
            // mask a clobber).
            val loaded = viewportStorage.load("testmap")
            assertEquals(48.2, loaded!!.centerLat, 1e-9)
            assertEquals(16.4, loaded.centerLon, 1e-9)
            assertEquals(12.0, loaded.magnification, 1e-9)
        }

    @Test
    fun `saveViewport after restore persists current viewport`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Seed a persisted viewport for the map initMap will open.
            val file = File(context.filesDir, "maps/viewport-testmap.json")
            file.parentFile?.mkdirs()
            file.writeText("""{"centerLat":48.2,"centerLon":16.4,"magnification":12.0,"angle":0.0}""")

            val gated = GatedDispatcher()
            viewportStorage.ioDispatcher = gated

            viewModel.initMap("/data/maps/testmap")
            mainDispatcherRule.dispatcher.scheduler.runCurrent()
            gated.release()
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            // Restore applied — the save guard is armed. Delete the file so
            // the only writer left is saveViewport itself (the first render's
            // view-change listener is still debounced on a real dispatcher).
            file.delete()

            viewModel.saveViewport()
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            val loaded = viewportStorage.load("testmap")
            assertEquals(48.2, loaded!!.centerLat, 1e-9)
            assertEquals(16.4, loaded.centerLon, 1e-9)
            assertEquals(12.0, loaded.magnification, 1e-9)
        }

    /**
     * Test dispatcher that queues dispatched blocks until [release] is called,
     * letting a test hold a coroutine suspended at a `withContext` boundary
     * (e.g. ViewportStorage.load) while the main thread performs other work.
     */
    private class GatedDispatcher : CoroutineDispatcher() {
        private val queue = ConcurrentLinkedQueue<Runnable>()
        private val released = AtomicBoolean(false)

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (released.get()) {
                block.run()
            } else {
                queue.offer(block)
            }
        }

        fun release() {
            released.set(true)
            while (true) {
                val block = queue.poll() ?: break
                block.run()
            }
        }
    }
}
