package com.naviveylin.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `platform` mirror rule (change `package-structure-rules-and-suite`, task 1.7; spec `package-structure` —
 * "a platform package mirrors a feature"): a `platform/<name>` row names a feature a `feature` row declares, the
 * package's files reach that feature, and no platform file serves another feature's package.
 *
 * The mirror's finding addresses the declaration **row**, not each of the package's files, so the day-one
 * declaration is counted with one baseline entry per unmirrored package.
 */
class PlatformRuleTest {

    private val declaration: StructureDeclaration = StructureDeclaration.parse(
        "# test declaration\n" +
            "app\tcom.naviveylin.concept\tconcept\t-\t-\n" +
            "core\tcom.naviveylin.core.port\tport\t-\tconcept\n" +
            "app\tcom.naviveylin.feature.search\tfeature\tsearch\tconcept,port\n" +
            "app\tcom.naviveylin.feature.map\tfeature\tmap\tconcept,port\n" +
            "app\tcom.naviveylin.platform.search\tplatform\tsearch\tconcept,port,feature/search\n" +
            "app\tcom.naviveylin.platform.map\tplatform\tmap\tconcept,port,feature/map\n" +
            "app\tcom.naviveylin.shell\tshell\t-\tany\n",
    )

    private fun tree(platformSearchImports: List<String>): List<SourceFile> = listOf(
        SourceFile("app/src/main/java/com/naviveylin/concept/Units.kt", "com.naviveylin.concept"),
        SourceFile("core/src/main/java/com/naviveylin/core/port/Store.kt", "com.naviveylin.core.port"),
        SourceFile("app/src/main/java/com/naviveylin/feature/search/SearchEngine.kt", "com.naviveylin.feature.search"),
        SourceFile("app/src/main/java/com/naviveylin/feature/map/MapModel.kt", "com.naviveylin.feature.map"),
        SourceFile(
            path = PLATFORM_FILE,
            packageName = "com.naviveylin.platform.search",
            imports = platformSearchImports,
        ),
        SourceFile(
            "app/src/main/java/com/naviveylin/platform/map/MapFiles.kt",
            "com.naviveylin.platform.map",
            listOf("com.naviveylin.feature.map.MapModel"),
        ),
        SourceFile("app/src/main/java/com/naviveylin/shell/AppShell.kt", "com.naviveylin.shell"),
    )

    private fun reportOf(
        platformSearchImports: List<String> = listOf("com.naviveylin.feature.search.SearchEngine"),
    ): StructureReport = StructureReport.of(declaration, tree(platformSearchImports), Baseline(emptyList()))

    @Test
    fun platformImportingSiblingPlatformFails() {
        val sibling = reportOf(
            listOf("com.naviveylin.feature.search.SearchEngine", "com.naviveylin.platform.map.MapFiles"),
        )

        assertFalse("a platform serving another platform must fail: ${sibling.summary()}", sibling.passed)
        assertTrue(
            "the violation must name the file: ${sibling.summary()}",
            sibling.summary().contains(PLATFORM_FILE),
        )
        assertTrue(
            "the violation must name the sibling import: ${sibling.summary()}",
            sibling.summary().contains("com.naviveylin.platform.map.MapFiles"),
        )
        assertTrue(
            "the violation must name the rule: ${sibling.summary()}",
            sibling.summary().contains(StructureRule.PLATFORM_IMPORTS_SIBLING_PLATFORM.id),
        )

        val own = reportOf()

        assertTrue(
            "a platform importing its own feature and kind must pass: ${own.summary()}",
            own.passed,
        )
    }

    @Test
    fun platformWithoutFeatureFails() {
        val orphan = StructureDeclaration.parse(
            "# test declaration\n" +
                "app\tcom.naviveylin.feature.search\tfeature\tsearch\tconcept,port\n" +
                "app\tcom.naviveylin.platform.search\tplatform\tsearch\tconcept,port,feature/search\n" +
                "app\tcom.naviveylin.platform.reports\tplatform\treports\tconcept,port,feature/reports\n",
        )
        val files = listOf(
            SourceFile("app/src/main/java/com/naviveylin/feature/search/SearchEngine.kt", "com.naviveylin.feature.search"),
            SourceFile(
                "app/src/main/java/com/naviveylin/platform/search/SearchFiles.kt",
                "com.naviveylin.platform.search",
                listOf("com.naviveylin.feature.search.SearchEngine"),
            ),
            SourceFile("app/src/main/java/com/naviveylin/platform/reports/Reports.kt", "com.naviveylin.platform.reports"),
        )

        val report = StructureReport.of(orphan, files, Baseline(emptyList()))

        assertFalse("a platform with no feature must fail: ${report.summary()}", report.passed)
        assertTrue(
            "the violation must name the row: ${report.summary()}",
            report.summary().contains("com.naviveylin.platform.reports"),
        )
        assertTrue(
            "the violation must name the feature the row mirrors: ${report.summary()}",
            report.summary().contains("reports"),
        )
        assertTrue(
            "the violation must name the rule: ${report.summary()}",
            report.summary().contains(StructureRule.MIRROR.id),
        )
        assertTrue(
            "the platform row that does have its feature must not be reported: ${report.summary()}",
            report.violations.none { it.pointer.contains("com.naviveylin.platform.search") },
        )
    }

    @Test
    fun platformPackageReachingNoFeatureFails() {
        val noFeatureImport = reportOf(listOf("com.naviveylin.concept.Units"))

        assertFalse(
            "a platform whose files reach none of its feature must fail: ${noFeatureImport.summary()}",
            noFeatureImport.passed,
        )
        assertTrue(
            "the violation must name the row and the feature's package: ${noFeatureImport.summary()}",
            noFeatureImport.summary().contains("com.naviveylin.platform.search") &&
                noFeatureImport.summary().contains("com.naviveylin.feature.search"),
        )
        assertTrue(
            "the violation must name the rule: ${noFeatureImport.summary()}",
            noFeatureImport.summary().contains(StructureRule.MIRROR.id),
        )
    }

    private companion object {
        const val PLATFORM_FILE = "app/src/main/java/com/naviveylin/platform/search/SearchScreen.kt"
    }
}