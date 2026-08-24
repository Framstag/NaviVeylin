package com.naviveylin.core.details

import com.framstag.libosmscout.client.DescriptionEntry

/**
 * One resolved bundle for both details views (phone dialog + Android Auto
 * screen), produced by [DetailsResolver.resolve]. Both UIs consume this single
 * output, so future changes affect both variants at once.
 *
 * @param title object name, else full address, else non-coordinate label,
 *              else nameHint, else "Location"
 * @param address full address row (street + house number + postal + city), or null
 * @param area admin hierarchy → reverse region → IsIn → postal, or null
 * @param destinationName address, else area, else nameHint, or null
 * @param displayEntries description entries after blank filtering and
 *                       street/address dedup (shared render list)
 */
data class DetailsData(
    val title: String,
    val address: String?,
    val area: String?,
    val destinationName: String?,
    val displayEntries: List<DescriptionEntry>
)
