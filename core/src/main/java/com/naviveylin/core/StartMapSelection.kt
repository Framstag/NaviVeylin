package com.naviveylin.core

/**
 * Picks the phone's start map out of the installed databases.
 *
 * The phone resolves its start destination before the navigation graph is built, and the answer must
 * be stable across starts and independent of the order the filesystem happens to list directories in
 * (spec: `start-map-selection` — the last opened map is reopened at the next start, a recorded map
 * that is no longer installed falls back deterministically, the start map comes from the shared
 * installed-map discovery; design D4).
 *
 * The candidate list is the *discovered* set — the caller hands in the directories the shared
 * discovery found (basemap overlay already excluded), so this rule never sees a directory that is not
 * a database.
 */
object StartMapSelection {

    /**
     * @param installed absolute paths of the discovered database directories, in any order.
     * @param lastUsed the persisted last opened map, or `null` when nothing was recorded yet.
     * @return [lastUsed] when it is still installed, else the lexicographically smallest installed
     *   path (the deterministic fallback, so a deleted map cannot leave the phone on a stale path),
     *   else `null` when nothing is installed — the caller then shows the map-manager entry point.
     */
    fun choose(installed: List<String>, lastUsed: String?): String? {
        if (lastUsed != null && installed.contains(lastUsed)) {
            return lastUsed
        }
        // Sorted inside the rule (not by the caller) so the result never depends on the discovery's
        // filesystem order — the spec promises "the same one on every such start".
        return installed.minOrNull()
    }

    /**
     * The database directory that the path a map screen was opened with refers to — the value to
     * record as the last opened map.
     *
     * The map manager navigates with its download target, which for an archive carrying its own
     * top-level directory is a container above the database (`maps/<name>` holding
     * `maps/<name>/<name>/types.dat`), while the shared discovery only reports directories that hold
     * `types.dat`. Recording the container verbatim would make the recorded map fail [choose]'s
     * membership test and quietly reopen a different map, so the value is normalized to the database
     * directory it refers to (spec: `start-map-selection` — the last opened map is reopened at the
     * next start).
     *
     * @param openedPath the path the map screen was opened with.
     * @return the matching database directory, the smallest one when the container holds several, or
     *   [openedPath] unchanged when no candidate is under it — the caller records what it opened and a
     *   not-yet-discovered path is simply ignored by [choose] until it is discovered.
     */
    fun databaseDirectoryFor(installed: List<String>, openedPath: String): String {
        val container = openedPath.trimEnd('/')
        return installed.firstOrNull { it == openedPath }
            ?: installed.filter { it.startsWith("$container/") }.minOrNull()
            ?: openedPath
    }
}
