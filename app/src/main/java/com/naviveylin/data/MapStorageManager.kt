package com.naviveylin.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the storage path for downloaded map files.
 * Maps are stored in [context.filesDir]/maps/ — Android internal storage,
 * no permissions required.
 */
@Singleton
class MapStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Root directory for all downloaded maps. */
    val mapsRootDir: Path
        get() = Paths.get(context.filesDir.absolutePath, "maps")

    /**
     * Returns the target directory for a specific map download.
     * Directory name is derived from the map name (lowercased, spaces → hyphens).
     */
    fun targetDirForMap(mapName: String): Path {
        val safeName = mapName.replace(Regex("\\s+"), "-").lowercase()
        return mapsRootDir.resolve(safeName)
    }

    /** Returns the absolute path string for a map's target directory. */
    fun targetDirPath(mapName: String): String =
        targetDirForMap(mapName).toString()
}
