package com.naviveylin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Unit tests for [SearchHistoryRepository]: entry content, youngest-first
 * ordering, the 50-entry cap with oldest eviction, and JSON persistence.
 */
@RunWith(RobolectricTestRunner::class)
class SearchHistoryRepositoryTest {

    private lateinit var repo: SearchHistoryRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // Fresh state per test: remove any persisted file from a previous run.
        File(context.filesDir, "maps/search_history.json").delete()
        repo = SearchHistoryRepository(context)
    }

    @Test
    fun recordAppendsEntryWithTextAndTimestamp() = runTest {
        repo.record("Dortmund Hbf")
        val entries = repo.history.value
        assertEquals(1, entries.size)
        assertEquals("Dortmund Hbf", entries[0].text)
        assertTrue("timestamp must be set", entries[0].timestamp > 0)
    }

    @Test
    fun youngestFirstOrdering() = runTest {
        repo.record("first")
        repo.record("second")
        repo.record("third")
        assertEquals(listOf("third", "second", "first"), repo.history.value.map { it.text })
    }

    @Test
    fun capAt50EvictsOldest() = runTest {
        repeat(55) { repo.record("query-$it") }
        val entries = repo.history.value
        assertEquals(SearchHistoryRepository.MAX_ENTRIES, entries.size)
        // Newest kept first; the 5 oldest (query-0..query-4) are evicted.
        assertEquals("query-54", entries.first().text)
        assertEquals("query-5", entries.last().text)
    }

    @Test
    fun persistenceRoundTrip() = runTest {
        repo.record("Café Central")
        repo.record("Hauptstraße 12")

        // A fresh instance must read the same entries from disk.
        val context: Context = ApplicationProvider.getApplicationContext()
        val reloaded = SearchHistoryRepository(context)
        reloaded.load()
        assertEquals(
            listOf("Hauptstraße 12", "Café Central"),
            reloaded.history.value.map { it.text }
        )
    }

    @Test
    fun blankTextNotRecorded() = runTest {
        repo.record("   ")
        assertEquals(0, repo.history.value.size)
    }
}
