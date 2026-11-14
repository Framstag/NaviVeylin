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
                SourceFile(path = path, packageName = file.packagee?.name ?: "")
            }
            .sortedBy { it.path }

    /**
     * The path from the project root, or `null` when the file is outside the covered source roots.
     * Konsist reports a path either from the project root or from the filesystem, so both are cut at the
     * root fragment rather than assumed one shape.
     */
    internal fun projectRelative(path: String): String? {
        val normalized = path.replace('\\', '/')
        mainSourceRoots.forEach { root ->
            val at = normalized.indexOf(root)
            if (at >= 0) return normalized.substring(at)
        }
        return null
    }
}
