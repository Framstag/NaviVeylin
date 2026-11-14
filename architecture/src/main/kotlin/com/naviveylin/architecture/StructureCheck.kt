package com.naviveylin.architecture

/** A source file the declaration is checked against: its path from the project root, and its package. */
data class SourceFile(val path: String, val packageName: String)

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
}

/** One finding: the rule that refused, the pointer of the file or row it refused, and the sentence. */
data class StructureViolation(val rule: StructureRule, val pointer: String, val message: String)

/**
 * The declaration check. It reads the declaration and a list of source files, so every case can hand
 * it the tree the test means — no test writes into the tree to make a package appear.
 */
class StructureCheck(private val declaration: StructureDeclaration) {

    fun violations(files: List<SourceFile>): List<StructureViolation> {
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
