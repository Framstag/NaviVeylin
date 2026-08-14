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
