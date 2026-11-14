package com.naviveylin.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `port` rule (change `package-structure-as-five-kinds`, task 1.5; spec `package-structure` — "port holds
 * abstraction only"): a port is the OS or hardware abstracted, so it names no framework type and imports
 * nothing of ours but `concept`.
 */
class PortRuleTest {

    private val declaration: StructureDeclaration = StructureDeclaration.parse(
        "# test declaration\n" +
            "core\tcom.naviveylin.concept\tconcept\t-\t-\n" +
            "core\tcom.naviveylin.core.port\tport\t-\tconcept\n",
    )

    /** The concept package the declaration also claims: a row with no file under it is its own violation. */
    private val units = SourceFile(
        path = "core/src/main/java/com/naviveylin/concept/Units.kt",
        packageName = "com.naviveylin.concept",
    )

    private fun reportOf(imports: List<String>): StructureReport = StructureReport.of(
        declaration,
        listOf(
            units,
            SourceFile(
                path = "core/src/main/java/com/naviveylin/core/port/Store.kt",
                packageName = "com.naviveylin.core.port",
                imports = imports,
            ),
        ),
        Baseline(emptyList()),
    )

    @Test
    fun portImportsNoAndroidType() {
        val report = reportOf(listOf("android.content.Context"))

        assertFalse("a port carrying an Android type must fail: ${report.summary()}", report.passed)
        assertTrue(
            "the violation must name the file: ${report.summary()}",
            report.summary().contains("core/src/main/java/com/naviveylin/core/port/Store.kt"),
        )
        assertTrue(
            "the violation must name the import: ${report.summary()}",
            report.summary().contains("android.content.Context"),
        )
        assertTrue(
            "the violation must name the rule: ${report.summary()}",
            report.summary().contains(StructureRule.PORT_IMPORTS_NO_ANDROID_TYPE.id),
        )
    }

    @Test
    fun portImportsOnlyConcept() {
        val allowed = reportOf(listOf("com.naviveylin.concept.Units"))
        assertTrue("a port importing a concept type must pass: ${allowed.summary()}", allowed.passed)

        val refused = reportOf(
            listOf("com.naviveylin.feature.favourites.Favourite", "com.naviveylin.concept.Units"),
        )

        assertFalse("a port reaching into a feature must fail: ${refused.summary()}", refused.passed)
        assertTrue(
            "the violation must name the file: ${refused.summary()}",
            refused.summary().contains("core/src/main/java/com/naviveylin/core/port/Store.kt"),
        )
        assertTrue(
            "the violation must name the import it reached through: ${refused.summary()}",
            refused.summary().contains("com.naviveylin.feature.favourites.Favourite"),
        )
        assertTrue(
            "the violation must name the rule: ${refused.summary()}",
            refused.summary().contains(StructureRule.PORT_IMPORTS_ONLY_CONCEPT.id),
        )
        assertTrue(
            "the concept import the file also carries must not be reported: ${refused.summary()}",
            !refused.summary().contains("imports com.naviveylin.concept.Units"),
        )
    }
}
