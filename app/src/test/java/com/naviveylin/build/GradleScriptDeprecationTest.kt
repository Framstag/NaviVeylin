package com.naviveylin.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the project's own build scripts to `build-jvm-toolchain`'s requirement "The build's scripts use no
 * construct Gradle 10 removes" (scenarios "A project script registers its tasks by name" and "The removal is
 * still detectable").
 *
 * The construct is the Kotlin DSL delegated-property registration `val name by tasks.registering { }`: Gradle
 * 9.6.1 deprecates it on every configuration and Gradle 10 removes it, so it is a build failure waiting for a
 * version bump (`guidelines/Build.md` §7). The named form `val name = tasks.register("name") { }` registers
 * the same provider under the same task name.
 *
 * The case reads the repository's own scripts rather than the Gradle log, because a unit test cannot configure
 * a second build: the working directory of an `:app` unit test is the module directory, the way
 * `ShippedSpecClassificationTest` reads the shipped spec set. The script count and the findings are printed
 * before the assertion, so the JUnit XML's `system-out` carries the measurement of the run that made it, and
 * a finding names its file, line and text.
 */
class GradleScriptDeprecationTest {

    private val repoRoot = File("..").canonicalFile

    @Test
    fun `no project build script uses a removed Gradle construct`() {
        val scripts = buildScripts()
        assertTrue(
            "the project's build scripts must be found under ${repoRoot.absolutePath} " +
                "(found ${scripts.size})",
            scripts.size >= 5
        )

        val findings = scripts.flatMap { script -> findingsIn(script.readLines()) }
        println("scripts=${scripts.size} findings=${findings.size}")
        findings.forEach { println("FINDING $it") }

        assertEquals(
            "a script that registers a task with the `by tasks.registering { }` delegate must use " +
                "`tasks.register(\"name\") { }` instead — Gradle 10 removes the delegate",
            emptyList<String>(),
            findings
        )
    }

    @Test
    fun `the removal is still detectable`() {
        val deprecated = listOf(
            "val checkHardcodedStrings by tasks.registering {",
            "val checkNoWallClockWaits by registering {",
            "  val koverReport by getting"
        )
        val named = listOf(
            "val checkHardcodedStrings = tasks.register(\"checkHardcodedStrings\") {",
            "    // the `by registering { }` delegate is refused, see the case above",
            "tasks.named(\"preBuild\") { dependsOn(checkHardcodedStrings) }"
        )

        assertTrue(
            "the scan must refuse the delegate form it exists for",
            deprecated.all { findingsIn(listOf(it)).size == 1 }
        )
        assertTrue(
            "the scan must accept the named form beside a comment that names the delegate",
            named.all { findingsIn(listOf(it)).isEmpty() }
        )
    }

    /** Every `*.gradle.kts` the project owns: the whole tree, minus build output and tool state. */
    private fun buildScripts(): List<File> =
        repoRoot.walkTopDown()
            .onEnter { it.name !in SKIPPED_DIRECTORIES }
            .filter { it.isFile && it.name.endsWith(".gradle.kts") }
            .sorted()
            .toList()

    /** One finding per code line that carries the removed delegate, as `path:line: text`. */
    private fun findingsIn(lines: List<String>): List<String> =
        lines.withIndex()
            .filter { (_, line) -> !line.isComment() }
            .filter { (_, line) -> REMOVED_DELEGATE.containsMatchIn(line) }
            .map { (index, line) -> ":${index + 1}: ${line.trim()}" }

    private fun String.isComment(): Boolean {
        val trimmed = trimStart()
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
    }

    private companion object {
        /** The Kotlin DSL delegates Gradle 10 removes: `by registering/getting/creating/existing`. */
        val REMOVED_DELEGATE = Regex("""\bby\s+(tasks\s*\.\s*)?(registering|getting|creating|existing)\b""")

        val SKIPPED_DIRECTORIES = setOf("build", ".git", ".gradle", ".idea", ".pi", "node_modules")
    }
}
