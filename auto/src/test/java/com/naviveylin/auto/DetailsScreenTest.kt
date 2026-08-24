package com.naviveylin.auto

import androidx.car.app.model.Row
import com.framstag.libosmscout.client.DescriptionEntry
import com.framstag.libosmscout.client.ObjectDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for the shared details screen (spec: auto-destination-details):
 * labeled attribute rows (coordinates/address/area/description), ALL
 * description attributes listed (no row cap — opening hours, phone, …),
 * street/address dedup, title fallback chain (name → address → label →
 * generic), and the "Navigate here" / "Show" actions.
 */
@RunWith(RobolectricTestRunner::class)
class DetailsScreenTest {

    private fun description(vararg entries: DescriptionEntry): ObjectDescription =
        ObjectDescription(entries.toList(), Double.NaN, Double.NaN, "area", "building", 1L)

    private fun entry(section: String, label: String, value: String): DescriptionEntry =
        DescriptionEntry().apply {
            sectionKey = section
            labelKey = label
            this.value = value
        }

    @Test
    fun coordinatesRowAlwaysPresentWithLabel() {
        val rows = buildAttributeList(
            lat = 51.5136, lon = 7.4653, address = null, description = null
        )
        assertEquals(1, rows.size)
        assertEquals("Coordinates", rows[0].title.toString())
        assertTrue(rows[0].texts.any { it.toString().contains("51.51360") })
        assertTrue(rows[0].texts.any { it.toString().contains("7.46530") })
    }

    @Test
    fun addressShownAsLabeledRow() {
        val rows = buildAttributeList(
            lat = 51.5136, lon = 7.4653,
            address = arrayOf("Kleppingstr.", "22", "Dortmund", "44139"),
            description = null
        )
        assertEquals(3, rows.size)
        assertEquals("Coordinates", rows[0].title.toString())
        assertEquals("Address", rows[1].title.toString())
        assertEquals("Kleppingstr. 22, 44139 Dortmund", rows[1].texts[0].toString())
        assertEquals("Area", rows[2].title.toString())
        assertEquals("Dortmund", rows[2].texts[0].toString())
    }

    @Test
    fun areaFallsBackToPostalArea() {
        val rows = buildAttributeList(
            lat = 51.5136, lon = 7.4653,
            address = arrayOf("", "", "", "44139"),
            description = null
        )
        assertEquals(2, rows.size)
        assertEquals("Area", rows[1].title.toString())
        assertEquals("44139", rows[1].texts[0].toString())
    }

    @Test
    fun areaFallsBackToDescriptionIsIn() {
        val rows = buildAttributeList(
            lat = 51.5136, lon = 7.4653, address = null,
            description = description(
                DescriptionEntry().apply {
                    sectionKey = "Location"
                    subsectionKey = "AdminLevel"
                    labelKey = "IsIn"
                    value = "Dortmund, Dortmund, Nordrhein-Westfalen"
                }
            )
        )
        assertEquals(3, rows.size)
        assertEquals("Area", rows[1].title.toString())
        assertEquals("Dortmund, Dortmund, Nordrhein-Westfalen", rows[1].texts[0].toString())
        // The IsIn row itself stays visible as a description entry (phone parity).
        assertEquals("IsIn", rows[2].title.toString())
    }

    @Test
    fun noAreaRowWithoutAreaData() {
        val rows = buildAttributeList(
            lat = 51.5136, lon = 7.4653, address = arrayOf("", "", "", ""),
            description = description(entry("General", "Type", "hotel"))
        )
        assertEquals(2, rows.size)
        assertEquals("Coordinates", rows[0].title.toString())
        assertEquals("Type", rows[1].title.toString())
    }

    @Test
    fun streetAndAddressEntriesNotDuplicated() {
        // The combined Address row covers Location/Location (street) and
        // Location/Address (house number) — neither reappears as a row.
        val rows = buildAttributeList(
            lat = 51.5136, lon = 7.4653,
            address = arrayOf("Kleppingstr.", "22", "Dortmund", "44139"),
            description = description(
                entry("Location", "Address", "22"),
                entry("Location", "Location", "Kleppingstr."),
                entry("General", "Type", "hotel")
            )
        )
        assertEquals(4, rows.size)
        assertEquals("Coordinates", rows[0].title.toString())
        assertEquals("Address", rows[1].title.toString())
        assertEquals("Area", rows[2].title.toString())
        assertEquals("Type", rows[3].title.toString())
    }

    @Test
    fun descriptionEntriesShownAsLabeledRows() {
        val rows = buildAttributeList(
            lat = 51.5136, lon = 7.4653, address = null,
            description = description(
                entry("General", "Name", "Mario's"),
                entry("General", "Type", "restaurant")
            )
        )
        // Coordinates row + 2 description rows
        assertEquals(3, rows.size)
        assertEquals("Name", rows[1].title.toString())
        assertEquals("Mario's", rows[1].texts[0].toString())
        assertEquals("Type", rows[2].title.toString())
    }

    @Test
    fun blankDescriptionEntriesSkipped() {
        val rows = buildAttributeList(
            lat = 51.5136, lon = 7.4653, address = null,
            description = description(
                entry("General", "Name", "   "),
                entry("General", "Type", "restaurant")
            )
        )
        assertEquals(2, rows.size)
        assertEquals("Type", rows[1].title.toString())
    }

