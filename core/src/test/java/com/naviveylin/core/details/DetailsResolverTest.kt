package com.naviveylin.core.details

import com.framstag.libosmscout.client.DescriptionEntry
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.ObjectDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pure resolver tests (spec: auto-destination-details — address/area/title
 * scenarios; spec: enhanced-details-sheet — lead behavior). The `phoneFixture*`
 * tests mirror the expected outputs of the phone details dialog compose tests
 * (`LocationDetailsDialogComposeTest`) one-to-one: the resolver must reproduce
 * the phone derivation exactly.
 */
@RunWith(RobolectricTestRunner::class)
class DetailsResolverTest {

    private fun entry(section: String, label: String, value: String, subsection: String = ""): DescriptionEntry =
        DescriptionEntry().apply {
            sectionKey = section
            subsectionKey = subsection
            labelKey = label
            this.value = value
        }

    private fun description(vararg entries: DescriptionEntry): ObjectDescription =
        ObjectDescription(entries.toList(), 51.5, 7.4)

    private fun input(
        label: String? = null,
        name: String? = null,
        admin: String? = null,
        postal: String? = null,
        description: ObjectDescription? = null,
        resolved: Array<String>? = null
    ) = DetailsInput(
        label = label,
        name = name,
        adminRegionHierarchy = admin,
        postalArea = postal,
        description = description,
        resolvedAddress = resolved
    )

    // ------------------------------------------------------------------
    // Address resolution (spec scenarios)
    // ------------------------------------------------------------------

    @Test
    fun addressCombinesStreetHouseNumberPostalAndCity() {
        // "Hauptstraße 12, 44339 Dortmund"
        val input = input(
            description = description(
                entry("Location", "Address", "12"),
                entry("Location", "Location", "Hauptstraße")
            ),
            resolved = arrayOf("Hauptstraße", "12", "Dortmund", "44339")
        )
        assertEquals("Hauptstraße 12, 44339 Dortmund", DetailsResolver.resolveAddress(input))
    }

    @Test
    fun addressWithStreetAndHouseNumberOnly() {
        // No postal/city anywhere → street + house number only.
        val input = input(
            description = description(
                entry("Location", "Address", "12"),
                entry("Location", "Location", "Hauptstraße")
            )
        )
        assertEquals("Hauptstraße 12", DetailsResolver.resolveAddress(input))
    }

    @Test
    fun addressStreetFromReverseLookup() {
        // Description lacks a street; the location index knows it.
        val input = input(
            description = description(entry("Location", "Address", "12")),
            resolved = arrayOf("Hauptstraße", "12", "Dortmund", "44339")
        )
        assertEquals("Hauptstraße 12, 44339 Dortmund", DetailsResolver.resolveAddress(input))
    }

    @Test
    fun addressFallsBackToDigitLabelAsStreet() {
        // Address search result: label carries the street.
        val input = input(
            label = "Hauptstraße 12",
            admin = "Eving/Dortmund/Dortmund",
            description = description(entry("Location", "Address", "12"))
        )
        assertEquals("Hauptstraße 12, Dortmund", DetailsResolver.resolveAddress(input))
    }

    @Test
    fun addressWithHouseNumberAndCityOnly() {
        val input = input(
            admin = "Eving/Dortmund/Dortmund",
            description = description(entry("Location", "Address", "12"))
        )
        assertEquals("12, Dortmund", DetailsResolver.resolveAddress(input))
    }

    @Test
    fun addressNullWithoutHouseNumber() {
        assertNull(
            DetailsResolver.resolveAddress(
                input(description = description(entry("General", "Type", "hotel")))
            )
        )
    }

    @Test
    fun addressIgnoresCoordinateLabel() {
        // Coordinate labels are never streets.
        assertNull(
            DetailsResolver.resolveAddress(input(label = "51.50000, 7.40000"))
        )
    }

    @Test
    fun addressIgnoresLabelEqualToName() {
        // "Hotel Central" with no digit and equal to name → not a street.
        assertNull(
            DetailsResolver.resolveAddress(
                input(
                    label = "Hotel Central",
                    name = "Hotel Central",
                    description = description(entry("General", "Name", "Hotel Central"))
                )
            )
        )
    }

