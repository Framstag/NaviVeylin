package com.naviveylin.architecture

import org.junit.Assert.assertEquals
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
            ProjectSources.mainSourceFiles(),
            Baseline.parse(ProjectSources.baselineText()),
        )

        assertTrue("the project's own tree must pass the check: ${report.summary()}", report.passed)
        assertEquals("every declaration row must be read: ${report.summary()}", 27, report.rows)
        assertEquals("every main source file must be classified: ${report.summary()}", 338, report.files)
        assertEquals(
            "the day-one baseline's 14 entries must be counted as outstanding exemptions: ${report.summary()}",
            14,
            report.exemptions,
        )
        assertEquals(
            "every rule the check evaluates must be counted: ${report.summary()}",
            LayerRules.ALL.size,
            report.rules,
        )
        assertTrue(
            "the green run must state its four figures: ${report.summary()}",
            report.summary().contains("rows=27 files=338 exemptions=14 rules=4"),
        )
    }
}
