package com.naviveylin.build.testing

/** One wall-clock wait in a test source. */
data class WallClockWaitFinding(
    /** Path as reported to the build (repo-relative). */
    val path: String,
    /** 1-based line of the wait. */
    val line: Int,
    /** The offending text, whitespace-collapsed for the message. */
    val call: String,
    /** What matched: a fixed sleep, a wall-clock deadline loop, or a clock-derived deadline. */
    val reason: String
)

/**
 * Build gate for the unit-test time rule (change `speed-up-test-iteration`, spec
 * `unit-test-suite-runtime` — Wall-clock waits in test sources are refused by a build check).
 *
 * Three patterns are refused, and there is **no allowlist**: a test decides its outcome from the
 * behaviour under test and from time it controls, so a site that needs to wait is converted to advance
 * an injected time source or the test scheduler, never declared.
 *
 *  1. a fixed sleep — `Thread.sleep(...)`, `TimeUnit.X.sleep(...)`;
 *  2. a wall-clock deadline loop — a `while (...)` header that reads `System.currentTimeMillis()`,
 *     `System.nanoTime()` or `SystemClock.elapsedRealtime*()`/`uptimeMillis()`;
 *  3. a clock-derived deadline binding — a `val`/`var` whose name names a deadline and whose
 *     initializer reads one of those clocks (the shape whose loop lives in another file).
 *
 * Deliberately **not** refused: reading the wall clock as *data* (`time = System.currentTimeMillis()`
 * building a stale fix, `DiagnosticsLog.time` measuring a block) — that is input, not a wait, and
 * refusing it would force unrelated behaviour changes. The residual is a wait whose deadline arrives
 * through a function parameter and is only compared inside a helper that this scan cannot see; the
 * `while`-header rule catches that helper at its own definition, which is why every awaiting helper is
 * expected to be a test source too.
 *
 * Comments and string literals are masked before matching, so a comment that *mentions* `Thread.sleep`
 * is not a finding. Pure and dependency-free, so it is unit-tested in `buildSrc` like the license and
 * coordinate logic; the Gradle task only points it at the test source roots.
 */
object WallClockWaitScanner {

    /** A fixed sleep. */
    private val SLEEP = Regex(
        """\bThread\s*\.\s*sleep\s*\(|\bTimeUnit\s*\.\s*[A-Za-z]+\s*\.\s*sleep\s*\("""
    )

    /** A wall-clock read — the only clocks a test may not pace itself with. */
    private val WALL_CLOCK = Regex(
        """\bSystem\s*\.\s*(?:currentTimeMillis|nanoTime)\s*\(\s*\)""" +
            """|\bSystemClock\s*\.\s*(?:elapsedRealtime|elapsedRealtimeNanos|uptimeMillis)\s*\(\s*\)"""
    )

    /** A `while` header opener; the header is taken paren-balanced from here. */
    private val WHILE = Regex("""\bwhile\s*\(""")

    /** A `val`/`var` declaration line, whose name and initializer the deadline binding rule reads. */
    private val DECLARATION = Regex("""\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)""")

    /** Names that mark a clock-derived binding as a deadline rather than as data. */
    private val DEADLINE_NAME = Regex("""(?i)(deadline|until|expire|expiry|due[A-Za-z]|timeoutAt)""")

    /** Each line of [text] with its offset, so a line's position survives without a second search. */
    private fun maskedLines(text: String): List<Pair<Int, String>> {
        val lines = mutableListOf<Pair<Int, String>>()
        var start = 0
        while (start <= text.length) {
            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            lines += start to text.substring(start, end)
            if (end == text.length) break
            start = end + 1
        }
        return lines
    }

    /** Find every refused wait in [source]. */
    fun scan(path: String, source: String): List<WallClockWaitFinding> {
        val masked = mask(source)
        val findings = mutableListOf<WallClockWaitFinding>()

        for (match in SLEEP.findAll(masked)) {
            findings += finding(path, source, match.range.first, match.value, "fixed sleep")
        }

        for (match in WHILE.findAll(masked)) {
            val open = masked.indexOf('(', match.range.first)
            if (open < 0) continue
            val close = matchingParen(masked, open) ?: continue
            val header = masked.substring(open, close + 1)
            val clock = WALL_CLOCK.find(header) ?: continue
            findings += finding(
                path, source, match.range.first, "while $header", "wall-clock deadline loop on '${clock.value}'"
            )
        }

        for ((offset, line) in maskedLines(masked)) {
            if (!WALL_CLOCK.containsMatchIn(line)) continue
            val name = DECLARATION.find(line)?.groupValues?.get(1) ?: continue
            if (!DEADLINE_NAME.containsMatchIn(name)) continue
            findings += finding(path, source, offset, line, "clock-derived deadline '$name'")
        }

        return findings.distinctBy { it.line to it.reason }
    }

    /** The message the Gradle task throws, naming every wait and the rule that has no exemption. */
    fun report(findings: List<WallClockWaitFinding>): String = buildString {
        appendLine(
            "Unit-test sources must not wait on the wall clock (spec unit-test-suite-runtime — " +
                "Wall-clock waits in test sources are refused by a build check)."
        )
        appendLine(
            "There is no allowlist: advance the injected time source (EngineTimeSource) or the " +
                "test scheduler and await the observable state instead."
        )
        findings.forEach { appendLine("  ${it.path}:${it.line}: ${it.reason} — ${it.call}") }
        append("  ${findings.size} wait(s) to convert.")
    }

    private fun finding(
        path: String,
        source: String,
        offset: Int,
        call: String,
        reason: String
    ) = WallClockWaitFinding(
        path = path,
        line = lineOf(source, offset),
        call = call.replace(Regex("\\s+"), " ").trim(),
        reason = reason
    )

    /** Replace comment and string characters with spaces, keeping offsets and newlines. */
    private fun mask(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (c == '/' && i + 1 < source.length && source[i + 1] == '/') {
                while (i < source.length && source[i] != '\n') { out.append(' '); i++ }
                continue
            }
            if (c == '/' && i + 1 < source.length && source[i + 1] == '*') {
                while (i < source.length) {
                    if (source[i] == '*' && i + 1 < source.length && source[i + 1] == '/') {
                        out.append("  "); i += 2; break
                    }
                    out.append(if (source[i] == '\n') '\n' else ' '); i++
                }
                continue
            }
            if (c == '"' && i + 2 < source.length && source.startsWith("\"\"\"", i)) {
                out.append("   "); i += 3 // consume the opening delimiter before looking for the closer
                while (i < source.length) {
                    if (source.startsWith("\"\"\"", i)) { out.append("   "); i += 3; break }
                    out.append(if (source[i] == '\n') '\n' else ' '); i++
                }
                continue
            }
            if (c == '"') {
                out.append(' '); i++
                while (i < source.length && source[i] != '"') {
                    if (source[i] == '\\' && i + 1 < source.length) { out.append("  "); i += 2; continue }
                    out.append(if (source[i] == '\n') '\n' else ' '); i++
                }
                if (i < source.length) { out.append(' '); i++ }
                continue
            }
            out.append(c); i++
        }
        return out.toString()
    }

    /** Index of the `)` closing the `(` at [open], or null when unbalanced. */
    private fun matchingParen(source: String, open: Int): Int? {
        var depth = 0
        var i = open
        while (i < source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    /** 1-based line of [offset]. */
    private fun lineOf(source: String, offset: Int): Int {
        var line = 1
        for (i in 0 until offset.coerceAtMost(source.length)) if (source[i] == '\n') line++
        return line
    }
}
