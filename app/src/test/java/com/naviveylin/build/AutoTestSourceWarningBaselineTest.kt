package com.naviveylin.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the `:auto` unit-test source set to the warning-free build `guidelines/Build.md` §4 requires
 * ("Warnings: the build must have no warnings — report them, don't ignore"), for the three diagnostics
 * change `fix-auto-test-source-warnings` removed (spec `build-test-gate` — "The `:auto` unit-test source set
 * carries no predetermined type, constant condition or unchecked cast").
 *
 * A compiler warning is invisible in a green run — the suite passes either way — so nothing but a scan keeps
 * the construct from coming back. Each case asserts the absence of one construct **and** shows that its
 * finder reports a sample of that construct, so a scan that reached no source cannot pass vacuously.
 *
 * The repository is read relative to the module directory, which is an `:app` unit test's working
 * directory — the way `GradleScriptDeprecationTest` reads the build scripts. It is a Kotlin text scan, so a
 * construct behind a nested parenthesis or inside a string literal is outside what it can see: a guard
 * against these three call sites returning, not a proof that no such construct exists anywhere.
 */
class AutoTestSourceWarningBaselineTest {

    private val repoRoot = File("..").canonicalFile

    /** The one unit-test root this guard owns: the `:auto` suite #221 names. */
    private val ownedRoot = File(repoRoot, "auto/src/test/java")

    /**
     * Main roots whose `class` declarations a type-parameter bound is resolved against, so the bound rule
     * needs no import graph.
     */
    private val mainRoots = listOf(
        "auto/src/main/java",
        "app/src/main/java",
        "core/src/main/java"
    ).map { File(repoRoot, it) }.filter { it.isDirectory }

    @Test
    fun `no type parameter is bounded by a class that cannot be extended`() {
        val finalNames = finalClassNames()
        assertTrue(
            "the scan must resolve the final classes a type parameter could name under " +
                "${repoRoot.path} (found ${finalNames.size})",
            finalNames.size >= 100
        )

        // Positive control: the finder reports the construct the sweep removed.
        assertEquals(
            listOf("T : AutoMapRenderer"),
            boundFindings("fun <T : AutoMapRenderer> track(renderer: T): T {", finalNames)
        )

        val sources = sourcesIn(ownedRoot)
        val findings = sources.flatMap { source ->
            boundFindings(source.readText(), finalNames).map { "${source.name}: $it" }
        }
        println("sources=${sources.size} finalClasses=${finalNames.size} findings=${findings.size}")
        findings.forEach { println("FINDING $it") }
        assertTrue(
            "a type parameter bounded by a final class has a predetermined value, so the compiler " +
                "reports the bound as noise — name the class in the signature instead: $findings",
            findings.isEmpty()
        )
    }

    @Test
    fun `a runIteration comparison against null names the block type`() {
        val sources = sourcesIn(ownedRoot)
        val calls = sources.flatMap { source ->
            throwingIterationCalls(source.readText()).map { source.name to it }
        }
        assertTrue(
            "the scan must reach the shape it guards — a runIteration call whose block throws " +
                "unconditionally (found ${calls.size} under ${ownedRoot.path})",
            calls.isNotEmpty()
        )

        // Positive control: a throwing block whose result is compared against null and whose type is
        // left to the compiler is a finding.
        assertEquals(
            listOf("runIteration(loop) { error(\"native render failed\") } == null"),
            throwingBlockFindings(
                "fun boom() = runIteration(loop) { error(\"native render failed\") } == null"
            )
        )
        // …and the same call naming its block type is not one.
        assertEquals(
            emptyList<String>(),
            throwingBlockFindings(
                "fun boom() = runIteration<Unit>(loop) { error(\"native render failed\") } == null"
            )
        )

        val findings = sources.flatMap { source ->
            throwingBlockFindings(source.readText()).map { "${source.name}: $it" }
        }
        println("sources=${sources.size} throwingCalls=${calls.size} findings=${findings.size}")
        findings.forEach { println("FINDING $it") }
        assertTrue(
            "a block that only throws infers `Nothing`, so comparing its result against null is a " +
                "predetermined condition the compiler reports — name the block's type so the " +
                "comparison asks what the test means: $findings",
            findings.isEmpty()
        )
    }

    @Test
    fun `no auto test source casts to a parameterized type unchecked`() {
        val sources = sourcesIn(ownedRoot)

        // Positive control: the unchecked cast the sweep removed is a finding, its checked star-projection
        // sibling is not.
        assertEquals(
            listOf("as Array<Any?>"),
            uncheckedCastFindings("val formatArgs = args[1] as Array<Any?>")
        )
        assertEquals(
            emptyList<String>(),
            uncheckedCastFindings("val formatArgs = args[1] as Array<*>")
        )

        val findings = sources.flatMap { source ->
            uncheckedCastFindings(source.readText()).map { "${source.name}: $it" }
        }
        println("sources=${sources.size} findings=${findings.size}")
        findings.forEach { println("FINDING $it") }
        assertTrue(
            "a cast to a parameterized type is unchecked at run time, so the compiler reports it — " +
                "cast to a star projection, use a checked `as?`, or copy through a typed collection: " +
                findings.joinToString(),
            findings.isEmpty()
        )
    }

    // --- the three finders, each a pure function of one source text ---

    /** `T : X` bounds whose `X` is declared final, so the bound fixes the type parameter's value. */
    private fun boundFindings(code: String, finalNames: Set<String>): List<String> =
        typeParameterBound.findAll(code).mapNotNull { match ->
            val bound = match.groupValues[2]
            if (bound in finalNames) "${match.groupValues[1]} : $bound" else null
        }.toList()

    /**
     * A `runIteration` call whose block only throws, where the call names no type argument and its result is
     * compared against `null` — the comparison the compiler proves.
     */
    private fun throwingBlockFindings(code: String): List<String> =
        throwingIterationCalls(code).mapNotNull { match ->
            val namesTypeArgument = match.groupValues[1].isNotBlank()
            if (namesTypeArgument) return@mapNotNull null
            val rest = code.substring(match.range.last + 1)
            val comparison = nullComparison.find(rest)?.value?.trim() ?: return@mapNotNull null
            "${match.value.trim()} $comparison"
        }.toList()

    private fun throwingIterationCalls(code: String): List<MatchResult> =
        throwingIteration.findAll(code).toList()

    /** A cast to a parameterized type that is not a star projection; `as?` is a checked cast. */
    private fun uncheckedCastFindings(code: String): List<String> =
        uncheckedCast.findAll(code).mapNotNull { match ->
            match.value.takeIf { match.groupValues[2].trim() != "*" }
        }.toList()

    // --- the scans ---

    private fun sourcesIn(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.sorted().toList()

    private fun finalClassNames(): Set<String> = mainRoots
        .flatMap { sourcesIn(it) }
        .flatMap { source ->
            classDeclaration.findAll(source.readText()).mapNotNull { match ->
                val modifiers = match.groupValues[1].trim().split(' ').filter { it.isNotEmpty() }
                match.groupValues[2].takeUnless { modifiers.any { modifier -> modifier in nonFinalMarkers } }
            }
        }
        .toSet()

    private companion object {
        /** Modifiers that leave a class extendable, so a bound naming it fixes nothing. */
        val nonFinalMarkers = setOf("open", "abstract", "sealed", "enum", "annotation")

        val typeParameterBound = Regex("""<\s*([A-Z]\w*)\s*:\s*([A-Z]\w*)""")

        val throwingIteration =
            Regex("""runIteration\s*(<\s*\w+\s*>)?\s*\([^()]*\)\s*\{\s*(?:error|throw )[^}]*}""")

        val nullComparison = Regex("""^\s*[!=]=\s*null""")

        val uncheckedCast = Regex("""\bas\s+([A-Z]\w*)\s*<([^<>]*)>""")

        val classDeclaration = Regex(
            """(?m)^[ \t]*(?:@[\w.]+(?:\([^)]*\))?[ \t]+)*""" +
                """(?:public |internal |private |protected )?""" +
                """((?:(?:open|abstract|sealed|enum|annotation|inner|data|value) )*)""" +
                """class[ \t]+([A-Z]\w*)"""
        )
    }
}
