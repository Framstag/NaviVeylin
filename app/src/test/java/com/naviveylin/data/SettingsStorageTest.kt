package com.naviveylin.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
