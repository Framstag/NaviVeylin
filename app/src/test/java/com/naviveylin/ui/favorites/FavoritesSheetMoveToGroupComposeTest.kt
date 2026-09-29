package com.naviveylin.ui.favorites

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
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
 * Compose tests for the "Move to group" action on a favorite row and its
 * destination dialog (spec `fav-management-ui`, spec `fav-service`).
 *
 * Runs under the default Robolectric sandbox because the sheet is backed by
 * `FakeOSMScoutClient`, whose constructor loads the host JNI stub
 * (guidelines/Design.md §11 — never mix sandbox configs on such tests).
 */
@RunWith(RobolectricTestRunner::class)
class FavoritesSheetMoveToGroupComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val moveLabel: String get() = context.getString(R.string.move_favorite_to_group)
    private val optionsLabel: String get() = context.getString(R.string.favorite_options)
    private val okLabel: String get() = context.getString(R.string.ok)
    private val cancelLabel: String get() = context.getString(R.string.cancel)
    private val newGroupLabel: String get() = context.getString(R.string.new_group_title)
    private val newGroupNameLabel: String get() = context.getString(R.string.new_group_name)

    /**
     * Sheet backed by the native-less fake client. The repository persists through
     * `Dispatchers.Default` by default; pinning it keeps the awaited state changes
     * inside the same Compose idling pass (same reason as the reorder sheet test).
     */
    private fun sheetViewModel(
        build: suspend FavoriteRepository.() -> Unit
    ): Pair<FavoritesViewModel, FakeOSMScoutClient> {
        val client = FakeOSMScoutClient()
        val repository = FavoriteRepository(client).apply { defaultDispatcher = Dispatchers.Unconfined }
        runBlocking {
            repository.init("/tmp/fav-move-group-test.json")
            repository.build()
        }
        return FavoritesViewModel(repository, context) to client
    }

    /** Shows the sheet on the group detail view of [groupName]. */
    private fun showGroupDetail(viewModel: FavoritesViewModel, groupName: String) {
        composeRule.setContent {
            FavoritesSheet(
                mapCenterLat = 0.0,
                mapCenterLon = 0.0,
                onDismiss = {},
                viewModel = viewModel
            )
        }
        composeRule.runOnIdle { viewModel.selectGroup(groupName) }
        composeRule.waitForIdle()
    }

    /** Opens the destination dialog for the first favorite row of the detail list. */
    private fun openMoveDialog() {
        composeRule.onAllNodesWithContentDescription(optionsLabel)[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(moveLabel).performClick()
        composeRule.waitForIdle()
    }

    private fun awaitCondition(condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 30_000) { condition() }
    }

    // --- The action itself ---

    @Test
    fun `row offers the move action when another group exists`() {
        val (viewModel, _) = sheetViewModel {
            addGroup("Cities")
            addGroup("Work")
            addFavorite("Cities", "Berlin", 52.5, 13.4)
        }
        showGroupDetail(viewModel, "Cities")

        composeRule.onAllNodesWithContentDescription(optionsLabel).assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription(optionsLabel)[0].performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(moveLabel).assertIsDisplayed()
    }

    @Test
    fun `the action is absent with a single group`() {
        val (viewModel, _) = sheetViewModel {
            addGroup("Cities")
            addFavorite("Cities", "Berlin", 52.5, 13.4)
        }
        showGroupDetail(viewModel, "Cities")

        composeRule.onAllNodesWithContentDescription(optionsLabel).assertCountEquals(0)
    }

    // --- The destination dialog ---

    @Test
    fun `the dialog lists the other groups but not the current one`() {
        val (viewModel, _) = sheetViewModel {
            addGroup("Cities")
            addGroup("Work")
            addFavorite("Cities", "Berlin", 52.5, 13.4)
        }
        showGroupDetail(viewModel, "Cities")

        openMoveDialog()

        composeRule.onNodeWithText("Work").assertIsDisplayed()
        composeRule.onNodeWithText(newGroupLabel).assertIsDisplayed()
        // "Cities" is still the detail title — the dialog must not offer it too.
        composeRule.onAllNodesWithText("Cities").assertCountEquals(1)
    }

    @Test
    fun `confirming moves the favorite into the chosen group`() {
        val (viewModel, client) = sheetViewModel {
            addGroup("Cities")
            addGroup("Work")
            addFavorite("Cities", "Berlin", 52.5, 13.4)
            addFavorite("Cities", "Rome", 41.9, 12.5)
            addFavorite("Work", "Office", 51.5, 7.4)
        }
        showGroupDetail(viewModel, "Cities")

        openMoveDialog()
        composeRule.onNodeWithText("Work").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(okLabel).performClick()

        awaitCondition {
            viewModel.uiState.value.groups["Work"]?.map { it.name } == listOf("Office", "Berlin")
        }
        assertEquals(listOf("Rome"), viewModel.uiState.value.groups["Cities"]?.map { it.name })
        assertEquals(1, client.moveFavoriteToGroupCalls.size)
        // The favorited coordinates travel with the favorite.
        assertEquals(
            52.5,
            viewModel.uiState.value.groups["Work"]?.last()?.lat ?: 0.0,
            1e-9
        )
    }

    @Test
    fun `dismissing the dialog changes nothing`() {
        val (viewModel, client) = sheetViewModel {
            addGroup("Cities")
            addGroup("Work")
            addFavorite("Cities", "Berlin", 52.5, 13.4)
        }
        showGroupDetail(viewModel, "Cities")

        openMoveDialog()
        composeRule.onNodeWithText("Work").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(cancelLabel).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("Berlin"), viewModel.uiState.value.groups["Cities"]?.map { it.name })
        assertTrue(viewModel.uiState.value.groups["Work"]?.isEmpty() == true)
        assertTrue(viewModel.uiState.value.groups["Cities"]?.isNotEmpty() == true)
    }

    @Test
    fun `the new group option creates the group and moves the favorite into it`() {
        val (viewModel, _) = sheetViewModel {
            addGroup("Cities")
            addGroup("Work")
            addFavorite("Cities", "Berlin", 52.5, 13.4)
        }
        showGroupDetail(viewModel, "Cities")

        openMoveDialog()
        composeRule.onNodeWithText(newGroupLabel).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("Fresh")
        composeRule.waitForIdle()
        composeRule.onNodeWithText(okLabel).performClick()

        awaitCondition {
            viewModel.uiState.value.groups["Fresh"]?.map { it.name } == listOf("Berlin")
        }
        assertTrue(viewModel.uiState.value.groups["Cities"]?.isEmpty() == true)
    }

    @Test
    fun `a refused name collision is reported and leaves the favorite in place`() {
        val (viewModel, _) = sheetViewModel {
            addGroup("Cities")
            addGroup("Work")
            addFavorite("Cities", "Office", 52.5, 13.4)
            addFavorite("Work", "Office", 51.5, 7.4)
        }
        showGroupDetail(viewModel, "Cities")

        openMoveDialog()
        composeRule.onNodeWithText("Work", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(okLabel).performClick()

        val conflict = context.getString(R.string.favorite_move_name_conflict, "Work", "Office")
        awaitCondition {
            composeRule.onAllNodesWithText(conflict).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf("Office"), viewModel.uiState.value.groups["Cities"]?.map { it.name })
        assertEquals(listOf("Office"), viewModel.uiState.value.groups["Work"]?.map { it.name })
    }

    @Test
    fun `the new group name field is offered only with the new group selected`() {
        val (viewModel, _) = sheetViewModel {
            addGroup("Cities")
            addGroup("Work")
            addFavorite("Cities", "Berlin", 52.5, 13.4)
        }
        showGroupDetail(viewModel, "Cities")

        openMoveDialog()

        // A destination group is preselected, so no name field is shown yet.
        composeRule.onAllNodesWithText(newGroupNameLabel).assertCountEquals(0)

        composeRule.onNodeWithText(newGroupLabel).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(newGroupNameLabel).assertIsDisplayed()
    }
}
