package com.naviveylin.build.testing

/** One refusal from [TripSendGateScanner]. */
data class TripSendGateFinding(
    val path: String,
    val line: Int,
    val call: String,
    val reason: String
)

/**
 * The trip-send gate (spec `car-host-fault-isolation` — Trip metadata publishing is bounded by the
 * navigation period; issue #71).
 *
 * The trip collector sends `NavigationManager.updateTrip` while the car session is **not** started, and
 * that is deliberate: the vehicle cluster and the heads-up display keep showing guidance while another
 * car app is in front. Its decision therefore comes from the navigation period alone
 * (`tripSendAllowed(...)`, `NavigationSession.kt`) and must never be taken from
 * `SessionHostGate.allowHostMutation()`, which is the session-start bound the sibling navigation-state
 * observer obeys. Copying that question onto the trip collector freezes the cluster — and it is exactly
 * the "obvious" fix a reviewer proposes, which is why it needs a rule rather than a comment.
 *
 * No unit test can reach the collector's line: [NavigationSession] needs a host-provided `CarContext`
 * and cannot be constructed under Robolectric, so the decision was extracted as a pure seam and *that*
 * is covered by `NavigationSessionTripSendTest`. This scanner covers the remaining half — the wiring —
 * by reading the source, the way `WallClockWaitScanner` covers the waits a test cannot police. There is
 * **no allowlist**: a file that holds a trip collector must obey the rule.
 */
object TripSendGateScanner {

    /** The marker that opens the trip collector's lambda. */
    const val COLLECTOR_MARKER = "CoroutineName(\"trip\")"

    private const val DECISION_CALL = "tripSendAllowed("
    private const val GATE_QUESTION = "allowHostMutation("

    private const val GATE_REASON =
        "the trip collector asks the session gate; the trip send is bounded by the navigation period, " +
            "not by the session start"

    private const val DECISION_REASON =
        "the trip collector does not take its decision from $DECISION_CALL...); the bound must stay a " +
            "named decision"

    /**
     * Findings for one file. Empty when the file holds no trip collector — the marker is what selects a
     * file for this rule, so an ordinary source file is not a finding. A collector whose shape the
     * scanner cannot read (no lambda, no closing brace) **is** a finding: a rule that silently stops
     * checking is worse than no rule.
     */
    fun scan(path: String, source: String): List<TripSendGateFinding> {
        val marker = source.indexOf(COLLECTOR_MARKER)
        if (marker < 0) return emptyList()

        val masked = maskCommentsAndStrings(source)
        val open = masked.indexOf('{', marker)
        if (open < 0) {
            return listOf(finding(path, source, marker, COLLECTOR_MARKER, "the trip collector's lambda was not found"))
        }
        val close = matchingBrace(masked, open)
        if (close < 0) {
            return listOf(finding(path, source, open, "{", "the trip collector's lambda is never closed"))
        }

        val body = masked.substring(open + 1, close)
        val findings = mutableListOf<TripSendGateFinding>()

        val gate = body.indexOf(GATE_QUESTION)
        if (gate >= 0) {
            findings += finding(path, source, open + 1 + gate, GATE_QUESTION, GATE_REASON)
        }
        if (!body.contains(DECISION_CALL)) {
            findings += finding(path, source, marker, COLLECTOR_MARKER, DECISION_REASON)
        }
        return findings
    }

    /** The message a build fails with, naming every finding and the rule behind it. */
    fun report(findings: List<TripSendGateFinding>): String = buildString {
        appendLine("trip-send gate: ${findings.size} finding(s) — the trip send is bounded by the navigation period, not by the session start")
        appendLine("(spec: car-host-fault-isolation — Trip metadata publishing is bounded by the navigation period)")
        findings.forEach { appendLine("  ${it.path}:${it.line}: ${it.call} — ${it.reason}") }
        appendLine("  there is no allowlist: a file that holds a trip collector must obey the rule")
    }

    /** Raised when no file holds a trip collector at all — the rule could not be checked. */
    fun reportMissingCollector(): String =
        "trip-send gate: no trip collector found (no source holds $COLLECTOR_MARKER) — the rule " +
            "\"the trip send is bounded by the navigation period\" cannot be checked; if the collector moved " +
            "or was renamed, point this check at its new shape (spec: car-host-fault-isolation — Trip metadata " +
            "publishing is bounded by the navigation period)"

    private fun finding(
        path: String,
        source: String,
        offset: Int,
        call: String,
        reason: String
    ): TripSendGateFinding = TripSendGateFinding(
        path = path,
        line = lineOf(source, offset),
        call = call.trim(),
        reason = reason
    )

    private fun lineOf(source: String, offset: Int): Int {
        var line = 1
        for (i in 0 until offset.coerceAtMost(source.length)) {
            if (source[i] == '\n') line++
        }
        return line
    }

    /** The index of the brace matching the one at [open], or -1. */
    private fun matchingBrace(source: String, open: Int): Int {
        var depth = 0
        for (i in open until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    /**
     * Comments and string bodies replaced by spaces, so a brace or a call name inside one cannot open a
     * block, hide a finding or satisfy the rule. The length and every newline are preserved, so offsets
     * and line numbers stay true. The marker itself lives in a string literal, which is why the marker is
     * searched in the raw source.
     */
    private fun maskCommentsAndStrings(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    while (i < source.length && source[i] != '\n') {
                        out.append(' ')
                        i++
                    }
                }
                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    out.append("  ")
                    i += 2
                    while (i < source.length && !(source[i] == '*' && i + 1 < source.length && source[i + 1] == '/')) {
                        out.append(if (source[i] == '\n') '\n' else ' ')
                        i++
                    }
                    if (i < source.length) {
                        out.append("  ")
                        i += 2
                    }
                }
                c == '"' && source.startsWith("\"\"\"", i) -> {
                    out.append("   ")
                    i += 3
                    while (i < source.length && !source.startsWith("\"\"\"", i)) {
                        out.append(if (source[i] == '\n') '\n' else ' ')
                        i++
                    }
                    if (i < source.length) {
                        out.append("   ")
                        i += 3
                    }
                }
                c == '"' -> {
                    out.append(' ')
                    i++
                    while (i < source.length && source[i] != '"' && source[i] != '\n') {
                        if (source[i] == '\\' && i + 1 < source.length) {
                            out.append("  ")
                            i += 2
                        } else {
                            out.append(' ')
                            i++
                        }
                    }
                    if (i < source.length && source[i] == '"') {
                        out.append(' ')
                        i++
                    }
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }
}