    @Test
    fun allDescriptionAttributesShown() {
        // Every attribute returned by the description API must be listed —
        // no fixed row limit drops attributes like opening hours or phone.
        val rows = buildAttributeList(
            lat = 51.5136, lon = 7.4653,
            address = arrayOf("Kleppingstr.", "22", "Dortmund", "44139"),
            description = description(
                entry("General", "Name", "Mario's"),
                entry("General", "Type", "restaurant"),
                entry("Contact", "Phone", "+49 231 123456"),
                entry("General", "OpeningHours", "Mo-Fr 09:00-18:00"),
                entry("General", "Website", "https://example.com")
            )
        )
        assertEquals(8, rows.size) // coordinates + address + area + 5 attributes
        assertEquals("OpeningHours", rows[6].title.toString())
        assertEquals("Mo-Fr 09:00-18:00", rows[6].texts[0].toString())
        assertEquals("Phone", rows[5].title.toString())
        assertEquals("Website", rows[7].title.toString())
    }

    @Test
    fun openingHoursShownOnDetailsScreen() {
        val rows = buildAttributeList(
            lat = 51.5136, lon = 7.4653, address = null,
            description = description(
                entry("General", "OpeningHours", "Mo-Fr 09:00-18:00")
            )
        )
        assertEquals(2, rows.size)
        assertEquals("OpeningHours", rows[1].title.toString())
        assertEquals("Mo-Fr 09:00-18:00", rows[1].texts[0].toString())
    }

    @Test
    fun noRowCapForLongDescriptions() {
        // More entries than any fixed pane cap: every one must be present.
        val rows = buildAttributeList(
            lat = 51.5136, lon = 7.4653, address = null,
            description = description(
                entry("General", "A", "1"),
                entry("General", "B", "2"),
                entry("General", "C", "3"),
                entry("General", "D", "4"),
                entry("General", "E", "5"),
                entry("General", "F", "6")
            )
        )
        assertEquals(7, rows.size) // coordinates + 6 entries
    }

    @Test
    fun descriptionEntriesKeepNativeOrder() {
        val rows = buildAttributeList(
            lat = 51.5136, lon = 7.4653,
            address = arrayOf("Kleppingstr.", "22", "Dortmund", "44139"),
            description = description(
                entry("General", "Type", "restaurant"),
                entry("Contact", "Phone", "+49 231 123456")
            )
        )
        assertEquals(5, rows.size)
        assertEquals("Coordinates", rows[0].title.toString())
        assertEquals("Address", rows[1].title.toString())
        assertEquals("Area", rows[2].title.toString())
        assertEquals("Type", rows[3].title.toString())
        assertEquals("Phone", rows[4].title.toString())
    }

    @Test
    fun navigateRowInvokesCallback() {
        var invoked = false
        val row = buildNavigateRow { invoked = true }
        assertEquals("▶ Navigate here", row.title.toString())
        assertTrue("row must be clickable", row.onClickDelegate != null)
        row.onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        assertTrue(invoked)
    }

    @Test
    fun showRowInvokesCallback() {
        var invoked = false
        val row = buildShowRow { invoked = true }
        assertEquals("◎ Show", row.title.toString())
        assertTrue("row must be clickable", row.onClickDelegate != null)
        row.onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        assertTrue(invoked)
    }

    @Test
    fun attributeRowsAreNotClickable() {
        // The details screen's only navigation trigger is the "Navigate here"
        // row: attribute rows carry no click listeners, so popping the screen
        // (system back) never starts navigation.
        val rows = buildAttributeList(
            lat = 51.5136, lon = 7.4653, address = null,
            description = description(entry("General", "Type", "restaurant"))
        )
        assertTrue("attribute rows must not be clickable", rows.all { it.onClickDelegate == null })
        // The action rows are clickable by contrast.
        assertTrue(buildNavigateRow {}.onClickDelegate != null)
        assertTrue(buildShowRow {}.onClickDelegate != null)
    }

    @Test
    fun titleShowsObjectName() {
        val title = resolveTitle(
            address = arrayOf("Kleppingstr.", "22", "Dortmund", "44139"),
            description = description(entry("General", "Name", "Mario's")),
            nameHint = "Mario's"
        )
        assertEquals("Mario's", title)
    }

    @Test
    fun titleFallsBackToAddress() {
        val title = resolveTitle(
            address = arrayOf("Kleppingstr.", "22", "Dortmund", "44139"),
            description = null,
            nameHint = "Some label"
        )
        assertEquals("Kleppingstr. 22, 44139 Dortmund", title)
    }

    @Test
    fun titleFallsBackToLabelWhenNoAddress() {
        // No street/house number → the label wins over the region (phone lead).
        val title = resolveTitle(
            address = arrayOf("", "", "Dortmund", "44139"),
            description = null,
            nameHint = "Some label"
        )
        assertEquals("Some label", title)
    }

    @Test
    fun titleFallsBackToAddressLikeLabel() {
        // Digit-bearing label is an address; title uses it (phone lead).
        val title = resolveTitle(address = null, description = null, nameHint = "Hauptstraße 12")
        assertEquals("Hauptstraße 12", title)
    }

    @Test
    fun titleFallsBackToLabel() {
        val title = resolveTitle(
            address = null,
            description = null,
            nameHint = "Mario's"
        )
        assertEquals("Mario's", title)
    }

    @Test
    fun titleFallsBackToGeneric() {
        val title = resolveTitle(address = null, description = null, nameHint = null)
        assertEquals("Location", title)
    }

    @Test
    fun destinationNameFallsBackToNull() {
        assertNull(resolveDestinationName(address = null, nameHint = null))
        assertEquals("Mario's", resolveDestinationName(address = null, nameHint = "Mario's"))
        assertEquals(
            "Kleppingstr. 22, 44139 Dortmund",
            resolveDestinationName(arrayOf("Kleppingstr.", "22", "Dortmund", "44139"))
        )
    }
}
