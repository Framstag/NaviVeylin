package com.naviveylin.architecture

/**
 * The rules a declaration row answers rather than a file's imports (change `package-structure-rules-and-suite`,
 * task 1.7; spec `package-structure` — "a platform package mirrors a feature" and "shell is the only kind that
 * hosts several features").
 *
 * Two of the three judge rows and one judges files, which is why the mirror rule's finding carries a row as its
 * subject: a `platform` row whose `feature/<name>` is not declared is one finding about the package, not one
 * finding per file. That granularity is what lets the day-one declaration be counted — a per-file mirror rule
 * needs an entry per file of every unmirrored platform package, 149 of them against 7 rows today.
 *
 * The mirror pairs by **name**, not by path: `platform/<name>` ↔ `feature/<name>`. A row whose package is not
 * `…platform.<name>` is a platform package before the rename, and a `feature` row whose package is not
 * `…feature.<name>` does not mirror a platform row — which is exactly the day-one state, where no package carries
 * either prefix.
 */
object DeclarationRules {

    /** The rules this object evaluates, in report order; the green run counts them with [LayerRules.ALL]. */
    val ALL: List<StructureRule> = listOf(
        StructureRule.MIRROR,
        StructureRule.PLATFORM_IMPORTS_SIBLING_PLATFORM,
        StructureRule.HOSTS_SEVERAL_FEATURES,
    )

    /** Every finding the declaration and the [files] carry, mirror rule first, then the file-level rules. */
    fun violations(
        declaration: StructureDeclaration,
        files: List<SourceFile>,
    ): List<StructureViolation> = mirrorFindings(declaration, files) +
        files.sortedBy { it.path }.flatMap { file -> declarationRuleFindings(declaration, file) }

    /**
     * A `platform/<name>` row names a feature a `feature/<name>` row declares, and the package's files reach that
     * feature. The finding addresses the row: a reader fixes a row, and one entry exempts the whole package.
     *
     * A row whose package holds no file is silent here — [StructureRule.ROW_WITHOUT_PACKAGE] already refuses it,
     * and two findings for one row would cost two entries to exempt.
     */
    private fun mirrorFindings(declaration: StructureDeclaration, files: List<SourceFile>): List<StructureViolation> {
        val features = declaration.rows.filter { it.kind == FEATURE && it.feature != NO_FEATURE }
        return declaration.rows
            .filter { it.kind == PLATFORM && it.feature != NO_FEATURE }
            .mapNotNull { row ->
                val packageFiles = files.filter { it.packageName == row.packageName }
                val featureRows = features.filter { it.feature == row.feature && isMirrorNamed(it.packageName) }
                val reaches = packageFiles.any { file ->
                    file.imports.any { name -> featureRows.any { belongsTo(name, it.packageName) } }
                }
                when {
                    packageFiles.isEmpty() -> null
                    featureRows.isEmpty() -> mirrorViolation(
                        row,
                        "${StructureRule.MIRROR.id}: ${row.pointer} is declared 'platform' and names feature " +
                            "${row.feature}, and no row declares $FEATURE/${row.feature} — a platform package " +
                            "mirrors a feature",
                    )
                    !reaches -> mirrorViolation(
                        row,
                        "${StructureRule.MIRROR.id}: ${row.pointer} is declared 'platform' with feature " +
                            "${row.feature}, and no file of ${row.packageName} imports " +
                            "${featureRows.first().packageName} — a platform package mirrors a feature it reaches",
                    )
                    else -> null
                }
            }
    }

    private fun mirrorViolation(row: DeclarationRow, message: String): StructureViolation = StructureViolation(
        rule = StructureRule.MIRROR,
        pointer = row.pointer,
        message = message,
        subject = BaselineEntry.ROW_PREFIX + row.module + "\t" + row.packageName,
    )

