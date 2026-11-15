package com.naviveylin.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `concept` rule (change `package-structure-as-five-kinds`, task 1.5; spec `package-structure` — "concept
 * is self-contained"): a concept holds what things are made of, so it imports nothing of ours and no Android
 * type.
 *
 * Each case states its file's imports and runs the check over them, so the assertion is about the rule and not
 * about the accident of this project's tree.
 */
class ConceptRuleTest {

    private val declaration: StructureDeclaration =
        StructureDeclaration.parse("# test declaration\napp\tcom.naviveylin.concept\tconcept\t-\t-\n")

    private fun reportOf(file: SourceFile): StructureReport =
        StructureReport.of(declaration, listOf(file), Baseline(emptyList()))

    @Test
    fun conceptImportsNothingOfOurs() {
        val report = reportOf(
            SourceFile(
                path = "app/src/main/java/com/naviveylin/concept/Concept.kt",
                packageName = "com.naviveylin.concept",
                imports = listOf("com.naviveylin.feature.search.SearchRequest"),
            ),
        )

        assertFalse("a concept file importing another of our packages must fail: ${report.summary()}", report.passed)
        assertTrue(
            "the violation must name the file: ${report.summary()}",
            report.summary().contains("app/src/main/java/com/naviveylin/concept/Concept.kt"),
        )
        assertTrue(
            "the violation must name the import: ${report.summary()}",
            report.summary().contains("com.naviveylin.feature.search.SearchRequest"),
        )
        assertTrue(
            "the violation must name the file's declared kind: ${report.summary()}",
            report.summary().contains("declared 'concept'"),
        )
        assertTrue(
            "the violation must name the rule: ${report.summary()}",
            report.summary().contains(StructureRule.CONCEPT_IMPORTS_NOTHING_OF_OURS.id),
        )
    }

    @Test
    fun conceptImportsNoAndroidType() {
        val android = reportOf(
            SourceFile(
                path = "app/src/main/java/com/naviveylin/concept/Concept.kt",
                packageName = "com.naviveylin.concept",
                imports = listOf("android.graphics.Canvas"),
            ),
        )

        assertFalse("a concept file importing the Android framework must fail: ${android.summary()}", android.passed)
        assertTrue(
            "the violation must name the import: ${android.summary()}",
            android.summary().contains("android.graphics.Canvas"),
        )
        assertTrue(
            "the violation must name the rule: ${android.summary()}",
            android.summary().contains(StructureRule.CONCEPT_IMPORTS_NO_ANDROID_TYPE.id),
        )

        val green = reportOf(
            SourceFile(
                path = "app/src/main/java/com/naviveylin/concept/Concept.kt",
                packageName = "com.naviveylin.concept",
                imports = listOf("com.naviveylin.concept.Units"),
            ),
        )
        assertTrue("the same file with no Android import must pass: ${green.summary()}", green.passed)
    }
}
