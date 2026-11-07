package com.naviveylin.core.mapsource

import com.framstag.libosmscout.client.InstalledMaps
import com.naviveylin.core.DiagnosticsLog
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The maps tree as the app reads it: the installations, told apart from what an update attempt left
 * behind.
 *
 * [InstalledMaps.findDatabaseDirectories] mirrors the native scan and answers "every directory holding
 * a `types.dat`" — which is exactly what a `.backup` of a swap holds, so a leftover from a crashed or
 * failed update is discovered as an installed map until [SlotInstall.sweep] runs
 * (spec `map-updates` — "Data left by an interrupted update is recognised, not mistaken for a map").
 * Every consumer that opens, lists, centres on or records a database therefore reads the tree through
 * this object rather than through the raw scan: the staging vocabulary lives in [SlotInstall], and the
 * leftover is named in the diagnostics instead of being silently dropped
 * (`guidelines/Design.md` §5, `guidelines/Logging.md` §3).
 *
 * The class is not a filter: [Found] keeps both halves, and both readers ([under] for a fresh scan and
 * [installedOnly] for a list handed in by the native scan) name every leftover they drop through
 * [reportLine], so a consumer that opens or lists databases still has the staging data to report. The
 * update check scans the tree itself (`MapUpdates`'s `installedDatabaseDirectories`) and applies the same
 * classification there.
 */
object MapTreeDiscovery {

    /**
     * What one scan of the maps tree found.
     *
     * @property installed the database directories the app opens, lists and records
     * @property staging the directories an earlier update attempt left behind, reported and never opened
     */
    data class Found(val installed: List<Path>, val staging: List<Path>)

    /**
     * Scan [mapsRoot] and classify what the scan finds.
     *
     * @param mapsRoot the maps tree (e.g. `<filesDir>/maps`)
     * @param excludeDirectory an absolute directory to skip (the basemap overlay), or `null`
     * @param report sink for the line naming each leftover; `null` writes it to [DiagnosticsLog]
     */
    fun under(
        mapsRoot: Path,
        excludeDirectory: Path? = null,
        report: ((String) -> Unit)? = null
    ): Found {
        val discovered = InstalledMaps.findDatabaseDirectories(
            mapsRoot.toString(),
            excludeDirectory?.toString()
        )
        val staging = discovered.filter { isStaging(it) }
        val installed = discovered.filterNot { isStaging(it) }
        val sink = report ?: { line -> DiagnosticsLog.log(TAG, line) }
        staging.forEach { name -> sink(reportLine(Paths.get(name))) }
        return Found(installed.map { Paths.get(it) }, staging.map { Paths.get(it) })
    }

    /**
     * The installations among [discovered] — for a consumer handed the tree by someone else, such as
     * the native map manager's installed list.
     *
     * @param report sink for the line naming each leftover this drops; `null` writes it to [DiagnosticsLog]
     */
    fun installedOnly(
        discovered: Collection<String>,
        report: ((String) -> Unit)? = null
    ): List<String> {
        val (staging, installed) = discovered.partition { isStaging(it) }
        val sink = report ?: { line -> DiagnosticsLog.log(TAG, line) }
        staging.forEach { name -> sink(reportLine(Paths.get(name))) }
        return installed
    }

    /**
     * The line one leftover is reported with: identity only — the directory and the installation it
     * belongs to, never a coordinate (`guidelines/Logging.md` §3).
     */
    fun reportLine(staging: Path): String {
        val name = staging.fileName.toString()
        return "map discovery: leftover staging $name of " +
            "${SlotInstall.stagingTargetOf(name) ?: name} — not opened or listed"
    }

    /** Whether one discovered directory is data an earlier update attempt left behind. */
    fun isStaging(directoryPath: String): Boolean =
        SlotInstall.isStaging(Paths.get(directoryPath).fileName.toString())

    private const val TAG = "MapTreeDiscovery"
}
