package com.naviveylin.architecture

/**
 * The layer rules over what a `concept`, a `port` and a `feature` package may import (change
 * `package-structure-as-five-kinds`, task 1.5, and change `package-structure-rules-and-suite`, task 1.6; spec
 * `package-structure` — "concept is self-contained", "port holds abstraction only", "features are mutually
 * disjunct" and "a feature is Android-free").
 *
 * A rule states the file, the import, the file's declared kind and the rule it contradicts, so a red run is a
 * finding a reader can act on without the check's help. Every rule is evaluated from the declaration and the
 * import names alone: a suppression annotation in the source exempts nothing (task 1.10).
 *
 * These rules judge **main** sources: the layers exist so that the shipped code stays separable, and a test source
 * sits in the package it tests and imports the framework and the layer above by necessity. Measured 2026-11-15 over
 * the tree's 502 test sources: 423 findings — 221 `feature imports no Android type`, 173 `feature imports nothing
 * upward`, 18 `concept imports no Android type`, 4 `concept imports nothing of ours` and 7 `feature imports another
 * feature` — and not one of them a defect of the shipped structure. [DeclarationRules] and [StructureCheck] judge
 * every source set, so a test file in an undeclared package still fails.
 */
object LayerRules {

    /** The rules this check evaluates, in report order; the green run counts them. */
    val ALL: List<StructureRule> = listOf(
        StructureRule.CONCEPT_IMPORTS_NOTHING_OF_OURS,
        StructureRule.CONCEPT_IMPORTS_NO_ANDROID_TYPE,
        StructureRule.PORT_IMPORTS_NO_ANDROID_TYPE,
        StructureRule.PORT_IMPORTS_ONLY_CONCEPT,
        StructureRule.FEATURE_IMPORTS_ANOTHER_FEATURE,
        StructureRule.FEATURE_IMPORTS_NO_ANDROID_TYPE,
        StructureRule.FEATURE_IMPORTS_NOTHING_UPWARD,
    )

    private const val OURS = "com.naviveylin."

    /** Every finding the [files] carry, given the kind their package is declared as. */
    fun violations(declaration: StructureDeclaration, files: List<SourceFile>): List<StructureViolation> =
        files.flatMap { file ->
            when (declaration.kindOf(file.packageName)) {
                CONCEPT -> conceptFindings(file)
                PORT -> portFindings(declaration, file)
                FEATURE -> featureFindings(declaration, file)
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

    /**
     * `feature/<name>` is a unit: it imports `concept` and `port`, never another feature, never upward into
     * `platform` or `shell`, and no Android type. Two features communicate through `shell`, which is the only
     * kind that knows several of them at once.
     */
    private fun featureFindings(declaration: StructureDeclaration, file: SourceFile): List<StructureViolation> =
        file.imports.mapNotNull { name ->
            val rule = when {
                isAndroid(name) -> StructureRule.FEATURE_IMPORTS_NO_ANDROID_TYPE
                !name.startsWith(OURS) -> null
                else -> {
                    val owner = rowOwning(declaration, name)
                    when {
                        owner == null || owner.packageName == file.packageName -> null
                        owner.kind == FEATURE -> StructureRule.FEATURE_IMPORTS_ANOTHER_FEATURE
                        owner.kind == PLATFORM || owner.kind == SHELL ->
                            StructureRule.FEATURE_IMPORTS_NOTHING_UPWARD
                        else -> null
                    }
                }
            } ?: return@mapNotNull null
            violation(file, FEATURE, rule, name)
        }

    /**
     * The declaration row an imported symbol belongs to — [StructureDeclaration.rowOwning], which a finding of
     * [DeclarationRules] needs for the same reason this one does.
     */
    private fun rowOwning(declaration: StructureDeclaration, import: String): DeclarationRow? =
        declaration.rowOwning(import)

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
    private const val FEATURE = "feature"
    private const val PLATFORM = "platform"
    private const val SHELL = "shell"
}
