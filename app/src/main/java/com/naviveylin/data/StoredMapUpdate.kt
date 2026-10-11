package com.naviveylin.data

import com.naviveylin.core.mapsource.MapUpdateItem
import com.naviveylin.core.mapsource.MapUpdateKind
import com.naviveylin.core.mapsource.RepositoryFailure
import com.naviveylin.core.mapsource.UpdateState
import kotlinx.serialization.Serializable

/**
 * One installation with an available update, as the settings file keeps it.
 *
 * Only the installations an update is available for are persisted: they are the ones the surface lists
 * and the only ones an action can act on, so the rest of the comparison's states are not worth a
 * settings rewrite. The identity is the installed directory's name, which is what the update addresses.
 *
 * @param kind name of the [MapUpdateKind]; a name a newer build wrote and this one does not know is dropped
 * @param directoryName the installed directory's name
 * @param displayName the name the region index gives the installation, when the check could read it
 * @param idPath the index identifier path of the leaf, absent for a basemap
 * @param downloadBytes the size of the published version's files as its metadata declared them
 */
@Serializable
data class StoredMapUpdate(
    val kind: String,
    val directoryName: String,
    val displayName: String? = null,
    val idPath: List<String>? = null,
    val downloadBytes: Long = 0L
)

/**
 * @return this installation as the settings file keeps it, or null when it has no update available —
 *         an installation that is up to date, no longer offered or unknown is not persisted
 */
fun MapUpdateItem.toStored(): StoredMapUpdate? {
    val available = state as? UpdateState.UpdateAvailable ?: return null
    return StoredMapUpdate(
        kind = kind.name,
        directoryName = directoryName,
        displayName = displayName,
        idPath = idPath,
        downloadBytes = available.downloadBytes
    )
}

/**
 * @return the installation this record describes, or null when the record names a kind this build does
 *         not know (written by a newer build) — an unreadable record is dropped rather than guessed at
 */
fun StoredMapUpdate.toItem(): MapUpdateItem? {
    val parsedKind = runCatching { MapUpdateKind.valueOf(kind) }.getOrNull() ?: return null
    return MapUpdateItem(
        kind = parsedKind,
        directoryName = directoryName,
        displayName = displayName,
        idPath = idPath,
        state = UpdateState.UpdateAvailable(downloadBytes)
    )
}

/**
 * What the surface reports about the last check: a completed one, or none yet.
 *
 * A failed attempt is not a report of its own — it leaves the last completed report standing and is
 * carried beside it ([MapUpdatesState.lastAttemptFailure]), so a repository that is briefly unreachable
 * cannot take the update list away from the user.
 */
sealed interface MapCheckReport {

    /** No check has completed on this install. */
    data object NeverChecked : MapCheckReport

    /**
     * A check completed.
     *
     * @param checkedAtEpochMs when it completed
     * @param updates the installations it found an update for
     */
    data class Completed(val checkedAtEpochMs: Long, val updates: List<MapUpdateItem>) : MapCheckReport
}

/**
 * The update state the map manager shows.
 *
 * @param report the last completed check, or that none has run
 * @param isChecking whether a check is running right now
 * @param lastAttemptFailure why the most recent attempt did not complete, or null when it completed or
 *        none has been made since the last one did
 */
data class MapUpdatesState(
    val report: MapCheckReport = MapCheckReport.NeverChecked,
    val isChecking: Boolean = false,
    val lastAttemptFailure: RepositoryFailure? = null
) {

    /** The installations the last completed check found an update for. */
    val updates: List<MapUpdateItem>
        get() = (report as? MapCheckReport.Completed)?.updates.orEmpty()

    /** When the last completed check ran, or 0 when none has. */
    val lastCheckedAtEpochMs: Long
        get() = (report as? MapCheckReport.Completed)?.checkedAtEpochMs ?: 0L

    /** Whether no check has completed on this install yet. */
    val neverChecked: Boolean
        get() = report is MapCheckReport.NeverChecked
}
