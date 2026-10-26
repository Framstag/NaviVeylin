package com.naviveylin.build.native

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for the submodule-source-tree gate over the generated libosmscout feature headers (§202, issue #136).
 *
 * The fixtures are the real shapes the rule has to separate: the four rendered headers beside their templates,
 * the submodule's own Android copies that sit beside no template, and the header a build tree holds — which is
 * generated too, but next to no template, so the gate must leave it alone (walking a build tree would otherwise
 * fail every host build that has one).
 */
class GeneratedFeatureHeadersTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun tree(vararg paths: String): File {
        val root = temp.root
        paths.forEach { relative ->
            val file = File(root, relative)
            file.parentFile.mkdirs()
            file.writeText("\n")
        }
        return root
    }

    @Test
    fun flagsEveryHeaderThatHasItsTemplateBesideIt() {
        val paths = listOf(
            "libosmscout/include/osmscout/lib/CoreFeatures.h",
            "libosmscout-map/include/osmscoutmap/MapFeatures.h",
            "libosmscout-map-cairo/include/osmscoutmapcairo/MapCairoFeatures.h",
            "libosmscout-client/include/osmscoutclient/ClientFeatures.h"
        )

        assertEquals(paths.sorted(), GeneratedFeatureHeaders.generated(paths, paths.map { "$it.cmake" }))
    }

    @Test
    fun leavesTheSubmodulesTrackedAndroidCopiesAlone() {
        val android = listOf(
            "Android/OsmScoutLib/jni/include/config/CoreFeatures.h",
            "Android/OsmScoutLib/jni/include/config/MapFeatures.h"
        )

        assertEquals(emptyList<String>(), GeneratedFeatureHeaders.generated(android, android))
    }

    @Test
    fun leavesABuildTreesHeaderAlone() {
        val buildTree = listOf("build/libosmscout-map/include/osmscoutmap/MapFeatures.h")

        assertEquals(emptyList<String>(), GeneratedFeatureHeaders.generated(buildTree, buildTree))
    }

    @Test
    fun findsTheGeneratedHeaderAndNothingElse() {
        val root = tree(
            // the module's template and the header rendered from it
            "libosmscout-map/include/osmscoutmap/MapFeatures.h.cmake",
            "libosmscout-map/include/osmscoutmap/MapFeatures.h",
            // a module whose header was not rendered (a fresh clone)
            "libosmscout-map-cairo/include/osmscoutmapcairo/MapCairoFeatures.h.cmake",
            // the submodule's tracked copies, and the header of a build tree in the same checkout
            "Android/OsmScoutLib/jni/include/config/MapFeatures.h",
            "build/libosmscout-map/include/osmscoutmap/MapFeatures.h",
            "hostbuild/libosmscout/include/osmscout/lib/CoreFeatures.h"
        )

        assertEquals(listOf("libosmscout-map/include/osmscoutmap/MapFeatures.h"), GeneratedFeatureHeaders.find(root))
    }

    @Test
    fun findsNothingInACleanTree() {
        val root = tree(
            "libosmscout/include/osmscout/lib/CoreFeatures.h.cmake",
            "Android/OsmScoutLib/jni/include/config/CoreFeatures.h"
        )

        assertEquals(emptyList<String>(), GeneratedFeatureHeaders.find(root))
    }

    @Test
    fun theReportNamesTheFilesAndTheRepair() {
        val report = GeneratedFeatureHeaders.report(
            listOf("libosmscout-map/include/osmscoutmap/MapFeatures.h"),
            "app/src/main/cpp/libosmscout"
        )

        assertTrue(report, report.contains("app/src/main/cpp/libosmscout/libosmscout-map/include/osmscoutmap/MapFeatures.h"))
        assertTrue(report, report.contains("have been"))
        assertTrue(report, report.contains("CMake build"))
        assertTrue(report, report.contains("§202"))
        assertTrue(report, report.contains("Re-run the build"))
    }
}
