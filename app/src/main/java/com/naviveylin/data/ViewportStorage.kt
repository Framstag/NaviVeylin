package com.naviveylin.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Singleton

/** Persists [ViewportState] per map to JSON files in app internal storage. */
@Singleton
class ViewportStorage(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun fileFor(mapKey: String): File {
        val safe = mapKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(context.filesDir, "maps/viewport-$safe.json")
    }

    /** Save viewport state for a map to disk. Runs on [Dispatchers.IO]. */
    suspend fun save(mapKey: String, state: ViewportState) {
        withContext(Dispatchers.IO) {
            try {
                val file = fileFor(mapKey)
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(ViewportState.serializer(), state))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save viewport", e)
            }
        }
    }

    /** Load viewport state for a map from disk. Returns null if file missing or corrupt. */
    suspend fun load(mapKey: String): ViewportState? = withContext(Dispatchers.IO) {
        val file = fileFor(mapKey)
        if (!file.exists()) {
            return@withContext null
        }
        try {
            json.decodeFromString(ViewportState.serializer(), file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load viewport, using default", e)
            null
        }
    }

    companion object {
        private const val TAG = "ViewportStorage"
    }
}
