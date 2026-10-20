package com.naviveylin.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds `unit-test-suite-runtime`'s requirement "A test source declares the opt-in its experimental time
 * control needs" (scenarios "A test source without the declaration is named" and "The test-bearing sources
 * need no missing-opt-in warning") to the rule `guidelines/Build.md` §4 states — "the build must have no
 * warnings".
 *
 * Why a case and not the warning: at `kotlinx-coroutines-test:1.9.0` the `TestScope` extensions
 * `advanceUntilIdle`, `advanceTimeBy` and `runCurrent` are `@ExperimentalCoroutinesApi`, and a file that calls
 * them without an opt-in warning is *still a passing build*. 91 such call sites had accumulated over nine
 * files before this guard existed, because a warning nobody reads is not a gate. The case reads the three test
 * source trees the four unit-test compile tasks cover the way
 * [com.naviveylin.featurelist.ShippedSpecClassificationTest] reads the repo — private helpers over
 * `File("..")`, no shell, no production code — so a failure names the offending file in the JUnit XML.
 *
 * The rule is import-level on purpose: the experimental `TestScope.advanceUntilIdle` and the stable
 * `TestCoroutineScheduler.advanceUntilIdle` share a name, so a call-shape rule would have to resolve receivers
 * in a regex, while the import states which declaration a file means. Files that drive the time control
 * through the stable member (`mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()`) import nothing from
 * the experimental surface and are correctly not findings.
 *
 * Comments and string literals are masked before the scan ([maskCommentsAndStrings]), so a KDoc sentence, a
 * commented-out annotation or a fixture string cannot satisfy the rule — the same discipline the wall-clock
 * scanner applies to test sources.
 */
class CoroutinesTestOptInGateTest {

    private val repoRoot = File("..").canonicalFile

    /** The test-source trees of the units the four unit-test compile tasks build. */
    private val testSourceRoots = listOf("app/src/test/java", "auto/src/test/java", "core/src/test/java")

    @Test
    fun aTestSourceThatUsesTheExperimentalTimeControlWithoutTheOptInIsNamed() {
        val fixture = """
            package com.naviveylin.example

            import kotlinx.coroutines.test.advanceUntilIdle
            import kotlinx.coroutines.test.runTest

            class FixtureTest {
                @Test
                fun aCase() = runTest {
                    advanceUntilIdle()
                }
            }
        """.trimIndent()

        assertEquals(
            "a source that imports the experimental time control without the opt-in must be named",
            listOf(FIXTURE_PATH),
            findingsIn(FIXTURE_PATH, fixture)
        )
    }

    @Test
    fun aTestSourceThatDeclaresTheOptInIsNotNamed() {
        val fixture = """
            package com.naviveylin.example

            import kotlinx.coroutines.ExperimentalCoroutinesApi
            import kotlinx.coroutines.test.advanceUntilIdle
            import kotlinx.coroutines.test.runTest

            @OptIn(ExperimentalCoroutinesApi::class)
            class FixtureTest {
                @Test
                fun aCase() = runTest {
                    advanceUntilIdle()
                }
            }
        """.trimIndent()

        assertEquals("a declared opt-in satisfies the rule", emptyList<String>(), findingsIn(FIXTURE_PATH, fixture))
    }

    @Test
    fun aTestSourceThatOnlyImportsTheMarkerIsStillNamed() {
        val fixture = """
            package com.naviveylin.example

            import kotlinx.coroutines.ExperimentalCoroutinesApi
            import kotlinx.coroutines.test.advanceUntilIdle
            import kotlinx.coroutines.test.runTest

            class FixtureTest {
                @Test
                fun aCase() = runTest {
                    advanceUntilIdle()
                }
            }
        """.trimIndent()

        assertEquals(
            "an import of the marker is not the annotation — the compiler still warns on the call site",
            listOf(FIXTURE_PATH),
            findingsIn(FIXTURE_PATH, fixture)
        )
    }

    @Test
    fun aCommentedOutAnnotationDoesNotSatisfyTheRule() {
        val fixture = """
            package com.naviveylin.example

            import kotlinx.coroutines.test.advanceUntilIdle
            import kotlinx.coroutines.test.runTest

            // @OptIn(ExperimentalCoroutinesApi::class) was removed by a refactor
            class FixtureTest {
                @Test
                fun aCase() = runTest {
                    advanceUntilIdle()
                }
            }
        """.trimIndent()

        assertEquals(
            "a comment that mentions the annotation is not the annotation",
            listOf(FIXTURE_PATH),
            findingsIn(FIXTURE_PATH, fixture)
        )
    }

    @Test
    fun aWildcardImportOfTheTestPackageIsNamed() {
        val fixture = """
            package com.naviveylin.example

            import kotlinx.coroutines.test.*

            class FixtureTest {
                @Test
                fun aCase() = runTest {
                    advanceUntilIdle()
                }
            }
        """.trimIndent()

        assertEquals(
            "a wildcard import can bring an experimental declaration in without naming it",
            listOf(FIXTURE_PATH),
            findingsIn(FIXTURE_PATH, fixture)
        )
    }

