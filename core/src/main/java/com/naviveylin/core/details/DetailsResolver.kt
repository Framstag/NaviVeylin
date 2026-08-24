package com.naviveylin.core.details

/**
 * Single source of truth for the details-view data resolution shared by the
 * phone details dialog (`LocationDetailsDialog`) and the Android Auto details
 * screen (`DetailsScreen`). The phone UI is the lead: every rule below is
 * lifted verbatim from the phone derivation (spec: enhanced-details-sheet) so
 * the AA view can never lag the phone view again (spec:
 * auto-destination-details — address/area/title scenarios).
 */
object DetailsResolver {

    /** Long-press labels are formatted coordinates ("%.5f, %.5f") — never an address. */
    private val COORDINATE_LABEL_REGEX = Regex("""-?\d+\.\d+,\s*-?\d+\.\d+""")

    private fun entries(input: DetailsInput) = input.description?.entries.orEmpty()

    /**
     * Object name: description `General/Name` entry, else the location entry's
     * name field.
     */
    fun resolveName(input: DetailsInput): String? =
        entries(input).firstOrNull {
            it.sectionKey == "General" && it.labelKey == "Name"
        }?.value?.takeIf { it.isNotBlank() }
            ?: input.name?.takeIf { it.isNotBlank() }

    /**
     * Full address row: street + house number, suffixed with postal code and
     * city when available (e.g. "Hauptstraße 12, 44339 Dortmund"). Street from
     * the description, else the reverse lookup, else an address-like search
     * label (contains a digit, not coordinates, not the object name). House
     * number from the description, else the reverse lookup. City from the
     * reverse region, else the deepest admin-region segment, else IsIn.
     * Returns null when no street/house number is available.
     */
    fun resolveAddress(input: DetailsInput): String? {
        val entries = entries(input)
        val locationEntries = entries.filter { it.sectionKey == "Location" }
        val label = input.label?.takeIf { it.isNotBlank() && it != "(unnamed)" }
        val name = resolveName(input)
        // A label that is not the object name, not coordinates, and contains a
        // digit is an address label (e.g. "Hauptstraße 12") — usable as street.
        val labelIsCoordinates = label?.matches(COORDINATE_LABEL_REGEX) == true
        val labelIsName = name != null && label == name
        val descStreet = locationEntries.firstOrNull { it.labelKey == "Location" }?.value
        val reverseStreet = input.resolvedAddress?.getOrNull(0)?.takeIf { it.isNotBlank() }
        val labelStreet = if (descStreet.isNullOrBlank() && reverseStreet.isNullOrBlank() &&
            label != null && !labelIsCoordinates && !labelIsName && label.any { it.isDigit() }
        ) {
            label
        } else {
            null
        }
        val houseNr = locationEntries.firstOrNull { it.labelKey == "Address" }?.value
            ?: input.resolvedAddress?.getOrNull(1)?.takeIf { it.isNotBlank() }
        val streetAndNumber = when {
            !descStreet.isNullOrBlank() && !houseNr.isNullOrBlank() -> "$descStreet $houseNr"
            reverseStreet != null && !houseNr.isNullOrBlank() -> "$reverseStreet $houseNr"
            labelStreet != null -> labelStreet
            !houseNr.isNullOrBlank() -> houseNr
            !descStreet.isNullOrBlank() -> descStreet
            else -> null
        }
        if (streetAndNumber == null) {
            return null
        }
        val reverseRegion = input.resolvedAddress?.getOrNull(2)?.takeIf { it.isNotBlank() }
        val reversePostal = input.resolvedAddress?.getOrNull(3)?.takeIf { it.isNotBlank() }
        val postal = reversePostal ?: input.postalArea?.takeIf { it.isNotBlank() }
        val isIn = entries.firstOrNull {
            it.sectionKey == "Location" &&
                it.subsectionKey == "AdminLevel" &&
                it.labelKey == "IsIn"
        }?.value
        val area = resolveArea(input)
        // City for the address line: deepest region name (reverse lookup gives
        // the address's own region; otherwise the last hierarchy segment).
        val city = reverseRegion
            ?: area?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: isIn
        val suffix = listOfNotNull(postal, city)
            .filter { it != streetAndNumber }
            .distinct()
            .joinToString(" ")
        return if (suffix.isNotBlank()) "$streetAndNumber, $suffix" else streetAndNumber
    }

    /**
     * Area row: admin region hierarchy, else the reverse-lookup region, else
     * the description's admin-level `IsIn`, else the postal area. Returns null
     * when none is available.
     */
    fun resolveArea(input: DetailsInput): String? {
        val entries = entries(input)
        val reverseRegion = input.resolvedAddress?.getOrNull(2)?.takeIf { it.isNotBlank() }
        val reversePostal = input.resolvedAddress?.getOrNull(3)?.takeIf { it.isNotBlank() }
        val isIn = entries.firstOrNull {
            it.sectionKey == "Location" &&
                it.subsectionKey == "AdminLevel" &&
                it.labelKey == "IsIn"
        }?.value
        val postal = reversePostal ?: input.postalArea?.takeIf { it.isNotBlank() }
        return input.adminRegionHierarchy?.takeIf { it.isNotBlank() }
            ?: reverseRegion
            ?: isIn
            ?: postal
    }

    /**
     * Title: object name, else the full address (incl. postal + city), else
     * the search label, else [nameHint], else the generic "Location".
     */
    fun resolveTitle(input: DetailsInput, nameHint: String? = null): String {
        val name = resolveName(input)
        if (name != null) return name
        val address = resolveAddress(input)
        if (address != null) return address
        val label = input.label?.takeIf { it.isNotBlank() }
        if (label != null) return label
        return nameHint?.takeIf { it.isNotBlank() } ?: "Location"
    }

    /**
     * Destination name for the navigation context and the map marker: the
     * resolved full address, else the area, else the caller's name hint, else
     * null (coordinates fallback).
     */
    fun resolveDestinationName(input: DetailsInput, nameHint: String? = null): String? {
        val address = resolveAddress(input)
        if (address != null) return address
        val area = resolveArea(input)
        if (area != null) return area
        return nameHint?.takeIf { it.isNotBlank() }
    }
}
