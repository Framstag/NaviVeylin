package com.naviveylin.data

import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
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
 * Specs: `basemap-loading` — "Reload basemap after download or delete"; `basemap-ui` — "Basemap status
 * clears when its source's data is deleted".
 */
@Singleton
class BasemapRegistrar @Inject constructor(
    private val client: OSMScoutClient,
    private val reloadNotifier: BasemapReloadNotifier
) {

    /**
     * Point the session at [basemapDirectory], or unload the basemap when it is blank.
     *
     * @param basemapDirectory the installed basemap directory, or an empty string to unload it
     */
    fun apply(basemapDirectory: String) {
        client.setBasemapLookupDirectory(basemapDirectory)
        client.reloadBasemap()
        reloadNotifier.bump()
    }
}
