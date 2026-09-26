package com.naviveylin.build.diagnostics

/** One log or diagnostics call that carries a device position. */
data class CoordinateLogFinding(
    /** Path as reported to the build (repo-relative). */
    val path: String,
    /** 1-based line of the call's opening parenthesis. */
    val line: Int,
    /** The offending call, whitespace-collapsed for the message. */
    val call: String,
    /** What matched: a coordinate identifier or a coordinate-shaped format. */
    val reason: String
)

/**
 * Build gate for the diagnostics privacy rule (change
 * `fix-diagnostics-coordinate-redaction`, spec: auto-diagnostics — Diagnostics carry
 * no coordinates): no log or diagnostics call may interpolate a latitude/longitude
 * value, and no call may format one with coordinate precision.
 *
 * The scan is paren-balanced rather than line-based, because the lines this rule
 * exists for are exactly the multi-line concatenated ones (the fix-received line in
 * `MapCanvasViewModel` spans three lines) — a line regex would let them through.
 *
 * Pure and dependency-free, so it is unit-tested in `buildSrc` like the license
 * logic; the Gradle task only points it at the source roots.
 */
object CoordinateLogScanner {

    /** The log/diagnostics entry points the rule applies to. */
    private val OPENERS = Regex(
        """\b(?:Log\.[diwev]|Log\.wtf|DiagnosticsLog\.log|DiagnosticsLog\.logThrowable)\s*\("""
    )

    /** Identifiers that hold a position (any of them in a call flags it). */
    private val COORDINATE_IDENTIFIERS = Regex(
        """\b(?:lat|lats|lon|lons|latitude|longitude|destLat|destLon|startLat|startLon|""" +
            """centerLat|centerLon|newLat|newLon|gpsLat|gpsLon|initialCenter|markerLat|markerLon)\b"""
    )

    /** Coordinate-shaped precision (5-7 decimals is a position, not a diagnostic value). */
    private val COORDINATE_FORMAT = Regex("""%\.[5-7]f""")

    /** Find every offending call in [source]. */
    fun scan(path: String, source: String): List<CoordinateLogFinding> {
        val findings = mutableListOf<CoordinateLogFinding>()
        for (opener in OPENERS.findAll(source)) {
            val open = opener.range.last
            val close = matchingParen(source, open) ?: continue
            val call = source.substring(open, close + 1)
            val identifier = COORDINATE_IDENTIFIERS.find(call)
            val reason = when {
                identifier != null -> "coordinate identifier '${identifier.value}'"
                COORDINATE_FORMAT.containsMatchIn(call) -> "coordinate-shaped format ${COORDINATE_FORMAT.find(call)!!.value}"
                else -> null
            } ?: continue
            findings += CoordinateLogFinding(
                path = path,
                line = lineOf(source, open),
                call = call.replace(Regex("\\s+"), " ").trim(),
                reason = reason
            )
        }
        return findings
    }

    /** Human-readable report for a Gradle failure. */
    fun report(findings: List<CoordinateLogFinding>): String = buildString {
        append("Diagnostic log lines must not carry device coordinates (spec: auto-diagnostics — ")
        append("Diagnostics carry no coordinates).\n")
        append("Log precision-free identity instead: object label/id, map database or map file name,\n")
        append("magnification, screen pixel, accuracy, bearing. Reword a message that only mentions\n")
        append("a coordinate word in prose if this is a false positive.\n\n")
        findings.forEach { append("${it.path}:${it.line}: ${it.reason} — ${it.call}\n") }
    }

    private fun lineOf(source: String, index: Int): Int {
        var line = 1
        for (i in 0 until index.coerceAtMost(source.length)) {
            if (source[i] == '\n') line++
        }
        return line
    }

    /**
     * Index of the parenthesis closing the one at [open], skipping string literals
     * (with `\"` escapes) and comments so a `)` inside a message does not end the call.
     */
    private fun matchingParen(source: String, open: Int): Int? {
        var depth = 0
        var i = open
        var inString = false
        var inLineComment = false
        var inBlockComment = false
        while (i < source.length) {
            val c = source[i]
            val next = if (i + 1 < source.length) source[i + 1] else '\u0000'
            when {
                inLineComment -> if (c == '\n') inLineComment = false
                inBlockComment -> if (c == '*' && next == '/') {
                    inBlockComment = false
                    i++
                }
                inString -> when {
                    c == '\\' -> i++
                    c == '"' -> inString = false
                }
                c == '/' && next == '/' -> inLineComment = true
                c == '/' && next == '*' -> {
                    inBlockComment = true
                    i++
                }
                c == '"' -> inString = true
                c == '(' -> depth++
                c == ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }
}
