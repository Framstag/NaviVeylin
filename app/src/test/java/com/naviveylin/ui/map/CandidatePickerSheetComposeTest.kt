package com.naviveylin.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.framstag.libosmscout.client.DescriptionEntry
import com.framstag.libosmscout.client.ObjectDescription
import org.junit.Assert.assertEquals
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
}
