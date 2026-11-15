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
    fun declarationText(): String = resolve(DECLARATION_PATH).readText()
}

/**
 * The files the check reads: the main sources of the three modules the structure covers. Konsist reads
 * source text, so `:app` — an application module no test classpath reaches — is covered like the others.
 *
 * The test source sets are a later task of the change (2.2); this list is main sources only.
 */
object ProjectSources {

    /** The source roots the structure covers, as a path fragment that survives an absolute path. */
    val mainSourceRoots: List<String> = listOf(
        "app/src/main/java/",
        "auto/src/main/java/",
        "core/src/main/java/",
    )

    fun mainSourceFiles(): List<SourceFile> =
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
        return mainSourceRoots.firstOrNull { relative.startsWith(it) }?.let { relative }
    }

    /** The project root in both shapes a file system reports it, so a symlinked checkout matches too. */
    private fun projectRootPrefixes(): List<String> =
        listOf(ProjectRoot.dir.absolutePath, runCatching { ProjectRoot.dir.canonicalPath }.getOrDefault(""))
            .map { it.replace('\\', '/').trimEnd('/') }
            .filter { it.isNotEmpty() && it.startsWith("/") }
            .distinct()
}
