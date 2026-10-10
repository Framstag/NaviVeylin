package com.naviveylin.data

import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.mapsource.MapSource
import java.nio.file.Path

/**
 * Performs a confirmed map-source switch, in the order the spec fixes.
 *
 * 1. every map directory of the other source is **unregistered** through the map manager — not merely
 *    removed from disk, so the running client stops reading it
 * 2. the switch's own deletion runs (files, the basemap last, the shared reload signal when the basemap
 *    went away) and is recorded in the diagnostics stream
 * 3. the new selection is **persisted** — last, so a crash cannot leave data deleted while the old
 *    source is still the stored one
 *
 * A directory that cannot be deleted is reported and does not block the switch (spec
 * `map-source-selection` — "A source switch unregisters, reloads and records what it deletes" /
 * "Partly failed deletion is reported and does not block the switch").
 *
 * The three effects are injected, so the order above is asserted in a unit test rather than inferred
 * from the file system afterwards.
 *
 * @param switcher plans and performs the deletion
 * @param unregister takes one map directory out of the running client's lookup set
 * @param persist stores the newly active source
 * @param reloadNotifier signalled when the deleted data included the basemap
 */
class MapSourceSwitchUseCase(
    private val switcher: MapSourceSwitcher,
    private val unregister: (Path) -> Unit,
    private val persist: suspend (MapSource) -> Unit,
    private val reloadNotifier: BasemapReloadNotifier
) {

    /**
     * Apply the switch.
     *
     * @param target the source that becomes active
     * @param previous the source that was active
     * @param plan what the user confirmed
     * @return the directories that could not be deleted, by path
     */
    suspend fun switchTo(
        target: MapSource,
        previous: MapSource,
        plan: MapSourceSwitchPlan
    ): List<Path> {
        for (directory in plan.mapDirectories) {
            unregister(directory)
        }
        val failed = switcher.apply(plan, previous, target, reloadNotifier)
        persist(target)
        return failed
    }
}
