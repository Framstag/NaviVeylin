package com.naviveylin.ui.favorites

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FavoriteLocation
import com.naviveylin.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose tests for the reorderable group grid (spec `group-grid-display` — drag
 * ends commit the visible order, drag affordance, tap and menu preserved; spec
 * `group-ordering` — the group order).
 *
 * The grid's order and contents arrive as parameters, so the tests drive
 * [GroupGrid] directly and record the commits it asks for — the same shape as
 * `FavoritesSheetReorderComposeTest`. The sheet's own wiring (the order channel and
 * the ViewModel action) is covered by `FavoritesViewModelTest` and
 * `FavoriteRepositoryTest`.
 *
 * Runs under the default Robolectric sandbox: no native stub is involved, but the
 * module's sheet tests all run in that sandbox and mixing configs is what breaks
 * them (guidelines/Design.md §11).
 */
@RunWith(RobolectricTestRunner::class)
class FavoritesSheetGroupReorderComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val reorderLabel: String = context.getString(R.string.reorder_group)
    private val groupOptionsLabel: String = context.getString(R.string.group_options)
    private val setColorLabel: String = context.getString(R.string.set_color)

    private fun groups(vararg names: String): Map<String, List<FavoriteLocation>> =
        names.associateWith { listOf(FavoriteLocation("$it fav", 1.0, 2.0)) }

    /**
     * Long-press drag from [start] by [dx] pixels horizontally, then release: in the
     * two-column grid the next cell is one card width to the right.
     */
    private fun dragBy(start: SemanticsNodeInteraction, dx: Float) {
        start.performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(dx * 0.25f, 0f))
            advanceEventTime(20)
            moveBy(Offset(dx * 0.25f, 0f))
            advanceEventTime(20)
            moveBy(Offset(dx * 0.25f, 0f))
            advanceEventTime(20)
            moveBy(Offset(dx * 0.25f, 0f))
            advanceEventTime(20)
            up()
        }
        composeRule.waitForIdle()
    }

    /** Horizontal distance between two group cards, in pixels. */
    private fun cardDistance(fromName: String, toName: String): Float {
        val from = composeRule.onNodeWithText(fromName).fetchSemanticsNode().boundsInRoot
        val to = composeRule.onNodeWithText(toName).fetchSemanticsNode().boundsInRoot
        return to.left - from.left
    }

    private fun showGrid(
        groupOrder: List<String>,
        groups: Map<String, List<FavoriteLocation>> = groups(*groupOrder.toTypedArray()),
        reorders: MutableList<Pair<String, Int>> = mutableListOf(),
        opened: MutableList<String> = mutableListOf(),
        colorsSet: MutableList<String> = mutableListOf()
    ) {
        composeRule.setContent {
            GroupGrid(
                groupOrder = groupOrder,
                groups = groups,
                groupColors = emptyMap(),
                onOpenGroup = { opened.add(it) },
                onRename = {},
                onDelete = {},
                onSetColor = { colorsSet.add(it) },
                onReorder = { groupName, newIndex -> reorders.add(groupName to newIndex) }
            )
        }
    }

    // --- Reorder interaction ---

    @Test
    fun `dragging a card to the next cell commits that position once`() {
        val reorders = mutableListOf<Pair<String, Int>>()
        showGrid(listOf("Cities", "Work", "Home"), reorders = reorders)

        dragBy(composeRule.onAllNodesWithContentDescription(reorderLabel)[0], cardDistance("Cities", "Work"))

        assertEquals(listOf("Cities" to 1), reorders)
    }

    @Test
    fun `a drag that ends where it started commits nothing`() {
        val reorders = mutableListOf<Pair<String, Int>>()
        showGrid(listOf("Cities", "Work", "Home"), reorders = reorders)

        dragBy(composeRule.onAllNodesWithContentDescription(reorderLabel)[0], 0f)

        assertTrue(reorders.isEmpty())
        composeRule.onNodeWithText("Cities").assertIsDisplayed()
    }

    @Test
    fun `dragging the only group commits nothing`() {
        val reorders = mutableListOf<Pair<String, Int>>()
        showGrid(listOf("Cities"), reorders = reorders)

        // A bounded drag: moving a card far past the grid's edge starts the
        // library's drag auto-scroll, which keeps running after the gesture.
        dragBy(composeRule.onAllNodesWithContentDescription(reorderLabel)[0], 40f)

        assertTrue(reorders.isEmpty())
        composeRule.onNodeWithText("Cities").assertIsDisplayed()
    }

    /** A group deleted mid-drag is not in the stored order any more: no commit. */
    @Test
    fun `a group that is no longer in the stored order commits nothing`() {
        val reorders = mutableListOf<Pair<String, Int>>()
        showGrid(
            groupOrder = listOf("Cities"),
            groups = groups("Cities", "Work"),
            reorders = reorders
        )

        dragBy(composeRule.onAllNodesWithContentDescription(reorderLabel)[1], 40f)

        assertTrue(reorders.isEmpty())
    }

    // --- Drag affordance, tap and menu ---

    @Test
    fun `every group offers a drag handle`() {
        showGrid(listOf("Cities", "Work", "Home"))

        composeRule.onAllNodesWithContentDescription(reorderLabel).assertCountEquals(3)
    }

    @Test
    fun `tapping a card body opens the group and starts no drag`() {
        val reorders = mutableListOf<Pair<String, Int>>()
        val opened = mutableListOf<String>()
        showGrid(listOf("Cities", "Work", "Home"), reorders = reorders, opened = opened)

        composeRule.onNodeWithText("Work").performClick()

        assertEquals(listOf("Work"), opened)
        assertTrue(reorders.isEmpty())
    }

    @Test
    fun `the card menu still opens and runs its actions`() {
        val reorders = mutableListOf<Pair<String, Int>>()
        val colorsSet = mutableListOf<String>()
        showGrid(listOf("Cities", "Work"), reorders = reorders, colorsSet = colorsSet)

        composeRule.onAllNodesWithContentDescription(groupOptionsLabel)[1].performClick()
        composeRule.onNodeWithText(setColorLabel).assertIsDisplayed()
        composeRule.onNodeWithText(setColorLabel).performClick()

        assertEquals(listOf("Work"), colorsSet)
        assertTrue(reorders.isEmpty())
    }
}