    // ------------------------------------------------------------------
    // Area resolution (spec scenarios)
    // ------------------------------------------------------------------

    @Test
    fun areaUsesAdminRegionHierarchy() {
        assertEquals(
            "Eving/Dortmund/Dortmund",
            DetailsResolver.resolveArea(input(admin = "Eving/Dortmund/Dortmund"))
        )
    }

    @Test
    fun areaFallsBackToReverseRegion() {
        assertEquals(
            "Dortmund",
            DetailsResolver.resolveArea(input(resolved = arrayOf("", "", "Dortmund", "44139")))
        )
    }

    @Test
    fun areaFallsBackToDescriptionIsIn() {
        val input = input(
            description = description(
                entry("Location", "IsIn", "Dortmund, Dortmund, Nordrhein-Westfalen", subsection = "AdminLevel")
            )
        )
        assertEquals("Dortmund, Dortmund, Nordrhein-Westfalen", DetailsResolver.resolveArea(input))
    }

    @Test
    fun areaFallsBackToPostalArea() {
        assertEquals("44139", DetailsResolver.resolveArea(input(resolved = arrayOf("", "", "", "44139"))))
        assertEquals("44339", DetailsResolver.resolveArea(input(postal = "44339")))
    }

    @Test
    fun areaNullWithoutAnyData() {
        assertNull(DetailsResolver.resolveArea(input()))
    }

    // ------------------------------------------------------------------
    // Title resolution (spec scenarios)
    // ------------------------------------------------------------------

    @Test
    fun titleShowsObjectNameFromDescription() {
        val input = input(
            description = description(
                entry("General", "Name", "Mario's"),
                entry("General", "Type", "restaurant")
            )
        )
        assertEquals("Mario's", DetailsResolver.resolveTitle(input))
    }

    @Test
    fun titleFallsBackToEntryName() {
        assertEquals("Mario's", DetailsResolver.resolveTitle(input(name = "Mario's")))
    }

    @Test
    fun titleFallsBackToFullAddress() {
        val input = input(
            description = description(
                entry("Location", "Address", "22"),
                entry("Location", "Location", "Kleppingstr.")
            ),
            resolved = arrayOf("", "", "Dortmund", "44139")
        )
        assertEquals("Kleppingstr. 22, 44139 Dortmund", DetailsResolver.resolveTitle(input))
    }

    @Test
    fun titleFallsBackToLabel() {
        assertEquals("Mario's", DetailsResolver.resolveTitle(input(label = "Mario's")))
    }

    @Test
    fun titleFallsBackToNameHint() {
        assertEquals(
            "Mario's",
            DetailsResolver.resolveTitle(input(), nameHint = "Mario's")
        )
    }

    @Test
    fun titleFallsBackToGeneric() {
        assertEquals("Location", DetailsResolver.resolveTitle(input()))
    }

    // ------------------------------------------------------------------
    // Phone fixture parity (task 1.3): expected values copied from
    // LocationDetailsDialogComposeTest, fed through the same inputs.
    // ------------------------------------------------------------------

    private fun phoneEntry(
        label: String = "Hotel Central",
        admin: String? = "Eving/Dortmund/Dortmund"
    ) = LocationEntry().apply {
        this.label = label
        this.lat = 51.5
        this.lon = 7.4
        this.adminRegionHierarchy = admin
    }

    @Test
    fun phoneFixture_areaFallsBackToDescriptionIsIn() {
        val entry = phoneEntry(admin = null)
        val desc = description(
            entry("Location", "Address", "12"),
            entry("Location", "Location", "Hauptstraße"),
            entry("Location", "IsIn", "Dortmund, Dortmund, Nordrhein-Westfalen", subsection = "AdminLevel")
        )
        val input = input(
            label = entry.label,
            name = entry.name,
            admin = entry.adminRegionHierarchy,
            postal = entry.postalArea,
            description = desc
        )
        assertEquals("Dortmund, Dortmund, Nordrhein-Westfalen", DetailsResolver.resolveArea(input))
    }

