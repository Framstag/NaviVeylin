package com.naviveylin.build.i18n

/** One hardcoded user-facing string literal in a UI text position. */
data class HardcodedStringFinding(
    /** Path as reported to the build (repo-relative). */
    val path: String,
    /** 1-based line of the literal. */
    val line: Int,
    /** The offending literal, without its quotes. */
    val literal: String,
    /** What matched: a text position, an interpolated literal, or a channel name. */
    val reason: String
)

/**
 * Build gate for the i18n rule (spec `i18n-l10n` — All user-facing text is
 * translatable): a user-facing string is a resource, never a Kotlin literal.
 *
 * The two gates this replaces were blind in three ways, and each blind spot is a
 * defect the rule exists for (`TODO.md` §80a/§104/§105, change
 * `fix-remaining-untranslated-strings`):
 *
 * 1. **Wrong module.** The inline tasks scanned `app/src/main/java` and
 *    `auto/src/main/java` only, so a literal in `:core` (the shared details
 *    resolver's generic title) was invisible. This scanner takes whatever source
 *    roots the calling task hands it.
 * 2. **Interpolated literals excused themselves.** Any literal containing `${`
 *    was skipped as a "dynamic template", which is exactly the shape of the
 *    favorites count (`"$favCount favorite${if (favCount != 1) "s" else ""}"`) —
 *    hand-rolled English pluralization that no translator can reach. The rule now
 *    exempts a literal only when the text *outside* its substitutions carries no
 *    letter at all: `"$zoomLevel"` and `"${a}-${b}"` stay exempt, `"$n favorite"`
 *    fails.
 * 3. **A channel's name was not a text position.** `NotificationChannel(id, name,
 *    description)` was matched by no pattern, although the name and description
 *    are user-visible text (Android shows them in Settings and re-applies them on
 *    every `createNotificationChannel`).
 *
 * The remaining exemptions are the ones the replaced tasks carried on purpose —
 * `%`-format templates, pure-symbol separators, and a single camelCase identifier
 * (an animation/label key such as `compassRotation`) — kept explicit so the rule's
 * intent stays readable and the false-positive surface stays the same.
 *
 * Pure and dependency-free, so it is unit-tested in `buildSrc` alongside the
 * license logic and `CoordinateLogScanner`; the Gradle task only points it at the
 * source roots.
 */
object HardcodedStringScanner {

    /** Text positions whose next literal is display text. */
    private val TEXT_POSITION = Regex(
        """(?:Text\(|text\s*=|label\s*=|title\s*=|contentDescription\s*=|placeholder\s*=|hint\s*=|""" +
            """\.setTitle\(|\.addText\(|\.setText\(|\.setContentTitle\(|\.setContentText\(|description\s*=)"""
    )

    /** `NotificationChannel(` — its name/description arguments are display text. */
    private val CHANNEL_POSITION = Regex("""NotificationChannel\s*\(""")

    /** A conditional between the position and the literal: `= if (x) "A"`, `else "B"`. */
    private val CONDITIONAL = Regex("""^(?:if\s*\([^)]*\)|else)\s*""")

