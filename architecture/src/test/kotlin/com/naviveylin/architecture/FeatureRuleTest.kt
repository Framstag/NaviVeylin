package com.naviveylin.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `feature` rules (change `package-structure-rules-and-suite`, task 1.6; spec `package-structure` —
 * "features are mutually disjunct" and "a feature is Android-free"): a feature imports `concept` and `port`,
 * never another feature, never upward into `platform` or `shell`, and never an Android type.
 *
 * Every case hands the check the package tree its assertion is about, so no case writes into this project's
 * tree to watch a violation appear.
 */
class FeatureRuleTest {

    private val declaration: StructureDeclaration = StructureDeclaration.parse(
        "# test declaration\n" +
            "app\tcom.naviveylin.concept\tconcept\t-\t-\n" +
            "core\tcom.naviveylin.core.port\tport\t-\tconcept\n" +
            "app\tcom.naviveylin.feature.navigation\tfeature\tnavigation\tconcept,port\n" +
            "app\tcom.naviveylin.feature.route\tfeature\troute\tconcept,port\n" +
            "app\tcom.naviveylin.platform.map\tplatform\tmap\tconcept,port,feature/map\n" +
            "app\tcom.naviveylin.shell\tshell\t-\tany\n",
    )

    /**
     * One file per declared package, so no case reports a `row-names-a-package-the-tree-lacks` violation by
     * accident: the case's imports belong to the `feature/navigation` file.
     */
    private fun tree(navigationImports: List<String>): List<SourceFile> = listOf(
        SourceFile("app/src/main/java/com/naviveylin/concept/Units.kt", "com.naviveylin.concept"),
        SourceFile("core/src/main/java/com/naviveylin/core/port/Store.kt", "com.naviveylin.core.port"),
        SourceFile(
            path = FEATURE_FILE,
            packageName = "com.naviveylin.feature.navigation",
            imports = navigationImports,
        ),
        SourceFile("app/src/main/java/com/naviveylin/feature/route/RouteLength.kt", "com.naviveylin.feature.route"),
        SourceFile("app/src/main/java/com/naviveylin/platform/map/MapCanvas.kt", "com.naviveylin.platform.map"),
        SourceFile("app/src/main/java/com/naviveylin/shell/AppShell.kt", "com.naviveylin.shell"),
    )

    private fun reportOf(navigationImports: List<String>): StructureReport =
        StructureReport.of(declaration, tree(navigationImports), Baseline(emptyList()))

    @Test
    fun featureDisjunctionViolationNamesTheImport() {
        val report = reportOf(listOf("com.naviveylin.feature.route.RouteLength"))

        assertFalse("a feature importing another feature must fail: ${report.summary()}", report.passed)
        assertTrue(
            "the violation must name the file: ${report.summary()}",
            report.summary().contains(FEATURE_FILE),
        )
        assertTrue(
            "the violation must name the import: ${report.summary()}",
            report.summary().contains("com.naviveylin.feature.route.RouteLength"),
        )
        assertTrue(
            "the violation must name the rule: ${report.summary()}",
            report.summary().contains(StructureRule.FEATURE_IMPORTS_ANOTHER_FEATURE.id),
        )
    }

    @Test
    fun featureImportingShellFails() {
        val shell = reportOf(listOf("com.naviveylin.shell.AppShell"))

        assertFalse("a feature reaching into shell must fail: ${shell.summary()}", shell.passed)
        assertTrue(
            "the violation must name the file: ${shell.summary()}",
            shell.summary().contains(FEATURE_FILE),
        )
        assertTrue(
            "the violation must name the shell import: ${shell.summary()}",
            shell.summary().contains("com.naviveylin.shell.AppShell"),
        )
        assertTrue(
            "the violation must name the upward rule: ${shell.summary()}",
            shell.summary().contains(StructureRule.FEATURE_IMPORTS_NOTHING_UPWARD.id),
        )

        val platform = reportOf(listOf("com.naviveylin.platform.map.MapCanvas"))

        assertFalse("a feature reaching into platform must fail: ${platform.summary()}", platform.passed)
        assertTrue(
            "the violation must name the platform import: ${platform.summary()}",
            platform.summary().contains("com.naviveylin.platform.map.MapCanvas"),
        )
        assertTrue(
            "the violation must name the upward rule: ${platform.summary()}",
            platform.summary().contains(StructureRule.FEATURE_IMPORTS_NOTHING_UPWARD.id),
        )
    }

