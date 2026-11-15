package com.naviveylin.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report a passing run prints (change `package-structure-as-five-kinds`, task 1.8; spec
 * `architecture-test-suite` — "A green run reports what it verified"): the declaration rows read, the source
 * files classified, the exemptions still outstanding and the rules evaluated, so a run that verified nothing
 * cannot pass silently.
 */
class StructureReportTest {

    @Test
    fun greenRunPrintsFigures() {
        val declaration = StructureDeclaration.parse(ProjectRoot.declarationText())
        val report = StructureReport.of(
            declaration,
            ProjectSources.sourceFiles(),
            Baseline.parse(ProjectSources.baselineText()),
        )

        assertTrue("the project's own tree must pass the check: ${report.summary()}", report.passed)
        assertEquals("every declaration row must be read: ${report.summary()}", 33, report.rows)
        assertEquals(
            "every main and test source file of the three modules must be classified: ${report.summary()}",
            857,
            report.files,
        )
        assertEquals(
            "the day-one baseline's 14 entries, the feature rules' 23 and the mirror rule's 9 rows must be " +
                "counted as outstanding exemptions: ${report.summary()}",
            46,
            report.exemptions,
        )
        assertEquals(
            "every rule the check evaluates must be counted: ${report.summary()}",
            StructureRules.ALL.size,
            report.rules,
        )
        assertTrue(
            "the green run must state its four figures: ${report.summary()}",
            report.summary().contains("rows=33 files=857 exemptions=46 rules=10"),
        )
    }

    /**
     * The figures are the tree's, not those of a copy of the tree: Konsist's project scope descends into every
     * directory under the root that carries no `.git`, so a loop item's scratch tree and any other nested copy of
     * `app/src/main/java/…` reach the check as source files of this project. Measured 2026-11-14 in the main
     * checkout, whose `.pi/issue-loop/items/839/scratch/fx` held 2 285 such files: `files=2623` against the tree's
     * 338, and `app/src/main/java/com/example/data/Basemap.kt` — a path no module of this tree has — reported as a
     * default-deny violation, so a baselined tree read red for a file it does not carry.
     */
    @Test
    fun aNestedCopyOfASourceRootIsNotTheTree() {
        val root = ProjectRoot.dir.absolutePath.replace('\\', '/').trimEnd('/')
        val treeFile = "app/src/main/java/com/naviveylin/core/MapRenderUtil.kt"

        listOf(
            ".pi/issue-loop/items/839/scratch/fx",
            ".pi/worktrees/loop",
            ".agents/skills/example",
        ).forEach { copy ->
            assertNull(
                "$root/$copy/$treeFile is a copy of the tree's source root, not a file of the tree",
                ProjectSources.projectRelative("$root/$copy/$treeFile"),
            )
        }

        assertEquals(
            "the tree's own file is a file of the tree",
            treeFile,
            ProjectSources.projectRelative("$root/$treeFile"),
        )
        assertEquals(
            "a path Konsist reports relative to the project root is a file of the tree",
            treeFile,
            ProjectSources.projectRelative(treeFile),
        )
    }
}
