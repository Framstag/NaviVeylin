package com.naviveylin.architecture

/** The ratchet's exemptions: the declaration's companion file (change `package-structure-as-five-kinds`, task 1.7). */
const val BASELINE_PATH: String = "architecture/baseline.tsv"

/**
 * One `architecture/baseline.tsv` row: the subject it exempts — a source file, or a declaration row — the rule
 * it is exempted from, and the change that removes the entry by moving or fixing its subject.
 *
 * A row-granularity entry writes its subject as `row <module>\t<package>`, which is the shape
 * [StructureViolation.subject] carries for a rule that judges a row; a file-granularity entry writes the file's
 * project-relative path. The rest of the entry — rule and owning change — is the same for both shapes, so one
 * refusal covers both and the check keeps one notion of a stale entry.
 */
data class BaselineEntry(val line: Int, val file: String, val rule: String, val change: String) {

    /** Whether this entry exempts a declaration row rather than a file. */
    val isRow: Boolean get() = file.startsWith(ROW_PREFIX)

    /** The pair an exemption names: the subject, and the rule it is exempted from. */
    val key: String get() = key(file, rule)

    /** The row as a reader can find it again: `architecture/baseline.tsv:<line> <subject> (<rule>)`. */
    val pointer: String get() = "$BASELINE_PATH:$line ${file.replace('\t', ' ')} ($rule)"

    companion object {
        const val ROW_PREFIX: String = "row "

        fun key(file: String, rule: String): String = "$file\t$rule"
    }
}

/**
 * The baseline (spec `architecture-test-suite` — "The baseline is shrink-only"): every file that does not yet
 * satisfy a rule is a named entry carrying the change that moves it, and this file is the only exemption the
 * check honours — a suppression annotation in the source exempts nothing (task 1.10).
 *
 * Both refusals need the run's own findings: an entry naming no change fails, and an entry whose file no longer
 * violates the rule it is exempted from fails. An entry naming a rule the check does not evaluate is stale for
 * the same reason — it exempts nothing, so the baseline cannot grow past the rules that exist.
 */
data class Baseline(
    val entries: List<BaselineEntry>,
    val unparsableLines: List<Int> = emptyList(),
) {

    private val claimedKeys: Set<String> = entries.map { it.key }.toSet()

    /** Whether this baseline exempts [violation]: a rule the check evaluates, and the subject it addresses. */
    fun claims(violation: StructureViolation): Boolean =
        violation.rule in StructureRules.ALL &&
            BaselineEntry.key(violation.subject, violation.rule.id) in claimedKeys

    /** The entries this run contradicts: no owning change, or a subject that no longer violates its rule. */
    fun refusals(findings: List<StructureViolation>): List<StructureViolation> {
        val live = findings.map { BaselineEntry.key(it.subject, it.rule.id) }.toSet()
        return unparsableLines.map { line ->
            StructureViolation(
                rule = StructureRule.BASELINE_ROW_UNPARSABLE,
                pointer = "$BASELINE_PATH:$line",
                message = "${StructureRule.BASELINE_ROW_UNPARSABLE.id}: $BASELINE_PATH:$line does not carry the " +
                    "three tab-separated fields (subject, rule, owning change) an exemption needs",
            )
        } + entries.flatMap { entry ->
            buildList {
                if (entry.change.isBlank() || entry.change == "-") {
                    add(
                        StructureViolation(
                            rule = StructureRule.EXEMPTION_WITHOUT_CHANGE,
                            pointer = entry.pointer,
                            message = "${StructureRule.EXEMPTION_WITHOUT_CHANGE.id}: ${entry.pointer} names no " +
                                "change that removes it — every exemption carries the change that moves its file",
                        ),
                    )
                }
                if (entry.key !in live) {
                    val rule = if (entry.isRow) StructureRule.STALE_ROW_EXEMPTION else StructureRule.STALE_EXEMPTION
                    add(
                        StructureViolation(
                            rule = rule,
                            pointer = entry.pointer,
                            message = "${rule.id}: ${entry.pointer} exempts the " +
                                (if (entry.isRow) "row" else "file") + " from '${entry.rule}', which it no longer " +
                                "violates — remove the entry, so the baseline can only shrink",
                        ),
                    )
                }
            }
        }
    }

    companion object {

        /**
         * Read a baseline from the text of `architecture/baseline.tsv`; comment lines start with `#`. A file
         * entry carries three tab-separated fields (`file`, `rule`, owning change) and a row entry four
         * (`row <module>`, `<package>`, `rule`, owning change) — the leading `row` keyword is what distinguishes
         * the shapes, and any other field count is refused rather than guessed at.
         */
        fun parse(text: String): Baseline {
            val entries = mutableListOf<BaselineEntry>()
            val unparsableLines = mutableListOf<Int>()
            text.lineSequence().forEachIndexed { index, raw ->
                val line = index + 1
                if (raw.isBlank() || raw.startsWith("#")) return@forEachIndexed
                val fields = raw.split('\t').map { it.trim() }
                val rowShaped = fields.firstOrNull()?.startsWith(BaselineEntry.ROW_PREFIX) == true
                val parsed = when {
                    rowShaped && fields.size == 4 -> BaselineEntry(
                        line = line,
                        file = "${fields[0]}\t${fields[1]}",
                        rule = fields[2],
                        change = fields[3],
                    )
                    !rowShaped && fields.size == 3 -> BaselineEntry(
                        line = line,
                        file = fields[0],
                        rule = fields[1],
                        change = fields[2],
                    )
                    else -> null
                }
                if (parsed == null) {
                    unparsableLines += line
                } else {
                    entries += parsed
                }
            }
            return Baseline(entries, unparsableLines)
        }
    }
}
