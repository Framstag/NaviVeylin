package com.naviveylin.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Dark mode preference: force on, force off, or follow the environment. */
@Serializable
enum class DarkModePreference {
    ON,
    OFF,
    AUTOMATIC
}

/** Map rendering strategy: tile-cached geographic tiles, or direct full-viewport native render. */
@Serializable
enum class RenderMode {
    TILES,
    DIRECT
}

/** User settings persisted to a JSON file. */
@Serializable
data class AppSettings(
    val followMode: Boolean = false,
    val autoZoomEnabled: Boolean = true,
    val freeFormNorthUp: Boolean = true,
    val navNorthUp: Boolean = false,
    val keepScreenOn: Boolean = true,
    val darkMode: DarkModePreference = DarkModePreference.AUTOMATIC,
    val ambientLightSensitivity: AmbientLightSensitivity = AmbientLightSensitivity.OFF,
    val laneHintsEnabled: Boolean = true,
    val renderMode: RenderMode = RenderMode.TILES,
    val styleSheet: String = "standard",
    /**
     * Overspeed warning delta (km/h): the badge warns when `current >= max +
     * delta`. Single global value shared with Android Auto (spec:
     * map-speed-widget — Overspeed warning color; auto-map-layout). Range
     * 0-30, default 5.
     */
    val overspeedWarningDeltaKmh: Int = 5
)

/** Persists [AppSettings] to a JSON file in app internal storage. */
@Singleton
class SettingsStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /** Dispatcher for file IO; swapped to a test dispatcher in unit tests. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val file: File
        get() = File(context.filesDir, "maps/settings.json")

    suspend fun save(settings: AppSettings) {
        withContext(ioDispatcher) {
            try {
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(AppSettings.serializer(), settings))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save settings", e)
            }
        }
    }

    suspend fun load(): AppSettings = withContext(ioDispatcher) {
        if (!file.exists()) return@withContext AppSettings()
        try {
            // One-time normalization of the legacy boolean field (pre-sensitivity
            // versions): true -> HIGH, false -> OFF. The re-encoded text still
            // decodes through the regular serializer; unknown keys are ignored.
            json.decodeFromString(AppSettings.serializer(), migrateLegacySettings(file.readText()))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
            AppSettings()
        }
    }

    companion object {
        private const val TAG = "SettingsStorage"
    }
}

/**
 * Normalize an old settings file whose ambient light option was a boolean
 * (`ambientLightDarkMode`) to the current enum field
 * (`ambientLightSensitivity`): `true -> "HIGH"`, `false -> "OFF"`. Non-JSON
 * or already-normalized input passes through unchanged, so the regular
 * serializer defaults handle missing keys.
 */
internal fun migrateLegacySettings(raw: String): String {
    val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return raw
    if (element !is JsonObject) return raw
    val legacy = element["ambientLightDarkMode"] ?: return raw
    val enabled = (legacy as? JsonPrimitive)?.booleanOrNull ?: return raw
    val migrated = element.filterKeys { it != "ambientLightDarkMode" } +
        ("ambientLightSensitivity" to JsonPrimitive(if (enabled) "HIGH" else "OFF"))
    return json.encodeToString(JsonObject.serializer(), JsonObject(migrated))
}

/** JSON instance shared by [SettingsStorage] and the legacy migration helper. */
private val json = Json { ignoreUnknownKeys = true }
