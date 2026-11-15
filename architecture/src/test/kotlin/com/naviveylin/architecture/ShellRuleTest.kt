package com.naviveylin.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `shell` hosting rule (change `package-structure-rules-and-suite`, task 1.7; spec `package-structure` —
 * "shell is the only kind that hosts several features"): a file that assembles several features belongs to
 * `shell`, and a `feature` or `platform` file that names two features fails.
 */
class ShellRuleTest {

    private val declaration: StructureDeclaration = StructureDeclaration.parse(
        "# test declaration\n" +
            "app\tcom.naviveylin.concept\tconcept\t-\t-\n" +
            "core\tcom.naviveylin.core.port\tport\t-\tconcept\n" +
            "app\tcom.naviveylin.feature.navigation\tfeature\tnavigation\tconcept,port\n" +
            "app\tcom.naviveylin.feature.route\tfeature\troute\tconcept,port\n" +
            "app\tcom.naviveylin.feature.search\tfeature\tsearch\tconcept,port\n" +
            "app\tcom.naviveylin.feature.share\tfeature\tshare\tconcept,port\n" +
            "app\tcom.naviveylin.feature.details\tfeature\tdetails\tconcept,port\n" +
            "app\tcom.naviveylin.feature.mapsource\tfeature\tmapsource\tconcept,port\n" +
            "app\tcom.naviveylin.shell\tshell\t-\tany\n",
    )

    /** One file per declared package, so a case reports no row-without-package by accident. */
    private fun tree(hostImports: List<String>, hostPath: String, hostPackage: String): List<SourceFile> = listOf(
        SourceFile("app/src/main/java/com/naviveylin/concept/Units.kt", "com.naviveylin.concept"),
        SourceFile("core/src/main/java/com/naviveylin/core/port/Store.kt", "com.naviveylin.core.port"),
        SourceFile("app/src/main/java/com/naviveylin/feature/navigation/Nav.kt", "com.naviveylin.feature.navigation"),
        SourceFile("app/src/main/java/com/naviveylin/feature/route/Route.kt", "com.naviveylin.feature.route"),
        SourceFile("app/src/main/java/com/naviveylin/feature/search/Search.kt", "com.naviveylin.feature.search"),
        SourceFile("app/src/main/java/com/naviveylin/feature/share/Share.kt", "com.naviveylin.feature.share"),
        SourceFile("app/src/main/java/com/naviveylin/feature/details/Details.kt", "com.naviveylin.feature.details"),
        SourceFile("app/src/main/java/com/naviveylin/feature/mapsource/Maps.kt", "com.naviveylin.feature.mapsource"),
        SourceFile(path = hostPath, packageName = hostPackage, imports = hostImports),
    )

    private val sixFeatures = listOf(
        "com.naviveylin.feature.navigation.Nav",
        "com.naviveylin.feature.route.Route",
        "com.naviveylin.feature.search.Search",
        "com.naviveylin.feature.share.Share",
        "com.naviveylin.feature.details.Details",
        "com.naviveylin.feature.mapsource.Maps",
    )

    @Test
    fun shellMayHostSeveralFeatures() {
        val report = StructureReport.of(
            declaration,
            tree(sixFeatures, SHELL_FILE, "com.naviveylin.shell"),
            Baseline(emptyList()),
        )

        assertTrue("a shell assembling six features must pass: ${report.summary()}", report.passed)
    }

    @Test
    fun featureHostingTwoFeaturesFails() {
        val report = StructureReport.of(
            declaration,
            tree(
                listOf("com.naviveylin.feature.navigation.Nav", "com.naviveylin.feature.search.Search"),
                FEATURE_FILE,
                "com.naviveylin.feature.route",
            ),
            Baseline(emptyList()),
        )

        assertFalse("a feature assembling two features must fail: ${report.summary()}", report.passed)
        assertTrue(
            "the violation must name the file that hosts: ${report.summary()}",
            report.summary().contains(FEATURE_FILE),
        )
        assertTrue(
            "the violation must name the rule: ${report.summary()}",
            report.summary().contains(StructureRule.HOSTS_SEVERAL_FEATURES.id),
        )
        assertTrue(
            "the violation must say which kind may host: ${report.summary()}",
            report.summary().contains("shell"),
        )
    }

    private companion object {
        const val SHELL_FILE = "app/src/main/java/com/naviveylin/shell/AppShell.kt"
        const val FEATURE_FILE = "app/src/main/java/com/naviveylin/feature/route/RoutePanel.kt"
    }
}