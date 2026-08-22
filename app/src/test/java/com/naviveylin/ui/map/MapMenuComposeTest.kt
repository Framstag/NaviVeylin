package com.naviveylin.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the map menu ([MapMenu]): the "Search POIs" entry is
 * present and opens the POI search (spec: POI search accessible from the map
 * menu), alongside the existing entries.
 */
@RunWith(RobolectricTestRunner::class)
class MapMenuComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dismissed = false
    private var downloadMaps = 0
    private var favorites = 0
    private var poiSearch = 0
    private var about = 0
    private var expandedState: MutableState<Boolean>? = null

    private fun launchMenu() {
        dismissed = false
        downloadMaps = 0
        favorites = 0
        poiSearch = 0
        about = 0
        composeRule.setContent {
            val expanded = remember { mutableStateOf(true) }
            expandedState = expanded
            Box(Modifier.fillMaxSize()) {
                MapMenu(
                    expanded = expanded.value,
                    onDismiss = { expanded.value = false },
                    onDownloadMaps = { downloadMaps++ },
                    onOpenFavorites = { favorites++ },
                    onOpenPoiSearch = { poiSearch++ },
                    onOpenAbout = { about++ },
                    toasterTopPadding = 4.dp
                )
            }
        }
    }

    /** Re-open the menu after it was dismissed by a previous selection. */
    private fun reopenMenu() {
        composeRule.runOnIdle { expandedState?.value = true }
        composeRule.waitForIdle()
    }

    @Test
    fun menuHiddenWhenNotExpanded() {
        dismissed = false
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                MapMenu(
                    expanded = false,
                    onDismiss = { dismissed = true },
                    onDownloadMaps = { downloadMaps++ },
                    onOpenFavorites = { favorites++ },
                    onOpenPoiSearch = { poiSearch++ },
                    onOpenAbout = { about++ },
                    toasterTopPadding = 4.dp
                )
            }
        }

        composeRule.onNodeWithText("Download Maps").assertDoesNotExist()
        composeRule.onNodeWithText("Favorites").assertDoesNotExist()
        composeRule.onNodeWithText("Search POIs").assertDoesNotExist()
        composeRule.onNodeWithText("About").assertDoesNotExist()
    }

    @Test
    fun menuClosesAfterSelection() {
        launchMenu()

        composeRule.onNodeWithText("About").performClick()

        assertEquals(1, about)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Download Maps").assertDoesNotExist()
    }

    @Test
    fun menuShowsAllEntries() {
        launchMenu()

        composeRule.onNodeWithText("Download Maps").assertIsDisplayed()
        composeRule.onNodeWithText("Favorites").assertIsDisplayed()
        composeRule.onNodeWithText("Search POIs").assertIsDisplayed()
        composeRule.onNodeWithText("About").assertIsDisplayed()
    }

    @Test
    fun poiSearchEntryOpensPoiSearch() {
        launchMenu()

        composeRule.onNodeWithText("Search POIs").performClick()

        assertEquals(1, poiSearch)
        assertTrue("menu dismissed on selection", expandedState?.value == false)
        assertEquals(0, downloadMaps)
        assertEquals(0, favorites)
        assertEquals(0, about)
    }

    @Test
    fun otherEntriesInvokeTheirCallbacks() {
        launchMenu()

        composeRule.onNodeWithText("Download Maps").performClick()
        assertEquals(1, downloadMaps)
        reopenMenu()
        composeRule.onNodeWithText("Favorites").performClick()
        assertEquals(1, favorites)
        reopenMenu()
        composeRule.onNodeWithText("About").performClick()
        assertEquals(1, about)
        assertTrue("each selection dismisses the menu", dismissed || expandedState?.value == false)
    }
}
