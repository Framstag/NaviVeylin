package com.naviveylin.core.details

import com.framstag.libosmscout.client.ObjectDescription

/**
 * All raw inputs the details views (phone dialog, Android Auto screen) need to
 * resolve address, area, and title — the phone `LocationEntry`/description and
 * the AA reverse-geocode array/name-hint map onto the same shape.
 *
 * @param label search/long-press label ("Hotel Central", "Hauptstraße 12", …)
 * @param name object name from the location entry (description `General/Name`
 *             takes precedence over this when both exist)
 * @param adminRegionHierarchy admin region tree, e.g. "Eving/Dortmund/Dortmund"
 * @param postalArea postal code from the location entry
 * @param description structured native description (may be null)
 * @param resolvedAddress reverse lookup result: [street, houseNumber,
 *                        adminRegion, postalArea] (may be null)
 */
data class DetailsInput(
    val label: String?,
    val name: String?,
    val adminRegionHierarchy: String?,
    val postalArea: String?,
    val description: ObjectDescription?,
    val resolvedAddress: Array<String>?
)
