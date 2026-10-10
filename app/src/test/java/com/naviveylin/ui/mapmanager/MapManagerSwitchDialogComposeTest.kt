package com.naviveylin.ui.mapmanager

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.naviveylin.data.MapSourceSwitchPlan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose tests for the source-switch confirmation dialog.
 *
 * Spec: map-download-ui — "Switching source asks before deleting"; map-source-selection — "Confirmed
 * switch deletes the other source's data" / "Cancelled switch changes nothing".
 */
@RunWith(RobolectricTestRunner::class)
class MapManagerSwitchDialogComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dialogNamesCountAndSize() {
        show(planOf(mapCount = 3, mapBytes = 2L * 1024 * 1024, basemapBytes = 1024 * 1024))

        composeRule.onNodeWithTag("source-switch-dialog").assertIsDisplayed()
        composeRule.onNodeWithText(
            "The newly selected source cannot use the data of the current one. " +
                "3 map(s) and 3.0 MB will be deleted."
        ).assertIsDisplayed()
    }

    @Test
    fun cancelKeepsTheActiveSource() {
        var confirmed = false
        var dismissed = false
        composeRule.setContent {
            SourceSwitchConfirmationDialog(
                plan = planOf(mapCount = 1, mapBytes = 1024, basemapBytes = 0),
                onConfirm = { confirmed = true },
                onDismiss = { dismissed = true }
            )
        }

        composeRule.onNodeWithText("Cancel").performClick()

        assertTrue("the dialog closes", dismissed)
        assertFalse("nothing is deleted by cancelling", confirmed)
    }

    @Test
    fun confirmAsksForTheDeletion() {
        var confirmed = false
        composeRule.setContent {
            SourceSwitchConfirmationDialog(
                plan = planOf(mapCount = 1, mapBytes = 1024, basemapBytes = 0),
                onConfirm = { confirmed = true },
                onDismiss = {}
            )
        }

        composeRule.onNodeWithText("Change source").performClick()

        assertTrue(confirmed)
    }

    @Test
    fun theBasemapIsPartOfWhatTheDialogNames() {
        show(planOf(mapCount = 0, mapBytes = 0, basemapBytes = 2L * 1024 * 1024))

        // A basemap alone still needs the confirmation, and its bytes are named.
        composeRule.onNodeWithTag("source-switch-dialog").assertIsDisplayed()
        composeRule.onNodeWithText(
            "The newly selected source cannot use the data of the current one. " +
                "0 map(s) and 2.0 MB will be deleted."
        ).assertIsDisplayed()
    }

    @Test
    fun theConfirmationIsATapTargetAndFitsTheDialog() {
        show(planOf(mapCount = 1, mapBytes = 1024, basemapBytes = 0))

        val dialog = composeRule.onNodeWithTag("source-switch-dialog").getBoundsInRoot()
        val confirm = composeRule.onNodeWithText("Change source").getBoundsInRoot()

        assertTrue(
            "the dialog is on screen",
            dialog.bottom - dialog.top > 0.dp && dialog.right > dialog.left
        )
        assertTrue("the confirmation is a tap target", confirm.bottom - confirm.top >= 48.dp)
        assertTrue(
            "the confirmation sits inside the dialog",
            confirm.left >= dialog.left && confirm.right <= dialog.right
        )
    }

    private fun show(plan: MapSourceSwitchPlan) {
        composeRule.setContent {
            SourceSwitchConfirmationDialog(plan = plan, onConfirm = {}, onDismiss = {})
        }
    }

    private fun planOf(mapCount: Int, mapBytes: Long, basemapBytes: Long): MapSourceSwitchPlan {
        val perMap = if (mapCount > 0) mapBytes / mapCount else 0L
        val directories = (1..mapCount).map { java.nio.file.Paths.get("/maps/map-$it") }
        return MapSourceSwitchPlan(
            mapDirectories = directories,
            mapBytes = perMap * mapCount,
            basemapDirectory = if (basemapBytes > 0) java.nio.file.Paths.get("/maps/basemap") else null,
            basemapBytes = basemapBytes
        )
    }
}
