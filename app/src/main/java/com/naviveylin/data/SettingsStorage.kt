package com.naviveylin.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    val laneHintsEnabled: Boolean = true,
    val renderMode: RenderMode = RenderMode.TILES
)

/** Persists [AppSettings] to a JSON file in app internal storage. */
@Singleton
class SettingsStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file: File
        get() = File(context.filesDir, "maps/settings.json")

    suspend fun save(settings: AppSettings) {
        withContext(Dispatchers.IO) {
            try {
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(AppSettings.serializer(), settings))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save settings", e)
            }
        }
    }

    suspend fun load(): AppSettings = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext AppSettings()
        try {
            json.decodeFromString(AppSettings.serializer(), file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
            AppSettings()
        }
    }

    companion object {
        private const val TAG = "SettingsStorage"
    }
}
