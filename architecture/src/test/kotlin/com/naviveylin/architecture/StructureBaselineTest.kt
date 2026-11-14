package com.naviveylin.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The ratchet and its one exemption (change `package-structure-as-five-kinds`, tasks 1.7 and 1.10; spec
 * `architecture-test-suite` — "The baseline is shrink-only" and "The baseline is the only exemption"): a file
 * that does not yet satisfy a rule is a named entry carrying the change that moves it, an entry that exempts
 * nothing fails, and no in-code suppression exempts.
 */
class StructureBaselineTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private val declaration: StructureDeclaration =
        StructureDeclaration.parse("# test declaration\napp\tcom.naviveylin.concept\tconcept\t-\t-\n")

    private fun baselineOf(vararg rows: String): Baseline =
        Baseline.parse("# test baseline\n" + rows.joinToString("\n") + if (rows.isEmpty()) "" else "\n")

    private fun reportOf(imports: List<String>, baseline: Baseline): StructureReport = StructureReport.of(
        declaration,
        listOf(SourceFile(SOURCE, "com.naviveylin.concept", imports)),
        baseline,
    )

    @Test
    fun staleExemptionFails() {
        val stale = reportOf(
            imports = listOf("com.naviveylin.concept.Units"),
            baseline = baselineOf(entry(ANDROID_RULE)),
        )

        assertFalse("an entry whose file no longer violates the rule must fail: ${stale.summary()}", stale.passed)
        assertTrue(
            "the violation must name the entry: ${stale.summary()}",
            stale.summary().contains("$BASELINE_PATH:2"),
        )
        assertTrue(
            "the violation must name the rule the entry claims: ${stale.summary()}",
            stale.summary().contains(ANDROID_RULE),
        )
        assertTrue(
            "the violation must name the file: ${stale.summary()}",
            stale.summary().contains(SOURCE),
        )

        val green = reportOf(imports = listOf("com.naviveylin.concept.Units"), baseline = baselineOf())
        assertTrue("the check must pass once the entry is removed: ${green.summary()}", green.passed)
    }

    @Test
    fun exemptionWithoutChangeFails() {
        val orphan = reportOf(
            imports = listOf("android.graphics.Canvas"),
            baseline = baselineOf("$SOURCE\t$ANDROID_RULE\t-"),
        )

        assertFalse("an entry naming no change must fail: ${orphan.summary()}", orphan.passed)
        assertTrue(
            "the violation must name the entry: ${orphan.summary()}",
            orphan.summary().contains("$BASELINE_PATH:2"),
        )
        assertTrue(
            "the violation must name what the entry lacks: ${orphan.summary()}",
            orphan.summary().contains(StructureRule.EXEMPTION_WITHOUT_CHANGE.id),
        )

        val green = reportOf(imports = listOf("android.graphics.Canvas"), baseline = baselineOf(entry(ANDROID_RULE)))
        assertTrue("the check must pass once the entry names its change: ${green.summary()}", green.passed)
    }

    @Test
    fun baselinedFileIsCountedNotReported() {
        val report = reportOf(
            imports = listOf("android.graphics.Canvas"),
            baseline = baselineOf(entry(ANDROID_RULE)),
        )

        assertTrue("a baselined file must not be reported: ${report.summary()}", report.passed)
        assertTrue("no violation may be listed: ${report.summary()}", report.violations.isEmpty())
        assertEquals("the exempted entry must be counted: ${report.summary()}", 1, report.exemptions)
        assertTrue(
            "the outstanding-exemption figure must state the count: ${report.summary()}",
            report.summary().contains("exemptions=1"),
        )
    }

    @Test
    fun suppressionAnnotationDoesNotExempt() {
        val file = File(temporary.newFolder(), SOURCE)
        assertTrue("could not create ${file.parentFile}", file.parentFile.mkdirs() || file.parentFile.isDirectory)
        file.writeText(
            "package com.naviveylin.concept\n" +
                "\n" +
                "import android.graphics.Canvas\n" +
                "\n" +
                "@Suppress(\"concept imports no Android type\")\n" +
                "class Concept\n",
        )

        val report = StructureReport.of(
            declaration,
            listOf(
                SourceFile(
                    path = SOURCE,
                    packageName = "com.naviveylin.concept",
                    imports = file.readLines()
                        .map { it.trim() }
                        .filter { it.startsWith("import ") }
                        .map { it.removePrefix("import ").trim().substringBefore(' ') },
                ),
            ),
            Baseline(emptyList()),
        )

        assertFalse("a suppression annotation must not exempt: ${report.summary()}", report.passed)
        assertTrue(
            "the violation must name the rule the annotation tried to silence: ${report.summary()}",
            report.summary().contains(StructureRule.CONCEPT_IMPORTS_NO_ANDROID_TYPE.id),
        )
    }

    private companion object {
        const val SOURCE = "app/src/main/java/com/naviveylin/concept/Concept.kt"
        const val ANDROID_RULE = "concept imports no Android type"
        const val OWNING_CHANGE = "package-structure-concept-migration"

        fun entry(rule: String): String = "$SOURCE\t$rule\t$OWNING_CHANGE"
    }
}
