package com.naviveylin.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AppSettingsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun defaultValues() {
        val settings = AppSettings()
        assertFalse(settings.followMode)
        assertTrue(settings.autoZoomEnabled)
        assertTrue(settings.freeFormNorthUp)
        assertFalse(settings.navNorthUp)
        assertEquals(DarkModePreference.AUTOMATIC, settings.darkMode)
        assertEquals(AmbientLightSensitivity.OFF, settings.ambientLightSensitivity)
    }

    @Test
    fun serializeAndDeserialize() {
        val settings = AppSettings(
            followMode = true,
            autoZoomEnabled = false,
            freeFormNorthUp = false,
            navNorthUp = true,
            darkMode = DarkModePreference.OFF
        )
        val encoded = json.encodeToString(AppSettings.serializer(), settings)
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertEquals(settings.followMode, decoded.followMode)
        assertEquals(settings.autoZoomEnabled, decoded.autoZoomEnabled)
        assertEquals(settings.freeFormNorthUp, decoded.freeFormNorthUp)
        assertEquals(settings.navNorthUp, decoded.navNorthUp)
        assertEquals(settings.darkMode, decoded.darkMode)
    }

    @Test
    fun backwardCompatibleWithOldFields() {
        // Old format without orientation fields — should decode with defaults
        val oldJson = """{"followMode":true,"autoZoomEnabled":false}"""
        val decoded = json.decodeFromString(AppSettings.serializer(), oldJson)
        assertTrue(decoded.followMode)
        assertFalse(decoded.autoZoomEnabled)
        assertTrue(decoded.freeFormNorthUp) // default
        assertFalse(decoded.navNorthUp)     // default
        assertEquals(DarkModePreference.AUTOMATIC, decoded.darkMode) // default
        assertEquals(AmbientLightSensitivity.OFF, decoded.ambientLightSensitivity) // default
    }

    @Test
    fun ambientSensitivityRoundTrip() {
        for (sensitivity in AmbientLightSensitivity.entries) {
            val settings = AppSettings(ambientLightSensitivity = sensitivity)
            val encoded = json.encodeToString(AppSettings.serializer(), settings)
            val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
            assertEquals(sensitivity, decoded.ambientLightSensitivity)
        }
    }

    @Test
    fun legacyEnabledBooleanMapsToHigh() {
        val normalized = migrateLegacySettings(
            """{"followMode":true,"ambientLightDarkMode":true,"overspeedWarningDeltaKmh":12}"""
        )
        val decoded = json.decodeFromString(AppSettings.serializer(), normalized)
        assertEquals(AmbientLightSensitivity.HIGH, decoded.ambientLightSensitivity)
        assertTrue(decoded.followMode)
        assertEquals(12, decoded.overspeedWarningDeltaKmh)
        assertFalse("legacy key must be removed from the normalized text", normalized.contains("ambientLightDarkMode"))
        assertTrue(normalized.contains("ambientLightSensitivity"))
    }

    @Test
    fun legacyDisabledBooleanMapsToOff() {
        val normalized = migrateLegacySettings("""{"ambientLightDarkMode":false,"followMode":true}""")
        val decoded = json.decodeFromString(AppSettings.serializer(), normalized)
        assertEquals(AmbientLightSensitivity.OFF, decoded.ambientLightSensitivity)
        assertTrue(decoded.followMode)
    }

    @Test
    fun missingLegacyKeyMapsToOff() {
        // Already-normalized file (no legacy key) passes through unchanged and
        // decodes with the OFF default.
        val raw = """{"darkMode":"AUTOMATIC","ambientLightSensitivity":"MEDIUM"}"""
        assertEquals(raw, migrateLegacySettings(raw))
        val decoded = json.decodeFromString(AppSettings.serializer(), raw)
        assertEquals(AmbientLightSensitivity.MEDIUM, decoded.ambientLightSensitivity)
    }

    @Test
    fun nonJsonPassesThroughUnchanged() {
        val raw = "not json at all"
        assertEquals(raw, migrateLegacySettings(raw))
    }

    @Test
    fun darkModeRoundTrip() {
        for (pref in DarkModePreference.entries) {
            val settings = AppSettings(darkMode = pref)
            val encoded = json.encodeToString(AppSettings.serializer(), settings)
            val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
            assertEquals(pref, decoded.darkMode)
        }
    }

    @Test
    fun partialOrientationFields() {
        // Only freeFormNorthUp present — navNorthUp should default
        val partialJson = """{"followMode":false,"autoZoomEnabled":true,"freeFormNorthUp":false}"""
        val decoded = json.decodeFromString(AppSettings.serializer(), partialJson)
        assertFalse(decoded.freeFormNorthUp)
        assertFalse(decoded.navNorthUp) // default
    }

    @Test
    fun roundTripPreservesOrientation() {
        val original = AppSettings(
            followMode = true,
            autoZoomEnabled = true,
            freeFormNorthUp = false,
            navNorthUp = true
        )
        val encoded = json.encodeToString(AppSettings.serializer(), original)
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertEquals(original, decoded)
    }
}
