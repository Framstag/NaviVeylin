package com.naviveylin.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the unit-test source sets to the warning-free build the gate's result evaluation requires
 * (spec: `build-test-gate` — "The unit-test source sets name a nullable value's non-null assumption";
 * the rule is `guidelines/Build.md` §4a "The verdict, the warnings and the failures", bullet
 * "Warnings: the build must have no warnings — report them, don't ignore").
 *
 * Two nullability diagnostics are invisible in a green run: a nullable value reaching a Java method whose
 * parameter is non-null still compiles and still passes, so nothing but a scan keeps the call sites from
 * returning. Measured 2026-10-29 on the base tree: `./gradlew :app:compileMobileDebugUnitTestKotlin
 * --rerun-tasks` prints one `w:` line per family, and the automotive flavor compiles the same test tree and
 * prints each again — four lines, two call sites:
 *
 * ```
 * StartMapResolverTest.kt:131:78 Java type mismatch: inferred type is 'String?', but 'String' was expected.
 * ShippedSpecClassificationTest.kt:51:20 Only safe (?.) or non-null asserted (!!.) calls are allowed on a
 *   nullable receiver, 'File?'.
 * ```
 *
 * The construct each case names is a *call-site* guard, not a proof that no nullable crosses a Java
 * signature: it reads Kotlin text, so a nullable value reached through an intermediate local it cannot
 * follow is outside what it can see. The repository is read relative to the module directory, which is an
 * `:app` unit test's working directory — the way [UnitTestSourceDeprecationBaselineTest] reads its sources.
 * The scanned-source count and the findings are printed before each assertion, so the JUnit XML's
 * `system-out` carries the measurement of the run that made it and a finding names its line.
 */
class UnitTestSourceNullabilityBaselineTest {

    private val repoRoot = File("..").canonicalFile

    /** The module test roots the gate's four unit-test suites compile from. */
    private val unitTestRoots = listOf(
        "app/src/test/java",
        "auto/src/test/java",
        "core/src/test/java"
    )

    /** This guard's own fixtures spell the refused forms in parts, and the scan still skips it by path. */
    private val self = "app/src/test/java/com/naviveylin/build/" +
        "UnitTestSourceNullabilityBaselineTest.kt"

    @Test
    fun `no unit test feeds an unasserted start map resolution into a membership check`() {
        val sources = unitTestSources()
        assertReachesSources(sources)

        val findings = sources.flatMap { source ->
            unassertedResolutionFindings(source.code).map { "$it in ${source.relative}" }
        }
        println("sources=${sources.size} findings=${findings.size}")
        findings.forEach { println("FINDING $it") }

        assertEquals(
            "a start-map resolution returns `String?`, and the Java-declared `List.contains(String)` cannot " +
                "take it: write `val first = requireNotNull(resolver().resolve()) { … }`, naming the fixture " +
                "the case knows is installed, instead of passing the nullable value",
            emptyList<String>(),
            findings
        )
    }

    @Test
    fun `no unit test dereferences a nullable parent directory without a safe call or an assertion`() {
        val sources = unitTestSources()
        assertReachesSources(sources)

        val findings = sources.flatMap { source ->
            parentFileDereferenceFindings(source.code).map { "$it in ${source.relative}" }
        }
        println("sources=${sources.size} findings=${findings.size}")
        findings.forEach { println("FINDING $it") }

        assertEquals(
            "`File.parentFile` is `File?`: dereference it behind a safe call (`?.`), a non-null assertion, or " +
                "`requireNotNull(…)` with a message that names the fixture the case knows is present",
            emptyList<String>(),
            findings
        )
    }

