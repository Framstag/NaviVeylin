package com.naviveylin.core.search

import com.framstag.libosmscout.client.PoiEntry

/**
 * The candidate set a filtered POI search asks the native walk for (spec: `poi-search` — A filtered
 * search asks for a candidate set, not a page; change `poi-name-filter`, design D2).
 *
 * The native walk already loads every object in the radius and truncates to the requested size
 * afterwards, so a filtered search asks for this cap and the filter narrows it: the displayed list is
 * then the nearest matches in the radius, not the filter applied to the nearest page of anything.
 * Shared by both variants so the phone and the car cannot disagree about the width.
 */
const val POI_CANDIDATE_CAP: Int = 2000

/**
 * Whether [entry] matches [foldedTerm] — a term already passed through [foldForSearchMatch].
 *
 * A match is a substring of the entry's name, operator or brand under the search's own folding, so
 * `rewe` matches `REWE Markt GmbH` and `muller` matches `Müller` (spec: `poi-search` — A name filter
 * narrows a POI result list; design D1). An empty folded term matches every entry, which is what
 * makes an empty filter today's list.
 */
fun matchesPoiFilter(entry: PoiEntry, foldedTerm: String): Boolean {
    if (foldedTerm.isEmpty()) return true
    return foldForSearchMatch(entry.label.orEmpty()).contains(foldedTerm) ||
        foldForSearchMatch(entry.operator.orEmpty()).contains(foldedTerm) ||
        foldForSearchMatch(entry.brand.orEmpty()).contains(foldedTerm)
}

/**
 * The displayed list of a filtered search: the nearest [displayMax] entries of [candidates] whose
 * name, operator or brand match [term] (spec: `poi-search` — A filtered search asks for a candidate
 * set, not a page; design D4).
 *
 * [candidates] arrives in the walk's own order (nearest first), so the result is the nearest matches
 * of that order. The derivation lives here — not on a surface — so both variants filter through one
 * implementation. An empty or blank term returns the unfiltered list, truncated to [displayMax].
 */
fun filterPoiEntries(candidates: List<PoiEntry>, term: String, displayMax: Int): List<PoiEntry> {
    if (displayMax <= 0) return emptyList()
    val foldedTerm = foldForSearchMatch(term)
    if (foldedTerm.isEmpty()) return candidates.take(displayMax)
    return candidates.asSequence()
        .filter { matchesPoiFilter(it, foldedTerm) }
        .take(displayMax)
        .toList()
}
