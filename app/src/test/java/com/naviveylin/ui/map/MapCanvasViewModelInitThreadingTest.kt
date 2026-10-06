package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportState
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `initMap` keeps every native and filesystem call off the main thread (spec: `map-render` — Map
 * initialization keeps native and file work off the main thread; `guidelines/Design.md` §4).
 *
 * **Why this file runs the ViewModel's `defaultDispatcher` on a real thread on purpose:** the other
 * `initMap` suites point it at the test dispatcher, which shares the main dispatcher's thread — there,
 * "off the main thread" is unobservable, so those cases cannot see this defect at all. Here the
 * dispatcher is a real single-thread executor while `Dispatchers.Main` stays the rule's test
 * dispatcher, so every call the client records can be compared with the thread main-dispatcher work
 * runs on. The production collaborators (`FavoriteRepository`, `ViewportStorage`) keep their
 * production dispatchers for the same reason: their work is part of the initialization.
 *
 * Waiting is a bounded real-clock poll that pumps the test scheduler (`awaitInitialization`), the
 * pattern `MapCanvasViewModelFixQualityTest` established for work on production dispatchers.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelInitThreadingTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var settingsStorage: SettingsStorage
    private lateinit var backgroundDispatcher: ExecutorCoroutineDispatcher
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var mainThread: Thread

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        settingsStorage = SettingsStorage(context)
        backgroundDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        viewModel = createViewModel()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
        backgroundDispatcher.close()
    }

    private fun createViewModel(): MapCanvasViewModel {
        val vm = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = settingsStorage,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            darkModeController = DarkModeController(settingsStorage),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        vm.defaultDispatcher = backgroundDispatcher
        return vm
    }

    /** One installed database directory under the app's own maps directory. */
    private fun installDatabase(relativePath: String): String {
        val dir = File(File(context.filesDir, "maps"), relativePath)
        assertTrue("could not create $dir", dir.mkdirs() || dir.isDirectory)
        assertTrue("could not create types.dat in $dir", File(dir, "types.dat").createNewFile())
        return dir.absolutePath
    }

    private fun mapKeyOf(mapPath: String): String = mapPath.substringAfterLast('/')

    /**
     * Starts an initialization and pumps the test scheduler until [condition] holds. The initialization
     * section deliberately runs on a real background thread in this file (that is its subject), so the case
     * drives its own scheduler from a real dispatcher while that thread works and awaits the observable
     * state — no wall-clock deadline and no sleep are involved
     * (spec `unit-test-suite-runtime` — Awaiting state, not a deadline). The loop is bounded by
     * `runTest`'s own timeout rather than by a clock this case reads.
     */
    private fun awaitInitialization(condition: () -> Boolean) = runBlocking {
        withContext(Dispatchers.Default) {
            val scheduler = mainDispatcherRule.dispatcher.scheduler
            // Pump BEFORE checking, exactly as the deadline version did: a state that is not yet set (e.g.
            // `isLoading` before initMap's first publication) must not satisfy the condition early.
            while (true) {
                scheduler.advanceUntilIdle()
                if (condition()) return@withContext
                yield()
            }
        }
    }

    /** The thread main-dispatcher work runs on in this case. */
    private suspend fun captureMainThread() {
        withContext(Dispatchers.Main) { mainThread = Thread.currentThread() }
    }

    /**
     * The requirement itself: no initialization entry point may be reached from the main thread
     * (spec scenario "No native call of initialization on the main thread").
     */
    private fun assertNoInitializationCallOnTheMainThread(
        expectedEntryPoints: Set<String> = ALL_ENTRY_POINTS
    ) {
        val onMain = client.initializationCallThreads.filter { it.second === mainThread }
        assertTrue(
            "initialization called ${onMain.map { it.first }} on the main thread — it must run on " +
                "the background dispatcher (spec: map-render — Map initialization keeps native and " +
                "file work off the main thread)",
            onMain.isEmpty()
        )
        val reached = client.initializationCallThreads.map { it.first }.toSet()
        assertEquals(
            "the case must observe every entry point its initialization reaches, otherwise the guard " +
                "is vacuous",
            expectedEntryPoints,
            reached
        )
    }

    @Test
    fun initializationRunsItsNativeAndFileWorkOffTheMainThread() = runTest(mainDispatcherRule.dispatcher) {
        captureMainThread()
        val selected = installDatabase("region-a")
        val regionB = installDatabase("nested/region-b")
        val basemap = installDatabase("basemap")

        viewModel.setScreenSize(100, 100)
        viewModel.initMap(selected)
        awaitInitialization { !viewModel.uiState.value.isLoading }

        assertNoInitializationCallOnTheMainThread()
        assertNull("the initialization completed without an error", viewModel.uiState.value.error)

        // Spec scenario "Additional databases registered in one batch": the whole-set registration is
        // unchanged by running it off the main thread — one call, the selected map excluded, the
        // basemap overlay never registered as a map.
        assertEquals("exactly one batch call", 1, client.openedDatabaseBatches.size)
        val batch = client.openedDatabaseBatches.single()
        assertEquals(setOf(regionB), batch.toSet())
        assertTrue("the basemap overlay is not registered as a map", !batch.contains(basemap))
        assertTrue("the selected map is not registered twice", !batch.contains(selected))
    }

    @Test
    fun coldStartKeepsTheLoadingStateAndTheMainThreadAlive() = runTest(mainDispatcherRule.dispatcher) {
        captureMainThread()
        val selected = installDatabase("region-a")
        val gate = CountDownLatch(1)
        client.openDatabaseGate = gate

        viewModel.setScreenSize(100, 100)
        viewModel.initMap(selected)

        // Wait for the (blocked) native open: at this point the off-main section is inside
        // openDatabase and has not returned.
        awaitInitialization { client.initializationCallThreads.any { it.first == "openDatabase" } }

        assertTrue(
            "the loading state is published before the off-main work starts and is still set while " +
                "the native open is in flight (spec scenario: Cold start with a slow database open)",
            viewModel.uiState.value.isLoading
        )
        assertTrue(
            "the stylesheet/icon refresh had already run when the native open was reached — and that " +
                "open is the background thread's call, so the refresh ran off the main thread too",
            File(context.filesDir, "stylesheets").isDirectory
        )

        // The main thread keeps running while the off-main open is still blocked: a main-dispatcher
        // coroutine completes. On the pre-change code this body ran ON the main dispatcher, so this
        // probe and the loading assertion above cannot both hold.
        var mainThreadRan = false
        val probe = launch(Dispatchers.Main) { mainThreadRan = true }
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertTrue(
            "the main thread must stay free while initialization runs (spec: map-render — the main " +
                "thread SHALL NOT block on it)",
            mainThreadRan
        )

        gate.countDown()
        awaitInitialization { !viewModel.uiState.value.isLoading }
        assertNull(viewModel.uiState.value.error)
        assertNoInitializationCallOnTheMainThread(
            // One installed map, so there is no additional-maps batch in this initialization.
            expectedEntryPoints = ALL_ENTRY_POINTS - "openDatabases"
        )
        probe.cancel()
    }

    @Test
    fun aRejectedPathStillReachesTheUiOffMain() = runTest(mainDispatcherRule.dispatcher) {
        captureMainThread()
        installDatabase("region-b")
        client.openDatabaseResult = false

        viewModel.setScreenSize(100, 100)
        viewModel.initMap("/data/maps/does-not-exist")
        awaitInitialization { !viewModel.uiState.value.isLoading }

        assertEquals(
            "the rejected path reaches the map screen as an error (spec scenario: Rejected database " +
                "path still reaches the UI) — the dispatcher move must not make the branch unreachable",
            "Could not open map database",
            viewModel.uiState.value.error
        )
        assertEquals(
            "a rejected primary map stops before the additional-maps batch",
            listOf("/data/maps/does-not-exist"),
            client.openedDatabases
        )
        assertTrue(client.openedDatabaseBatches.isEmpty())
        // The rejected open stops the section: nothing after it is reached.
        assertNoInitializationCallOnTheMainThread(expectedEntryPoints = setOf("openDatabase"))
    }

    @Test
    fun aSupersededInitializationStopsBeforeTheNextClientMutation() = runTest(mainDispatcherRule.dispatcher) {
        captureMainThread()
        val mapA = installDatabase("map-a")
        val mapB = installDatabase("map-b")
        val viewportStorage = ViewportStorage(context)
        viewportStorage.save(
            mapKeyOf(mapA),
            ViewportState(centerLat = 1.0, centerLon = 2.0, magnification = 15.0)
        )
        viewportStorage.save(
            mapKeyOf(mapB),
            ViewportState(centerLat = 3.0, centerLon = 4.0, magnification = 16.0)
        )

        val gate = CountDownLatch(1)
        client.openDatabaseGate = gate

        viewModel.setScreenSize(100, 100)
        viewModel.initMap(mapA)
        awaitInitialization { client.openedDatabases.contains(mapA) }

        // The re-entry supersedes the first initialization while its native open is in flight — a
        // blocking JNI call cannot be interrupted, so the section must stop at the next step
        // (spec scenario: Second initialization supersedes the first).
        viewModel.initMap(mapB)
        gate.countDown()
        awaitInitialization { !viewModel.uiState.value.isLoading }

        assertEquals(
            "the superseded initialization registered no database set of its own: the only batch is " +
                "the surviving initialization's (its additional maps are the other installed one)",
            listOf(mapA),
            client.openedDatabaseBatches.single()
        )
        assertEquals(
            "the surviving initialization publishes its own restored viewport, not the superseded one",
            3.0,
            viewModel.uiState.value.viewport.centerLat,
            0.0
        )
        assertEquals(16.0, viewModel.uiState.value.viewport.magnification, 0.0)
        assertNoInitializationCallOnTheMainThread()
    }

    private companion object {
        /** Every entry point an initialization with additional installed maps reaches. */
        val ALL_ENTRY_POINTS = setOf(
            "openDatabase",
            "openDatabases",
            "setNativeDataCacheSize",
            "getDatabaseBoundingBox",
            "loadFavoriteLocations"
        )

        /** Deadline of the real-clock waits: the initialization is a handful of JNI/file calls. */
        const val DEADLINE_MS = 10_000L

        /** Poll interval of the real-clock waits. */
        const val POLL_MS = 10L
    }
}
