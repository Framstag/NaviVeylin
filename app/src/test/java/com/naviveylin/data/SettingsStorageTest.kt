package com.naviveylin.data

import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStorageTest {

    @Test
    fun roundTripPersistsDarkModePreference() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        storage.save(AppSettings(darkMode = DarkModePreference.ON))
        val loaded = storage.load()
        assertEquals(DarkModePreference.ON, loaded.darkMode)
    }

    @Test
    fun saveOffAndReload() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        storage.save(AppSettings(darkMode = DarkModePreference.OFF))
        val loaded = storage.load()
        assertEquals(DarkModePreference.OFF, loaded.darkMode)
    }

    @Test
    fun saveOverwritesPreviousPreference() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        storage.save(AppSettings(darkMode = DarkModePreference.ON))
        storage.save(AppSettings(darkMode = DarkModePreference.AUTOMATIC))
        val loaded = storage.load()
        assertEquals(DarkModePreference.AUTOMATIC, loaded.darkMode)
    }

    @Test
    fun missingFileDefaultsToAutomatic() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        val loaded = storage.load()
        assertEquals(DarkModePreference.AUTOMATIC, loaded.darkMode)
    }

    @Test
    fun missingFileDefaultsToTiles() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        val loaded = storage.load()
        assertEquals(RenderMode.TILES, loaded.renderMode)
    }

    @Test
    fun roundTripPersistsRenderMode() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        storage.save(AppSettings(renderMode = RenderMode.DIRECT))
        val loaded = storage.load()
        assertEquals(RenderMode.DIRECT, loaded.renderMode)
    }

    @Test
    fun oldSettingsJsonWithoutRenderModeLoadsAsTiles() = runTest {
        // Simulate a settings file written by an app version predating the
        // renderMode setting — the missing key must decode to the default.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = java.io.File(context.filesDir, "maps/settings.json")
        file.parentFile?.mkdirs()
        file.writeText("""{"followMode":true,"keepScreenOn":false}""")
        val storage = SettingsStorage(context)
        val loaded = storage.load()
        assertEquals(true, loaded.followMode)
        assertEquals(false, loaded.keepScreenOn)
        assertEquals(RenderMode.TILES, loaded.renderMode)
    }

    @Test
    fun roundTripPersistsLastOpenedMap() = runTest {
        // The start map is recorded as a settings field (spec: start-map-selection — the last
        // opened map is reopened at the next start).
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        val path = "/data/user/0/com.framstag.naviveylin/files/maps/iceland"
        storage.update { it.copy(lastMapPath = path) }
        assertEquals(path, storage.load().lastMapPath)
    }

    @Test
    fun oldSettingsJsonWithoutLastMapPathLoadsAsNull() = runTest {
        // Simulate a settings file written by an app version predating the start-map change: the
        // missing key must decode to null (no recorded map — the deterministic pick), and an
        // unrelated write applied to that file must keep every other field (spec:
        // settings-persistence — unknown/absent keys do not fail a write).
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = java.io.File(context.filesDir, "maps/settings.json")
        file.parentFile?.mkdirs()
        file.writeText("""{"followMode":true,"keepScreenOn":false,"styleSheet":"cycle"}""")
        val storage = SettingsStorage(context)
        assertNull(storage.load().lastMapPath)

        storage.update { it.copy(darkMode = DarkModePreference.ON) }

        val loaded = storage.load()
        assertNull(loaded.lastMapPath)
        assertEquals(true, loaded.followMode)
        assertEquals(false, loaded.keepScreenOn)
        assertEquals("cycle", loaded.styleSheet)
        assertEquals(DarkModePreference.ON, loaded.darkMode)
    }

    @Test
    fun otherSettingsSurviveDarkModeRoundTrip() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        storage.save(
            AppSettings(
                followMode = true,
                keepScreenOn = false,
                darkMode = DarkModePreference.ON
            )
        )
        val loaded = storage.load()
        assertEquals(true, loaded.followMode)
        assertEquals(false, loaded.keepScreenOn)
        assertEquals(DarkModePreference.ON, loaded.darkMode)
    }

    @Test
    fun roundTripPersistsStyleSheet() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        storage.save(AppSettings(styleSheet = "cycle"))
        val loaded = storage.load()
        assertEquals("cycle", loaded.styleSheet)
    }

    @Test
    fun missingStyleSheetDefaultsToStandard() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        val loaded = storage.load()
        assertEquals("standard", loaded.styleSheet)
    }

    @Test
    fun oldSettingsJsonWithoutStyleSheetLoadsAsStandard() = runTest {
        // Simulate a settings file written by an app version predating the
        // styleSheet setting — the missing key must decode to the default.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = java.io.File(context.filesDir, "maps/settings.json")
        file.parentFile?.mkdirs()
        file.writeText("""{"followMode":true,"renderMode":"DIRECT"}""")
        val storage = SettingsStorage(context)
        val loaded = storage.load()
        assertEquals(true, loaded.followMode)
        assertEquals(RenderMode.DIRECT, loaded.renderMode)
        assertEquals("standard", loaded.styleSheet)
    }

    @Test
    fun roundTripPersistsOverspeedWarningDelta() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        storage.save(AppSettings(overspeedWarningDeltaKmh = 10))
        val loaded = storage.load()
        assertEquals(10, loaded.overspeedWarningDeltaKmh)
    }

    @Test
    fun missingFileDefaultsOverspeedDeltaToFive() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        val loaded = storage.load()
        assertEquals(5, loaded.overspeedWarningDeltaKmh)
    }

    @Test
    fun oldSettingsJsonWithoutOverspeedDeltaLoadsAsFive() = runTest {
        // Settings file written by an app version predating the delta — the
        // missing key must decode to the default 5.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = java.io.File(context.filesDir, "maps/settings.json")
        file.parentFile?.mkdirs()
        file.writeText("""{"followMode":true,"laneHintsEnabled":false}""")
        val storage = SettingsStorage(context)
        val loaded = storage.load()
        assertEquals(true, loaded.followMode)
        assertEquals(false, loaded.laneHintsEnabled)
        assertEquals(5, loaded.overspeedWarningDeltaKmh)
    }

    // --- Serialized read-modify-write (spec: settings-persistence) ---

    @Test
    fun updateAppliesToTheCurrentFileNotAStaleSnapshot() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())

        storage.update { it.copy(keepScreenOn = false) }
        storage.update { it.copy(darkMode = DarkModePreference.ON) }

        val loaded = storage.load()
        assertEquals(false, loaded.keepScreenOn)
        assertEquals(DarkModePreference.ON, loaded.darkMode)
    }

    @Test
    fun concurrentWritersLoseNoUpdate() {
        // Two surfaces (phone settings, car preferences) write the same file in one
        // process. The transform waits on a two-party barrier so the read of BOTH
        // writers provably happens before EITHER write — the lost-update window is
        // held open instead of being left to scheduling luck. With the write
        // transaction the second writer cannot enter its transform until the first
        // has written, so the barrier times out for each of them and both fields
        // keep their written values.
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        val barrier = CyclicBarrier(2)

        runBlocking {
            val jobs = listOf(
                async(Dispatchers.IO) {
                    storage.update { settings ->
                        runCatching { barrier.await(500, TimeUnit.MILLISECONDS) }
                        settings.copy(darkMode = DarkModePreference.ON)
                    }
                },
                async(Dispatchers.IO) {
                    storage.update { settings ->
                        runCatching { barrier.await(500, TimeUnit.MILLISECONDS) }
                        settings.copy(keepScreenOn = false)
                    }
                }
            )
            jobs.forEach { it.await() }
        }

        val loaded = runBlocking { storage.load() }
        assertEquals(DarkModePreference.ON, loaded.darkMode)
        assertEquals(false, loaded.keepScreenOn)
    }

    @Test
    fun updateTouchesOnlyTheFieldsTheWriterOwns() = runTest {
        // A car-style subset write must leave phone-only fields and both per-surface
        // anchors as they were, including a car anchor the car never chose (null =
        // inherit from the phone).
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        storage.save(
            AppSettings(
                keepScreenOn = false,
                routingAnchorId = "bottom-center",
                autoRoutingAnchorId = null
            )
        )

        storage.update { it.copy(styleSheet = "night") }

        val loaded = storage.load()
        assertEquals("night", loaded.styleSheet)
        assertEquals(false, loaded.keepScreenOn)
        assertEquals("bottom-center", loaded.routingAnchorId)
        assertEquals(null, loaded.autoRoutingAnchorId)
    }

    @Test
    fun unknownKeysDoNotBreakAnUpdate() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = java.io.File(context.filesDir, "maps/settings.json")
        file.parentFile?.mkdirs()
        file.writeText("""{"keepScreenOn":false,"someFutureSetting":7}""")
        val storage = SettingsStorage(context)

        storage.update { it.copy(darkMode = DarkModePreference.OFF) }

        val loaded = storage.load()
        assertEquals(DarkModePreference.OFF, loaded.darkMode)
        assertEquals(false, loaded.keepScreenOn)
    }
}
