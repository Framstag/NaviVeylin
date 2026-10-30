package com.naviveylin.data

import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.AutoSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The announcement switches the voice-guidance capability added: two per-surface voice switches and one
 * shared GPS switch (spec: `voice-guidance` — The voice switch is per surface, persisted and on by
 * default, GPS announcements are one shared switch, off by default; spec: `settings-persistence` — The
 * announcement fields declare per-surface and shared ownership). The defaults are the behaviour before
 * the switches existed, so a settings file written earlier decodes to speech on and GPS off.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsStorageVoiceSwitchTest {

    @Test
    fun freshSettingsHaveBothVoiceSwitchesOn() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        val loaded = storage.load()
        assertEquals(true, loaded.voiceGuidanceEnabled)
        assertEquals(true, loaded.autoVoiceGuidanceEnabled)
        assertEquals(false, loaded.announceGpsState)
    }

    @Test
    fun olderSettingsDecodeToTheDefaults() = runTest {
        // A settings file written before the announcement switches existed: the missing
        // keys must decode to voice on for both surfaces and GPS off, with no migration.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = java.io.File(context.filesDir, "maps/settings.json")
        file.parentFile?.mkdirs()
        file.writeText("""{"followMode":true,"laneHintsEnabled":false}""")
        val storage = SettingsStorage(context)

        val loaded = storage.load()

        assertEquals(true, loaded.followMode)
        assertEquals(false, loaded.laneHintsEnabled)
        assertEquals(true, loaded.voiceGuidanceEnabled)
        assertEquals(true, loaded.autoVoiceGuidanceEnabled)
        assertEquals(false, loaded.announceGpsState)
    }

    @Test
    fun aCarWriteLeavesThePhoneVoiceValueAlone() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        storage.save(AppSettings(voiceGuidanceEnabled = false, autoVoiceGuidanceEnabled = true))

        // The car writes its own settings through the AutoSettings mapping, exactly as
        // AutoSettingsProvider.save does.
        val carView = storage.load().toAutoSettings().copy(voiceGuidanceEnabled = false)
        storage.update { current -> carView.toAppSettings(current) }

        val loaded = storage.load()
        assertEquals(false, loaded.voiceGuidanceEnabled)
        assertEquals(false, loaded.autoVoiceGuidanceEnabled)
        // and the car is shown its own value again on the next read.
        assertEquals(false, loaded.toAutoSettings().voiceGuidanceEnabled)
    }

    @Test
    fun theSharedGpsValueIsOneField() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())

        // Phone side write, then a car side write: the second applies to the first one's
        // result and the file holds ONE GPS value, which both surfaces read.
        storage.update { it.copy(announceGpsState = true) }
        val carView = storage.load().toAutoSettings()
        assertEquals(true, carView.announceGpsState)
        storage.update { current ->
            carView.copy(announceGpsState = false).toAppSettings(current)
        }

        assertEquals(false, storage.load().announceGpsState)
        assertEquals(false, storage.load().toAutoSettings().announceGpsState)

        // The car's own voice field and the phone's stay independent across the same write.
        val independent = storage.load().toAutoSettings().copy(voiceGuidanceEnabled = false)
        storage.update { current -> independent.toAppSettings(current) }
        val loaded = storage.load()
        assertEquals(false, loaded.autoVoiceGuidanceEnabled)
        assertEquals(true, loaded.voiceGuidanceEnabled)
    }

    @Test
    fun carMappingNeverWritesThePhoneVoiceField() {
        // A pure mapping statement, independent of the storage: the car's view carries
        // its own voice value and a write-back lands in autoVoiceGuidanceEnabled only.
        val base = AppSettings(voiceGuidanceEnabled = true, autoVoiceGuidanceEnabled = true)
        val written = AutoSettings(voiceGuidanceEnabled = false).toAppSettings(base)
        assertEquals(true, written.voiceGuidanceEnabled)
        assertEquals(false, written.autoVoiceGuidanceEnabled)
        // The resolved car view is the car's value itself.
        assertEquals(false, written.toAutoSettings().voiceGuidanceEnabled)
    }
}
