package com.naviveylin.core

/**
 * Provides the search history (shared with the phone app) to Android Auto
 * screens. Implemented in the [:app] module via Hilt, backed by the same
 * `maps/search_history.json` the phone search sheet uses.
 */
interface AutoSearchHistoryProvider {

    /** Load the search history entries, youngest first. */
    suspend fun load(): List<String>
}
