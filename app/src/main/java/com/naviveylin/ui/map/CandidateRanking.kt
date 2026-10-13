package com.naviveylin.ui.map

import com.framstag.libosmscout.client.ObjectDescription

/**
 * Candidates whose name contains [nameHint] first, in the map database's own order otherwise.
 *
 * A stable partition, never a filter: a name the database does not match leaves the ranking
 * untouched and no candidate is dropped (spec: share/location-receiving — Shared place name ranks
 * the candidate list). `guidelines/Design.md` §9 keeps name *matching* the map library's job; this
 * only orders what the library already matched.
 *
 * Top-level so the picker's own UI test can rank with the same function the map screen uses.
 */
internal fun rankCandidatesByName(
    candidates: List<ObjectDescription>,
    nameHint: String?
): List<ObjectDescription> {
    val hint = nameHint?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return candidates
    val (matching, rest) = candidates.partition { it.objectName()?.lowercase()?.contains(hint) == true }
    return matching + rest
}
