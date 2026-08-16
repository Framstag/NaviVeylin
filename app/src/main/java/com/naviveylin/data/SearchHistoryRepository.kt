package com.naviveylin.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A single recorded search selection: the search text and when it was selected. */
@Serializable
data class SearchHistoryEntry(
    val text: String,
    val timestamp: Long
)

/** Persisted container for the search history list. */
@Serializable
data class SearchHistoryData(
    val entries: List<SearchHistoryEntry> = emptyList()
)

/**
 * Persists search selections to a JSON file in app internal storage.
 *
 * Entries are exposed youngest-first. [record] appends a new entry and evicts
 * the oldest entry when the list exceeds [MAX_ENTRIES]. Persistence mirrors
 * [SettingsStorage]: kotlinx.serialization JSON, atomic write via temp file +
 * rename, all I/O on [Dispatchers.Default].
 */
@Singleton
class SearchHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file: File
        get() = File(context.filesDir, "maps/search_history.json")

    private val _history = MutableStateFlow<List<SearchHistoryEntry>>(emptyList())
    val history: StateFlow<List<SearchHistoryEntry>> = _history.asStateFlow()

    private var loaded = false

    /** Serializes [record] so concurrent calls cannot lose entries (read-modify-write). */
    private val mutex = Mutex()

    /** Load history from disk once. Safe to call repeatedly. */
    suspend fun load() {
        withContext(Dispatchers.Default) {
            if (loaded) return@withContext
            loaded = true
            val entries = if (file.exists()) {
                try {
                    json.decodeFromString(SearchHistoryData.serializer(), file.readText()).entries
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load search history, starting empty", e)
                    emptyList()
                }
            } else {
                emptyList()
            }
            _history.value = entries
        }
    }

    /**
     * Record a search selection. Blank text is ignored. Appends the entry
     * (youngest first) and evicts the oldest entry beyond [MAX_ENTRIES].
     */
    suspend fun record(text: String) {
        if (text.isBlank()) return
        mutex.withLock {
            withContext(Dispatchers.Default) {
                load()
                val newEntry = SearchHistoryEntry(text = text, timestamp = System.currentTimeMillis())
                val updated = (listOf(newEntry) + _history.value).take(MAX_ENTRIES)
                _history.value = updated
                persist(updated)
            }
        }
    }

    private fun persist(entries: List<SearchHistoryEntry>) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(SearchHistoryData.serializer(), SearchHistoryData(entries)))
            if (!tmp.renameTo(file)) {
                // Rename can fail on some filesystems; fall back to direct write.
                file.writeText(json.encodeToString(SearchHistoryData.serializer(), SearchHistoryData(entries)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save search history", e)
        }
    }

    companion object {
        const val MAX_ENTRIES = 50
        private const val TAG = "SearchHistoryRepository"
    }
}
