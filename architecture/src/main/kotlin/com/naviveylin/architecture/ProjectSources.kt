package com.naviveylin.architecture

import com.lemonappdev.konsist.api.Konsist
import java.io.File

/** The project root, found the way Konsist finds it: the nearest directory above that carries `settings.gradle.kts`. */
object ProjectRoot {

    val dir: File by lazy {
        var candidate: File? = File("").absoluteFile
        while (candidate != null && !File(candidate, "settings.gradle.kts").isFile) {
            candidate = candidate.parentFile
        }
        requireNotNull(candidate) { "no settings.gradle.kts above ${File("").absolutePath}" }
    }

    fun resolve(path: String): File = File(dir, path)

    /** The declaration as the tree ships it, comment lines and all. */
    fun declarationText(): String = declarationText(DECLARATION_PATH)

    /**
     * The declaration a given path ships, comment lines and all. A missing or unreadable file is refused by
     * name: the check cannot tell a tree that has no structure from a tree whose structure is empty, so it
     * reports failure instead of passing on no evidence (change `package-structure-as-five-kinds`, task 1.11).
     */
    fun declarationText(path: String): String {
        val file = resolve(path)
        if (!file.isFile) {
            throw DeclarationParseError(
                "$path: the structure declaration is missing — a check that cannot read the declaration " +
                    "reports failure, never a pass"
            )
        }
        return try {
            file.readText()
        } catch (error: java.io.IOException) {
            throw DeclarationParseError("$path: the structure declaration is unreadable (${error.message})")
        }
    }
}

/**
 * The files the check reads: the main **and** test sources of the three modules the structure covers (change
 * `package-structure-rules-and-suite`, task 2.2; spec `architecture-test-suite` — "The check reads every source
 * set of the three modules"). Konsist reads source text, so `:app` — an application module no test classpath
 * reaches — is covered like the others, and a violation in a test source is a violation like any other: the
 * rule is about the package a file sits in, not about the artifact it compiles into.
 */
object ProjectSources {

    /** The source roots the structure covers, as a path fragment that survives an absolute path. */
    val sourceRoots: List<String> = listOf(
        "app/src/main/java/",
        "auto/src/main/java/",
        "core/src/main/java/",
        "app/src/test/java/",
        "auto/src/test/java/",
        "core/src/test/java/",
    )

    fun sourceFiles(): List<SourceFile> =
        Konsist.scopeFromProject().files
            .mapNotNull { file ->
                val path = projectRelative(file.path) ?: return@mapNotNull null
                SourceFile(
                    path = path,
                    packageName = file.packagee?.name ?: "",
                    imports = file.imports.map { it.name },
                )
            }
            .sortedBy { it.path }

    /** The baseline as the tree ships it, comment lines and all (task 1.7). */
    fun baselineText(): String = ProjectRoot.resolve(BASELINE_PATH)
        .readText()

    /**
     * The path from the project root, or `null` when the file is not one of the tree's covered sources.
     *
     * Konsist's project scope descends into every directory under the root that carries no `.git`, so a loop
     * item's scratch tree, a nested checkout and any other copy of `app/src/main/java/…` arrive here as source
     * files of this project: 2 285 of them in the main checkout on 2026-11-14, which made a green run read
     * `files=2623` and report a `com/example/data/Basemap.kt` no module of this tree has (case
     * `aNestedCopyOfASourceRootIsNotTheTree`). A path is therefore cut at the project root first, and only a path
     * that starts with a covered source root after that cut is the tree's. Konsist reports a path either from the
     * project root or from the filesystem, so both shapes are read — the second one refused, because an absolute
     * path outside the root is some other tree.
     */
    internal fun projectRelative(path: String): String? {
        val normalized = path.replace('\\', '/')
        val relative = when {
            normalized.startsWith("/") -> projectRootPrefixes()
                .firstOrNull { normalized.startsWith("$it/") }
                ?.let { normalized.removePrefix("$it/") }
                ?: return null
            else -> normalized
        }
        return sourceRoots.firstOrNull { relative.startsWith(it) }?.let { relative }
    }

    /**
     * The main sources alone — the scope [LayerRules]' import rules judge, while the shape and declaration rules
     * judge every source set (see [LayerRules]).
     */
    fun mainSourceFiles(): List<SourceFile> = sourceFiles().filterNot { it.isTestSource }

    /** The project root in both shapes a file system reports it, so a symlinked checkout matches too. */
    private fun projectRootPrefixes(): List<String> =
        listOf(ProjectRoot.dir.absolutePath, runCatching { ProjectRoot.dir.canonicalPath }.getOrDefault(""))
            .map { it.replace('\\', '/').trimEnd('/') }
            .filter { it.isNotEmpty() && it.startsWith("/") }
            .distinct()
}
