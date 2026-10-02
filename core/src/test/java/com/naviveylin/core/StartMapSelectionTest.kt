package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [StartMapSelection.choose], the rule that decides which installed database the phone
 * opens as its primary map (spec `start-map-selection` — the last opened map is reopened at the next
 * start; a recorded map that is no longer installed falls back deterministically; no map installed
 * shows the entry point).
 *
 * Plain JUnit: the rule is pure and touches neither Android nor the native stub.
 */
class StartMapSelectionTest {

    private val region = "/data/user/0/com.framstag.naviveylin/files/maps/nordrhein-westfalen"
    private val iceland = "/data/user/0/com.framstag.naviveylin/files/maps/iceland"

    @Test
    fun `the recorded map is reopened when it is still installed`() {
        assertEquals(
            iceland,
            StartMapSelection.choose(installed = listOf(region, iceland), lastUsed = iceland)
        )
    }

    @Test
    fun `a recorded map that is no longer installed falls back deterministically`() {
        val deleted = "/data/user/0/com.framstag.naviveylin/files/maps/deleted"

        assertEquals(
            iceland,
            StartMapSelection.choose(installed = listOf(region, iceland), lastUsed = deleted)
        )
    }

    @Test
    fun `the result does not depend on the discovery order`() {
        assertEquals(
            StartMapSelection.choose(installed = listOf(region, iceland), lastUsed = null),
            StartMapSelection.choose(installed = listOf(iceland, region), lastUsed = null)
        )
    }

    @Test
    fun `unsorted candidates pick the smallest path`() {
        assertEquals(
            iceland,
            StartMapSelection.choose(installed = listOf(region, iceland), lastUsed = null)
        )
    }

    @Test
    fun `no installed map has no start map`() {
        assertNull(StartMapSelection.choose(installed = emptyList(), lastUsed = region))
        assertNull(StartMapSelection.choose(installed = emptyList(), lastUsed = null))
    }

    @Test
    fun `an opened database path is recorded as itself`() {
        assertEquals(
            iceland,
            StartMapSelection.databaseDirectoryFor(listOf(region, iceland), iceland)
        )
    }

    @Test
    fun `a container path is recorded as the database below it`() {
        // The map manager navigates with its download target; an archive with its own top-level
        // directory puts the database one level below it.
        val container = "/data/user/0/com.framstag.naviveylin/files/maps/iceland"
        val database = "$container/iceland"

        assertEquals(
            database,
            StartMapSelection.databaseDirectoryFor(listOf(region, database), container)
        )
        assertEquals(
            database,
            StartMapSelection.databaseDirectoryFor(listOf(region, database), "$container/")
        )
    }

    @Test
    fun `an opened path the discovery does not report is recorded unchanged`() {
        val unknown = "/data/user/0/com.framstag.naviveylin/files/maps/unknown"

        assertEquals(
            unknown,
            StartMapSelection.databaseDirectoryFor(listOf(region, iceland), unknown)
        )
    }
}
