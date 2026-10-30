package com.naviveylin.data

import com.framstag.libosmscout.client.MapDownloadManager
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import java.nio.file.Paths
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands the running native client the basemap directory to use — or an empty value to unload it — and
 * asks it to reload, so the change is visible without an app restart.
 *
 * One place does this, because two callers change what the basemap is: the basemap section (download,
 * replace, delete) and a map-source switch, which deletes the other source's basemap as part of the
 * switch. Before this existed, a switch unregistered the directory on disk but left the running client
 * pointing at it (found while preparing the car-session verification, 2026-10-09).
 *
 * It is also the one place that decides whether the directory may be registered at all: a basemap whose
 * `types.dat` names another data format version cannot be opened by this build's reader, so it is unloaded
 * instead of being handed over and failing inside the native open ([BasemapDataFormat], issue #23).
 *
 * Specs: `basemap-loading` — "Reload basemap after download or delete"; `basemap-ui` — "Basemap status
 * clears when its source's data is deleted".
 */
@Singleton
class BasemapRegistrar @Inject constructor(
    private val client: OSMScoutClient,
    private val reloadNotifier: BasemapReloadNotifier
) {

    /**
     * Point the session at [basemapDirectory], or unload the basemap when it is blank or carries a data
     * format this build cannot read.
     *
     * @param basemapDirectory the installed basemap directory, or an empty string to unload it
     */
    fun apply(basemapDirectory: String) {
        val directory = basemapDirectory.trim()
        client.setBasemapLookupDirectory(registerable(directory))
        client.reloadBasemap()
        reloadNotifier.bump()
    }

    /** [directory] when this build can read it, else the empty value that unloads the basemap. */
    private fun registerable(directory: String): String {
        if (directory.isEmpty()) {
            return directory
        }
        val admission = runCatching {
            BasemapDataFormat.admit(Paths.get(directory), MapDownloadManager.DATABASE_FORMAT_VERSION)
        }.getOrNull() ?: return directory
        return when (admission) {
            is BasemapAdmission.Rejected -> {
                BasemapDataFormat.logRejection(admission)
                ""
            }
            BasemapAdmission.Absent, is BasemapAdmission.Admitted -> directory
        }
    }
}