    @Test
    fun phoneFixture_fullAddressWithStreetAndHouseNumber() {
        val entry = phoneEntry()
        val desc = objectDescription(
            entry("Location", "Address", "12"),
            entry("Location", "Location", "Hauptstraße"),
            entry("General", "Type", "hotel")
        )
        val input = input(
            label = entry.label,
            name = entry.name,
            admin = entry.adminRegionHierarchy,
            postal = entry.postalArea,
            description = desc
        )
        // Phone compose test expects "Hauptstraße 12, Dortmund" (title + row).
        assertEquals("Hauptstraße 12, Dortmund", DetailsResolver.resolveAddress(input))
        assertEquals("Hauptstraße 12, Dortmund", DetailsResolver.resolveTitle(input))
        assertEquals("Eving/Dortmund/Dortmund", DetailsResolver.resolveArea(input))
    }

    @Test
    fun phoneFixture_addressIncludesCityWhenOnlyHouseNumber() {
        val entry = phoneEntry()
        val desc = objectDescription(entry("Location", "Address", "12"))
        val input = input(
            label = entry.label,
            name = entry.name,
            admin = entry.adminRegionHierarchy,
            postal = entry.postalArea,
            description = desc
        )
        assertEquals("12, Dortmund", DetailsResolver.resolveAddress(input))
        assertEquals("12, Dortmund", DetailsResolver.resolveTitle(input))
    }

    @Test
    fun phoneFixture_streetFromReverseLookup() {
        val entry = phoneEntry()
        val desc = objectDescription(entry("Location", "Address", "12"))
        val input = input(
            label = entry.label,
            name = entry.name,
            admin = entry.adminRegionHierarchy,
            postal = entry.postalArea,
            description = desc,
            resolved = arrayOf("Hauptstraße", "12", "Dortmund", "44339")
        )
        assertEquals("Hauptstraße 12, 44339 Dortmund", DetailsResolver.resolveAddress(input))
    }

    @Test
    fun phoneFixture_streetFromSearchLabel() {
        val entry = phoneEntry(label = "Hauptstraße 12")
        val desc = objectDescription(entry("Location", "Address", "12"))
        val input = input(
            label = entry.label,
            name = entry.name,
            admin = entry.adminRegionHierarchy,
            postal = entry.postalArea,
            description = desc
        )
        assertEquals("Hauptstraße 12, Dortmund", DetailsResolver.resolveAddress(input))
    }

    @Test
    fun phoneFixture_noAddressWithoutHouseNumber() {
        val entry = phoneEntry()
        val desc = objectDescription(entry("General", "Type", "hotel"))
        val input = input(
            label = entry.label,
            name = entry.name,
            admin = entry.adminRegionHierarchy,
            postal = entry.postalArea,
            description = desc
        )
        assertNull(DetailsResolver.resolveAddress(input))
    }

    @Test
    fun phoneFixture_titleShowsObjectName() {
        val entry = phoneEntry(label = "51.50000, 7.40000")
        val desc = objectDescription(
            entry("General", "Name", "Mario's"),
            entry("General", "Type", "restaurant")
        )
        val input = input(
            label = entry.label,
            name = entry.name,
            admin = entry.adminRegionHierarchy,
            postal = entry.postalArea,
            description = desc
        )
        assertEquals("Mario's", DetailsResolver.resolveTitle(input))
    }

    @Test
    fun phoneFixture_titleFallsBackToAddress() {
        val entry = phoneEntry(label = "51.50000, 7.40000", admin = null)
        val desc = objectDescription(
            entry("Location", "Address", "12"),
            entry("Location", "Location", "Hauptstraße")
        )
        val input = input(
            label = entry.label,
            name = entry.name,
            admin = entry.adminRegionHierarchy,
            postal = entry.postalArea,
            description = desc
        )
        assertEquals("Hauptstraße 12", DetailsResolver.resolveTitle(input))
    }

    private fun objectDescription(vararg entries: DescriptionEntry): ObjectDescription =
        ObjectDescription(entries.toList(), 51.5, 7.4)
}
