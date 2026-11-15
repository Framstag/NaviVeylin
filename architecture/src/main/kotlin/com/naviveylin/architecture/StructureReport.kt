package com.naviveylin.architecture

/**
 * The result of one structure run (change `package-structure-as-five-kinds`, task 1.8; spec
 * `architecture-test-suite` — "A green run reports what it verified").
 *
 * The four figures are what a passing run states: the declaration rows read, the source files classified, the
 * exemptions still outstanding, and the rules evaluated. A run that verified nothing cannot pass silently,
 * because a declaration with no rows is a refusal rather than an empty pass.
 */
data class StructureReport(
    val rows: Int,
    val files: Int,
    val exemptions: Int,
    val rules: Int,
    val violations: List<StructureViolation>,
) {

    /** A run passes only when nothing is left to report: an exemption is counted, never silently dropped. */
    val passed: Boolean get() = violations.isEmpty()

    /** The run's figures, then every violation it reports. */
    fun summary(): String = buildString {
        append("rows=$rows files=$files exemptions=$exemptions rules=$rules")
        violations.forEach { append('\n').append(it.message) }
    }

    companion object {

        /** Evaluate the declaration, its rules and the baseline over [files]. */
        fun of(declaration: StructureDeclaration, files: List<SourceFile>, baseline: Baseline): StructureReport {
            val findings = LayerRules.violations(declaration, files.filterNot { it.isTestSource }) +
                DeclarationRules.violations(declaration, files)
            val exemptions = baseline.entries.count { entry ->
                findings.any { it.subject == entry.file && it.rule.id == entry.rule }
            }
            val violations = StructureCheck(declaration).violations(files) +
                baseline.refusals(findings) +
                findings.filterNot { baseline.claims(it) }
            return StructureReport(
                rows = declaration.rows.size,
                files = files.size,
                exemptions = exemptions,
                rules = StructureRules.ALL.size,
                violations = violations,
            )
        }
    }
}
