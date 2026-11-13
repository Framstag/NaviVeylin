package com.naviveylin.core.search

import com.framstag.libosmscout.client.PoiEntry

/**
 * Primary text of a POI result row — one composition shared by the phone's POI list and the Android
 * Auto POI rows (spec: `poi-search` — POI results list; spec: `auto-search` — A car POI row names its
 * operator or brand; change `poi-name-filter`, design D6).
 *
 * The name with the brand (preferred) or operator in parentheses when it differs; the brand or
 * operator alone when there is no name; "(unnamed)" only when name, operator and brand are all
 * absent. Keeping the rule here is what lets a car row that survived a name filter show the attribute
 * that matched instead of a title rule of its own.
 */
fun poiDisplayLabel(entry: PoiEntry): String {
    val name = entry.label
    val brand = entry.brand
    val operator = entry.operator
    return when {
        name.isNotEmpty() -> {
            val extra = when {
                !brand.isNullOrEmpty() && brand != name -> brand
                !operator.isNullOrEmpty() && operator != name -> operator
                else -> null
            }
            if (extra != null) "$name ($extra)" else name
        }
        !brand.isNullOrEmpty() -> brand
        !operator.isNullOrEmpty() -> operator
        else -> "(unnamed)"
    }
}
