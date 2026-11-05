package com.naviveylin.test

/**
 * The `key=value` fields of a diagnostics record, read from the part of the line the record's own [marker]
 * names.
 *
 * A case that asserts a record carries no coordinate decides on these fields and never on the whole line
 * (issue #728): the reader prefixes every line with `[yyyy-MM-dd HH:mm:ss.SSS]`, and that stamp's `ss.SSS`
 * field reads `13.412` at second `:13`, millisecond `412`, while a record also carries its own measurements
 * (`duration=13ms`, `deviation_m=40.0`) — numbers a whole-line substring check reads as a position. The
 * case's result was then decided by the wall clock, so a red could not be told from a real one without a
 * per-case re-run (spec: `unit-test-suite-runtime` — A coordinate check decides on the record's fields, not
 * on the line's timing numbers).
 *
 * A token that carries no `=` keeps its own text as the key, so a bare coordinate appended to a record shows
 * up as a key the schema does not name instead of being dropped.
 */
internal fun recordFields(line: String, marker: String): Map<String, String> =
    line.substringAfter(marker).trim()
        .split(' ')
        .filter { it.isNotEmpty() }
        .associate { token -> token.substringBefore('=') to token.substringAfter('=', "") }

/**
 * The coordinate shape `auto-diagnostics` names: 1–3 integer digits and 4+ decimals
 * (`DeepLinkPrivacyTest`'s token). Every timing number a record carries is shorter than that — a stamp's
 * `13.412` has three decimals, a duration is a whole count of milliseconds — so this shape tells a position
 * from a clock reading.
 */
internal val coordinateShapedToken = Regex("""-?\b\d{1,3}\.\d{4,}\b""")
