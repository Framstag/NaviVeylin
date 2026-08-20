package com.naviveylin.core

import com.framstag.libosmscout.client.OSMScoutClient

/**
 * Provider for the shared [OSMScoutClient] instance, consumed by Android Auto screens.
 * Implemented in the [:app] module via Hilt.
 */
interface AutoClientProvider {
    fun client(): OSMScoutClient

    /**
     * Open all installed map databases under `filesDir/maps` so map rendering,
     * search, and reverse geocoding work without the phone UI's `initMap()`
     * step. Best effort: per-directory failures are logged and skipped.
     */
    suspend fun openMapDatabases(filesDir: String)
}
