package com.naviveylin.architecture

/**
 * The layer rules over what a `concept` and a `port` package may import (change
 * `package-structure-as-five-kinds`, task 1.5; spec `package-structure` — "concept is self-contained" and
 * "port holds abstraction only").
 *
 * A rule states the file, the import, the file's declared kind and the rule it contradicts, so a red run is a
 * finding a reader can act on without the check's help. Every rule is evaluated from the declaration and the
 * import names alone: a suppression annotation in the source exempts nothing (task 1.10).
 */
object LayerRules {

    /** The rules this check evaluates, in report order; the green run counts them. */
    val ALL: List<StructureRule> = listOf(
        StructureRule.CONCEPT_IMPORTS_NOTHING_OF_OURS,
        StructureRule.CONCEPT_IMPORTS_NO_ANDROID_TYPE,
        StructureRule.PORT_IMPORTS_NO_ANDROID_TYPE,
        StructureRule.PORT_IMPORTS_ONLY_CONCEPT,
    )

    private const val OURS = "com.naviveylin."

    /** Every finding the [files] carry, given the kind their package is declared as. */
    fun violations(declaration: StructureDeclaration, files: List<SourceFile>): List<StructureViolation> =
        files.flatMap { file ->
            when (declaration.kindOf(file.packageName)) {
                CONCEPT -> conceptFindings(file)
                PORT -> portFindings(declaration, file)
                else -> emptyList()
            }
        }

    /**
     * `concept` is self-contained: it imports nothing of ours from another package and no Android type.
     *
     * `core.ManeuverSymbols` and `core.CarSurfaceHost` are today's exceptions; they are baseline entries, not
     * declaration rows, because a row addresses a package and declaring them `platform` would move files.
     */
    private fun conceptFindings(file: SourceFile): List<StructureViolation> = file.imports.mapNotNull { name ->
        val rule = when {
            isAndroid(name) -> StructureRule.CONCEPT_IMPORTS_NO_ANDROID_TYPE
            name.startsWith(OURS) && packageOf(name) != file.packageName ->
                StructureRule.CONCEPT_IMPORTS_NOTHING_OF_OURS
            else -> null
        } ?: return@mapNotNull null
        violation(file, CONCEPT, rule, name)
    }

    /** `port` holds abstraction only: no Android type, and nothing of ours but a `concept` package. */
    private fun portFindings(declaration: StructureDeclaration, file: SourceFile): List<StructureViolation> =
        file.imports.mapNotNull { name ->
            val importedPackage = packageOf(name)
            val rule = when {
                isAndroid(name) -> StructureRule.PORT_IMPORTS_NO_ANDROID_TYPE
                name.startsWith(OURS) && importedPackage != file.packageName &&
                    declaration.kindOf(importedPackage) != CONCEPT ->
                    StructureRule.PORT_IMPORTS_ONLY_CONCEPT
                else -> null
            } ?: return@mapNotNull null
            violation(file, PORT, rule, name)
        }

    private fun violation(file: SourceFile, kind: String, rule: StructureRule, import: String): StructureViolation =
        StructureViolation(
            rule = rule,
            pointer = file.path,
            message = "${file.path} is declared '$kind' and imports $import, which '${rule.id}' forbids",
        )

    private fun isAndroid(name: String): Boolean =
        name.startsWith("android.") || name.startsWith("androidx.")

    /** The package an imported symbol sits in: `com.a.b.C` and `com.a.b.c` both name `com.a.b`. */
    private fun packageOf(name: String): String = name.substringBeforeLast('.')

    private const val CONCEPT = "concept"
    private const val PORT = "port"
}
