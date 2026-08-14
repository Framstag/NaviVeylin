package com.naviveylin.core

import com.framstag.libosmscout.client.LocationEntry

/**
 * Provider for location search, consumed by Android Auto screens.
 * Implemented in the [:app] module via Hilt.
 */
fun interface AutoSearchProvider {
    fun searchLocations(query: String, limit: Int): List<LocationEntry>
}
