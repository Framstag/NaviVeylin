package com.naviveylin.core

import java.util.Locale

/**
 * Coordinate string formatting and parsing (spec: `i18n-l10n` — Coordinate
 * string is locale-stable; Coordinate input accepts the locale decimal
 * separator; spec: `fav-management-ui` — Coordinate entry accepts either
 * decimal separator).
 *
 * A coordinate string is data, not display text: it is always formatted with a
 * fixed locale and a `.` decimal separator, so a pair never becomes ambiguous on
 * a device whose locale uses a comma as decimal separator (`51,50000, 7,40000`),
 * and `DetailsResolver.COORDINATE_LABEL_REGEX` recognises it on every device.
 * Numeric display values (distances, speeds, sizes) keep the device locale — see
 * [formatDistanceNumber].
 *
 * Entry runs the other direction: [parseLatitude] / [parseLongitude] accept both
 * separators, so a value the UI prefilled can be saved unchanged and a user of a
 * comma-decimal locale may type the form their keyboard offers.
 */

/** Layout of a coordinate pair: 5 decimals, dot separator, `", "` between the values. */
const val COORDINATE_PAIR_PATTERN: String = "%.5f, %.5f"

/** Layout of a single coordinate value: 5 decimals, dot separator. */
const val COORDINATE_PATTERN: String = "%.5f"

/**
 * Locale of every coordinate string. Fixed on purpose: coordinates are data
 * (spec: `i18n-l10n` — Coordinate string is locale-stable), unlike the numeric
 * display values that follow the device locale.
 */
private val COORDINATE_LOCALE: Locale = Locale.US

/**
 * Grammar of a single coordinate value: optional sign, digits, at most one `.`
 * or `,` separator followed by digits. Rejects grouping (`1,234,567`), exponent
 * forms (`1e2`), a bare separator (`.5`, `5.`) and any other trailing text.
 */
private val COORDINATE_GRAMMAR = Regex("""[+-]?\d+(?:[.,]\d+)?""")

private val LATITUDE_RANGE = -90.0..90.0
private val LONGITUDE_RANGE = -180.0..180.0

/**
 * Format a latitude/longitude pair as one locale-stable string.
 *
 * @param lat latitude in degrees
 * @param lon longitude in degrees
 * @param pattern layout pattern with two `%f` arguments; the phone passes the
 *   `coordinates_format` resource so the pair layout stays a resource
 * @return e.g. `"51.51391, 7.47434"`, independent of the device locale
 */
fun formatCoordinatePair(
    lat: Double,
    lon: Double,
    pattern: String = COORDINATE_PAIR_PATTERN
): String = String.format(COORDINATE_LOCALE, pattern, lat, lon)

/**
 * Format a single coordinate value for a coordinate input field, so the field's
 * prefilled text is parseable again by [parseLatitude] / [parseLongitude].
 *
 * @param value coordinate in degrees
 * @return e.g. `"51.51391"`, independent of the device locale
 */
fun formatCoordinate(value: Double): String =
    String.format(COORDINATE_LOCALE, COORDINATE_PATTERN, value)

/**
 * Parse a latitude typed by the user, accepting a `.` or a `,` as decimal
 * separator.
 *
 * @return the latitude, or `null` when [text] is not a coordinate in
 *   `-90..90` (invalid text, out of range, or a multi-separator value)
 */
fun parseLatitude(text: String?): Double? = parseCoordinate(text, LATITUDE_RANGE)

/**
 * Parse a longitude typed by the user, accepting a `.` or a `,` as decimal
 * separator.
 *
 * @return the longitude, or `null` when [text] is not a coordinate in
 *   `-180..180` (invalid text, out of range, or a multi-separator value)
 */
fun parseLongitude(text: String?): Double? = parseCoordinate(text, LONGITUDE_RANGE)

/** Parse one coordinate value and hold it to [range]. */
private fun parseCoordinate(text: String?, range: ClosedFloatingPointRange<Double>): Double? {
    val trimmed = text?.trim().orEmpty()
    if (!COORDINATE_GRAMMAR.matches(trimmed)) return null
    val value = trimmed.replace(',', '.').toDoubleOrNull() ?: return null
    return value.takeIf { it in range }
}