    @Test
    fun featurePurityViolationNamesTheImport() {
        val report = reportOf(listOf("android.content.Context"))

        assertFalse("a feature carrying an Android type must fail: ${report.summary()}", report.passed)
        assertTrue(
            "the violation must name the file: ${report.summary()}",
            report.summary().contains(FEATURE_FILE),
        )
        assertTrue(
            "the violation must name the import: ${report.summary()}",
            report.summary().contains("android.content.Context"),
        )
        assertTrue(
            "the violation must name the purity rule: ${report.summary()}",
            report.summary().contains(StructureRule.FEATURE_IMPORTS_NO_ANDROID_TYPE.id),
        )

        val androidx = reportOf(listOf("androidx.compose.runtime.Composable"))

        assertFalse("an `androidx` type is the framework too: ${androidx.summary()}", androidx.passed)
        assertTrue(
            "the violation must name the rule: ${androidx.summary()}",
            androidx.summary().contains(StructureRule.FEATURE_IMPORTS_NO_ANDROID_TYPE.id),
        )
    }

    @Test
    fun featureImportingPortPasses() {
        val report = reportOf(
            listOf("com.naviveylin.concept.Units", "com.naviveylin.core.port.Store"),
        )

        assertTrue(
            "a feature importing a concept and a port must pass: ${report.summary()}",
            report.passed,
        )
    }

    /**
     * The report's completeness (task 2.1; spec `architecture-test-suite` — "A violation names the file, the
     * import and the rule"): one disjunction violation carries all four fragments a reader needs — the file, the
     * import, the file's declared kind and the rule the file contradicts.
     */
    @Test
    fun violationNamesFileImportAndRule() {
        val report = reportOf(listOf("com.naviveylin.feature.route.RouteLength"))
        val message = report.violations.single { it.rule == StructureRule.FEATURE_IMPORTS_ANOTHER_FEATURE }.message

        assertTrue("the message must name the file: $message", message.contains(FEATURE_FILE))
        assertTrue(
            "the message must name the import: $message",
            message.contains("com.naviveylin.feature.route.RouteLength"),
        )
        assertTrue("the message must name the declared kind: $message", message.contains("declared 'feature'"))
        assertTrue(
            "the message must name the contradicted rule: $message",
            message.contains(StructureRule.FEATURE_IMPORTS_ANOTHER_FEATURE.id),
        )
    }

    /**
     * Which row owns an import is the **longest** declared package it names: the real declaration holds
     * `com.naviveylin.core` as a `concept` row and `com.naviveylin.core.search` as a `feature` row, so
     * `core.search`'s own types must resolve to `core.search`. Here the shorter row is a `platform` one, so a
     * check that resolved the row by prefix order alone would report the upward rule where the disjunction rule
     * applies.
     */
    @Test
    fun nestedRowOwnsTheImportItPrefixes() {
        val nested = StructureDeclaration.parse(
            "# test declaration\n" +
                "app\tcom.naviveylin.feature.navigation\tfeature\tnavigation\tconcept,port\n" +
                "app\tcom.naviveylin.ui\tplatform\tui\tany\n" +
                "app\tcom.naviveylin.ui.route\tfeature\troute\tconcept,port\n",
        )
        val report = StructureReport.of(
            nested,
            listOf(
                SourceFile(
                    path = FEATURE_FILE,
                    packageName = "com.naviveylin.feature.navigation",
                    imports = listOf("com.naviveylin.ui.route.RouteLength"),
                ),
                SourceFile("app/src/main/java/com/naviveylin/ui/Shell.kt", "com.naviveylin.ui"),
                SourceFile("app/src/main/java/com/naviveylin/ui/route/RouteLength.kt", "com.naviveylin.ui.route"),
            ),
            Baseline(emptyList()),
        )

        assertFalse("a feature importing a nested feature row must fail: ${report.summary()}", report.passed)
        assertTrue(
            "the longer row prefixing the import owns it, so the disjunction rule applies: ${report.summary()}",
            report.violations.any {
                it.rule == StructureRule.FEATURE_IMPORTS_ANOTHER_FEATURE &&
                    it.message.contains(FEATURE_FILE) &&
                    it.message.contains("com.naviveylin.ui.route.RouteLength")
            },
        )
        assertTrue(
            "the shorter row must not claim the import as an upward one: ${report.summary()}",
            report.violations.none { it.rule == StructureRule.FEATURE_IMPORTS_NOTHING_UPWARD },
        )
    }

    private companion object {
        const val FEATURE_FILE = "app/src/main/java/com/naviveylin/feature/navigation/Navigation.kt"
    }
}