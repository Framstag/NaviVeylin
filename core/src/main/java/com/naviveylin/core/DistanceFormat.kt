package com.naviveylin.core

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Shared distance-display rounding (phone + Android Auto, spec:
 * auto/navigation-view — distance display): exact value up to 50 m,
 * multiples of 50 m from 50 m to 1 km, multiples of 100 m (one decimal km)
 * above 1 km.
 */
fun roundDistanceMeters(meters: Double): Double {
    if (meters <= 50.0) return meters
    if (meters < 1000.0) return (meters / 50.0).roundToInt() * 50.0
    return (meters / 100.0).roundToInt() * 100.0
}

/**
 * Format a distance in meters for display, locale-aware: meters below 1 km,
 * kilometers above (e.g. "350" / "1,2" in German, "1.2" in English).
 * Returns the numeric part only; the unit suffix comes from a string resource
 * (`distance_unit_m` / `distance_unit_km`).
 *
 * @param meters distance in meters
 * @param locale locale for decimal separators (default: device locale)
 * @return formatted numeric part, e.g. "350" or "1.2"
 */
fun formatDistanceNumber(meters: Double, locale: Locale = Locale.getDefault()): String {
    val rounded = roundDistanceMeters(meters)
    return if (rounded >= 1000) {
        String.format(locale, "%.1f", rounded / 1000.0)
    } else {
        String.format(locale, "%.0f", rounded.toDouble())
    }
}

/** True when [formatDistanceNumber] expresses the distance in kilometers. */
fun distanceUsesKilometers(meters: Double): Boolean = roundDistanceMeters(meters) >= 1000

/**
 * Format a distance in meters as a kilometer string for the search result lists
 * (phone and Android Auto): sub-10 km keeps one decimal place so nearby results
 * are distinguishable, larger distances round to whole kilometers. Mirrors
 * upstream `LocationSearchRanker.formatDistanceKm`.
 *
 * Locale-aware (German comma decimals). Returns the numeric part only; the "km"
 * unit suffix comes from a string resource (`distance_unit_km`).
 *
 * Single implementation for both surfaces (`guidelines/Design.md` §4): the
 * parity requirement is that the phone and the car format a search distance
 * identically.
 *
 * @param meters distance in meters
 * @param locale locale for decimal separators (default: device locale)
 * @return formatted value, e.g. "0.5" or "12"
 */
fun formatDistanceKm(meters: Double, locale: Locale = Locale.getDefault()): String {
    val km = meters / 1000.0
    return if (km < 10.0) {
        String.format(locale, "%.1f", km)
    } else {
        String.format(locale, "%.0f", km)
    }
}

/**
 * Format a duration in seconds for display, e.g. "45 min" or "1h 20min".
 * Shared by the route summary and the navigation details step list
 * (spec: move-routing-summary — per-step time).
 */
fun formatDurationText(durationSec: Double): String {
    val hours = (durationSec / 3600.0).toInt()
    val minutes = ((durationSec % 3600.0) / 60.0).toInt()
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes} min"
}

/**
 * Format a **step's** duration in seconds for display, e.g. "45 s", "5 min", "1h 5min".
 *
 * Per-step durations are the travel times of the route's own legs and are routinely below a minute,
 * so - unlike [formatDurationText], which the route's total, the notification and the ETA strings
 * share - this formatter keeps the seconds and never prints "0 min" for a leg that took 45 s (owner
 * finding, 2026-10-03: every step of a city route read "0 min").
 *
 * A value below a second means "unknown" and produces an empty string, so a caller omits the value
 * instead of printing a duration it does not have.
 *
 * @param durationSec duration in seconds; below 1.0 means unknown
 * @return formatted duration, or the empty string when the duration is unknown
 */
fun formatStepDurationText(durationSec: Double): String {
    if (durationSec < 1.0) return ""
    val total = durationSec.toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}min"
        minutes > 0 -> "${minutes} min"
        else -> "${seconds} s"
    }
}
