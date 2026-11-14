package com.naviveylin.architecture

/** The ratchet's exemptions: the declaration's companion file (change `package-structure-as-five-kinds`, task 1.7). */
const val BASELINE_PATH: String = "architecture/baseline.tsv"

/**
 * One `architecture/baseline.tsv` row: the main source file, the rule it is exempted from, and the change that
 * removes the entry by moving or fixing the file.
 */
data class BaselineEntry(val line: Int, val file: String, val rule: String, val change: String) {

    /** The pair an exemption names: the file, and the rule it is exempted from. */
    val key: String get() = key(file, rule)

    /** The row as a reader can find it again: `architecture/baseline.tsv:<line> <file> (<rule>)`. */
    val pointer: String get() = "$BASELINE_PATH:$line $file ($rule)"

    companion object {
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

    /** Whether this baseline exempts [violation], which the layer rules report against the file's path. */
    fun claims(violation: StructureViolation): Boolean =
        violation.rule in LayerRules.ALL && BaselineEntry.key(violation.pointer, violation.rule.id) in claimedKeys

    /** The entries this run contradicts: no owning change, or a file that no longer violates its rule. */
    fun refusals(findings: List<StructureViolation>): List<StructureViolation> {
        val live = findings.map { BaselineEntry.key(it.pointer, it.rule.id) }.toSet()
        return unparsableLines.map { line ->
            StructureViolation(
                rule = StructureRule.BASELINE_ROW_UNPARSABLE,
                pointer = "$BASELINE_PATH:$line",
                message = "${StructureRule.BASELINE_ROW_UNPARSABLE.id}: $BASELINE_PATH:$line does not carry the " +
                    "three tab-separated fields (file, rule, owning change) an exemption needs",
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
                    add(
                        StructureViolation(
                            rule = StructureRule.STALE_EXEMPTION,
                            pointer = entry.pointer,
                            message = "${StructureRule.STALE_EXEMPTION.id}: ${entry.pointer} exempts the file " +
                                "from '${entry.rule}', which it no longer violates — remove the entry, so the " +
                                "baseline can only shrink",
                        ),
                    )
                }
            }
        }
    }

    companion object {

        /** Read a baseline from the text of `architecture/baseline.tsv`; comment lines start with `#`. */
        fun parse(text: String): Baseline {
            val entries = mutableListOf<BaselineEntry>()
            val unparsableLines = mutableListOf<Int>()
            text.lineSequence().forEachIndexed { index, raw ->
                val line = index + 1
                if (raw.isBlank() || raw.startsWith("#")) return@forEachIndexed
                val fields = raw.split('\t')
                if (fields.size != 3) {
                    unparsableLines += line
                    return@forEachIndexed
                }
                entries += BaselineEntry(
                    line = line,
                    file = fields[0].trim(),
                    rule = fields[1].trim(),
                    change = fields[2].trim(),
                )
            }
            return Baseline(entries, unparsableLines)
        }
    }
}