    /** A Kotlin string literal, stopping at the first unescaped quote. */
    private val LITERAL = Regex("""^"((?:[^"\\\n]|\\.)*)"""")

    /** `$name` or `${expression}` inside a literal. */
    private val INTERPOLATION = Regex("""\$(?:\{[^}]*\}|[A-Za-z_][A-Za-z0-9_]*)""")

    private val SYMBOLS_ONLY = Regex("""^[^A-Za-z0-9]+$""")
    private val CAMEL_IDENTIFIER = Regex("""^[a-z][a-zA-Z0-9]*$""")
    private val LETTER = Regex("""[A-Za-z]""")

    /** Find every offending literal in [source]. */
    fun scan(path: String, source: String): List<HardcodedStringFinding> {
        val findings = mutableListOf<HardcodedStringFinding>()
        var line = 1
        for (lineText in source.lineSequence()) {
            if (lineText.isNotBlank()) {
                lineFindings(lineText).forEach { (literal, reason) ->
                    findings += HardcodedStringFinding(
                        path = path,
                        line = line,
                        literal = literal,
                        reason = reason
                    )
                }
            }
            line++
        }
        return findings
    }

    /** The offending literals of one source line: literal to reason. */
    private fun lineFindings(line: String): List<Pair<String, String>> {
        val found = mutableListOf<Pair<String, String>>()
        for (position in TEXT_POSITION.findAll(line)) {
            val literal = literalAfter(line, position.range.last + 1) ?: continue
            if (isExempt(literal)) continue
            found += literal to reasonFor(literal)
        }
        for (position in CHANNEL_POSITION.findAll(line)) {
            val arguments = line.substring(position.range.last + 1)
            for (argument in topLevelArguments(arguments).drop(1)) {
                val literal = firstLiteral(argument) ?: continue
                if (isExempt(literal)) continue
                found += literal to "notification-channel name/description"
            }
        }
        return found
    }

    /** The first literal in [rest] when only a conditional stands before it. */
    private fun literalAfter(line: String, from: Int): String? {
        var rest = line.substring(from)
        rest = rest.trimStart()
        CONDITIONAL.find(rest)?.let { rest = rest.substring(it.range.last + 1).trimStart() }
        return LITERAL.find(rest)?.groupValues?.get(1)
    }

    /**
     * [text]'s arguments split on top-level commas (parenthesis depth 1), so a
     * nested call's commas do not split an argument.
     */
    private fun topLevelArguments(text: String): List<String> {
        val arguments = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var inString = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inString -> {
                    if (c == '\\') {
                        current.append(c)
                        i++
                        if (i < text.length) current.append(text[i])
                    } else {
                        current.append(c)
                        if (c == '"') inString = false
                    }
                }
                c == '"' -> {
                    inString = true
                    current.append(c)
                }
                c == '(' || c == '[' || c == '{' -> {
                    depth++
                    current.append(c)
                }
                c == ')' && depth == 0 -> {
                    arguments += current.toString()
                    return arguments
                }
                c == ')' || c == ']' || c == '}' -> {
                    depth--
                    current.append(c)
                }
                c == ',' && depth == 0 -> {
                    arguments += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        arguments += current.toString()
        return arguments
    }

    /** The first literal inside [argument], if any. */
    private fun firstLiteral(argument: String): String? =
        LITERAL.find(argument.trimStart())?.groupValues?.get(1)

    /** True when [literal] is deliberately not display text (see the class KDoc). */
    private fun isExempt(literal: String): Boolean {
        if (literal.isEmpty()) return true
        if (literal.contains('%')) return true
        if (SYMBOLS_ONLY.matches(literal)) return true
        if (CAMEL_IDENTIFIER.matches(literal)) return true
        // A pure template (only substitutions, no words of its own) is a value,
        // not text: `"$zoomLevel"`, `"${a}-${b}"` stay exempt; `"$n favorite"` does not.
        val outside = literal.replace(INTERPOLATION, "")
        return literal != outside && !LETTER.containsMatchIn(outside)
    }

    private fun reasonFor(literal: String): String =
        if (literal.contains('$')) "interpolated display text" else "hardcoded display text"

    /** Human-readable report for a Gradle failure. */
    fun report(findings: List<HardcodedStringFinding>): String = buildString {
        append("Hardcoded user-facing strings found (spec: i18n-l10n — All user-facing text is\n")
        append("translatable). Move them to the module's res/values/strings.xml (with values-de),\n")
        append("a count-dependent string to a <plurals> entry rendered with the count as a format\n")
        append("argument, and a generic/shared title to the calling surface — a shared module owns\n")
        append("no wording. See guidelines/UI.md — Internationalisation / Localisation.\n\n")
        findings.forEach {
            append("${it.path}:${it.line}: ${it.reason} — \"${it.literal}\"\n")
        }
    }
}
