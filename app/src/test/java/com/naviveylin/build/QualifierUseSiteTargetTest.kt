package com.naviveylin.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the project's Kotlin sources to `build-test-gate`'s requirement "An injected property states its
 * qualifier's use-site target" (scenarios "A qualifier on an injected property names `param:`" and "The
 * implicit target is still detectable"), the source-set half of the gate's result evaluation.
 *
 * The construct is a qualifier annotation applied to a property declaration with no use-site target —
 * `@ApplicationContext private val context: Context`. Kotlin applies it to the constructor's value parameter
 * today and warns that a later default will also apply it to the field
 * (`This annotation is currently applied to the value parameter only, but in the future it will also be
 * applied to field`), so the site is a future hard error waiting for a language version bump
 * (`guidelines/Build.md` §4, "Warnings: the build must have no warnings — report them, don't ignore").
 * `@param:ApplicationContext` states the meaning the code has today.
 *
 * The case reads the repository's own sources rather than the compiler's log, because a unit test cannot
 * compile the module it runs in: the working directory of an `:app` unit test is the module directory, the
 * way [GradleScriptDeprecationTest] reads the project's build scripts. The scanned-source count and the
 * findings are printed before the assertion, so the JUnit XML's `system-out` carries the measurement of the
 * run that made it, and a finding names its line and text.
 */
class QualifierUseSiteTargetTest {

    private val repoRoot = File("..").canonicalFile

    @Test
    fun `no qualifier on an injected property relies on the implicit target`() {
        val sources = kotlinSources()
        assertTrue(
            "the modules' Kotlin sources must be found under ${repoRoot.absolutePath} " +
                "(found ${sources.size})",
            sources.size >= 200
        )

        val findings = sources.flatMap { source ->
            findingsIn(source.readLines()).map { "$it in ${relative(source)}" }
        }
        println("sources=${sources.size} findings=${findings.size}")
        findings.forEach { println("FINDING $it") }

        assertEquals(
            "a qualifier applied to an injected property must name its use-site target — " +
                "write `@param:ApplicationContext` instead of `@ApplicationContext`, because the " +
                "unqualified form is applied to the value parameter only and a later Kotlin default " +
                "will also apply it to the field",
            emptyList<String>(),
            findings
        )
    }

    @Test
    fun `the implicit target is still detectable`() {
        val implicit = listOf(
            "    @ApplicationContext private val context: Context,",
            "    @Named(\"clock\") val clock: Clock",
            "@ActivityContext var activity: Context"
        )
        val explicit = listOf(
            "    @param:ApplicationContext private val context: Context,",
            "    @field:ApplicationContext lateinit var context: Context",
            "    fun provideViewportStorage(@ApplicationContext context: Context): ViewportStorage {",
            "    // the qualified form `@ApplicationContext private val` is refused, see the case above",
            "    @Singleton class Controller @Inject constructor(private val context: Context)"
        )

        assertTrue(
            "the scan must refuse every implicit qualifier target it exists for",
            implicit.all { findingsIn(listOf(it)).size == 1 }
        )
        assertTrue(
            "the scan must accept the targeted form, a bare method parameter, a constructor with no " +
                "qualified parameter, and a comment that names the refused form",
            explicit.all { findingsIn(listOf(it)).isEmpty() }
        )
    }

    /**
     * Every Kotlin source of the six source sets the gate compiles — `app`, `auto` and `core`'s `main` and
     * `test` trees. The libosmscout submodule's own Kotlin module (`app/src/main/cpp/libosmscout/libosmscout-kotlin`)
     * is outside them on purpose: the project never patches the submodule (`guidelines/Design.md` §5), so a
     * finding there would be a guard whose fix is forbidden.
     */
    private fun kotlinSources(): List<File> =
        repoRoot.walkTopDown()
            .onEnter { it.name !in SKIPPED_DIRECTORIES }
            .filter { it.isFile && it.name.endsWith(".kt") }
            .filter { source ->
                val path = relative(source)
                SCANNED_SOURCE_SETS.any { sourceSet -> path.startsWith("$sourceSet/") }
            }
            .sorted()
            .toList()

    private fun relative(source: File): String = source.relativeTo(repoRoot).invariantSeparatorsPath

    /**
     * One finding per line that applies a qualifier to a property declaration without a use-site target, as
     * `:<line>: <text>` — the caller hands in one source's lines, so the finding carries no path.
     */
    private fun findingsIn(lines: List<String>): List<String> =
        lines.withIndex()
            .filter { (_, line) -> !line.isComment() }
            .filter { (_, line) -> IMPLICIT_QUALIFIER.containsMatchIn(line) }
            .map { (index, line) -> ":${index + 1}: ${line.trim()}" }

    private fun String.isComment(): Boolean {
        val trimmed = trimStart()
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
    }

    private companion object {
        /**
         * A qualifier the project injects with — `javax.inject.Qualifier`/Hilt, declared with `METHOD`,
         * `PARAMETER` and `FIELD` targets — applied to a `val`/`var` declaration. A use-site target such as
         * `param:` precedes the annotation name and therefore never matches: the name begins lowercase.
         */
        val IMPLICIT_QUALIFIER = Regex(
            """@(ApplicationContext|ActivityContext|Named)(\([^)]*\))?\s+""" +
                """(?:(?:private|internal|public|protected)\s+)*(?:val|var)\s"""
        )

        val SCANNED_SOURCE_SETS = listOf(
            "app/src/main/java",
            "app/src/test/java",
            "auto/src/main/java",
            "auto/src/test/java",
            "core/src/main/java",
            "core/src/test/java"
        )

        val SKIPPED_DIRECTORIES = setOf("build", ".git", ".gradle", ".idea", ".pi", "node_modules")
    }
}