    @Test
    fun `the scan refuses the unasserted forms and accepts the stated ones`() {
        // Assembled from parts on purpose: this file is inside the scan surface, so a fixture written whole
        // would be a finding of the guard it feeds.
        val resolveCall = "res" + "olve()"
        val unassertedResolution = listOf(
            "        val first = resolver().$resolveCall",
            "        assertTrue(listOf(region, nested).contains(fi" + "rst))"
        )
        val statedResolution = listOf(
            "        val first = requireNotNull(resolver().$resolveCall) { \"a map is installed\" }",
            "        assertTrue(listOf(region, nested).contains(fi" + "rst))"
        )

        assertEquals(
            "the scan must refuse a nullable resolution passed to a membership check",
            1,
            unassertedResolutionFindings(unassertedResolution.joinToString("\n")).size
        )
        assertEquals(
            "the scan must accept a resolution named non-null before it is used",
            emptyList<String>(),
            unassertedResolutionFindings(statedResolution.joinToString("\n"))
        )

        val dereference = ".parent" + "File.relativeTo(specsDir).path"
        val assertedForms = listOf(
            "        logFile.parentFile?.mkdirs()",
            "        File(logFile.parentFile, ROTATED_FILE).delete()",
            "        val parent = requireNotNull(spec.parentFile) { \"a spec.md has a directory\" }",
            "        val parent = spec.parentFile!!"
        )

        assertEquals(
            "the scan must refuse a parent directory dereferenced without a safe call or an assertion",
            1,
            parentFileDereferenceFindings("        .map { it$dereference }").size
        )
        assertEquals(
            "the scan must accept a safe call, a non-null assertion, `requireNotNull` and a platform-typed " +
                "argument that is not dereferenced",
            emptyList<String>(),
            assertedForms.flatMap { parentFileDereferenceFindings(it) }
        )
    }

    private fun assertReachesSources(sources: List<Source>) {
        assertTrue(
            "the modules' unit-test sources must be found under ${repoRoot.absolutePath} " +
                "(found ${sources.size})",
            sources.size >= 200
        )
    }

    /** One scanned source file: its repository-relative path and its code with line comments removed. */
    private data class Source(val relative: String, val code: String)

    /**
     * One finding per local bound on a `…resolve()` call whose value is later handed to `.contains(…)`, as
     * `:<line>: <text>` — the caller hands in one source's code, so the finding carries no path.
     */
    private fun unassertedResolutionFindings(code: String): List<String> =
        NULLABLE_RESOLUTION_BINDING.findAll(code).mapNotNull { binding ->
            val value = binding.groupValues[1]
            val use = Regex("""\.contains\(\s*${Regex.escape(value)}\s*\)""").find(code)
                ?: return@mapNotNull null
            ":${lineOf(code, use.range.first)}: ${use.value.trim()}"
        }.toList()

    /** One finding per `parentFile` member access that is neither a safe call nor a non-null assertion. */
    private fun parentFileDereferenceFindings(code: String): List<String> =
        PARENT_FILE_DEREFERENCE.findAll(code)
            .map { ":${lineOf(code, it.range.first)}: ${it.value.trim()}" }
            .toList()

    private fun lineOf(code: String, index: Int): Int = code.take(index).count { it == '\n' } + 1

    /** Every `*.kt` file under the module test roots, minus this guard. */
    private fun unitTestSources(): List<Source> =
        unitTestRoots.flatMap { root ->
            val dir = File(repoRoot, root)
            assertTrue("the unit-test source root must exist: ${dir.absolutePath}", dir.isDirectory)
            dir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") }
                .map { file ->
                    Source(
                        file.relativeTo(repoRoot).invariantSeparatorsPath,
                        file.readLines().joinToString("\n") { it.substringBefore("//") }
                    )
                }
                .toList()
        }.filterNot { it.relative == self }

    private companion object {
        /** A local bound on a call that returns `String?` — the start-map resolution — and nothing asserted. */
        val NULLABLE_RESOLUTION_BINDING = Regex("""(?m)^\s*val\s+(\w+)\s*=\s*[^\n]*\.resolve\(\)\s*$""")

        /**
         * `parentFile` followed by a member access. A safe call (`?.`), a non-null assertion (`!!`), an
         * argument position (`File(x.parentFile, …)`) and `requireNotNull(…)` all spell something else
         * between the property and the dot, and are therefore not this family.
         */
        val PARENT_FILE_DEREFERENCE = Regex("""\.parentFile\.[A-Za-z_]""")
    }
}
