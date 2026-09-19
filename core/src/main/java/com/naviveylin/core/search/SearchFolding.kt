package com.naviveylin.core.search

import java.text.Normalizer
import java.util.Locale

/**
 * Text folding for the search result ranking (spec: search-result-ranking —
 * "Query attributes classified as criteria or context").
 *
 * Applied to *both* sides of every comparison — the query criterion and the
 * entry's attribute name — so a difference from the backend's own
 * transliteration table can only cost a marking, never a result or an order
 * (`guidelines/Design.md` §4: the ranker owns its comparison, and the search
 * itself keeps matching names as before).
 *
 * Folds: case, diacritics (`ü` → `u`, `é` → `e`), sharp s (`ß` → `ss`),
 * punctuation and repeated whitespace.
 */
fun foldForSearchMatch(text: String): String {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    return decomposed
        // strip combining marks left by NFD
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace("ß", "ss")
        .replace(NON_ALPHANUMERIC, " ")
        .trim()
        .replace(REPEATED_WHITESPACE, " ")
}

/** Folded, whitespace-separated tokens of [text]; empty tokens are dropped. */
fun foldedTokens(text: String): List<String> =
    foldForSearchMatch(text).split(' ').filter { it.isNotEmpty() }

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
private val REPEATED_WHITESPACE = Regex("\\s+")
