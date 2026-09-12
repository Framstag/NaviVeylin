package com.naviveylin.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Entry-point matrix for the unified search dialog (spec: search-dialog —
 * entry points): the search button in the action column invokes the open
 * callback in both orientations. The menu entry is covered by
 * [MapMenuComposeTest] and the `/` key by [MapKeyShortcutsTest].
 */
@RunWith(RobolectricTestRunner::class)
class MapActionColumnComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun launchColumn(isLandscape: Boolean, onOpenSearch: () -> Unit) {
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                MapActionColumn(
                    isLandscape = isLandscape,
                    onToggleMenu = {},
                    onOpenSearch = onOpenSearch,
                    onToggleFavorites = {}
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun searchButtonVisibleInPortrait() {
        launchColumn(isLandscape = false, onOpenSearch = {})
        composeRule.onNodeWithContentDescription("Search location").assertIsDisplayed()
    }

    @Test
    fun searchButtonVisibleInLandscape() {
        launchColumn(isLandscape = true, onOpenSearch = {})
        composeRule.onNodeWithContentDescription("Search location").assertIsDisplayed()
    }

    @Test
    fun searchButtonOpensSearchInPortrait() {
        var opened = 0
        launchColumn(isLandscape = false, onOpenSearch = { opened++ })

        composeRule.onNodeWithContentDescription("Search location").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun searchButtonOpensSearchInLandscape() {
        var opened = 0
        launchColumn(isLandscape = true, onOpenSearch = { opened++ })

        composeRule.onNodeWithContentDescription("Search location").performClick()

        assertEquals(1, opened)
    }
}
