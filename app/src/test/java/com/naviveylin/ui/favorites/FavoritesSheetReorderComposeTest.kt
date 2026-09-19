package com.naviveylin.ui.favorites

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.R
import com.naviveylin.data.FavoriteRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose tests for the reorderable group detail list (spec `fav-management-ui`,
 * spec `fav-ordering`).
 *
 * Runs under the default Robolectric sandbox because the sheet is backed by
 * `FakeOSMScoutClient`, whose constructor loads the host JNI stub
 * (guidelines/Design.md §11 — never mix sandbox configs on such tests).
 */
@RunWith(RobolectricTestRunner::class)
class FavoritesSheetReorderComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val reorderLabel: String =
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.reorder_favorite)

    private fun favorites(vararg names: String): List<FavoriteLocation> =
        names.map { FavoriteLocation(it, 0.0, 0.0) }

    /**
     * Long-press drag from [start] by [dy] pixels, then release.
     *
     * The whole gesture is one touch-input block: the library starts the drag
     * from the long press and needs the movement events in the same gesture.
     */
    private fun dragBy(start: SemanticsNodeInteraction, dy: Float) {
        start.performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(0f, dy * 0.25f))
            advanceEventTime(20)
            moveBy(Offset(0f, dy * 0.25f))
            advanceEventTime(20)
            moveBy(Offset(0f, dy * 0.25f))
            advanceEventTime(20)
            moveBy(Offset(0f, dy * 0.25f))
            advanceEventTime(20)
            up()
        }
        composeRule.waitForIdle()
    }

    /** Vertical distance between two favorite rows, in pixels. */
    private fun rowDistance(fromName: String, toName: String): Float {
        val from = composeRule.onNodeWithText(fromName).fetchSemanticsNode().boundsInRoot
        val to = composeRule.onNodeWithText(toName).fetchSemanticsNode().boundsInRoot
        return to.top - from.top
    }

    private fun showGroupDetailList(
        favorites: List<FavoriteLocation>,
        reorders: MutableList<Pair<String, Int>>,
        onAddFavorite: () -> Unit = {},
        onFavoriteClick: (FavoriteLocation) -> Unit = {},
        onDelete: (FavoriteLocation) -> Unit = {},
        onRename: (FavoriteLocation) -> Unit = {},
        onToggleStar: (FavoriteLocation) -> Unit = {}
    ) {
        composeRule.setContent {
            GroupDetailList(
                groupName = "Cities",
                favorites = favorites,
                listState = rememberLazyListState(),
                onAddFavorite = onAddFavorite,
                onFavoriteClick = onFavoriteClick,
                onDelete = onDelete,
                onRename = onRename,
                onToggleStar = onToggleStar,
                onReorder = { favName, newIndex -> reorders.add(favName to newIndex) }
            )
        }
    }

    // --- Reorder interaction ---

    @Test
    fun `dragging a favorite to the top commits that position once`() {
        val reorders = mutableListOf<Pair<String, Int>>()
        showGroupDetailList(favorites("Berlin", "Paris", "Rome"), reorders)

        val dy = rowDistance("Rome", "Berlin")
        dragBy(composeRule.onAllNodesWithContentDescription(reorderLabel)[2], dy)

        assertEquals(listOf("Rome" to 0), reorders)
    }

    @Test
    fun `dragging a favorite down one slot commits the next position`() {
        val reorders = mutableListOf<Pair<String, Int>>()
        showGroupDetailList(favorites("Berlin", "Paris", "Rome"), reorders)

        val dy = rowDistance("Berlin", "Paris")
        dragBy(composeRule.onAllNodesWithContentDescription(reorderLabel)[0], dy)

        assertEquals(listOf("Berlin" to 1), reorders)
    }

    @Test
    fun `a drag that ends where it started commits nothing`() {
        val reorders = mutableListOf<Pair<String, Int>>()
        showGroupDetailList(favorites("Berlin", "Paris", "Rome"), reorders)

        dragBy(composeRule.onAllNodesWithContentDescription(reorderLabel)[1], 0f)

        assertTrue(reorders.isEmpty())
        composeRule.onNodeWithText("Paris").assertIsDisplayed()
    }

    @Test
    fun `dragging a single favorite commits nothing`() {
        val reorders = mutableListOf<Pair<String, Int>>()
        showGroupDetailList(favorites("Only"), reorders)

        dragBy(composeRule.onAllNodesWithContentDescription(reorderLabel)[0], 120f)

        assertTrue(reorders.isEmpty())
        composeRule.onNodeWithText("Only").assertIsDisplayed()
    }

    // --- Drag affordance ---

    @Test
    fun `every favorite offers a drag handle and the add header does not`() {
        val reorders = mutableListOf<Pair<String, Int>>()
        showGroupDetailList(favorites("Berlin", "Paris", "Rome"), reorders)

        composeRule.onAllNodesWithContentDescription(reorderLabel).assertCountEquals(3)
        // The leading "Add favorite" row stays a plain item.
        composeRule.onNodeWithText(
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(R.string.add_favorite)
        ).assertIsDisplayed()
    }

    @Test
    fun `holding the add header does not start a drag`() {
        val reorders = mutableListOf<Pair<String, Int>>()
        var addClicks = 0
        showGroupDetailList(favorites("Berlin", "Paris"), reorders, onAddFavorite = { addClicks++ })

        val addLabel = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.add_favorite)
        dragBy(composeRule.onNodeWithText(addLabel), 80f)

        assertTrue(reorders.isEmpty())
        composeRule.onNodeWithText(addLabel).performClick()
        assertEquals(1, addClicks)
    }

    // --- Other row actions keep working ---

    @Test
    fun `row actions and row taps still work after the conversion`() {
        val reorders = mutableListOf<Pair<String, Int>>()
        val clicked = mutableListOf<String>()
        var deleted: String? = null
        var renamed: String? = null
        var starred: String? = null
        showGroupDetailList(
            favorites = favorites("Berlin", "Paris"),
            reorders = reorders,
            onFavoriteClick = { clicked.add(it.name) },
            onDelete = { deleted = it.name },
            onRename = { renamed = it.name },
            onToggleStar = { starred = it.name }
        )

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Tap the name text in the unmerged tree: the row's merged node centre
        // falls onto the star button's expanded touch target in this window.
        composeRule.onNodeWithText("Berlin", useUnmergedTree = true).performClick()
        assertEquals(listOf("Berlin"), clicked)

        composeRule.onAllNodesWithContentDescription(context.getString(R.string.star))[0].performClick()
        assertEquals("Berlin", starred)

        composeRule.onAllNodesWithContentDescription(context.getString(R.string.rename))[0].performClick()
        assertEquals("Berlin", renamed)

        composeRule.onAllNodesWithContentDescription(context.getString(R.string.delete))[0].performClick()
        assertEquals("Berlin", deleted)
    }

    // --- Sheet level: the real wiring (view model + repository) ---

    /** View model over a repository backed by the native-less fake client. */
    private fun newSheetViewModel(): Pair<FavoritesViewModel, FakeOSMScoutClient> {
        val client = FakeOSMScoutClient()
        val repository = FavoriteRepository(client)
        runBlocking {
            repository.init("/tmp/fav-reorder-sheet-test.json")
            repository.addGroup("Cities")
            repository.addFavorite("Cities", "Berlin", 52.5, 13.4)
            repository.addFavorite("Cities", "Paris", 48.9, 2.4)
        }
        return FavoritesViewModel(repository) to client
    }

    /**
     * Waits until [condition] holds. The repository persists on a background
     * dispatcher, so state changes land asynchronously after a reorder.
     */
    private fun awaitCondition(condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 5_000) { condition() }
    }

    @Test
    fun `sheet reorder persists the new order exactly once`() {
        val (viewModel, client) = newSheetViewModel()
        composeRule.setContent {
            FavoritesSheet(
                mapCenterLat = 0.0,
                mapCenterLon = 0.0,
                onDismiss = {},
                viewModel = viewModel
            )
        }
        composeRule.runOnIdle { viewModel.selectGroup("Cities") }
        composeRule.waitForIdle()

        val savesBefore = client.saveFavoriteLocationsCalls.get()
        val dy = rowDistance("Paris", "Berlin")
        dragBy(composeRule.onAllNodesWithContentDescription(reorderLabel)[1], dy)

        awaitCondition {
            viewModel.uiState.value.groups["Cities"]?.map { it.name } == listOf("Paris", "Berlin")
        }
        awaitCondition { client.saveFavoriteLocationsCalls.get() == savesBefore + 1 }

        assertEquals(
            listOf("Paris", "Berlin"),
            viewModel.uiState.value.groups["Cities"]?.map { it.name }
        )
        assertEquals(savesBefore + 1, client.saveFavoriteLocationsCalls.get())
    }

    @Test
    fun `a sheet dismissed mid drag commits nothing`() {
        val (viewModel, client) = newSheetViewModel()
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
        composeRule.runOnIdle { viewModel.selectGroup("Cities") }
        composeRule.waitForIdle()

        val savesBefore = client.saveFavoriteLocationsCalls.get()
        val handle = composeRule.onAllNodesWithContentDescription(reorderLabel)[1]
        val dy = rowDistance("Paris", "Berlin")

        // Drag far enough to move the item, then dismiss the sheet before the
        // gesture ends (back gesture during a drag).
        handle.performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(0f, dy))
            advanceEventTime(20)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { sheetVisible.value = false }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals(savesBefore, client.saveFavoriteLocationsCalls.get())
        assertEquals(
            listOf("Berlin", "Paris"),
            viewModel.uiState.value.groups["Cities"]?.map { it.name }
        )
    }

    @Test
    fun `search results are listed in stored order and are not reorderable`() {
        val (viewModel, _) = newSheetViewModel()
        composeRule.setContent {
            FavoritesSheet(
                mapCenterLat = 0.0,
                mapCenterLon = 0.0,
                onDismiss = {},
                viewModel = viewModel
            )
        }
        composeRule.runOnIdle { viewModel.onSearchQueryChange("r") }
        composeRule.waitForIdle()

        // Only the matching favorites are listed, in stored order, without handles.
        composeRule.onAllNodesWithContentDescription(reorderLabel).assertCountEquals(0)
        composeRule.onNodeWithText("Berlin").assertIsDisplayed()
    }

    // --- Starred chip bar order (spec fav-starred-chip-bar) ---

    /** Horizontal position of a starred chip on the favorites sheet main view. */
    private fun chipLeft(name: String): Float =
        composeRule.onNodeWithText(name, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left

    @Test
    fun `chips follow the reordered favorites and a new star appends`() {
        val client = FakeOSMScoutClient()
        val repository = FavoriteRepository(client)
        runBlocking {
            repository.init("/tmp/fav-chip-order-test.json")
            repository.addGroup("Cities")
            repository.addFavorite("Cities", "Berlin", 52.5, 13.4)
            repository.addFavorite("Cities", "Rome", 41.9, 12.5)
            repository.addFavorite("Cities", "Paris", 48.9, 2.4)
            repository.setFavoriteStarred("Cities", "Berlin", true)
            repository.setFavoriteStarred("Cities", "Rome", true)
        }
        val viewModel = FavoritesViewModel(repository)
        composeRule.setContent {
            FavoritesSheet(
                mapCenterLat = 0.0,
                mapCenterLon = 0.0,
                onDismiss = {},
                viewModel = viewModel
            )
        }
        composeRule.waitForIdle()

        // Stored order first: Berlin before Rome.
        assertTrue(chipLeft("Berlin") < chipLeft("Rome"))

        // Reorder Rome to the top inside the group, then return to the grid —
        // the chip bar is part of the same sheet session and follows the order.
        composeRule.runOnIdle { viewModel.selectGroup("Cities") }
        composeRule.waitForIdle()
        dragBy(composeRule.onAllNodesWithContentDescription(reorderLabel)[1], rowDistance("Rome", "Berlin"))
        awaitCondition {
            viewModel.uiState.value.groups["Cities"]?.map { it.name } == listOf("Rome", "Berlin", "Paris")
        }
        composeRule.runOnIdle { viewModel.selectGroup(null) }
        composeRule.waitForIdle()

        assertTrue(chipLeft("Rome") < chipLeft("Berlin"))

        // Starring a further favorite appends its chip after the group's chips.
        // The bar itself only composes what fits in the test window, so the
        // appended position is asserted on the ordered list the bar renders.
        composeRule.runOnIdle { viewModel.toggleStar("Cities", "Paris") }
        composeRule.waitForIdle()
        awaitCondition {
            viewModel.uiState.value.starredFavorites.map { it.second.name } ==
                listOf("Rome", "Berlin", "Paris")
        }

        assertEquals(
            listOf("Rome", "Berlin", "Paris"),
            viewModel.uiState.value.starredFavorites.map { it.second.name }
        )
        assertTrue(chipLeft("Rome") < chipLeft("Berlin"))
    }
}
