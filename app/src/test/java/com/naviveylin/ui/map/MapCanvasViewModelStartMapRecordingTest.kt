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
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
 * Verifies that opening a map records it as the last opened map (spec `start-map-selection` — the
 * last opened map is reopened at the next start; a map opened without the map manager is recorded),
 * that the recorded value is the database directory the open path refers to, and that the write runs
 * through the settings I/O dispatcher instead of the caller's.
 *
 * `initMap` is the seam: every way a map reaches the screen goes through it, so no navigation call
 * site has to remember to record anything.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelStartMapRecordingTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var settingsStorage: SettingsStorage
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        settingsStorage = SettingsStorage(context)
        settingsStorage.ioDispatcher = mainDispatcherRule.dispatcher
        viewModel = createViewModel()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private fun createViewModel(): MapCanvasViewModel {
        val viewportStorage = ViewportStorage(context).also {
            it.ioDispatcher = mainDispatcherRule.dispatcher
        }
        val vm = MapCanvasViewModel(
            viewportStorage = viewportStorage,
            settingsStorage = settingsStorage,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client).also {
                it.defaultDispatcher = mainDispatcherRule.dispatcher
            },
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            darkModeController = DarkModeController(settingsStorage),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        vm.defaultDispatcher = mainDispatcherRule.dispatcher
        return vm
    }

    /** Install a database directory the shared discovery reports. */
    private fun installDatabase(relativePath: String): String {
        val dir = File(File(context.filesDir, "maps"), relativePath)
        assertTrue("could not create $dir", dir.mkdirs() || dir.isDirectory)
        assertTrue("could not create types.dat in $dir", File(dir, "types.dat").createNewFile())
        return dir.absolutePath
    }

    private fun openMap(mapPath: String) {
        viewModel.setScreenSize(100, 100)
        viewModel.initMap(mapPath)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `opening a map records it as the last opened map`() = runTest(mainDispatcherRule.dispatcher) {
        val mapB = installDatabase("region-b")

        openMap(mapB)

        assertEquals(mapB, settingsStorage.load().lastMapPath)
    }

    @Test
    fun `the most recently opened map is the recorded one`() = runTest(mainDispatcherRule.dispatcher) {
        val mapA = installDatabase("region-a")
        val mapB = installDatabase("region-b")

        openMap(mapB)
        openMap(mapA)

        assertEquals(
            "a later open replaces the earlier recording",
            mapA,
            settingsStorage.load().lastMapPath
        )
    }

    @Test
    fun `an open that does not go through the map manager is recorded`() = runTest(mainDispatcherRule.dispatcher) {
        // A deep link / shared location reaches the map screen directly with the database path.
        val mapA = installDatabase("region-a")
        val deepLinkedPath = File(mapA).absolutePath

        openMap(deepLinkedPath)

        assertEquals(deepLinkedPath, settingsStorage.load().lastMapPath)
    }

    @Test
    fun `a container path is recorded as the database below it`() = runTest(mainDispatcherRule.dispatcher) {
        // The map manager navigates with its download target; an archive carrying its own top-level
        // directory puts the database one level below it. Recording the container would make the
        // recorded map invisible to the start-map rule.
        val nestedDatabase = installDatabase("iceland/iceland")
        val container = File(context.filesDir, "maps/iceland").absolutePath
        assertTrue("the container itself carries no database", !File(container, "types.dat").exists())

        openMap(container)

        assertEquals(nestedDatabase, settingsStorage.load().lastMapPath)
    }

    @Test
    fun `the recording goes through the settings dispatcher`() = runTest(mainDispatcherRule.dispatcher) {
        val mapA = installDatabase("region-a")
        val dispatcher = CountingDispatcher(mainDispatcherRule.dispatcher)
        settingsStorage.ioDispatcher = dispatcher

        openMap(mapA)

        assertTrue(
            "the settings write must be dispatched, not performed on the caller's dispatcher",
            dispatcher.dispatches.get() > 0
        )
        assertEquals(mapA, settingsStorage.load().lastMapPath)
    }

    private class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        val dispatches = AtomicInteger()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches.incrementAndGet()
            delegate.dispatch(context, block)
        }
    }
}
