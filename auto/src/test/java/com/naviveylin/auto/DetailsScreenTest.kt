package com.naviveylin.auto

import androidx.car.app.model.Action
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
 * labeled attribute rows (coordinates/address/area/description), title
 * fallback chain (name → address → label → generic), pane row cap, and
 * pane-level action buttons ("Navigate here" primary + "Show" secondary —
 * PaneTemplate rows are not actionable on car hosts, so buttons live at
 * pane level).
 */
@RunWith(RobolectricTestRunner::class)
class DetailsScreenTest {

    private val navigateAction = Action.Builder()
        .setTitle("Navigate here")
        .setFlags(Action.FLAG_PRIMARY)
        .build()
    private val showAction = Action.Builder().setTitle("Show").build()

    private fun description(vararg entries: DescriptionEntry): ObjectDescription =
        ObjectDescription(entries.toList(), Double.NaN, Double.NaN, "area", "building", 1L)

    private fun entry(section: String, label: String, value: String): DescriptionEntry =
        DescriptionEntry().apply {
            sectionKey = section
            labelKey = label
            this.value = value
        }

    @Test
    fun navigateButtonAlwaysShownAsPrimaryPaneAction() {
        val pane = buildDetailsPane(
            rows = listOf(Row.Builder().setTitle("Test").build()),
            navigateAction = navigateAction,
            showAction = showAction
        )
        assertEquals(2, pane.actions.size)
        assertEquals("Navigate here", pane.actions[0].title.toString())
        assertTrue(pane.actions[0].flags and Action.FLAG_PRIMARY != 0)
    }

    @Test
    fun showButtonShownAsSecondaryPaneAction() {
        val pane = buildDetailsPane(
            rows = listOf(Row.Builder().setTitle("Test").build()),
            navigateAction = navigateAction,
            showAction = showAction
        )
        assertEquals(2, pane.actions.size)
        assertEquals("Navigate here", pane.actions[0].title.toString())
        assertEquals("Show", pane.actions[1].title.toString())
        assertTrue(pane.actions[1].flags and Action.FLAG_PRIMARY == 0)
    }

    @Test
    fun noClearActionInPane() {
        val pane = buildDetailsPane(
            rows = listOf(Row.Builder().setTitle("Test").build()),
            navigateAction = navigateAction,
            showAction = showAction
        )
        assertEquals(
            listOf("Navigate here", "Show"),
            pane.actions.map { it.title.toString() }
        )
    }

    @Test
    fun coordinatesRowAlwaysPresentWithLabel() {
        val rows = buildDetailsRows(
            lat = 51.5136, lon = 7.4653, address = null, description = null
        )
        assertEquals(1, rows.size)
        assertEquals("Coordinates", rows[0].title.toString())
        assertTrue(rows[0].texts.any { it.toString().contains("51.51360") })
        assertTrue(rows[0].texts.any { it.toString().contains("7.46530") })
    }

    @Test
    fun addressShownAsLabeledRow() {
        val rows = buildDetailsRows(
            lat = 51.5136, lon = 7.4653,
            address = arrayOf("Kleppingstr.", "22", "Dortmund", "44139"),
            description = null
        )
        assertEquals(3, rows.size)
        assertEquals("Coordinates", rows[0].title.toString())
        assertEquals("Address", rows[1].title.toString())
        assertEquals("Kleppingstr. 22", rows[1].texts[0].toString())
        assertEquals("Area", rows[2].title.toString())
        assertEquals("Dortmund", rows[2].texts[0].toString())
    }

    @Test
    fun areaFallsBackToPostalArea() {
        val rows = buildDetailsRows(
            lat = 51.5136, lon = 7.4653,
            address = arrayOf("", "", "", "44139"),
            description = null
        )
        assertEquals(2, rows.size)
        assertEquals("Area", rows[1].title.toString())
        assertEquals("44139", rows[1].texts[0].toString())
    }

    @Test
    fun descriptionEntriesShownAsLabeledRows() {
        val rows = buildDetailsRows(
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
        val rows = buildDetailsRows(
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
    fun paneCapsRowsAtFour() {
        val rows = buildDetailsRows(
            lat = 51.5136, lon = 7.4653, address = null,
            description = description(
                entry("General", "A", "1"),
                entry("General", "B", "2"),
                entry("General", "C", "3"),
                entry("General", "D", "4"),
                entry("General", "E", "5")
            )
        )
        // Coordinates row + description entries fill up to the 4-row cap.
        assertEquals(4, rows.size)
        val pane = buildDetailsPane(
            rows = rows,
            navigateAction = navigateAction,
            showAction = showAction
        )
        assertEquals(4, pane.rows.size)
    }

    @Test
    fun navigateActionInvokesCallback() {
        var invoked = false
        val action = buildNavigateAction { invoked = true }
        assertEquals("Navigate here", action.title.toString())
        assertTrue(action.flags and Action.FLAG_PRIMARY != 0)
        action.onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        assertTrue(invoked)
    }

    @Test
    fun showActionInvokesCallback() {
        var invoked = false
        val action = buildShowAction { invoked = true }
        assertEquals("Show", action.title.toString())
        assertTrue(action.flags and Action.FLAG_PRIMARY == 0)
        action.onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        assertTrue(invoked)
    }

    @Test
    fun backNavigationDoesNotTriggerNavigation() {
        // The details screen's only navigation trigger is the "Navigate here"
        // pane button: rows carry no click listeners and the pane carries no
        // actions other than the provided navigate/show, so popping the
        // screen (system back) never starts navigation.
        val rows = buildDetailsRows(
            lat = 51.5136, lon = 7.4653, address = null,
            description = description(entry("General", "Type", "restaurant"))
        )
        assertTrue("rows must not be clickable", rows.all { it.onClickDelegate == null })
        val pane = buildDetailsPane(
            rows = rows,
            navigateAction = navigateAction,
            showAction = showAction
        )
        assertEquals(
            listOf("Navigate here", "Show"),
            pane.actions.map { it.title.toString() }
        )
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
        assertEquals("Kleppingstr. 22", title)
    }

    @Test
    fun titleFallsBackToAdminRegion() {
        val title = resolveTitle(
            address = arrayOf("", "", "Dortmund", "44139"),
            description = null,
            nameHint = "Some label"
        )
        assertEquals("Dortmund", title)
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
        assertEquals("Kleppingstr. 22", resolveDestinationName(arrayOf("Kleppingstr.", "22", "Dortmund", "44139")))
    }
}
