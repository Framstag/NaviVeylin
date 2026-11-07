package com.naviveylin.build.diagnostics

/** One native log statement that carries a device position. */
data class NativeCoordinateLogFinding(
    /** Path as reported to the build (repo-relative). */
    val path: String,
    /** 1-based line of the statement's `osmscout::log` opener. */
    val line: Int,
    /** The offending statement, whitespace-collapsed for the message. */
    val statement: String,
    /** What matched: the position accessor that hands the value to the stream. */
    val reason: String
)

/**
 * Build gate for the diagnostics privacy rule over **native** sources (issue #125, change
 * `fix-native-coordinate-log-gate`; spec `auto-diagnostics` — Diagnostics carry no coordinates).
 *
 * The Kotlin gate ([CoordinateLogScanner]) reads `*.kt` files and knows the app's log call
 * shapes. Native code logs through `osmscout::log` as a *stream* — `osmscout::log.Warn() <<
 * "msg " << value;` — so the call shape differs in three ways, and each one is the reason this
 * scanner is a sibling rather than a reuse: there is no closing parenthesis to balance, the
 * statement ends at a `;`, and the position arrives as an accessor call (`coord.GetDisplayText()`,
 * `fix.GetLat()`) rather than as a Kotlin identifier.
 *
 * The accessor list is the position-shaped half of the Kotlin identifier list: `GetLat`, `GetLon`,
 * `GetLatitude`, `GetLongitude` (a coordinate component) and `GetDisplayText` (a whole position
 * rendered as a string, which is what `GeoCoord` and `GeoBox` do). Like the Kotlin gate this one
 * carries **no allowlist on purpose**: a statement that hands a position to the log stream is a
 * coordinate log line, and the fix is to log identity instead of rewording around the gate.
 *
 * Two residuals are stated rather than papered over:
 * - The list is accessor-shaped, not type-aware. `GetDisplayText` also exists on `TileId`,
 *   `Tiling` and `Pixel`, whose display text is a tile or pixel identity, not a position; such a
 *   statement would be a false positive and is reworded (the same escape the Kotlin gate has).
 * - A position copied into a local *before* the statement (`auto text = coord.GetDisplayText();`
 *   then `log.Warn() << text;`) is not followed. The bridge's log lines build their identity
 *   inline; a local that carries a position is a reword, not a rename.
 *
 * Only the sources this project owns are scanned (`app/build.gradle.kts` names them): the app's
 * own native files and the JNI translation unit. Upstream libosmscout is not gated here — it is a
 * fork patch layer, not this project's logging policy, and its `GetDisplayText` uses are boxes
 * and tiles.
 *
 * Pure and dependency-free, so it is unit-tested in `buildSrc` like the Kotlin scanner; the Gradle
 * task only points it at the files.
 */
object NativeCoordinateLogScanner {

    /** `osmscout::log.<Level>()` — the streaming entry points the rule applies to. */
    private val OPENER = Regex("""osmscout::log\.(?:Debug|Info|Warn|Error)\s*\(\s*\)""")

    /** An accessor that hands a position, or a position's text, to the stream. */
    private val POSITION_ACCESSOR = Regex(
        """\bGet(?:DisplayText|Lat|Lon|Latitude|Longitude)\s*\("""
    )

    /** Find every offending statement in [source]. */
    fun scan(path: String, source: String): List<NativeCoordinateLogFinding> {
        val findings = mutableListOf<NativeCoordinateLogFinding>()
        var i = 0
        while (i < source.length) {
            val c = source[i]
            val next = if (i + 1 < source.length) source[i + 1] else '\u0000'
            when {
                c == '/' && next == '/' -> i = endOfLine(source, i)
                c == '/' && next == '*' -> i = endOfBlockComment(source, i)
                c == '"' -> i = endOfString(source, i)
                c == '\'' -> i = endOfCharLiteral(source, i)
                else -> {
                    val opener = OPENER.matchAt(source, i)
                    if (opener == null) {
                        i++
                    } else {
                        val end = endOfStatement(source, opener.range.last + 1)
                        val statement = source.substring(i, end)
                        val accessor = POSITION_ACCESSOR.find(statement)
                        if (accessor != null) {
                            findings += NativeCoordinateLogFinding(
                                path = path,
                                line = lineOf(source, i),
                                statement = statement.replace(Regex("\\s+"), " ").trim(),
                                reason = "position accessor '${accessor.value.removeSuffix("(").trim()}'"
                            )
                        }
                        i = end
                    }
                }
            }
        }
        return findings
    }

    /** Human-readable report for a Gradle failure. */
    fun report(findings: List<NativeCoordinateLogFinding>): String = buildString {
        append("Native log lines must not carry device coordinates (spec: auto-diagnostics — ")
        append("Diagnostics carry no coordinates).\n")
        append("Log precision-free identity instead: object label/id, map database or map file name,\n")
        append("magnification, screen pixel, accuracy, bearing — never `GetDisplayText()`, `GetLat()`\n")
        append("or `GetLon()` on a position.\n\n")
        findings.forEach { append("${it.path}:${it.line}: ${it.reason} — ${it.statement}\n") }
    }

    /**
     * Index of the `;` that closes the statement starting at [from], skipping string literals,
     * character literals and comments so a `;` inside a message does not end it early, and
     * honouring parentheses so a nested call's own `)` cannot end it either.
     */
    private fun endOfStatement(source: String, from: Int): Int {
        var depth = 0
        var i = from
        while (i < source.length) {
            val c = source[i]
            val next = if (i + 1 < source.length) source[i + 1] else '\u0000'
            when {
                c == '/' && next == '/' -> i = endOfLine(source, i)
                c == '/' && next == '*' -> i = endOfBlockComment(source, i)
                c == '"' -> i = endOfString(source, i)
                c == '\'' -> i = endOfCharLiteral(source, i)
                c == '(' -> {
                    depth++
                    i++
                }
                c == ')' -> {
                    depth--
                    i++
                }
                c == ';' && depth <= 0 -> return i + 1
                else -> i++
            }
        }
        return source.length
    }

    /** Index just past the `//` comment that starts at [at] (the newline itself is not consumed). */
    private fun endOfLine(source: String, at: Int): Int {
        val newline = source.indexOf('\n', at)
        return if (newline < 0) source.length else newline
    }

    /** Index just past the block comment that starts at [at]. */
    private fun endOfBlockComment(source: String, at: Int): Int {
        val close = source.indexOf("*/", at + 2)
        return if (close < 0) source.length else close + 2
    }

    /** Index just past the string literal that opens at [at], honouring `\"` and `\\`. */
    private fun endOfString(source: String, at: Int): Int {
        var i = at + 1
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                else -> i++
            }
        }
        return source.length
    }

    /**
     * Index just past the character literal that opens at [at]. The literal is recognised only
     * when it closes within a few characters, so a digit separator (`1'000'000`) does not swallow
     * the rest of the statement.
     */
    private fun endOfCharLiteral(source: String, at: Int): Int {
        var i = at + 1
        while (i < source.length && i <= at + 8) {
            when (source[i]) {
                '\\' -> i += 2
                '\'' -> return i + 1
                '\n' -> return at + 1
                else -> i++
            }
        }
        return at + 1
    }

    private fun lineOf(source: String, index: Int): Int {
        var line = 1
        for (i in 0 until index.coerceAtMost(source.length)) {
            if (source[i] == '\n') line++
        }
        return line
    }
}
