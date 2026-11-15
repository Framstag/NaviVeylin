package com.naviveylin.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The check's scope (change `package-structure-rules-and-suite`, task 2.2; spec `architecture-test-suite` — "The
 * check reads every source set of the three modules"): the main **and** test sources of `:app`, `:auto` and
 * `:core` are read, a violation in a test source is reported like a violation in a main source, and none of it
 * needs a device or an assembled app module — Konsist reads source text.
 */
class StructureScopeTest {

    private val files = ProjectSources.sourceFiles()
    private val declaration = StructureDeclaration.parse(ProjectRoot.declarationText())

    /**
     * The six roots the spec names, written out rather than read back from [ProjectSources]: a case that derives
     * its expectation from the code under test cannot fail when a root is dropped from it.
     */
    private val sixRoots = listOf(
        "app/src/main/java/",
        "app/src/test/java/",
        "auto/src/main/java/",
        "auto/src/test/java/",
        "core/src/main/java/",
        "core/src/test/java/",
    )

    @Test
    fun everySourceSetOfTheThreeModulesIsRead() {
        sixRoots.forEach { root ->
            assertTrue(
                "the check must read $root, and reads no file of it",
                files.any { it.path.startsWith(root) },
            )
        }
    }

    @Test
    fun violationInTestSourceIsReported() {
        val stranger = SourceFile(
            path = "app/src/test/java/com/naviveylin/ui/scratch/ScratchTest.kt",
            packageName = "com.naviveylin.ui.scratch",
        )
        val report = StructureReport.of(declaration, files + stranger, Baseline.parse(ProjectSources.baselineText()))

        assertFalse("a violating test source must fail the check: ${report.summary()}", report.passed)
        assertTrue(
            "the violation must name the test file: ${report.summary()}",
            report.summary().contains(stranger.path),
        )
        assertTrue(
            "the violation must name the rule: ${report.summary()}",
            report.summary().contains(StructureRule.UNDECLARED_PACKAGE.id),
        )
    }

    @Test
    fun violationInTheAppModuleIsReported() {
        val findings = LayerRules.violations(declaration, ProjectSources.mainSourceFiles())

        assertTrue(
            "the application module's main sources must be read, and no finding names one",
            findings.any { it.pointer.startsWith("app/src/main/java/com/naviveylin/") },
        )
    }

    @Test
    fun suiteRunsWithoutDeviceOrAppBuild() {
        assertNull(
            "the check must read the tree's text, not the Android framework: android.os.Build is on the " +
                "classpath, so this suite is no longer a plain JVM one",
            runCatching { Class.forName("android.os.Build") }.getOrNull(),
        )
        assertFalse("the tree must carry source files to read", files.isEmpty())
    }
}