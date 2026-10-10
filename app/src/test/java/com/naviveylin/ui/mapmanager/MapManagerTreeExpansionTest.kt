package com.naviveylin.ui.mapmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the tree's expand/collapse toggle.
 *
 * Spec: map-repository-source — "Expanding a leaf fetches its metadata" (the toggle tells the screen
 * whether the row was just expanded, which is when the leaves it reveals must have their metadata read).
 *
 * The case exists because the inline condition that consumed this decision was inverted once and nothing
 * noticed: a repository region expanded, its leaf row appeared with no size, and only the device run made
 * the missing probe visible (2026-10-09).
 */
class MapManagerTreeExpansionTest {

    @Test
    fun justExpandedIsTrueOnlyWhenTheDirectoryWasClosed() {
        val closed = emptySet<String>()

        val opened = toggleExpandedDirs(closed, "europe")

        assertEquals(setOf("europe"), opened.expanded)
        assertTrue("a closed row that is toggled was just expanded", opened.justExpanded)
    }

    @Test
    fun collapsingDoesNotAskForMetadata() {
        val expanded = setOf("europe", "europe/germany")

        val collapsed = toggleExpandedDirs(expanded, "europe")

        assertEquals(setOf("europe/germany"), collapsed.expanded)
        assertFalse("a collapse reveals no new leaf, so nothing is read", collapsed.justExpanded)
    }

    @Test
    fun onlyTheToggledRowChanges() {
        val expanded = setOf("europe", "asia")

        val toggled = toggleExpandedDirs(expanded, "europe/germany")

        assertEquals(setOf("europe", "asia", "europe/germany"), toggled.expanded)
        assertTrue(toggled.justExpanded)
    }

    @Test
    fun expandCollapseExpandAsksAgain() {
        val first = toggleExpandedDirs(emptySet(), "europe")
        val second = toggleExpandedDirs(first.expanded, "europe")
        val third = toggleExpandedDirs(second.expanded, "europe")

        assertTrue(first.justExpanded)
        assertFalse(second.justExpanded)
        assertTrue("re-opening the row asks for the metadata again", third.justExpanded)
    }
}
