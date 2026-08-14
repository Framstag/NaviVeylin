package com.naviveylin.core

import com.framstag.libosmscout.client.OSMScoutClient

/**
 * Provider for the shared [OSMScoutClient] instance, consumed by Android Auto screens.
 * Implemented in the [:app] module via Hilt.
 */
fun interface AutoClientProvider {
    fun client(): OSMScoutClient
}
