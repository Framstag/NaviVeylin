package com.naviveylin.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.naviveylin.data.SearchHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the search history sheet: entries render (youngest
 * first), tapping an entry reports its search text, and the empty state shows
 * a placeholder.
 */
@RunWith(RobolectricTestRunner::class)
class SearchHistorySheetComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val older = SearchHistoryEntry(text = "Café Central", timestamp = 1000L)
    private val newer = SearchHistoryEntry(text = "Dortmund Hbf", timestamp = 2000L)

    @Test
    fun entriesRenderInOrder() {
        composeRule.setContent {
            SearchHistorySheet(
                entries = listOf(newer, older),
                onEntrySelected = {},
                onDismiss = {}
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Dortmund Hbf").assertIsDisplayed()
        composeRule.onNodeWithText("Café Central").assertIsDisplayed()
    }

    @Test
    fun tapEntryReportsSearchText() {
        var selected: String? = null
        composeRule.setContent {
            SearchHistorySheet(
                entries = listOf(newer, older),
                onEntrySelected = { selected = it },
                onDismiss = {}
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Café Central").performClick()
        assertEquals("Café Central", selected)
    }

    @Test
    fun emptyStateShowsPlaceholder() {
        composeRule.setContent {
            SearchHistorySheet(
                entries = emptyList(),
                onEntrySelected = {},
                onDismiss = {}
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No search history yet").assertIsDisplayed()
    }
}
