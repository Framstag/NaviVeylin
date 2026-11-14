package com.naviveylin.core.search

import com.framstag.libosmscout.client.PoiEntry

/**
 * The name filter of a POI result list, and the derivation that turns one search's
 * candidates into the rows a surface displays — one implementation shared by the phone
 * sheet and the Android Auto results screen (spec: `poi-search` — A name filter narrows
 * a POI result list; A filtered search asks for a candidate set, not a page; change
 * `poi-name-filter`, design D1/D2/D4).
 *
 * The term is compared against the entry's name, operator and brand under the search's
 * own folding ([foldForSearchMatch]), so a filter can never disagree with the ranker
 * about `Müller` and `muller`. Filtering runs here rather than in the native walk because
 * `osmscout::POIService` has no name predicate and a second folding implementation would
 * cost results and not just markings (design D1).
 */

/**
 * The number of candidates a POI walk is asked for while a name filter is in effect
 * (design D2).
 *
 * The native side truncates only after it has loaded every object in the radius, so
 * widening this request costs marshalling, not a second walk. The number is declared
 * rather than measured: its `counted=`/`cap=` census needs a submodule patch and is a
 * follow-up, not part of this change (design D8).
 */
const val POI_CANDIDATE_CAP = 2000

/**
 * Whether [entry] matches [foldedTerm] — the term is a substring of the entry's name,
 * operator or brand under the search's folding. An empty term matches everything, so an
 * empty filter is the unfiltered list.
 */
fun matchesPoiFilter(entry: PoiEntry, foldedTerm: String): Boolean {
    if (foldedTerm.isEmpty()) return true
    return foldForSearchMatch(entry.label.orEmpty()).contains(foldedTerm) ||
        foldForSearchMatch(entry.operator.orEmpty()).contains(foldedTerm) ||
        foldForSearchMatch(entry.brand.orEmpty()).contains(foldedTerm)
}

/**
 * The list a surface displays: the nearest [displayMax] candidates matching [term], in the
 * order the walk returned them (nearest first). An empty or blank term keeps today's list;
 * a candidate set the display limit already truncated is never what a filter is applied to
 * — the caller widens the walk instead (design D3).
 */
fun filterPoiEntries(candidates: List<PoiEntry>, term: String, displayMax: Int): List<PoiEntry> {
    val folded = foldForSearchMatch(term)
    if (folded.isEmpty()) return candidates.take(displayMax)
    return candidates.asSequence()
        .filter { matchesPoiFilter(it, folded) }
        .take(displayMax)
        .toList()
}
