package com.naviveylin.ui.mapmanager

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.naviveylin.core.mapsource.MapUpdateItem
import com.naviveylin.core.mapsource.MapUpdateKind
import com.naviveylin.core.mapsource.UpdateState
import com.naviveylin.data.MapCheckReport
import com.naviveylin.data.MapUpdatesState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the updates section itself: what it states when there is nothing to update, what it
 * lists when there is, and which action each control dispatches.
 *
 * Whether the section is *present* at all is the map manager screen's rule (the active source), so that half
 * is asserted in `MapManagerScreenOrderingTest`, which renders the screen.
 *
 * Spec: map-updates — "The updates section belongs to the repository source" / "The updates section lists
 * what can be updated" / "The user can run the check on demand".
 */
@RunWith(RobolectricTestRunner::class)
class MapUpdatesSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val berlin = MapUpdateItem(
        kind = MapUpdateKind.REGION,
        directoryName = "europe-germany-berlin",
        displayName = "Berlin",
        idPath = listOf("europe", "germany", "berlin"),
        state = UpdateState.UpdateAvailable(48L * 1024L * 1024L)
    )
    private val basemap = MapUpdateItem(
        kind = MapUpdateKind.BASEMAP,
        directoryName = "basemap",
        displayName = null,
        idPath = null,
        state = UpdateState.UpdateAvailable(1024L * 1024L * 1024L)
    )

    @Test
    fun noCheckHasRunYetIsStated() {
        show(MapUpdatesState(report = MapCheckReport.NeverChecked))

        composeRule.onNodeWithTag("map-updates-section").assertIsDisplayed()
        composeRule.onNodeWithTag("map-updates-status").assertIsDisplayed()
        composeRule.onNodeWithText("No update check has run yet").assertIsDisplayed()
        composeRule.onNodeWithTag("check-for-updates").assertIsDisplayed()
    }

    @Test
    fun nothingToUpdateIsStatedNotLeftBlank() {
        show(
            MapUpdatesState(
                report = MapCheckReport.Completed(checkedAtEpochMs = 0L, updates = emptyList())
            )
        )

        composeRule.onNodeWithText("No updates available", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("map-updates-count").assertDoesNotExist()
        composeRule.onNodeWithTag("update-all").assertDoesNotExist()
    }

    @Test
    fun everyUpdatableItemIsListed() {
        show(
            MapUpdatesState(
                report = MapCheckReport.Completed(checkedAtEpochMs = 0L, updates = listOf(berlin, basemap))
            )
        )

        composeRule.onNodeWithText("2 updates available").assertIsDisplayed()
        composeRule.onNodeWithTag("update-row-europe-germany-berlin").assertIsDisplayed()
        composeRule.onNodeWithTag("update-row-basemap").assertIsDisplayed()
        composeRule.onNodeWithText("Berlin").assertIsDisplayed()
        composeRule.onNodeWithText("World Basemap · 1024.0 MB").assertIsDisplayed()
        composeRule.onNodeWithTag("update-all").assertIsDisplayed()
    }

    @Test
    fun theRowActionsDispatchTheirOwnItem() {
        val updated = mutableListOf<MapUpdateItem>()
        var updateAllCalls = 0
        var checkCalls = 0
        show(
            MapUpdatesState(
                report = MapCheckReport.Completed(checkedAtEpochMs = 0L, updates = listOf(berlin, basemap))
            ),
            onUpdate = { updated += it },
            onUpdateAll = { updateAllCalls++ },
            onCheck = { checkCalls++ }
        )

        composeRule.onNodeWithTag("update-europe-germany-berlin").performClick()
        composeRule.onNodeWithTag("update-all").performClick()
        composeRule.onNodeWithTag("check-for-updates").performClick()

        assertEquals(listOf(berlin), updated)
        assertEquals(1, updateAllCalls)
        assertEquals(1, checkCalls)
    }

    @Test
    fun aRunningTransferIsStatedAndCancellable() {
        val cancelled = mutableListOf<String>()
        show(
            MapUpdatesState(
                report = MapCheckReport.Completed(checkedAtEpochMs = 0L, updates = listOf(berlin))
            ),
            transfers = mapOf(berlin.directoryName to UpdateTransferState.Transferring(42)),
            onCancel = { cancelled += it }
        )

        composeRule.onNodeWithText("Updating…").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel-europe-germany-berlin").assertIsDisplayed()
        composeRule.onNodeWithTag("update-europe-germany-berlin").assertDoesNotExist()

        composeRule.onNodeWithTag("cancel-europe-germany-berlin").performClick()
        assertEquals(listOf(berlin.directoryName), cancelled)
    }

    @Test
    fun aFailedTransferIsStatedAndDismissed() {
        val dismissed = mutableListOf<String>()
        show(
            MapUpdatesState(
                report = MapCheckReport.Completed(checkedAtEpochMs = 0L, updates = listOf(berlin))
            ),
            transfers = mapOf(
                berlin.directoryName to UpdateTransferState.Failed(
                    "The update could not be installed — the map you had is unchanged"
                )
            ),
            onDismissError = { dismissed += it }
        )

        composeRule.onNodeWithText("The update could not be installed — the map you had is unchanged")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("ok-europe-germany-berlin").performClick()

        assertEquals(listOf(berlin.directoryName), dismissed)
    }

    @Test
    fun aCheckThatDidNotCompleteIsStated() {
        show(
            MapUpdatesState(
                report = MapCheckReport.Completed(checkedAtEpochMs = 0L, updates = listOf(berlin)),
                lastAttemptFailure = com.naviveylin.core.mapsource.RepositoryFailure.TransportFailed("offline")
            )
        )

        composeRule.onNodeWithTag("map-updates-check-failed").assertIsDisplayed()
        composeRule.onNodeWithTag("update-row-europe-germany-berlin").assertIsDisplayed()
    }

    private fun show(
        state: MapUpdatesState,
        transfers: Map<String, UpdateTransferState> = emptyMap(),
        isUpdating: Boolean = false,
        onCheck: () -> Unit = {},
        onUpdate: (MapUpdateItem) -> Unit = {},
        onUpdateAll: () -> Unit = {},
        onCancel: (String) -> Unit = {},
        onDismissError: (String) -> Unit = {}
    ) {
        composeRule.setContent {
            androidx.compose.material3.MaterialTheme {
                MapUpdatesSection(
                    state = state,
                    transfers = transfers,
                    isUpdating = isUpdating,
                    onCheck = onCheck,
                    onUpdate = onUpdate,
                    onUpdateAll = onUpdateAll,
                    onCancel = onCancel,
                    onDismissError = onDismissError
                )
            }
        }
    }
}
