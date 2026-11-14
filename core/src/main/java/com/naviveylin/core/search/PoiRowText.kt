package com.naviveylin.core.search

import com.framstag.libosmscout.client.PoiEntry

/**
 * The title of a POI result row — one composition shared by the phone POIs sheet and the
 * Android Auto POI results list (spec: `poi-search` — Result item display; spec:
 * `auto-search` — A car POI row names its operator or brand; change `poi-name-filter`,
 * design D6).
 *
 * The rule was private in the phone's search dialog, which left the car composing a title
 * of its own — and a row that survived a name filter on the operator could not show the
 * operator. The precedent is [rowTitle] for place rows: the rule lives in `:core` and each
 * surface renders its answer.
 *
 * The name with the brand (preferred) or the operator in parentheses, the brand or operator
 * alone when there is no name, `(unnamed)` when the entry carries none of the three.
 */
fun poiDisplayLabel(entry: PoiEntry): String {
    val name = entry.label.orEmpty()
    val brand = entry.brand.orEmpty()
    val operator = entry.operator.orEmpty()
    return when {
        name.isNotEmpty() -> {
            val extra = when {
                brand.isNotEmpty() && brand != name -> brand
                operator.isNotEmpty() && operator != name -> operator
                else -> null
            }
            if (extra != null) "$name ($extra)" else name
        }
        brand.isNotEmpty() -> brand
        operator.isNotEmpty() -> operator
        else -> "(unnamed)"
    }
}