    @Test
    fun everyTestSourceThatUsesTheExperimentalTimeControlDeclaresTheOptIn() {
        val roots = testSourceRoots.map { File(repoRoot, it) }
        roots.forEach { root ->
            assertTrue("the test-source tree must exist at ${root.absolutePath}", root.isDirectory)
        }

        val sources = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        val findings = sources
            .flatMap { file -> findingsIn(file.relativeTo(repoRoot).path, file.readText()) }
            .sorted()
        println("scanned=${sources.size} missingOptIn=${findings.size} $findings")

        assertEquals(
            "every test source that imports the experimental coroutines-test declarations must declare the " +
                "opt-in, and a source that does not call one must drop its import",
            emptyList<String>(),
            findings
        )
    }

    /**
     * The rule: a source whose applied opt-in marker is absent and which imports a `kotlinx.coroutines.test`
     * declaration outside [STABLE_TEST_DECLARATIONS] — or the whole package by wildcard — is a finding. The
     * marker is an applied `@OptIn` / `@file:OptIn` in code, never a mention in a comment or a string.
     */
    private fun findingsIn(relativePath: String, source: String): List<String> {
        val code = maskCommentsAndStrings(source)
        if (OPT_IN_ANNOTATION.containsMatchIn(code)) return emptyList()
        val named = TEST_IMPORT.findAll(code)
            .map { it.groupValues[1] }
            .filterNot { it in STABLE_TEST_DECLARATIONS }
            .toList()
        return if (named.isEmpty() && !TEST_WILDCARD_IMPORT.containsMatchIn(code)) {
            emptyList()
        } else {
            listOf(relativePath)
        }
    }

    /**
     * Replaces every comment and string literal with spaces, keeping the line structure, so the scan sees only
     * code. Handles `//`, `/* */` (nested blocks included), `"…"` with escapes, `"""…"""` and `'…'`.
     */
    private fun maskCommentsAndStrings(source: String): String {
        val masked = StringBuilder(source.length)
        var index = 0
        fun blank(character: Char) {
            masked.append(if (character == '\n') '\n' else ' ')
        }
        while (index < source.length) {
            when {
                source.startsWith("\"\"\"", index) -> {
                    blank(' ')
                    blank(' ')
                    blank(' ')
                    index += 3
                    while (index < source.length && !source.startsWith("\"\"\"", index)) {
                        blank(source[index])
                        index++
                    }
                    if (index < source.length) {
                        blank(' ')
                        blank(' ')
                        blank(' ')
                        index += 3
                    }
                }

                source[index] == '"' -> {
                    blank('"')
                    index++
                    while (index < source.length && source[index] != '"') {
                        if (source[index] == '\\' && index + 1 < source.length) {
                            blank(source[index])
                            index++
                        }
                        blank(source[index])
                        index++
                    }
                    if (index < source.length) {
                        blank('"')
                        index++
                    }
                }

                source[index] == '\'' -> {
                    blank('\'')
                    index++
                    while (index < source.length && source[index] != '\'') {
                        if (source[index] == '\\' && index + 1 < source.length) {
                            blank(source[index])
                            index++
                        }
                        blank(source[index])
                        index++
                    }
                    if (index < source.length) {
                        blank('\'')
                        index++
                    }
                }

                source.startsWith("//", index) -> {
                    while (index < source.length && source[index] != '\n') {
                        blank(source[index])
                        index++
                    }
                }

                source.startsWith("/*", index) -> {
                    var depth = 0
                    while (index < source.length) {
                        when {
                            source.startsWith("/*", index) -> {
                                blank('/')
                                blank('*')
                                index += 2
                                depth++
                            }

                            source.startsWith("*/", index) -> {
                                blank('*')
                                blank('/')
                                index += 2
                                depth--
                                if (depth == 0) break
                            }

                            else -> {
                                blank(source[index])
                                index++
                            }
                        }
                    }
                }

                else -> {
                    masked.append(source[index])
                    index++
                }
            }
        }
        return masked.toString()
    }

    private companion object {
        const val FIXTURE_PATH = "app/src/test/java/com/naviveylin/example/FixtureTest.kt"

        /** An applied opt-in that names the marker, in the class-level or the file-level spelling. */
        val OPT_IN_ANNOTATION = Regex("""@(?:file:)?OptIn\s*\([^)]*ExperimentalCoroutinesApi""")

        /** `import kotlinx.coroutines.test.<name>` at the start of a line, an alias included as the name. */
        val TEST_IMPORT = Regex("""^import\s+kotlinx\.coroutines\.test\.([A-Za-z_][A-Za-z0-9_]*)""", RegexOption.MULTILINE)

        /** `import kotlinx.coroutines.test.*` — the names it brings in are not visible to an import-level rule. */
        val TEST_WILDCARD_IMPORT = Regex("""^import\s+kotlinx\.coroutines\.test\.\*""", RegexOption.MULTILINE)

        /**
         * The `kotlinx.coroutines.test` declarations the pinned library leaves usable without an opt-in.
         * Deliberately short, and it grows only with a measurement: anything not named here must declare the
         * opt-in, so a declaration the library *stabilises* later surfaces as a one-line report rather than as
         * a missing guard.
         */
        val STABLE_TEST_DECLARATIONS = setOf(
            "runTest",
            "TestScope",
            "TestDispatcher",
            "StandardTestDispatcher",
            "TestCoroutineScheduler"
        )
    }
}
