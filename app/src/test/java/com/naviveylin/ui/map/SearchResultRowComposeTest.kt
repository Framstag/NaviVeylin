package com.naviveylin.ui.map

import android.graphics.Bitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.test.ComposeSandboxRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The result row as it is rendered (change `search-result-row-content`, tasks 4.1/4.4).
 *
 * The text composition itself is pinned by `:core` `ResultRowTextTest`; what is asserted here is
 * the row's use of it — one leading slot, the identifying first line, the detail line, and no
 * admin region hierarchy — because that is the part a composer change could silently drop.
 *
 * No `@Config`: the default Robolectric sandbox, per AGENTS.md.
 */
@RunWith(RobolectricTestRunner::class)
class SearchResultRowComposeTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(label: String, region: Array<String>? = arrayOf("Dortmund")): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.region = region
            adminRegionHierarchy = HIERARCHY
            lat = 51.5136
            lon = 7.4653
        }

    private fun icon(): Bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)

    /**
     * The rows' leading-artwork nodes. The unmerged tree, because the row is `clickable` and
     * therefore merges its descendants: without it the query would find nothing and a count-0
     * assertion would pass even on a row that does carry an icon.
     */
    private fun iconNodes() = composeRule.onAllNodesWithTag(SearchRowIconTag, useUnmergedTree = true)

    @Test
    fun `the first line carries the label and the city, and the hierarchy is gone`() {
        composeRule.setContent {
            SearchResultItem(entry = entry("Rosenweg 20"), distanceText = "12.3 km", onClick = {})
        }

        composeRule.onNodeWithText("Rosenweg 20 - Dortmund").assertIsDisplayed()
        composeRule.onNodeWithText("12.3 km").assertIsDisplayed()
        // The hierarchy path stays in the location details sheet.
        iconNodes().assertCountEquals(0)
        assertNoNodeWithHierarchy()
    }

    @Test
    fun `the detail line replaces the hierarchy line`() {
        composeRule.setContent {
            SearchResultItem(
                entry = entry("Rosenweg 20"),
                detailText = "restaurant · 44139",
                onClick = {}
            )
        }

        composeRule.onNodeWithText("restaurant · 44139").assertIsDisplayed()
        assertNoNodeWithHierarchy()
    }

    @Test
    fun `a row without a marking and without an icon shows no leading artwork`() {
        composeRule.setContent {
            SearchResultItem(entry = entry("Rosenweg 20"), onClick = {})
        }

        iconNodes().assertCountEquals(0)
    }

    @Test
    fun `the type icon is drawn in the leading slot`() {
        composeRule.setContent {
            SearchResultItem(entry = entry("Rosenweg 20"), typeIcon = icon(), onClick = {})
        }

        iconNodes().assertCountEquals(1)
        composeRule.onNodeWithText("Rosenweg 20 - Dortmund").assertIsDisplayed()
    }

    @Test
    fun `a marked row that also has an icon keeps its accessibility description`() {
        composeRule.setContent {
            SearchResultItem(
                entry = entry("Rosenweg 20"),
                isFavorite = true,
                isPerfect = true,
                typeIcon = icon(),
                onClick = {}
            )
        }

        // One slot, both facts, and the label still states them.
        iconNodes().assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Exact match and favorite").assertIsDisplayed()
    }

    @Test
    fun `a marked row without an icon keeps the marking alone`() {
        composeRule.setContent {
            SearchResultItem(entry = entry("Rosenweg 20"), isPerfect = true, onClick = {})
        }

        iconNodes().assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Exact match").assertIsDisplayed()
    }

    /** The row must not render the admin region hierarchy: it belongs to the details sheet. */
    private fun assertNoNodeWithHierarchy() {
        composeRule.onAllNodesWithText(HIERARCHY).assertCountEquals(0)
    }

    private companion object {
        const val HIERARCHY = "Eving/Dortmund/Arnsberg/Nordrhein-Westfalen/Deutschland"
    }
}
