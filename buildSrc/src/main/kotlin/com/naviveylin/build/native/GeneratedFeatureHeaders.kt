package com.naviveylin.build.native

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * The generated `*Features.h` headers of the libosmscout modules — the build gate over the submodule's
 * source tree.
 *
 * libosmscout renders each of its feature headers from the tracked `*.cmake` template beside it, and every
 * module's target lists its own source tree *before* its binary include directory (`cmake/ProjectConfig.cmake`,
 * `target_include_directories(... PRIVATE include ${PROJECT_SOURCE_DIR}/include ${CMAKE_CURRENT_BINARY_DIR}/include
 * … ${CMAKE_CURRENT_SOURCE_DIR}/include)`), so a generated header that sits in the submodule source tree wins over
 * the one the build generates — in *every* build of that working tree. A host build then compiles
 * `libosmscout-map*` against the Android configuration's feature macros and reports false failures and false
 * passes for every test keyed on them (§202, issue #136: with Pango disabled, the Cairo text test measured the
 * substituted family as the configured font file and failed `REQUIRE(differs)`).
 *
 * The native build renders the headers into the app's CMake build tree instead (`app/src/main/cpp/CMakeLists.txt`,
 * `OSMSCOUT_FEATURES_DIR`); [`CheckSubmoduleFeatureHeaders`] is the gate over the source tree, and it removes what
 * it finds, because a copy here is what the very build it gates would otherwise have to repair — an existing
 * working tree keeps its headers until the app build removes them (§202).
 */
object GeneratedFeatureHeaders {

    /**
     * A generated header is one whose own `.cmake` template is its sibling — which is exactly how the build
     * decides to render it, and which never matches a header the submodule tracks: its Android copies of
     * `CoreFeatures.h` and `MapFeatures.h` (`Android/OsmScoutLib/jni/include/config/`) sit beside no template.
     *
     * @param candidates the scanned files whose name ends in `Features.h`, relative to the scanned root
     * @param files every scanned file, same relative form — the template's existence is read from it
     */
    fun generated(candidates: Collection<String>, files: Collection<String>): List<String> =
        candidates.filter { it.endsWith("Features.h") && "$it.cmake" in files }.sorted()

    /**
     * Scans the submodule source tree and returns the generated feature headers in it, relative to
     * [submoduleDir] with `/` separators.
     *
     * Only the modules' `include` roots are walked: the generated header is the sibling of its template, so it
     * can only be there, and the submodule's own build trees (`build/`, `hostbuild/`) hold no template — walking
     * them would fingerprint gigabytes for a hit that cannot happen.
     */
    fun find(submoduleDir: File): List<String> {
        val root = submoduleDir.absoluteFile
        val candidates = mutableListOf<String>()
        val files = mutableListOf<String>()

        for (module in submoduleDir.listFiles().orEmpty()) {
            val includeDir = File(module, "include")
            if (!includeDir.isDirectory) {
                continue
            }
            for (file in includeDir.walkTopDown()) {
                if (!file.isFile) {
                    continue
                }
                val relative = file.absoluteFile.relativeTo(root).invariantSeparatorsPath
                files += relative
                if (file.name.endsWith("Features.h")) {
                    candidates += relative
                }
            }
        }

        return generated(candidates, files)
    }

    /**
     * The message of a failing gate: what was removed, and why a copy in the source tree is not a local detail.
     *
     * @param paths the generated headers, as [find] returns them
     * @param submoduleDir the submodule's directory, the prefix of every reported file
     */
    fun report(paths: List<String>, submoduleDir: String): String = buildString {
        appendLine("Generated libosmscout feature headers were present in the submodule source tree and have been")
        appendLine("removed:")
        paths.forEach { appendLine("  $submoduleDir/$it") }
        appendLine()
        appendLine("A generated header here wins over the one this build renders into the app's CMake build tree")
        appendLine("(app/src/main/cpp/CMakeLists.txt, OSMSCOUT_FEATURES_DIR) — every module's target lists its source")
        appendLine("tree before its binary include directory — in every build of this working tree. A host build then")
        appendLine("compiles libosmscout against the Android configuration's feature macros and reports false failures")
        appendLine("and false passes for every test keyed on them (§202). Re-run the build: the next one finds none")
        appendLine("here.")
    }
}

/**
 * `:app:checkSubmoduleFeatureHeaders` — removes every generated feature header the libosmscout source tree holds
 * and fails the build naming them (see [GeneratedFeatureHeaders]).
 *
 * It removes because a gate that only refuses would block the build that repairs the tree: the copies are the
 * app build's own generated artifacts, `preBuild` runs before the native configure, and the configure is what
 * writes the build-tree headers. One failing build per working tree, then green — no manual step.
 *
 * The submodule directory is `@Internal` on purpose: fingerprinting it would hash the whole vendored tree,
 * including its build directories, on every build. The scan itself reads the modules' `include` roots only, and
 * a gate has no outputs, so the task runs whenever it is in the graph.
 */
abstract class CheckSubmoduleFeatureHeaders : DefaultTask() {

    @get:Internal
    abstract val submoduleDir: DirectoryProperty

    @TaskAction
    fun check() {
        val dir = submoduleDir.get().asFile
        if (!dir.isDirectory) {
            throw GradleException(
                "libosmscout submodule not found at $dir. " +
                    "Initialize the submodule first: git submodule update --init --recursive"
            )
        }

        val generated = GeneratedFeatureHeaders.find(dir)
        if (generated.isNotEmpty()) {
            generated.forEach { File(dir, it).delete() }
            throw GradleException(GeneratedFeatureHeaders.report(generated, dir.path))
        }
    }
}
