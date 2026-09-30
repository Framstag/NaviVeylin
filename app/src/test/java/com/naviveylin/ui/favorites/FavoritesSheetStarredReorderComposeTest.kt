package com.naviveylin.ui.favorites

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.R
import com.naviveylin.data.FavoriteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose tests for the reorderable starred chip bar (spec `fav-starred-chip-bar` —
 * one flat sequence in the stored starred order, long-press drag, tap and swipe
 * preserved; spec `starred-ordering` — the order itself).
 *
 * The bar's order and contents arrive as parameters, so most cases drive
 * [StarredChipBar] directly and record the commits it asks for — the same shape as
 * `FavoritesSheetGroupReorderComposeTest`. The sheet-level cases at the end cover the
 * real wiring (view model + repository + persist).
 *
 * Runs under the default Robolectric sandbox: the sheet-backed cases use
 * `FakeOSMScoutClient`, so the class must not set `@Config`/`@GraphicsMode`
 * (guidelines/Design.md §11).
 */
@RunWith(RobolectricTestRunner::class)
class FavoritesSheetStarredReorderComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun fav(name: String) = FavoriteLocation(name, 1.0, 2.0)

    private fun stars(vararg entries: Pair<String, String>): List<Pair<String, FavoriteLocation>> =
        entries.map { it.first to fav(it.second) }

    /**
     * Long-press drag from [start] by [dx] pixels horizontally, then release: one chip
     * width moves a chip one position in the bar.
     *
     * The drag stops about one position short of the bar's edge on purpose — a drag
     * parked at the edge leaves the library's auto-scroll running and perturbs whatever
     * runs next in the same JVM (ki_processing_failures.log, 2026-09-28).
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

    /** Horizontal distance between two chips, in pixels. */
    private fun chipDistance(fromName: String, toName: String): Float {
        val from = composeRule.onNodeWithText(fromName, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val to = composeRule.onNodeWithText(toName, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        return to.left - from.left
    }

    private fun chipLeft(name: String): Float =
        composeRule.onNodeWithText(name, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left

    private fun showBar(
        order: List<Pair<String, FavoriteLocation>>,
        reorders: MutableList<Triple<String, String, Int>> = mutableListOf(),
        routed: MutableList<String> = mutableListOf()
    ) {
        composeRule.setContent {
            StarredChipBar(
                starredFavorites = order,
                onChipClick = { _, fav -> routed.add(fav.name) },
                onReorder = { groupName, favName, newIndex ->
                    reorders.add(Triple(groupName, favName, newIndex))
                }
            )
        }
    }

    // --- Order the bar renders ---

    @Test
    fun `chips render in the given order across groups`() {
        showBar(stars("Cities" to "Berlin", "Work" to "Office", "Cities" to "Rome"))

        assertTrue(chipLeft("Berlin") < chipLeft("Office"))
        assertTrue(chipLeft("Office") < chipLeft("Rome"))
    }

    @Test
    fun `each chip carries its own group as the secondary line`() {
        showBar(stars("Cities" to "Home", "Work" to "Home"))

        // Same favorite name in two groups: two chips, each with its own group line.
        composeRule.onAllNodesWithText("Home", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onAllNodesWithText("Cities", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("Work", useUnmergedTree = true).assertCountEquals(1)
    }

    // --- Reorder interaction ---

    @Test
    fun `dragging a chip one position commits that position once`() {
        val reorders = mutableListOf<Triple<String, String, Int>>()
        val routed = mutableListOf<String>()
        showBar(
            stars("Cities" to "Berlin", "Cities" to "Rome", "Work" to "Office"),
            reorders = reorders,
            routed = routed
        )

        dragBy(
            composeRule.onNodeWithText("Berlin", useUnmergedTree = true),
            chipDistance("Berlin", "Rome")
        )

        assertEquals(listOf(Triple("Cities", "Berlin", 1)), reorders)
        // A drag is not a tap either.
        assertTrue(routed.isEmpty())

        // The suppression belongs to the gesture: a genuine tap after it still routes.
        composeRule.onNodeWithText("Berlin", useUnmergedTree = true).performClick()
        assertEquals(listOf("Berlin"), routed)
    }

    @Test
    fun `dragging a chip to the front across a group boundary commits position zero`() {
        val reorders = mutableListOf<Triple<String, String, Int>>()
        showBar(
            stars("Cities" to "Berlin", "Cities" to "Rome", "Work" to "Office"),
            reorders = reorders
        )

        // Leftward: the chip travels from the third position to the first.
        dragBy(
            composeRule.onNodeWithText("Office", useUnmergedTree = true),
            chipLeft("Berlin") - chipLeft("Office")
        )

        assertEquals(listOf(Triple("Work", "Office", 0)), reorders)
    }

    @Test
    fun `a drag that ends where it started commits nothing`() {
        val reorders = mutableListOf<Triple<String, String, Int>>()
        val routed = mutableListOf<String>()
        showBar(stars("Cities" to "Berlin", "Cities" to "Rome"), reorders = reorders, routed = routed)

        dragBy(composeRule.onNodeWithText("Berlin", useUnmergedTree = true), 0f)

        assertTrue(reorders.isEmpty())
        // A long press is the drag gesture, not a tap: releasing without moving must
        // not also open the route panel.
        assertTrue("a held chip must not route on release", routed.isEmpty())
        assertTrue(chipLeft("Berlin") < chipLeft("Rome"))

        // ... and the suppression must not outlive that gesture.
        composeRule.onNodeWithText("Berlin", useUnmergedTree = true).performClick()
        assertEquals(listOf("Berlin"), routed)
        assertTrue(reorders.isEmpty())
    }

    @Test
    fun `a single chip cannot be reordered`() {
        val reorders = mutableListOf<Triple<String, String, Int>>()
        showBar(stars("Cities" to "Berlin"), reorders = reorders)

        dragBy(composeRule.onNodeWithText("Berlin", useUnmergedTree = true), 200f)

        assertTrue(reorders.isEmpty())
    }

    @Test
    fun `a bar dismissed during a drag commits nothing`() {
        val reorders = mutableListOf<Triple<String, String, Int>>()
        val barVisible = mutableStateOf(true)
        val order = stars("Cities" to "Berlin", "Cities" to "Rome")
        composeRule.setContent {
            if (barVisible.value) {
                StarredChipBar(
                    starredFavorites = order,
                    onChipClick = { _, _ -> },
                    onReorder = { groupName, favName, newIndex ->
                        reorders.add(Triple(groupName, favName, newIndex))
                    }
                )
            }
        }

        val dx = chipDistance("Berlin", "Rome")
        composeRule.onNodeWithText("Berlin", useUnmergedTree = true).performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(dx, 0f))
            advanceEventTime(20)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { barVisible.value = false }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()

        assertTrue(reorders.isEmpty())
    }

    // --- Tap and swipe keep working ---

    @Test
    fun `tapping a chip reports the route destination and commits nothing`() {
        val reorders = mutableListOf<Triple<String, String, Int>>()
        val routed = mutableListOf<String>()
        showBar(stars("Cities" to "Berlin", "Cities" to "Rome"), reorders = reorders, routed = routed)

        composeRule.onNodeWithText("Berlin", useUnmergedTree = true).performClick()

        assertEquals(listOf("Berlin"), routed)
        assertTrue(reorders.isEmpty())
    }

    // --- Sheet level: the real wiring (view model + repository + persist) ---

    /**
     * A repository whose persist resolves on the calling thread, so the state change
     * lands inside the same Compose idling pass (see
     * `FavoritesSheetReorderComposeTest.repositoryWithInlinePersist`).
     */
    private fun repositoryWithInlinePersist(client: FakeOSMScoutClient): FavoriteRepository =
        FavoriteRepository(client).apply { defaultDispatcher = Dispatchers.Unconfined }

    private fun newSheetWithStars(): Pair<FavoritesViewModel, FakeOSMScoutClient> {
        val client = FakeOSMScoutClient()
        val repository = repositoryWithInlinePersist(client)
        runBlocking {
            repository.init("/tmp/fav-starred-reorder-test.json")
            repository.addGroup("Cities")
            repository.addGroup("Work")
            repository.addFavorite("Cities", "Berlin", 52.5, 13.4)
            repository.addFavorite("Cities", "Rome", 41.9, 12.5)
            repository.addFavorite("Work", "Office", 51.5, 7.4)
            repository.setFavoriteStarred("Cities", "Berlin", true)
            repository.setFavoriteStarred("Cities", "Rome", true)
            repository.setFavoriteStarred("Work", "Office", true)
        }
        return FavoritesViewModel(repository, context) to client
    }

    private fun awaitCondition(condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 30_000) { condition() }
    }

    @Test
    fun `sheet chip drag persists the new starred order exactly once`() {
        val (viewModel, client) = newSheetWithStars()
        composeRule.setContent {
            FavoritesSheet(
                mapCenterLat = 0.0,
                mapCenterLon = 0.0,
                onDismiss = {},
                viewModel = viewModel
            )
        }
        composeRule.waitForIdle()

        val savesBefore = client.saveFavoriteLocationsCalls.get()
        dragBy(
            composeRule.onNodeWithText("Office", useUnmergedTree = true),
            chipLeft("Berlin") - chipLeft("Office")
        )

        awaitCondition {
            viewModel.uiState.value.starredFavorites.map { it.second.name } ==
                listOf("Office", "Berlin", "Rome")
        }
        awaitCondition { client.saveFavoriteLocationsCalls.get() == savesBefore + 1 }

        assertEquals(
            listOf("Office", "Berlin", "Rome"),
            viewModel.uiState.value.starredFavorites.map { it.second.name }
        )
        assertEquals(1, client.moveStarredFavoriteCalls.size)
        assertEquals(savesBefore + 1, client.saveFavoriteLocationsCalls.get())
    }

    @Test
    fun `sheet dismissed mid chip drag commits nothing`() {
        val (viewModel, client) = newSheetWithStars()
        val sheetVisible = mutableStateOf(true)
        composeRule.setContent {
            if (sheetVisible.value) {
                FavoritesSheet(
                    mapCenterLat = 0.0,
                    mapCenterLon = 0.0,
                    onDismiss = {},
                    viewModel = viewModel
                )
            }
        }
        composeRule.waitForIdle()

        val savesBefore = client.saveFavoriteLocationsCalls.get()
        val dx = chipDistance("Berlin", "Office")
        composeRule.onNodeWithText("Berlin", useUnmergedTree = true).performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(dx, 0f))
            advanceEventTime(20)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { sheetVisible.value = false }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()

        assertTrue(client.moveStarredFavoriteCalls.isEmpty())
        assertEquals(savesBefore, client.saveFavoriteLocationsCalls.get())
        assertEquals(
            listOf("Berlin", "Rome", "Office"),
            viewModel.uiState.value.starredFavorites.map { it.second.name }
        )
    }
}
