package com.naviveylin.util

import java.util.Locale

/**
 * Format a distance in meters as a kilometer string for display, mirroring
 * upstream `LocationSearchRanker.formatDistanceKm`: sub-10 km keeps one
 * decimal place so nearby results are distinguishable, larger distances round
 * to whole kilometers.
 *
 * Delegates to the single implementation in `:core` (`formatDistanceKm`), which
 * the Android Auto search rows also use, so both surfaces format a search
 * distance identically.
 *
 * @param meters distance in meters
 * @param locale locale for decimal separators (default: device locale)
 * @return formatted value, e.g. "0.5" or "12"
 */
fun formatDistanceKm(meters: Double, locale: Locale = Locale.getDefault()): String =
    com.naviveylin.core.formatDistanceKm(meters, locale)
