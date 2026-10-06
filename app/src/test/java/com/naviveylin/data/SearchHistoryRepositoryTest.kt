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

    /**
     * Repeating a search moves its entry to the front instead of adding a second one
     * (spec: search-history — No duplicate entry for the same search text).
     */
    @Test
    fun repeatingSearchMovesEntryToFront() = runTest {
        repo.record("Bochum")
        repo.record("Essen")
        repo.record("Bochum")

        assertEquals(listOf("Bochum", "Essen"), repo.history.value.map { it.text })
        assertEquals(1, repo.history.value.count { it.text == "Bochum" })
    }

    @Test
    fun repeatingAnEntryAtTheCapDropsNothing() = runTest {
        repeat(SearchHistoryRepository.MAX_ENTRIES) { repo.record("query-$it") }

        // The repeat of the oldest entry reuses it, so no other entry is evicted.
        repo.record("query-0")

        val entries = repo.history.value
        assertEquals(SearchHistoryRepository.MAX_ENTRIES, entries.size)
        assertEquals("query-0", entries.first().text)
        assertEquals("query-1", entries.last().text)
    }

    /**
     * A file written before the move-to-front rule can hold the same text several times;
     * loading it collapses the duplicates (spec: search-history — Duplicate entries are
     * collapsed when the history is loaded).
     */
    @Test
    fun loadCollapsesPersistedDuplicates() = runTest {
        seedHistoryFile("Bochum" to 300L, "Essen" to 200L, "Bochum" to 100L)

        val reloaded = SearchHistoryRepository(ApplicationProvider.getApplicationContext())
        reloaded.load()

        assertEquals(listOf("Bochum", "Essen"), reloaded.history.value.map { it.text })
        assertEquals(300L, reloaded.history.value.first { it.text == "Bochum" }.timestamp)
    }

    /**
     * The collapse reaches the file: the next instance reads the collapsed list, and a
     * file that holds no duplicate is left untouched (its bytes stay as written).
     */
    @Test
    fun collapseIsPersistedAndACleanFileIsNotRewritten() = runTest {
        seedHistoryFile("Bochum" to 300L, "Bochum" to 100L)
        val collapsing = SearchHistoryRepository(ApplicationProvider.getApplicationContext())
        collapsing.load()

        val context: Context = ApplicationProvider.getApplicationContext()
        val cleanBytes = historyFile(context).readText()
        val again = SearchHistoryRepository(context)
        again.load()
        assertEquals(1, again.history.value.size)
        assertEquals(cleanBytes, historyFile(context).readText())
    }

    /**
     * An out-of-order file (newer entry stored after the older one) must not leave a stale
     * date behind (spec: search-history — An out-of-order file cannot keep a stale date).
     */
    @Test
    fun collapseKeepsTheNewestDateOfAnOutOfOrderFile() = runTest {
        seedHistoryFile("Bochum" to 100L, "Essen" to 200L, "Bochum" to 300L)

        val reloaded = SearchHistoryRepository(ApplicationProvider.getApplicationContext())
        reloaded.load()

        assertEquals(
            listOf("Bochum" to 300L, "Essen" to 200L),
            reloaded.history.value.map { it.text to it.timestamp }
        )
    }

    private fun historyFile(context: Context): File =
        File(context.filesDir, "maps/search_history.json")

    private fun seedHistoryFile(vararg entries: Pair<String, Long>) {
        val file = historyFile(ApplicationProvider.getApplicationContext())
        file.parentFile?.mkdirs()
        val body = entries.joinToString(",") { (text, timestamp) ->
            """{"text":"$text","timestamp":$timestamp}"""
        }
        file.writeText("""{"entries":[$body]}""")
    }
}
