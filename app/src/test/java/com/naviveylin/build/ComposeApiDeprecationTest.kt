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
 * point the build stops compiling. Nothing in this build turns a Kotlin warning into a failure (the gate's log
 * carries ~109 `w:` lines on HEAD, most of them opt-in notices in test sources), so a call site nobody reads
 * stays until an upgrade breaks it.
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
            "a Kotlin source that builds a quadratic Bézier with `quadraticBezierTo`/`relativeQuadraticBezierTo` " +
                "must use `quadraticTo`/`relativeQuadraticTo` instead — the pinned Compose ui 1.7.6 deprecates " +
                "the `Bezier` spelling and a later release removes it",
            emptyList<String>(),
            findings
        )
    }

    @Test
    fun `the scan is still detectable`() {
        val deprecated = listOf(
            "        path.quadraticBezierTo(0f, -hPx, x1 * hPx, y1 * hPx)",
            "        path.relativeQuadraticBezierTo(0f, -hPx, x1 * hPx, y1 * hPx)"
        )
        val kept = listOf(
            "        path.quadraticTo(0f, -hPx, x1 * hPx, y1 * hPx)",
            "        path.relativeQuadraticTo(0f, -hPx, x1 * hPx, y1 * hPx)",
            "    // the deprecated `quadraticBezierTo` spelling is refused, see the case above"
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

    /** Every `*.kt` the project owns: the whole tree, minus build output and tool state. */
    private fun kotlinSources(): List<File> =
        repoRoot.walkTopDown()
            .onEnter { it.name !in SKIPPED_DIRECTORIES }
            .filter { it.isFile && it.name.endsWith(".kt") }
            .sorted()
            .toList()

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

        val SKIPPED_DIRECTORIES = setOf("build", ".git", ".gradle", ".idea", ".pi", "node_modules")
    }
}
