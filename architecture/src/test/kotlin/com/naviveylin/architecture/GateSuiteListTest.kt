package com.naviveylin.architecture

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate cases of change `package-structure-as-five-kinds` (task 5.1): the structure check belongs to the
 * project's verification gate, so a change cannot reach the main branch with a structure violation.
 *
 * The gate's own command is the root `test` task (`tools/loop/loop.conf` `LOOP_GATE_COMPILED_CMD`), and it
 * reaches `:architecture:test` because `settings.gradle.kts` includes the module — the reachability this
 * class asserts, together with the two records that tell a reader the suite is owed: the guideline rule and
 * the workflow's suite report.
 */
class GateSuiteListTest {

    private val aggregateTest = Regex("""(^|\s)test(\s|$)""")

    @Test
    fun gateSuiteListIncludesArchitecture() {
        assertSettingsIncludeTheModule()

        val gate = loopConfValue("LOOP_GATE_COMPILED_CMD")
        assertTrue("tools/loop/loop.conf carries no LOOP_GATE_COMPILED_CMD", gate.isNotEmpty())
        assertTrue(
            "the gate's command runs no aggregate test task, so it reaches no module's suite: '$gate'",
            aggregateTest.containsMatchIn(gate),
        )

        val owner = ProjectRoot.resolve("guidelines/BuildEvidence.md").readText()
        assertTrue(
            "`guidelines/BuildEvidence.md` §4b names no architecture suite, so no reader is told the gate " +
                "owes it",
            owner.contains(":architecture:test"),
        )

        val workflow = ProjectRoot.resolve(".github/workflows/build.yml").readText()
        assertTrue(
            "`.github/workflows/build.yml` names the structure suite nowhere, so its suite report leaves " +
                "the check uncounted",
            workflow.contains("architecture/build/test-results/test"),
        )
    }

    @Test
    fun gateFailsAtStructureCheck() {
        assertSettingsIncludeTheModule()

        val violatingTree = ProjectSources.mainSourceFiles() + SourceFile(
            path = "core/src/main/java/com/naviveylin/probe/Probe.kt",
            packageName = "com.naviveylin.probe",
        )
        val violations =
            StructureCheck(StructureDeclaration.parse(ProjectRoot.declarationText())).violations(violatingTree)
        assertTrue(
            "the suite the gate's aggregate test task runs must refuse a violating tree, or the gate " +
                "records a green run on a tree the structure forbids: $violations",
            violations.isNotEmpty(),
        )
    }

    private fun assertSettingsIncludeTheModule() {
        val settings = ProjectRoot.resolve("settings.gradle.kts").readText()
        assertTrue(
            "settings.gradle.kts does not include :architecture, so no aggregate test run reaches the " +
                "structure check",
            settings.contains("include(\":architecture\")"),
        )
    }

    /** The value a `tools/loop/loop.conf` key carries, or "" when the file states no such key. */
    private fun loopConfValue(key: String): String =
        ProjectRoot.resolve("tools/loop/loop.conf").readText().lineSequence()
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
            ?: ""
}
