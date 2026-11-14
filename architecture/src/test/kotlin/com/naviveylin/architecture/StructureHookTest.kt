package com.naviveylin.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hook cases of change `package-structure-as-five-kinds` (task 1.9): every unit-test task of `:app`,
 * `:auto` and `:core` runs the structure check before it, so a focused run cannot report a pass on a tree
 * the structure forbids.
 *
 * A Gradle invocation cannot be nested inside the test task it would start, so the two halves that make the
 * focused run fail are asserted here, and one real invocation joins them — the change's revert-check 3.6:
 * a module's build file declares the prerequisite, and the task it names refuses a file in a package no row
 * of the declaration claims.
 */
class StructureHookTest {

    private val hookedModules = listOf("app", "auto", "core")
    private val declaration = StructureDeclaration.parse(ProjectRoot.declarationText())
    private val treeFiles = ProjectSources.mainSourceFiles()

    @Test
    fun focusedTestRunOnViolatingTreeFails() {
        hookedModules.forEach(::assertTestTaskRunsTheCheck)

        val violatingTree = treeFiles + SourceFile(
            path = "app/src/main/java/com/naviveylin/probe/Probe.kt",
            packageName = "com.naviveylin.probe",
        )
        val violations = StructureCheck(declaration).violations(violatingTree)
        assertTrue(
            "the task every unit-test task depends on must refuse a violating tree, or the focused run " +
                "reports the class's pass on a tree the structure forbids: $violations",
            violations.isNotEmpty(),
        )
    }

    @Test
    fun satisfyingTreeRunsTestsNormally() {
        hookedModules.forEach(::assertTestTaskRunsTheCheck)
        assertEquals(
            "a satisfying tree raises no violation, so the prerequisite passes and the module's own result " +
                "stands",
            emptyList<StructureViolation>(),
            StructureCheck(declaration).violations(treeFiles),
        )
    }

    /** The hook a module's build file owes: a prerequisite on the structure check for every `Test` task. */
    private fun assertTestTaskRunsTheCheck(module: String) {
        val path = "$module/build.gradle.kts"
        val code = ProjectRoot.resolve(path).readText().lineSequence()
            .map { it.substringBefore("//") }
            .joinToString("\n")
        assertTrue(
            "$path makes no Test task depend on :architecture:test, so a focused run of $module's tests " +
                "passes on a violating tree (`guidelines/BuildEvidence.md` §4b)",
            code.contains("dependsOn(\":architecture:test\")"),
        )
    }
}
