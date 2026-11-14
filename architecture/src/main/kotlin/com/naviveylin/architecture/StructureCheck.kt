package com.naviveylin.architecture

/**
 * A source file the check reads: its path from the project root, its package, and the `import` names the layer
 * rules of [LayerRules] are statements about.
 */
data class SourceFile(
    val path: String,
    val packageName: String,
    val imports: List<String> = emptyList(),
)

/** The rules this part of the check evaluates. Each id is what a report names. */
enum class StructureRule(val id: String) {
    /** One row per package (spec `package-structure`, "A source file belongs to exactly one kind"). */
    ONE_ROW_PER_PACKAGE("one-row-per-package"),

    /** A row that names a package the tree no longer has (spec `package-structure`, "The structure
     * declaration is the single machine-readable description"). */
    ROW_WITHOUT_PACKAGE("row-names-a-package-the-tree-lacks"),

    /** Default deny: a file in a package no row claims (spec `package-structure`, "An undeclared
     * package fails by default"). */
    UNDECLARED_PACKAGE("default-deny"),

    /** A declaration with no rows while source files exist (spec `architecture-test-suite`, "The check fails
     * closed"): the check refuses to report an empty rule set as a pass. */
    EMPTY_DECLARATION("empty-declaration"),

    /** A `concept` file importing another of our packages (spec `package-structure`, "concept is
     * self-contained"). */
    CONCEPT_IMPORTS_NOTHING_OF_OURS("concept imports nothing of ours"),

    /** A `concept` file importing the Android framework (spec `package-structure`, "concept is
     * self-contained"). */
    CONCEPT_IMPORTS_NO_ANDROID_TYPE("concept imports no Android type"),

    /** A `port` file importing the Android framework (spec `package-structure`, "port holds abstraction only"). */
    PORT_IMPORTS_NO_ANDROID_TYPE("port imports no Android type"),

    /** A `port` file importing a package that is not `concept` (spec `package-structure`, "port holds
     * abstraction only"). */
    PORT_IMPORTS_ONLY_CONCEPT("port imports only concept"),

    /** A baseline entry whose file no longer violates the rule it exempts (spec `architecture-test-suite`, "The
     * baseline is shrink-only"). */
    STALE_EXEMPTION("stale-exemption"),

    /** A baseline entry naming no change that removes it (spec `architecture-test-suite`, "The baseline is
     * shrink-only"). */
    EXEMPTION_WITHOUT_CHANGE("exemption-without-change"),

    /** A baseline line that is not three tab-separated fields, so the exemption cannot be read. */
    BASELINE_ROW_UNPARSABLE("baseline-row-unparsable"),
}

/** One finding: the rule that refused, the pointer of the file or row it refused, and the sentence. */
data class StructureViolation(val rule: StructureRule, val pointer: String, val message: String)

/**
 * The declaration check. It reads the declaration and a list of source files, so every case can hand
 * it the tree the test means — no test writes into the tree to make a package appear.
 */
class StructureCheck(private val declaration: StructureDeclaration) {

    fun violations(files: List<SourceFile>): List<StructureViolation> {
        if (declaration.rows.isEmpty()) {
            return listOf(
                StructureViolation(
                    rule = StructureRule.EMPTY_DECLARATION,
                    pointer = DECLARATION_PATH,
                    message = "${StructureRule.EMPTY_DECLARATION.id}: $DECLARATION_PATH holds no declaration " +
                        "rows while ${files.size} source files exist — the check cannot determine the structure",
                ),
            )
        }

        val found = mutableListOf<StructureViolation>()

        declaration.rows.groupBy { it.packageName }
            .filterValues { it.size > 1 }
            .toSortedMap()
            .forEach { (packageName, rows) ->
                val pointers = rows.joinToString(" and ") { it.pointer }
                found += StructureViolation(
                    rule = StructureRule.ONE_ROW_PER_PACKAGE,
                    pointer = pointers,
                    message = "${StructureRule.ONE_ROW_PER_PACKAGE.id}: package $packageName is claimed " +
                        "${rows.size} times, by $pointers — one row per package",
                )
            }

        val packagesInTree = files.map { it.packageName }.toSet()
        declaration.rows
            .filter { it.packageName !in packagesInTree }
            .forEach { row ->
                found += StructureViolation(
                    rule = StructureRule.ROW_WITHOUT_PACKAGE,
                    pointer = row.pointer,
                    message = "${StructureRule.ROW_WITHOUT_PACKAGE.id}: ${row.pointer} names package " +
                        "${row.packageName}, and no source file of that package is in the tree",
                )
            }

        val declared = declaration.declaredPackages()
        files
            .filter { it.packageName !in declared }
            .sortedBy { it.path }
            .forEach { file ->
                found += StructureViolation(
                    rule = StructureRule.UNDECLARED_PACKAGE,
                    pointer = file.path,
                    message = "${StructureRule.UNDECLARED_PACKAGE.id}: ${file.path} sits in package " +
                        "${file.packageName}, which no row of $DECLARATION_PATH claims",
                )
            }

        return found
    }
}