    /** The file-level declaration rules, by the kind the file's package is declared as. */
    private fun declarationRuleFindings(
        declaration: StructureDeclaration,
        file: SourceFile,
    ): List<StructureViolation> = when (declaration.kindOf(file.packageName)) {
        PLATFORM -> siblingPlatformFindings(declaration, file) + hostingFindings(declaration, file)
        null, SHELL -> emptyList()
        else -> hostingFindings(declaration, file)
    }

    /**
     * `platform/<name>` serves one feature: a file of such a package that reaches another `platform/<other>`
     * package fails. Both sides are read from the name shape, because that is what pairs a platform package with
     * its feature — a `ui.map` file reaching `ui.route` is not the pairing this rule is about, and reporting it
     * would name a structure a later task has yet to create.
     */
    private fun siblingPlatformFindings(
        declaration: StructureDeclaration,
        file: SourceFile,
    ): List<StructureViolation> {
        if (!isMirrorNamed(file.packageName)) return emptyList()
        return file.imports.mapNotNull { name ->
            val owner = declaration.rowOwning(name)
            when {
                owner == null || owner.kind != PLATFORM -> null
                !isMirrorNamed(owner.packageName) -> null
                owner.packageName == file.packageName -> null
                else -> StructureViolation(
                    rule = StructureRule.PLATFORM_IMPORTS_SIBLING_PLATFORM,
                    pointer = file.path,
                    message = "${file.path} is declared 'platform' and imports $name from the platform package " +
                        "${owner.packageName}, which '${StructureRule.PLATFORM_IMPORTS_SIBLING_PLATFORM.id}' " +
                        "forbids — a platform package mirrors its own feature",
                )
            }
        }
    }

    /**
     * `shell` is the only kind that hosts several features: a file of any other kind that names more than one
     * `feature/<name>` or more than one `platform/<name>` package fails, and the finding names them, so a reader
     * sees what to split.
     *
     * The count is over mirror-named packages for the reason [mirrorFindings] gives: the declaration has no
     * `feature/<name>` package before the migration, so a rule keyed on the name reports nothing until the pilot —
     * measured at this base, keying it on the declared kind instead would report 24 files (19 of them tests of one
     * platform package) whose split is the platform migration's task, not this one's.
     */
    private fun hostingFindings(
        declaration: StructureDeclaration,
        file: SourceFile,
    ): List<StructureViolation> {
        val kind = declaration.kindOf(file.packageName)
        val hosted = file.imports
            .mapNotNull { declaration.rowOwning(it) }
            .filter { it.packageName != file.packageName && it.feature != NO_FEATURE }
            .filter { isMirrorNamed(it.packageName) && (it.kind == FEATURE || it.kind == PLATFORM) }
            .map { it.packageName }
            .distinct()
            .sorted()
        if (hosted.size < 2) return emptyList()
        return listOf(
            StructureViolation(
                rule = StructureRule.HOSTS_SEVERAL_FEATURES,
                pointer = file.path,
                message = "${file.path} is declared '$kind' and names ${hosted.size} features " +
                    "(${hosted.joinToString(", ")}) — only 'shell' hosts several features, so this file " +
                    "belongs to '${StructureRule.HOSTS_SEVERAL_FEATURES.id}'",
            ),
        )
    }

    private fun belongsTo(importName: String, packageName: String): Boolean =
        importName == packageName || importName.startsWith("$packageName.")

    /**
     * Whether [packageName] is named `<kind>.<name>` — the shape the mirror pairs, so `com.naviveylin.platform.map`
     * is a `platform/map` package while `com.naviveylin.navigation` is not a `feature/navigation` one.
     */
    private fun isMirrorNamed(packageName: String): Boolean {
        val segments = packageName.split('.')
        return segments.size >= 2 && (segments[segments.size - 2] == FEATURE || segments[segments.size - 2] == PLATFORM)
    }

    private const val FEATURE = "feature"
    private const val PLATFORM = "platform"
    private const val SHELL = "shell"
    private const val NO_FEATURE = "-"
}