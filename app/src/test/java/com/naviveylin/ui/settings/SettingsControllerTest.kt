package com.naviveylin.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.data.AppSettings
import com.naviveylin.data.SettingsStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The one writer (design D2; spec: `settings-persistence` — "Settings writes are serialized
 * read-modify-write transactions").
 *
 * What a write has to hold: it reaches the file, and the document the file then holds is what the
 * controller's flow emits — once, whole, and not ahead of the write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsControllerTest {

    @Test
    fun aWriteLandsOnceAndEmits() = runTest {
        val storage = storage()
        val controller = SettingsController(storage)
        controller.load()
        val seen = mutableListOf<AppSettings>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.settings.collect { seen += it }
        }

        val before = seen.size
        controller.update { it.copy(keepScreenOn = false) }
        val emitted = seen.drop(before)

        assertEquals("one write is one emission", 1, emitted.size)
        assertEquals("the emission carries the written value", false, emitted.single().keepScreenOn)
        assertEquals(
            "the emission carries the whole document, not the field",
            true,
            emitted.single().laneHintsEnabled
        )
        assertEquals("the write reached the settings file", false, storage.load().keepScreenOn)
        collector.cancel()
    }

    @Test
    fun aLoadPublishesThePersistedDocument() = runTest {
        storage().update { it.copy(laneHintsEnabled = false, styleSheet = "winter-sports") }
        val controller = SettingsController(storage())

        val loaded = controller.load()

        assertEquals(false, loaded.laneHintsEnabled)
        assertEquals("winter-sports", loaded.styleSheet)
        assertEquals("the flow holds what the reader was handed", loaded, controller.settings.value)
    }

    @Test
    fun aLateLoadDoesNotPublishOverANewerWrite() = runTest {
        val storage = storage()
        // The file's dispatcher is driven by hand, so the test owns the interleaving: the read
        // returns before the write begins and the read's continuation arrives after the write
        // published — the document it holds is the one the write replaced.
        val io = QueuedDispatcher()
        storage.ioDispatcher = io
        val controller = SettingsController(storage)
        val seen = mutableListOf<Boolean>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.settings.collect { seen += it.preferSymbolPoiIcons }
        }

        launch { controller.load() }
        runCurrent()
        io.runNext()

        launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.update { it.copy(preferSymbolPoiIcons = true) }
        }
        io.runNext()

        runCurrent()

        assertEquals(
            "the stale read publishes no document, so the write is one emission",
            listOf(false, true),
            seen
        )
        assertEquals(true, controller.settings.value.preferSymbolPoiIcons)
        assertEquals("the write reached the settings file", true, storage().load().preferSymbolPoiIcons)
        collector.cancel()
    }

    /** A dispatcher whose tasks run only when the test says so. */
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runNext() = tasks.removeFirst().run()
    }

    @Test
    fun aWriteAppliesToThePersistedDocument() = runTest {
        val storage = storage()
        val controller = SettingsController(storage)
        // A value another seam wrote after this host's last read: the transform must see it, so the
        // controller cannot revert a field it does not own (design D2 — one writer, one transaction).
        storage().update { it.copy(renderMode = com.naviveylin.data.RenderMode.DIRECT) }
        controller.load()

        controller.update { it.copy(keepScreenOn = false) }

        val persisted = storage.load()
        assertEquals(com.naviveylin.data.RenderMode.DIRECT, persisted.renderMode)
        assertEquals(false, persisted.keepScreenOn)
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun storage(): SettingsStorage = SettingsStorage(context())
}
