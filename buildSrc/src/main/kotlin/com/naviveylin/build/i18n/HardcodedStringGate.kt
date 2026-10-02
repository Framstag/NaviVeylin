package com.naviveylin.build.i18n

import java.io.File
import java.util.Locale

/**
 * Source-tree helper for the i18n gate's Gradle tasks: walks the given source
 * roots, scans every Kotlin file with [HardcodedStringScanner] and reports the
 * findings with repo-relative paths.
 *
 * Kept in `buildSrc` (with its scanner) so the three module tasks carry the same
 * rule and the same message instead of three copies of the walk.
 */
object HardcodedStringGate {

    /** Scan [sourceDirs], reporting paths relative to [projectDir]. */
    fun scanTrees(sourceDirs: List<File>, projectDir: File): List<HardcodedStringFinding> =
        sourceDirs.filter { it.exists() }.flatMap { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension.equals("kt", ignoreCase = true) }
                .sortedBy { it.path.lowercase(Locale.ROOT) }
                .flatMap { file ->
                    HardcodedStringScanner.scan(
                        file.relativeTo(projectDir).path,
                        file.readText()
                    ).asSequence()
                }
                .toList()
        }
}
