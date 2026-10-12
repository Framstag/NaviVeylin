package com.naviveylin.ui.map

import androidx.compose.runtime.mutableStateMapOf
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.TypeIconSource
import com.naviveylin.core.search.needsResolvedAddress
import com.naviveylin.core.search.resolvedAddressText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The two native lookups a search result row needs, kept out of the composition so both
 * result surfaces (the unified search dialog and the route panel's picker) share one
 * implementation and so the rules are testable without a Compose host
 * (change `search-result-row-content`, tasks 4.1/4.2/6.1).
 *
 * Both run off the main thread: `getAddressAt` and `renderTypeIcon` are native work on the
 * database thread, and nothing here may ever run inside a car host callback
 * (`guidelines/Design.md` §4).
 */

/** The bridge as a type-artwork source; the row-icon cache keeps the results (design D7). */
internal fun typeIconSource(client: OSMScoutClient): TypeIconSource =
    TypeIconSource { objectType, sizePx -> client.renderTypeIcon(objectType, sizePx) }

/**
 * The address `street houseNumber` at [entry]'s position, or `null` when the row does not
 * need one (spec: `location-search` — "A name hit shows the address resolved at its
 * position") and when the location index holds none there (same spec — "A name hit without
 * an indexed address stays readable").
 *
 * The predicate is [needsResolvedAddress]: a street or address hit already carries its
 * street in the label, and asking the index for it would spend a lookup to contradict it.
 * A failed lookup is an absent address, never a failure of the row.
 */
internal suspend fun resolveRowAddress(client: OSMScoutClient?, entry: LocationEntry): String? {
    if (client == null || !needsResolvedAddress(entry)) return null
    val address = withContext(Dispatchers.Default) {
        runCatching { client.getAddressAt(entry.lat, entry.lon) }.getOrNull()
    }
    return resolvedAddressText(address)
}

/**
 * Caches the addresses of the rows of the current result list, keyed by their position, so a
 * row that leaves and re-enters the viewport does not spend a second lookup — and so a row
 * that found no address does not ask again.
 *
 * Observable: the composition reads it while the resolution writes it, both on the main
 * thread. The owning surface drops the whole cache when the query changes.
 */
internal class RowAddressCache {
    private val addresses = mutableStateMapOf<Pair<Double, Double>, String>()

    /** The address resolved for this position, or `null` when it is unknown or there is none. */
    fun get(lat: Double, lon: Double): String? = addresses[lat to lon]?.ifEmpty { null }

    /** Whether this position has been looked up already (an empty answer counts). */
    fun contains(lat: Double, lon: Double): Boolean = addresses.containsKey(lat to lon)

    fun put(lat: Double, lon: Double, address: String?) {
        addresses[lat to lon] = address.orEmpty()
    }
}
