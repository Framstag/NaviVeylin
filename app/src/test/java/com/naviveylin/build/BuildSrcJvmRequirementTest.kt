package com.naviveylin.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the build logic to `build-jvm-toolchain`'s requirement "The build logic compiles on the JVM the daemon
 * criteria select" (scenarios "The build script names no JVM of its own", "The criteria still pin the JVM the
 * build logic compiles with", "A reintroduced toolchain is refused" and "The scan is still detectable").
 *
 * `buildSrc/build.gradle.kts` declared `toolchain { languageVersion = JavaLanguageVersion.of(17) }` from
 * 2026-10-06 to 2026-10-24 (issue #92, migrated `TODO.md` §146). `buildSrc/` has no `settings.gradle.kts`, so the
 * foojay toolchain resolver that `settings.gradle.kts:9` applies to the main build never reached the buildSrc
 * build: Gradle could only **auto-detect** a local JDK 17, and a machine that had none failed at configuration
 * with "Cannot find a Java installation … matching: {languageVersion=17 …}. Toolchain download repositories have
 * not been configured." — before any task, naming neither the file nor the fix. Measured on this machine
 * 2026-10-24: `./gradlew -Dorg.gradle.java.installations.auto-detect=false help` → `BUILD FAILED in 645ms`.
 *
 * The pin bought nothing: `buildSrc` has no Java source at all (`:buildSrc:compileJava NO-SOURCE`), so the
 * toolchain only ever set Kotlin's `jvmTarget`, and the daemon JVM criteria in
 * `gradle/gradle-daemon-jvm.properties` (`toolchainVersion=21`) already name the JVM that compiles the build
 * logic. The fix is therefore the absence of the pin, not a provisioned 17: a machine that satisfies the
 * criteria configures the build without a second installation.
 *
 * Three surfaces assert the rule, and this class is the host-side one: the file scan below, the same predicate
 * in the CI step "Check daemon JVM criteria" (refused in seconds, before the SDK/vcpkg work) and the
 * configuration command `guidelines/Build.md` §2d prints. The second case reads the criteria file because the
 * absence is only safe while something still pins the JVM the build logic compiles with; the third reads the
 * workflow's step out of the file, the way [VersionedGradleJavaHomeTest] does, because a unit test cannot run a
 * CI step.
 *
 * The case reads the repository's own files rather than a Gradle log: the working directory of an `:app` unit
 * test is the module directory, so the repository root is `File("..")`. Line counts and findings are printed
 * before the assertion, so the JUnit XML's `system-out` carries the measurement of the run that made it.
 */
class BuildSrcJvmRequirementTest {

    private val repoRoot = File("..").canonicalFile
    private val buildScript = File(repoRoot, "buildSrc/build.gradle.kts")
    private val criteria = File(repoRoot, "gradle/gradle-daemon-jvm.properties")
    private val workflow = File(repoRoot, ".github/workflows/build.yml")

    @Test
    fun `the build script names no JVM of its own`() {
        assertTrue(
            "the build logic's build script must exist at ${buildScript.absolutePath}",
            buildScript.isFile
        )
        val lines = buildScript.readLines()
        val findings = findingsIn(lines)
        println("buildScript=${buildScript.name} lines=${lines.size} findings=${findings.size}")
        findings.forEach { println("FINDING $it") }

        assertEquals(
            "buildSrc must declare no JVM toolchain of its own: it compiles with the daemon JVM the " +
                "criteria in gradle/gradle-daemon-jvm.properties select, and a toolchain it cannot provision " +
                "fails the whole configuration on a machine without that JDK — naming neither this file nor " +
                "the fix (issue #92, guidelines/Build.md §2d)",
            emptyList<String>(),
            findings
        )
    }

    @Test
    fun `the criteria still pin the JVM the build logic compiles with`() {
        assertTrue(
            "the daemon JVM criteria must exist at ${criteria.absolutePath}",
            criteria.isFile
        )
        val lines = criteria.readLines()
        val version = lines.filter { it.trim().startsWith("toolchainVersion") }
        val vendor = lines.filter { it.trim().startsWith("toolchainVendor") }
        println("criteria=${criteria.name} lines=${lines.size} versionLines=${version.size} vendorLines=${vendor.size}")

        assertEquals(
            "removing buildSrc's toolchain is only safe while the daemon criteria pin a version: " +
                "gradle/gradle-daemon-jvm.properties must carry exactly one toolchainVersion line " +
                "(guidelines/Build.md §2d)",
            listOf("toolchainVersion=21"),
            version
        )
        assertEquals(
            "the criteria must carry no vendor criterion, so any installed JDK 21 satisfies them and the " +
                "build logic compiles without a download (guidelines/Build.md §2d, issue #91)",
            emptyList<String>(),
            vendor
        )
    }

    @Test
    fun `a reintroduced toolchain is refused`() {
        val step = daemonJvmStep()
        // The workflow writes its pattern with YAML/`grep` escaping; compare the unescaped text.
        val unescaped = step.replace("\\", "")
        println("stepChars=${step.length}")
        assertTrue(
            "the CI step \"Check daemon JVM criteria\" must name buildSrc/build.gradle.kts, so a " +
                "reintroduced toolchain fails the pipeline in seconds instead of a configuration that needs " +
                "a JDK nobody provisioned (issue #92)",
            unescaped.contains("buildSrc/build.gradle.kts")
        )
        assertTrue(
            "the CI step \"Check daemon JVM criteria\" must refuse a toolchain or a languageVersion in the " +
                "build logic's build script (guidelines/Build.md §2d, issue #92)",
            unescaped.contains("toolchain")
        )
    }

    @Test
    fun `the scan is still detectable`() {
        val offending = listOf(
            "java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }",
            "kotlin { jvmToolchain(17) }",
            "        languageVersion = JavaLanguageVersion.of(21)"
        )
        val tolerated = listOf(
            "// java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }",
            "// buildSrc declares no toolchain: the daemon JVM criteria select the JVM it compiles with",
            "java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }",
            "kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }"
        )

        val found = offending.map { findingsIn(listOf(it)) }
        println("offending=${found.map { it.size }} tolerated=${tolerated.map { findingsIn(listOf(it)).size }}")

        assertTrue(
            "the scan must report every line that asks for a JVM toolchain, so a passing scan means the " +
                "requirement is absent rather than that the scan cannot see it: $found",
            found.all { it.size == 1 }
        )
        assertTrue(
            "a comment that names a toolchain, and a compile *target* (sourceCompatibility/targetCompatibility, " +
                "jvmTarget) that installs nothing, must not be reported",
            tolerated.all { findingsIn(listOf(it)).isEmpty() }
        )
    }

    /** One finding per code line that asks for a JVM toolchain, as `:<line>: <text>`. */
    private fun findingsIn(lines: List<String>): List<String> =
        lines.withIndex()
            .filter { (_, line) -> !line.isComment() }
            .filter { (_, line) -> TOOLCHAIN_REQUIREMENT.containsMatchIn(line) }
            .map { (index, line) -> ":${index + 1}: ${line.trim()}" }

    private fun String.isComment(): Boolean = trimStart().startsWith("//") ||
        trimStart().startsWith("#") ||
        trimStart().startsWith("*")

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
            "the workflow must carry the step \"Check daemon JVM criteria\" (guidelines/Build.md §2d) — " +
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
        /**
         * A JVM the build logic would have to *find*: a toolchain spec (Kotlin DSL `toolchain`, `jvmToolchain`),
         * a `languageVersion` line, or a `JavaLanguageVersion` value. A compile target is not one — it names no
         * installation — so the tolerated fixtures in "the scan is still detectable" pin that boundary.
         */
        val TOOLCHAIN_REQUIREMENT = Regex("""(?i)(toolchain|languageVersion|JavaLanguageVersion)""")
    }
}
