package com.naviveylin.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the versioned Gradle configuration to `build-jvm-toolchain`'s requirement "The versioned property
 * source names no Java home" (scenarios "The property source carries no Java home", "The pipeline refuses a
 * reintroduced line" and "The scan is still detectable").
 *
 * The line `org.gradle.java.home=/usr/lib/jvm/java-17-openjdk` lived in `gradle.properties` from 2026-10-06 to
 * 2026-10-22 and was silently wrong on every machine but the one that wrote it: the checked-in daemon JVM
 * criteria in `gradle/gradle-daemon-jvm.properties` take precedence over it (Gradle 9.6.1, "Daemon JVM
 * Toolchains"), so it selected no JVM where the criteria applied and it never existed anywhere else. #91
 * removed the line and left the rule in `guidelines/Build.md` §2, but nothing failed when it came back, so a
 * copy-paste from an archived design (`openspec/changes/archive/2026-08-18-github-build/design.md`,
 * `…/2026-10-05-speed-up-build-test-gate/design.md`) could land it again with no case to redden.
 *
 * Two surfaces assert the rule, and this class is the host-side one: the file scan below (every project-wide
 * property source) and the CI step "Check daemon JVM criteria", which refuses a reintroduced line with
 * `::error` before the SDK work. The second case reads that step out of the workflow because a unit test
 * cannot run it, the way `GradleScriptDeprecationTest` reads the build scripts instead of the Gradle log.
 *
 * The case reads the repository's own files rather than a Gradle log: the working directory of an `:app` unit
 * test is the module directory, so the repository root is `File("..")`. Line counts and findings are printed
 * before the assertion, so the JUnit XML's `system-out` carries the measurement of the run that made it.
 */
class VersionedGradleJavaHomeTest {

    private val repoRoot = File("..").canonicalFile
    private val propertySource = File(repoRoot, "gradle.properties")
    private val workflow = File(repoRoot, ".github/workflows/build.yml")

    @Test
    fun `the versioned property source names no Java home`() {
        assertTrue(
            "the project-wide Gradle property source must exist at ${propertySource.absolutePath}",
            propertySource.isFile
        )
        val lines = propertySource.readLines()
        val findings = findingsIn(lines)
        println(
            "propertySource=${propertySource.name} lines=${lines.size} findings=${findings.size}"
        )
        findings.forEach { println("FINDING $it") }

        assertEquals(
            "the versioned gradle.properties must carry no org.gradle.java.home line — the daemon JVM " +
                "criteria select the JVM, and a machine-local absolute path is wrong on every machine but " +
                "the one that wrote it (issue #91)",
            emptyList<String>(),
            findings
        )
    }

    @Test
    fun `the pipeline refuses a reintroduced line`() {
        val step = daemonJvmStep()
        // The workflow writes the pattern with YAML/`grep` escaping; compare the unescaped text.
        val unescaped = step.replace("\\", "")
        println("stepChars=${step.length}")
        assertTrue(
            "the CI step \"Check daemon JVM criteria\" must read the versioned property source, so a " +
                "reintroduced org.gradle.java.home fails the pipeline instead of silently selecting no JVM",
            unescaped.contains("gradle.properties")
        )
        assertTrue(
            "the CI step \"Check daemon JVM criteria\" must refuse the org.gradle.java.home key in " +
                "gradle.properties (guidelines/Build.md §2, issue #91)",
            unescaped.contains("org.gradle.java.home")
        )
    }

    @Test
    fun `the scan is still detectable`() {
        val offending = listOf(
            "org.gradle.java.home=/usr/lib/jvm/java-17-openjdk",
            "  org.gradle.java.home = /opt/jdk-21"
        )
        val tolerated = listOf(
            "# org.gradle.java.home=/usr/lib/jvm/java-17-openjdk",
            "#   the daemon JVM comes from gradle/gradle-daemon-jvm.properties, never from this file",
            "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8"
        )

        val found = offending.map { findingsIn(listOf(it)) }
        println("offending=${found.map { it.size }} tolerated=${tolerated.map { findingsIn(listOf(it)).size }}")

        assertTrue(
            "the scan must report every line that sets org.gradle.java.home, so a passing scan means the " +
                "line is absent rather than that the scan cannot see it: $found",
            found.all { it.size == 1 }
        )
        assertTrue(
            "a comment that names the key, and any other property, must not be reported",
            tolerated.all { findingsIn(listOf(it)).isEmpty() }
        )
    }

    /** One finding per code line that sets the key, as `:<line>: <text>` — one script's lines come in. */
    private fun findingsIn(lines: List<String>): List<String> =
        lines.withIndex()
            .filter { (_, line) -> !line.isComment() }
            .filter { (_, line) -> JAVA_HOME_ASSIGNMENT.containsMatchIn(line) }
            .map { (index, line) -> ":${index + 1}: ${line.trim()}" }

    private fun String.isComment(): Boolean = trimStart().startsWith("#") ||
        trimStart().startsWith("!") ||
        trimStart().startsWith("//")

    /**
     * The `run:` body of the workflow's daemon-JVM step: from its `- name:` line to the next list item at the
     * same indentation. An assertion about the step's text is all a host case can make of a workflow, which is
     * the same bargain `tools/check-action-runtimes.sh` makes about `uses:` pins.
     */
    private fun daemonJvmStep(): String {
        assertTrue(
            "the workflow must exist at ${workflow.absolutePath}",
            workflow.isFile
        )
        val lines = workflow.readLines()
        val start = lines.indexOfFirst { it.trim() == "- name: Check daemon JVM criteria" }
        assertTrue(
            "the workflow must carry the step \"Check daemon JVM criteria\" (guidelines/Build.md §2) — " +
                "found it at line index $start of ${workflow.name}",
            start >= 0
        )
        val stepIndent = lines[start].indexOf('-')
        val rest = lines.drop(start + 1)
        val next = rest.indexOfFirst { line ->
            line.trimStart().startsWith("-") && line.indexOf('-') <= stepIndent
        }
        val body = if (next < 0) rest else rest.subList(0, next)
        return body.joinToString("\n")
    }

    private companion object {
        /** The key whose value is a machine-local absolute path; `=` may be padded, the key may be indented. */
        val JAVA_HOME_ASSIGNMENT = Regex("""^\s*org\s*\.\s*gradle\s*\.\s*java\s*\.\s*home\s*=""")
    }
}
