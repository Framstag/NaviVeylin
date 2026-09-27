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
 * value, and no call may format one with coordinate precision. Neither may a call
 * interpolate a whole position-carrying value — a request/destination object or an
 * `Intent`'s data — where no coordinate identifier appears in the source at all
 * (`TODO.md` §87).
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

    /**
     * Names this repo uses for input that may carry a position (share/deep link/
     * fix). A *bare* `$name` interpolation of one hands the whole value — and its
     * `toString()` — to the message.
     */
    private val CARRIER_NAMES = setOf(
        "request", "req", "intent", "original", "fix", "location", "loc",
        "dest", "destination", "pair"
    )

    /** Types that hold a position; a local declared with one is a carrier too. */
    private val CARRIER_TYPES = setOf(
        "SharedLocationRequest", "DeepLinkDestination", "Intent", "Uri", "Location", "GpsFix"
    )

    /** `val name: Type` declarations, so an explicitly typed carrier is seen too. */
    private val DECLARATION = Regex(
        """\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([A-Za-z_][A-Za-z0-9_.]*)"""
    )

    /** A `$name` or `${expression}` interpolation inside a log call. */
    private val INTERPOLATION = Regex("""\$(?:\{([^}]*)\}|([A-Za-z_][A-Za-z0-9_]*))""")

    /** A URI read off an intent-shaped value (`${original?.data}`). */
    private val INTENT_DATA = Regex("""\.(?:data|dataString)\b""")

    /** What may follow the URI read and still be a scalar property read off it. */
    private val SAFE_URI_READ = Regex("""^\s*\??\.\s*[A-Za-z_][A-Za-z0-9_]*""")

    /** A bare identifier — no property access, no call, no `?:` — i.e. a whole value. */
    private val BARE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /** Find every offending call in [source]. */
    fun scan(path: String, source: String): List<CoordinateLogFinding> {
        val findings = mutableListOf<CoordinateLogFinding>()
        val carriers = carrierNames(source)
        for (opener in OPENERS.findAll(source)) {
            val open = opener.range.last
            val close = matchingParen(source, open) ?: continue
            val call = source.substring(open, close + 1)
            val identifier = COORDINATE_IDENTIFIERS.find(call)
            val reason = when {
                identifier != null -> "coordinate identifier '${identifier.value}'"
                COORDINATE_FORMAT.containsMatchIn(call) -> "coordinate-shaped format ${COORDINATE_FORMAT.find(call)!!.value}"
                else -> interpolatedCarrier(call, carriers)
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

    /**
     * The names that carry a position in [source]: the repo's carrier names plus
     * every local declared with a position-carrying type.
     */
    private fun carrierNames(source: String): Set<String> {
        val declared = DECLARATION.findAll(source)
            .filter { it.groupValues[2].substringAfterLast('.') in CARRIER_TYPES }
            .map { it.groupValues[1] }
            .toSet()
        return CARRIER_NAMES + declared
    }

    /**
     * The regression class this rule exists for (change `fix-diagnostics-coordinate-redaction`,
     * `TODO.md` §87): a value that arrives *whole* — an interpolated request/destination
     * object or an `Intent`'s data — so no coordinate identifier appears in the source.
     */
    private fun interpolatedCarrier(call: String, carriers: Set<String>): String? {
        for (match in INTERPOLATION.findAll(call)) {
            val expression = match.groupValues[1].ifEmpty { match.groupValues[2] }.trim()
            if (expression.isEmpty()) continue
            if (BARE_NAME.matches(expression) && expression in carriers) {
                return "position-carrying object '$expression'"
            }
            wholeUri(expression)?.let { return "intent data '$it'" }
        }
        return null
    }

    /**
     * The expression when it hands the whole URI over (`${original?.data}`,
     * `${intent?.data ?: "-"}`, `${intent?.data.toString()}`), or null when the
     * interpolation merely *reads* a scalar off it (`${intent?.data?.scheme}`,
     * which is identity, not a position).
     */
    private fun wholeUri(expression: String): String? {
        val access = INTENT_DATA.find(expression) ?: return null
        val suffix = expression.substring(access.range.last + 1)
        val read = SAFE_URI_READ.find(suffix)?.value?.trimEnd()
        if (read != null && !read.endsWith("toString")) return null
        return expression.replace(Regex("\\s+"), "")
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
