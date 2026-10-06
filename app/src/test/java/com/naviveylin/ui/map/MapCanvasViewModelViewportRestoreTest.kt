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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        // Every collaborator the restore path hops through runs on this test's scheduler: with a real
        // Dispatchers.IO hop in the path no amount of advancing reaches the state under test, which is
        // what forced the bounded real-clock polls this class used to carry (spec
        // `unit-test-suite-runtime` — a case awaits observable state, not a deadline). The renderer has
        // its own seam because the restore window deliberately gates the ViewModel's initialization.
        viewportStorage.ioDispatcher = mainDispatcherRule.dispatcher
        val settingsStorage = SettingsStorage(context).also { it.ioDispatcher = mainDispatcherRule.dispatcher }
        val searchHistoryRepository = SearchHistoryRepository(context)
            .also { it.defaultDispatcher = mainDispatcherRule.dispatcher }
        val favoriteRepository = FavoriteRepository(client)
            .also { it.defaultDispatcher = mainDispatcherRule.dispatcher }
        viewModel = MapCanvasViewModel(
            viewportStorage = viewportStorage,
            settingsStorage = settingsStorage,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = favoriteRepository,
            searchHistoryRepository = searchHistoryRepository,
            locationService = LocationService(context),
            darkModeController = DarkModeController(settingsStorage),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel.rendererDispatcher = mainDispatcherRule.dispatcher
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
            // Assert the window: the size report below must land while the restore is in progress.
            driveUntil("initMap's viewport load must be held before the size report") {
                gated.heldCount > 0
            }

            // The composable reports its size while the restore is still in
            // progress. No renderer exists yet — no render may be submitted
            // with the uninitialized default viewport.
            viewModel.setScreenSize(100, 100)
            assertEquals(0, client.renderCount.get() + client.renderWithRouteAndPoisCount.get())

            // Let the restore complete. The frame's debounce runs on this test's scheduler, so
            // advancing it is what would fire a buggy default-viewport render queued before the
            // restore — the render-count assertion below catches that.
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            gated.release()
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            // Drive the scheduler until the first render lands. That is a state, not a real-time wait:
            // the renderer's debounce and the restore path are on dispatchers this test owns.
            driveUntil("the restored viewport must reach the first render") {
                client.renderCount.get() + client.renderWithRouteAndPoisCount.get() >= 1
            }

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

            // The restore must actually have been applied before the save is exercised: the guard
            // is armed by the applied restore, and the file is deleted below, so a save that still
            // no-ops would make the load return null (the shape this case failed with). The hops
            // before the gated load run on real dispatchers, so wait for the observable state
            // instead of assuming the scheduler drained them.
            driveUntil("the persisted viewport must be restored before the save is exercised") {
                viewModel.uiState.value.viewport.centerLat == 48.2
            }

            // Restore applied — the save guard is armed. Delete the file so
            // the only writer left is saveViewport itself (the first render's
            // view-change listener is still debounced on a real dispatcher).
            file.delete()

            viewModel.saveViewport()
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            driveUntil("saveViewport must persist the restored viewport") {
                file.exists()
            }

            val loaded = viewportStorage.load("testmap")
            assertEquals(48.2, loaded!!.centerLat, 1e-9)
            assertEquals(16.4, loaded.centerLon, 1e-9)
            assertEquals(12.0, loaded.magnification, 1e-9)
        }

    @Test
    fun `re-entry while init suspended keeps the new map viewport`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Seed distinct persisted viewports for two map paths.
            val fileA = File(context.filesDir, "maps/viewport-mapA.json")
            fileA.parentFile?.mkdirs()
            fileA.writeText("""{"centerLat":48.2,"centerLon":16.4,"magnification":12.0,"angle":0.0}""")
            val fileB = File(context.filesDir, "maps/viewport-mapB.json")
            fileB.writeText("""{"centerLat":52.5,"centerLon":13.4,"magnification":14.0,"angle":0.0}""")

            // Hold the FIRST storage dispatch (initMap(mapA)'s viewport load);
            // later dispatches run inline, so the re-entry initMap(mapB) runs
            // to completion while mapA's coroutine is still suspended. Without
            // the supersession fix, mapA's held load resumes on release and
            // clobbers mapB's restore; with it, the cancelled mapA init aborts.
            val gated = FirstDispatchGatedDispatcher()
            viewportStorage.ioDispatcher = gated

            viewModel.initMap("/data/maps/mapA")
            mainDispatcherRule.dispatcher.scheduler.runCurrent()
            // Assert the window instead of assuming it: mapA's init must be the one suspended at the
            // held load before the re-entry runs.
            driveUntil("mapA's init must be suspended at the held viewport load") {
                gated.heldCount > 0
            }
            viewModel.initMap("/data/maps/mapB")

            // mapB's load ran inline; poll until its restored viewport lands.
            val scheduler = mainDispatcherRule.dispatcher.scheduler
            driveUntil("mapB's restored viewport must land before the held load is released") {
                viewModel.uiState.value.viewport.centerLat == 52.5
            }

            // Run mapA's held load: the cancelled mapA init aborts here
            // (without the fix it would apply mapA's restore on top of mapB's).
            gated.release()
            scheduler.advanceUntilIdle()

            val vp = viewModel.uiState.value.viewport
            assertEquals(52.5, vp.centerLat, 1e-9)
            assertEquals(13.4, vp.centerLon, 1e-9)
            assertEquals(14.0, vp.magnification, 1e-9)

            // Save guard re-armed for the NEW init: a lifecycle save persists
            // mapB's restored center, not a stale one.
            viewModel.saveViewport()
            scheduler.advanceUntilIdle()
            val loaded = viewportStorage.load("mapB")
            assertEquals(52.5, loaded!!.centerLat, 1e-9)
            assertEquals(13.4, loaded.centerLon, 1e-9)
            assertEquals(14.0, loaded.magnification, 1e-9)
        }

    @Test
    fun `saveViewport during re-entry window keeps persisted viewport`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Seed distinct persisted viewports for two map paths.
            val fileA = File(context.filesDir, "maps/viewport-mapA.json")
            fileA.parentFile?.mkdirs()
            fileA.writeText("""{"centerLat":48.2,"centerLon":16.4,"magnification":12.0,"angle":0.0}""")
            val fileB = File(context.filesDir, "maps/viewport-mapB.json")
            fileB.writeText("""{"centerLat":52.5,"centerLon":13.4,"magnification":14.0,"angle":0.0}""")

            val gated = FirstDispatchGatedDispatcher()
            viewportStorage.ioDispatcher = gated

            // mapA init suspended at the held load; re-entry mapB re-armed the
            // save guard (synchronously in initMap) but its coroutine has not
            // run yet — the guard window a lifecycle save must not write into.
            viewModel.initMap("/data/maps/mapA")
            mainDispatcherRule.dispatcher.scheduler.runCurrent()
            driveUntil("mapA's init must be suspended at the held viewport load") {
                gated.heldCount > 0
            }
            viewModel.initMap("/data/maps/mapB")

            // Lifecycle save in the window: with the fix it no-ops (guard
            // false); without it, the guard had already been re-armed and the
            // default viewport would be queued and written on release.
            viewModel.saveViewport()
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            gated.release()
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            // The file still holds mapB's seeded content — never the default.
            val loaded = viewportStorage.load("mapB")
            assertEquals(52.5, loaded!!.centerLat, 1e-9)
            assertEquals(13.4, loaded.centerLon, 1e-9)
            assertEquals(14.0, loaded.magnification, 1e-9)
        }

    @Test
    fun `initMap renders at restored viewport not renderer default`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Seed a persisted viewport for the map initMap will open.
            val file = File(context.filesDir, "maps/viewport-testmap.json")
            file.parentFile?.mkdirs()
            file.writeText("""{"centerLat":48.2,"centerLon":16.4,"magnification":12.0,"angle":0.0}""")

            // Screen size known BEFORE initMap (no size-change renderMap fires
            // afterwards — the re-entry condition). Gate the VM's default
            // dispatcher from the moment the initialization is over, so the
            // init-time dark push has already submitted its render while the
            // persisted style load is held. Without the fix that render uses the
            // renderer's DEFAULT viewport (mag 5 → native 2^5=32) — the low-zoom
            // frame seen after re-entry — with it, the restored magnification
            // (12 → 2^12=4096).
            //
            // "Initialization over" is the loading state being cleared: the
            // initialization's own off-main section (spec: map-render — Map
            // initialization keeps native and file work off the main thread) runs
            // while it is still set, and the renderer is created only after that
            // section — so the first held block is the style load that follows the
            // dark push.
            viewModel.setScreenSize(100, 100)
            val gated = PostInitializationGatedDispatcher { !viewModel.uiState.value.isLoading }
            viewModel.defaultDispatcher = gated
            viewModel.initMap("/data/maps/testmap")

            // Run initMap up to the style load (held by the gate): the dark
            // push has submitted its render by then. Real-dispatcher
            // resumptions arrive asynchronously, so poll for the held block.
            val scheduler = mainDispatcherRule.dispatcher.scheduler
            scheduler.advanceUntilIdle()
            assertTrue(
                "the initialization must have run (and reached the renderer) before the held style load",
                client.openedDatabases.isNotEmpty() && gated.heldCount > 0
            )

            // The debounce runs on this test's scheduler, so advancing it fires the frame while the
            // style load is still held — no real-time pause is involved.
            scheduler.advanceUntilIdle()

            // The first render must already be at the restored magnification.
            assertTrue(
                "first render at default mag: ${client.renderMags}",
                client.renderMags.isNotEmpty() && client.renderMags.first() == Math.pow(2.0, 12.0)
            )

            // Release the style load; the final render is at the restored mag.
            gated.release()
            scheduler.advanceUntilIdle()
            assertTrue(
                "the released style load must produce its frame (renders: " +
                    "${client.renderCount.get() + client.renderWithRouteAndPoisCount.get()})",
                client.renderCount.get() + client.renderWithRouteAndPoisCount.get() >= 2
            )

            assertEquals(Math.pow(2.0, 12.0), client.lastRenderMag, 1e-9)
        }

    /**
     * Test dispatcher that queues dispatched blocks until [release] is called,
     * letting a test hold a coroutine suspended at a `withContext` boundary
     * (e.g. ViewportStorage.load) while the main thread performs other work.
     */
    private class GatedDispatcher : CoroutineDispatcher() {
        private val queue = ConcurrentLinkedQueue<Runnable>()
        private val released = AtomicBoolean(false)

        /** Number of blocks currently held (all dispatches are held until [release]). */
        val heldCount: Int get() = queue.size

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

    /**
     * Holds every block dispatched once [initializationDone] reports true; blocks dispatched before
     * that run inline.
     *
     * The first hop to the ViewModel's `defaultDispatcher` during `initMap` is the initialization's own
     * off-main section (spec: `map-render` — Map initialization keeps native and file work off the main
     * thread); the init-time *style load* is a later hop, after the renderer exists. A case that wants
     * to hold that load while the renderer submits a render therefore gates on the moment the
     * initialization is over, not on a dispatch ordinal (which moves whenever the section gains or
     * loses a suspension point).
     */
    private class PostInitializationGatedDispatcher(
        private val initializationDone: () -> Boolean
    ) : CoroutineDispatcher() {
        private val queue = ConcurrentLinkedQueue<Runnable>()

        @Volatile
        private var released = false

        /** Number of blocks currently held. */
        val heldCount: Int get() = queue.size

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (!released && initializationDone()) {
                queue.offer(block)
            } else {
                block.run()
            }
        }

        fun release() {
            released = true
            while (true) {
                val block = queue.poll() ?: break
                block.run()
            }
        }
    }

    /**
     * Holds only the FIRST dispatched block until [release]; all later blocks
     * run inline. Lets a test keep initMap(mapA) suspended at the viewport
     * load while a re-entry initMap(mapB) completes its own load — the exact
     * window the supersession fix closes.
     *
     * After [release] every dispatch runs inline, including one that arrives
     * later: a test that releases the gate before its first dispatch happened —
     * a real possibility, because the hops before the viewport load run on real
     * dispatchers the test scheduler cannot drain — would otherwise have that
     * dispatch queued with nothing left to drain it, suspending the calling
     * coroutine forever. A test that needs the ordering guarantee waits for
     * [heldCount] itself (see `driveUntil`), so the intent is asserted
     * instead of assumed.
     */
    private class FirstDispatchGatedDispatcher : CoroutineDispatcher() {
        private val first = AtomicBoolean(true)
        private val released = AtomicBoolean(false)
        private val queue = ConcurrentLinkedQueue<Runnable>()

        /** Number of blocks currently held (first dispatch only). */
        val heldCount: Int get() = queue.size

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (!released.get() && first.compareAndSet(true, false)) {
                queue.offer(block)
            } else {
                block.run()
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

    /**
     * Drive this test's scheduler to idle and assert [condition]. Nothing waits real time: every hop the
     * restore path takes is on a dispatcher this test owns (the ViewModel's initialization, storage and
     * renderer seams plus the gated ones), so "the coroutine is suspended at the held load" is a state
     * `advanceUntilIdle` reaches rather than something to poll for — the bounded real-clock poll this
     * replaces is what let the class hang a whole flavor run (`TODO.md` §117).
     */
    private fun TestScope.driveUntil(reason: String, condition: () -> Boolean) {
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertTrue(reason, condition())
    }
}
