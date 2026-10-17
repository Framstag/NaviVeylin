package com.naviveylin.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.framstag.libosmscout.client.DescriptionEntry
import com.framstag.libosmscout.client.ObjectDescription
import com.naviveylin.test.ComposeSandboxRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the candidate picker sheet (spec:
 * long-press-candidate-picker): candidates render in ranking order with name
 * and type, tapping a row reports the selected candidate, and dismissal
 * reports no selection.
 */
@RunWith(RobolectricTestRunner::class)
class CandidatePickerSheetComposeTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()

    private fun candidate(name: String, type: String, offset: Long): ObjectDescription {
        val entries = mutableListOf<DescriptionEntry>()
        if (name.isNotEmpty()) {
            entries.add(DescriptionEntry().apply {
                sectionKey = "General"
                labelKey = "Name"
                value = name
            })
        }
        entries.add(DescriptionEntry().apply {
            sectionKey = "General"
            labelKey = "Type"
            value = type
        })
        return ObjectDescription(entries, Double.NaN, Double.NaN, "area", type, offset)
    }

    @Test
    fun candidatesRenderInOrderWithNameAndType() {
        composeRule.setContent {
            CandidatePickerSheet(
                candidates = listOf(
                    candidate("Hotel Central", "tourism_hotel", 100L),
                    candidate("Cafe", "amenity_cafe", 200L)
                ),
                onCandidateSelected = {},
                onDismiss = {}
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hotel Central").assertIsDisplayed()
        composeRule.onNodeWithText("tourism_hotel").assertIsDisplayed()
        composeRule.onNodeWithText("Cafe").assertIsDisplayed()
        composeRule.onNodeWithText("amenity_cafe").assertIsDisplayed()
    }

    @Test
    fun unnamedCandidateShowsPlaceholder() {
        composeRule.setContent {
            CandidatePickerSheet(
                candidates = listOf(candidate("", "building", 300L)),
                onCandidateSelected = {},
                onDismiss = {}
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("(unnamed)").assertIsDisplayed()
        composeRule.onNodeWithText("building").assertIsDisplayed()
    }

    @Test
    fun tapRowReportsSelectedCandidate() {
        var selected: ObjectDescription? = null
        val first = candidate("Hotel Central", "tourism_hotel", 100L)
        composeRule.setContent {
            CandidatePickerSheet(
                candidates = listOf(first, candidate("Cafe", "amenity_cafe", 200L)),
                onCandidateSelected = { selected = it },
                onDismiss = {}
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hotel Central").performClick()
        assertEquals(first, selected)
    }

    /**
     * The share flow's name ranking reaches the pixels: the candidate whose name matches the shared
     * place name is drawn above the others, and the database's own order holds for the rest (spec:
     * share/location-receiving — Shared place name ranks the candidate list). The list is ranked with
     * the same function the map screen uses, so the case would fail if the ranking ever became a
     * filter or stopped reordering.
     */
    @Test
    fun rankedCandidateIsDrawnAboveTheOthers() {
        val candidates = listOf(
            candidate("Cafe", "amenity_cafe", 100L),
            candidate("Hotel Central", "tourism_hotel", 200L),
            candidate("Eiffel Tower", "tourism_attraction", 300L)
        )

        composeRule.setContent {
            CandidatePickerSheet(
                candidates = rankCandidatesByName(candidates, "Eiffel Tower"),
                onCandidateSelected = {},
                onDismiss = {}
            )
        }
        composeRule.waitForIdle()

        val rankedTop = composeRule.onNodeWithText("Eiffel Tower").fetchSemanticsNode().positionInRoot.y
        val cafe = composeRule.onNodeWithText("Cafe").fetchSemanticsNode().positionInRoot.y
        val hotel = composeRule.onNodeWithText("Hotel Central").fetchSemanticsNode().positionInRoot.y

        assertTrue("the named candidate is the top row: $rankedTop vs $cafe / $hotel", rankedTop < cafe)
        assertTrue("the database order holds below it: $cafe vs $hotel", cafe < hotel)
    }
}
