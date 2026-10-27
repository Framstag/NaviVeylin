package com.naviveylin.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the project's own Kotlin sources to `build-jvm-toolchain`'s requirement "The project's own Kotlin
 * sources call no deprecated quadratic path builder" (scenarios "A marker outline is built with the kept
 * builder" and "The scan is still detectable").
 *
 * `androidx.compose.ui.graphics.Path.quadraticBezierTo` is deprecated in the pinned Compose ui 1.7.6 with
 * "'fun quadraticBezierTo(x1, y1, x2, y2)' is deprecated. Use quadraticTo() for consistency with cubicTo()":
 * the replacement takes the same control and end points, so the call is behaviour-identical. The call site is
 * the defect — every `:app` compile prints the warning, and the name is removed in a later release, at which
 * point the build stops compiling. Nothing in this build turns a Kotlin warning into a failure (the post-fix
 * gate's compile log carries 53 `w:` lines, the pre-fix run 28, mostly opt-in notices in test sources — change
 * `fix-deprecated-quadratic-path-builder`, `evidence/gate.log` and `evidence/head-red-run.log`), so a call site
 * nobody reads stays until an upgrade breaks it.
 *
 * The case reads the repository's own sources rather than the Kotlin compile log, for the reason
 * `GradleScriptDeprecationTest` reads the build scripts: a unit test cannot observe another compilation. The
 * file count and the findings are printed before the assertion, so the JUnit XML's `system-out` carries the
 * measurement of the run that made it, and a finding names its file, line and text.
 */
class ComposeApiDeprecationTest {

    private val repoRoot = File("..").canonicalFile

    @Test
    fun `no project Kotlin source calls a deprecated quadratic path builder`() {
        val sources = kotlinSources()
        assertTrue(
            "the project's Kotlin sources must be found under ${repoRoot.absolutePath} " +
                "(found ${sources.size})",
            sources.size >= 50
        )

        val findings = sources.flatMap { source ->
            findingsIn(source.relativeTo(repoRoot).invariantSeparatorsPath, source.readLines())
        }
        println("files=${sources.size} findings=${findings.size}")
        findings.forEach { println("FINDING $it") }

        assertEquals(
            "a Kotlin source that builds a quadratic Bézier with `$DEPRECATED`/`relative$DEPRECATED` " +
                "must use `quadraticTo`/`relativeQuadraticTo` instead — the pinned Compose ui 1.7.6 deprecates " +
                "the `Bezier` spelling and a later release removes it",
            emptyList<String>(),
            findings
        )
    }

    @Test
    fun `the scan reads the sources a directory named build holds`() {
        val scanned = kotlinSources().map { it.relativeTo(repoRoot).invariantSeparatorsPath }
        println("scanned=${scanned.size}")

        val packageDirectorySources = listOf(
            "app/src/test/java/com/naviveylin/build/ComposeApiDeprecationTest.kt",
            "buildSrc/src/main/kotlin/com/naviveylin/build/testing/BuildFlags.kt"
        )
        val missing = packageDirectorySources.filterNot(scanned::contains)
        assertTrue(
            "the scan must read every `*.kt` the project owns, including the sources a package directory " +
                "named `build` holds — a directory is Gradle output by the marker its project root writes, " +
                "not by its name (read ${scanned.size}, missing $missing)",
            missing.isEmpty()
        )
        assertTrue(
            "the scan must still prune Gradle output: ${scanned.count { it.startsWith("app/build/") }} " +
                "source(s) under app/build/ were read",
            scanned.none { it.startsWith("app/build/") }
        )
    }

    @Test
    fun `the scan is still detectable`() {
        val deprecated = listOf(
            "        path.$DEPRECATED(0f, -hPx, x1 * hPx, y1 * hPx)",
            "        path.relative$DEPRECATED(0f, -hPx, x1 * hPx, y1 * hPx)"
        )
        val kept = listOf(
            "        path.quadraticTo(0f, -hPx, x1 * hPx, y1 * hPx)",
            "        path.relativeQuadraticTo(0f, -hPx, x1 * hPx, y1 * hPx)",
            "    // the deprecated `$DEPRECATED` spelling is refused, see the case above"
        )

        assertTrue(
            "the scan must refuse every deprecated spelling it exists for",
            deprecated.all { findingsIn("probe.kt", listOf(it)).size == 1 }
        )
        assertTrue(
            "the scan must accept the kept spelling and a comment that names the deprecated one",
            kept.all { findingsIn("probe.kt", listOf(it)).isEmpty() }
        )
    }

    /**
     * Every `*.kt` the project owns: the whole tree, minus Gradle's own output and tool state.
     *
     * A directory named `build` is output only when a Gradle project root writes into it — the marker
     * (`build.gradle.kts`/`build.gradle`) in its parent. Pruning by name alone also swallowed the project's own
     * `com.naviveylin.build` package directories, 37 `.kt` files on base `3f972aec` (issue #223), so a passing
     * scan promised coverage of sources it had never read.
     */
    private fun kotlinSources(): List<File> =
        repoRoot.walkTopDown()
            .onEnter { !it.isGradleOutput() && it.name !in SKIPPED_TOOL_DIRECTORIES }
            .filter { it.isFile && it.name.endsWith(".kt") }
            .sorted()
            .toList()

    /** True for a `build` directory a Gradle project root writes into, and for nothing else. */
    private fun File.isGradleOutput(): Boolean =
        name == "build" && parentFile?.let {
            File(it, "build.gradle.kts").isFile || File(it, "build.gradle").isFile
        } == true

    /** One finding per code line that calls a deprecated spelling, as `<path>:<line>: <text>`. */
    private fun findingsIn(path: String, lines: List<String>): List<String> =
        lines.withIndex()
            .filter { (_, line) -> !line.isComment() }
            .filter { (_, line) -> DEPRECATED_QUADRATIC.containsMatchIn(line) }
            .map { (index, line) -> "$path:${index + 1}: ${line.trim()}" }

    private fun String.isComment(): Boolean {
        val trimmed = trimStart()
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
    }

    private companion object {
        /**
         * The spellings Compose ui 1.7.6 deprecates: `quadraticBezierTo` and its relative form, whose
         * replacements drop the `Bezier` infix. The capital `Q` is what makes the second alternative a
         * whole identifier of its own rather than a suffix of the first.
         */
        val DEPRECATED_QUADRATIC = Regex("""\b(?:relative)?[qQ]uadraticBezierTo\b""")

        /**
         * The deprecated spelling, assembled from parts: the scan reads this file too, so a source line here
         * must not carry the spelling the case above refuses.
         */
        val DEPRECATED = "quadratic" + "Bezier" + "To"

        val SKIPPED_TOOL_DIRECTORIES = setOf(".git", ".gradle", ".idea", ".pi", "node_modules")
    }
}
