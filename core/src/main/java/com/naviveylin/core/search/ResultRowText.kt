package com.naviveylin.core.search

import com.framstag.libosmscout.client.LocationEntry

/**
 * The text of a location result row — one composition shared by the phone Places
 * list, the phone route-panel start/destination picker and the Android Auto search
 * rows (specs: `location-search` — Result item display; `auto-search` — Search
 * results displayed as list; change `search-result-row-content`, design D1–D4).
 *
 * The surfaces supply the row-level facts only they own — whether the entry's label
 * repeats in the current list, and the address they resolved for the row — and render
 * the result. Keeping the composition here is what stops the surfaces from drifting:
 * the car row carried the postal code twice before this change, because it composed
 * its own description from a `region` array that is `[adminRegion, postalArea]` and
 * not the hierarchy its javadoc promises.
 */

/** Separator between the label and the entry's admin region name. */
private const val TITLE_SEPARATOR = " - "

/** Separator between the parts of a row's detail line. */
private const val DETAIL_SEPARATOR = " · "

/**
 * First line of a result row: the entry's [LocationEntry.label] with its admin region
 * name appended — `region[0]`, in practice the city — so two entries sharing a label
 * in different municipalities can be told apart without opening them (spec:
 * `location-search` — Result shows label and region).
 *
 * The name is left out when the entry has none, and when it is the label itself under
 * the search's own folding, so a query naming the region never renders the same name
 * twice (same spec — Region name is not repeated when the label is that name). The
 * admin region hierarchy is deliberately not part of the row: it stays in the location
 * details sheet.
 */
fun rowTitle(entry: LocationEntry): String {
    val label = entry.label.orEmpty()
    val region = entry.region?.firstOrNull()?.trim().orEmpty()
    if (region.isEmpty()) return label
    if (label.isEmpty()) return region
    if (foldForSearchMatch(region) == foldForSearchMatch(label)) return label
    return label + TITLE_SEPARATOR + region
}

/**
 * Detail line of a result row, or `null` when the row has nothing to add.
 *
 * A row whose label repeats among the results shows what tells it apart:
 * [LocationEntry.name] when it differs from the label, [LocationEntry.objectTypeName]
 * and [LocationEntry.postalArea] — the admin region name is not repeated, because the
 * title carries it (spec: `location-search` — Duplicate results show disambiguation
 * fields).
 *
 * [resolvedAddress] is the address `street houseNumber` the caller resolved at the
 * entry's position; it joins the line whenever it is present, which is what puts a
 * name hit's street on the row (same spec — A name hit shows the address resolved at
 * its position). An address hit already carries street and house number in its label,
 * so its caller passes `null`.
 */
fun rowDetail(entry: LocationEntry, isDuplicate: Boolean, resolvedAddress: String?): String? {
    val parts = mutableListOf<String>()
    if (isDuplicate) {
        val name = entry.name?.trim().orEmpty()
        if (name.isNotEmpty() && name != entry.label.orEmpty()) {
            parts.add(name)
        }
        entry.objectTypeName?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    }
    resolvedAddress?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    if (isDuplicate) {
        entry.postalArea?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    }
    if (parts.isEmpty()) return null
    return parts.joinToString(DETAIL_SEPARATOR)
}

/**
 * The one description line a car `Row` has room for: the entry's postal area.
 *
 * The admin region name is on the title, and the row's second text line is the distance,
 * so the postal area is what is left to state — once. The street address a phone row
 * resolves for a name hit has no room on the car at all: that is the documented,
 * platform-constrained deviation (spec: `auto-search` — Results list shown, Postal code
 * is shown once, Resolved street address is a phone-only row fact).
 */
fun carRowDescription(entry: LocationEntry): String =
    entry.postalArea?.trim().orEmpty()

/**
 * Whether the row needs an address resolved at its position (change
 * `search-result-row-content`, design D8).
 *
 * Only a hit whose label is the POI's own name carries no street and no house number and
 * benefits: `matchedComponent == "poi"` is the bridge's word for that shape. A street hit
 * (`"location"`) and an address hit (`"address"`) already carry their street in the label,
 * so looking one up would spend a database lookup to contradict it, and a region or a
 * coordinate hit has no address to find.
 */
fun needsResolvedAddress(entry: LocationEntry): Boolean =
    entry.matchedComponent == "poi" && !entry.label.isNullOrEmpty()

/**
 * The row's address text from a `getAddressAt` result (`String[]{street, houseNumber,
 * adminRegion, postalArea}`), or `null` when there is nothing to show.
 *
 * Empty and absent parts are dropped, so a coordinate with no house number in the index
 * yields the street alone rather than a stray separator or the word "null".
 */
fun resolvedAddressText(address: Array<String>?): String? {
    if (address == null) return null
    val street = address.getOrNull(0)?.trim().orEmpty()
    val houseNumber = address.getOrNull(1)?.trim().orEmpty()
    val text = listOf(street, houseNumber).filter { it.isNotEmpty() }.joinToString(" ")
    return text.ifEmpty { null }
}
