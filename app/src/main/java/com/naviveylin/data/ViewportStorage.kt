package com.naviveylin.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Singleton

/** Persists [ViewportState] per map to JSON files in app internal storage. */
@Singleton
class ViewportStorage(
    @param:ApplicationContext private val context: Context
) {
    /** Dispatcher for file IO; swapped to a test dispatcher in unit tests. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val json = Json { ignoreUnknownKeys = true }

    private fun fileFor(mapKey: String): File =
        File(context.filesDir, "maps/" + fileNameFor(mapKey))

    /** Save viewport state for a map to disk. Runs on [Dispatchers.IO]. */
    suspend fun save(mapKey: String, state: ViewportState) {
        // Reject uninitialized/invalid viewports (NaN, infinity, out-of-range
        // coordinates): persisting one would clobber a previously restored
        // viewport and make the next session open at the wrong place.
        if (!state.isValid()) {
            Log.w(TAG, "save rejected: invalid viewport state $state")
            return
        }
        withContext(ioDispatcher) {
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
    suspend fun load(mapKey: String): ViewportState? = withContext(ioDispatcher) {
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

        /**
         * The viewport key of a map database path: the path's last segment, i.e. the map's file
         * name (for `/data/maps/germany.osm` the key is `germany.osm`). One rule for load and save,
         * so the two cannot key different files (spec: viewport-persist).
         */
        internal fun keyFor(mapPath: String): String = mapPath.substringAfterLast('/')

        /**
         * The file a map key is persisted under. Every character outside `A-Za-z0-9._-` becomes
         * `_`, so the key is always one path segment (spec: viewport-persist — Save viewport state).
         */
        internal fun fileNameFor(mapKey: String): String {
            val safe = mapKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
            return "viewport-$safe.json"
        }
    }
}
