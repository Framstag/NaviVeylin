package com.naviveylin.data

import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.core.mapsource.MapSourceMarker
import com.naviveylin.core.mapsource.RepositoryUrlPlanner
import java.nio.file.Files
import java.nio.file.Path

/**
 * What switching map source would delete, computed before the user is asked and before anything is
 * removed.
 *
 * @param mapDirectories directories that belong to a source other than the one being activated
 * @param mapBytes their total size in bytes
 * @param basemapDirectory the basemap directory when it belongs to another source, else null
 * @param basemapBytes the basemap's size in bytes
 */
data class MapSourceSwitchPlan(
    val mapDirectories: List<Path>,
    val mapBytes: Long,
    val basemapDirectory: Path?,
    val basemapBytes: Long
) {

    /** How many map directories the switch removes. */
    val mapCount: Int
        get() = mapDirectories.size

    /** Bytes the switch reclaims, the basemap included. */
    val totalBytes: Long
        get() = mapBytes + basemapBytes

    /** True when a switch would delete nothing and therefore needs no confirmation. */
    val isEmpty: Boolean
        get() = mapDirectories.isEmpty() && basemapDirectory == null

    /** The directories the switch removes, the basemap first so a reader never sees a half state. */
    val directoriesToDelete: List<Path>
        get() = listOfNotNull(basemapDirectory) + mapDirectories
}

/**
 * Plans and performs a map-source switch: the maps and the basemap that belong to another source are
 * removed, and the ones that belong to the newly active source stay (spec `map-source-selection` —
 * "Switching source is gated by a confirmation naming what will be deleted" / "A source switch
 * unregisters, reloads and records what it deletes").
 *
 * Attribution is the marker an installed directory carries; a directory without a marker (installed
 * before this change) belongs to the built-in provider, so switching away from the shipped provider
 * removes it too.
 *
 * @param mapsRoot the root directory of downloaded maps, whose child `basemap` is the basemap
 */
class MapSourceSwitcher(private val mapsRoot: Path) {

    /** Where the basemap lives below [mapsRoot]. */
    private val basemapDirectory: Path
        get() = mapsRoot.resolve(RepositoryUrlPlanner.BASEMAP_DIRECTORY_NAME)

    /**
     * What activating [target] would delete.
     *
     * @param target the source that is about to become active
     * @return the plan; check [MapSourceSwitchPlan.isEmpty] before asking the user
     */
    fun plan(target: MapSource): MapSourceSwitchPlan {
        val foreign = mapDirectories().filter { directoryOf(it) != target }
        val basemap = basemapDirectory.takeIf { Files.isDirectory(it) && directoryOf(it) != target }
        return MapSourceSwitchPlan(
            mapDirectories = foreign,
            mapBytes = foreign.sumOf { sizeInBytes(it) },
            basemapDirectory = basemap,
            basemapBytes = basemap?.let { sizeInBytes(it) } ?: 0L
        )
    }

    /**
     * Delete what [plan] named and record the switch.
     *
     * A directory that cannot be deleted is reported and does not block the switch: it stays where it
     * is, still attributed to its own source.
     *
     * @param plan the plan the user confirmed
     * @param previous the source that was active before the switch
     * @param target the source that becomes active
     * @param reloadNotifier signalled once when a basemap was deleted
     * @return the directories that could not be deleted
     */
    fun apply(
        plan: MapSourceSwitchPlan,
        previous: MapSource,
        target: MapSource,
        reloadNotifier: BasemapReloadNotifier
    ): List<Path> {
        val failed = mutableListOf<Path>()
        var deletedBasemap = false

        for (directory in plan.directoriesToDelete) {
            val deleted = deleteRecursively(directory)
            if (!deleted) {
                failed.add(directory)
            } else if (directory == plan.basemapDirectory) {
                deletedBasemap = true
            }
        }

        if (deletedBasemap) {
            // A deleted basemap must leave every renderer, including one in another variant whose
            // session is still alive (spec `basemap-ui`).
            reloadNotifier.bump()
        }

        DiagnosticsLog.log(
            TAG,
            "map source switched from=${previous.kind.name} to=${target.kind.name} " +
                "deletedDirectories=${plan.directoriesToDelete.size - failed.size} " +
                "bytesReclaimed=${plan.totalBytes} failed=${failed.size}"
        )
        return failed
    }

    /** The map directories stored below [mapsRoot], i.e. everything except the basemap. */
    private fun mapDirectories(): List<Path> {
        if (!Files.isDirectory(mapsRoot)) {
            return emptyList()
        }
        return Files.list(mapsRoot).use { children ->
            children.filter { Files.isDirectory(it) && it != basemapDirectory }
                .collect(java.util.stream.Collectors.toList())
        }
    }

    /** The source a directory belongs to: its marker, or the built-in provider without one. */
    private fun directoryOf(directory: Path): MapSource =
        runCatching { MapSourceMarker.sourceOf(directory).toSource() }
            .getOrElse { MapSource.BuiltInProvider }

    private fun sizeInBytes(directory: Path): Long =
        runCatching {
            Files.walk(directory).filter { Files.isRegularFile(it) }
                .mapToLong { runCatching { Files.size(it) }.getOrDefault(0L) }
                .sum()
        }.getOrDefault(0L)

    private fun deleteRecursively(directory: Path): Boolean {
        // The map manager's own delete removes the directory as it unregisters it, so a directory that is
        // already gone is a success, not a failure — otherwise every switch would report the maps it did
        // delete as undeletable (found on device 2026-10-09).
        if (!Files.exists(directory)) {
            return true
        }
        return runCatching {
            Files.walk(directory).sorted(Comparator.reverseOrder()).forEach { path ->
                Files.deleteIfExists(path)
            }
            !Files.exists(directory)
        }.getOrDefault(false)
    }

    private companion object {
        private const val TAG = "MapSourceSwitch"
    }
}
