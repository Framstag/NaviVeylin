package com.naviveylin.build

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the pinned Kover plugin to the Gradle-10 readiness it is chosen for (spec:
 * `kover-aggregate-report` — "No deprecation regression from the declaration").
 *
 * Kover 0.9.8 passes a `Project` object to `DependencyHandler.add` while its plugin is applied
 * (`kotlinx.kover.gradle.plugin.appliers.PrepareKoverKt.prepare`), which Gradle 9.6 reports as
 * "Using a Project object as a dependency notation has been deprecated … This will fail with an
 * error in Gradle 10." The stack frame is inside the plugin, so no line of this repository can
 * silence it: the only lever is the pinned version, and upstream credits the fix to 0.9.9 — its
 * changelog entry for issue #818 reads "Fixed Gradle 9.6.0 deprecation warning". The absence of the
 * warning with 0.9.9 is that changelog's attribution, not something a host test can observe.
 *
 * The repository files are read relative to the module directory, which is an `:app` unit test's
 * working directory — the way `ShippedSpecClassificationTest` and `FavAutoZoomClampRangeTest` read
 * theirs. The version is read out of `build.gradle.kts` rather than written down twice, so a
 * downgrade fails here, and the guideline is read so the two artefacts cannot drift apart.
 */
class KoverDeprecationBaselineTest {

    private val repoRoot = File("..").canonicalFile
    private val rootBuild = File(repoRoot, "build.gradle.kts")
    private val buildGuideline = File(repoRoot, "guidelines/Build.md")

    /**
     * The first Kover release whose plugin-apply path does not add a `Project`-object dependency:
     * the release that fixes upstream issue #818 ([the 0.9.9 changelog](https://github.com/Kotlin/kotlinx-kover/releases/tag/v0.9.9)).
     * Older releases are the defect; nothing newer is required.
     */
    private val firstCleanRelease = KoverVersion(0, 9, 9)

    @Test
    fun `the pinned kover version clears the project-object deprecation`() {
        val declared = declaredKoverVersion()
        println("koverDeclared=$declared firstCleanRelease=$firstCleanRelease")
        assertTrue(
            "build.gradle.kts must pin Kover $firstCleanRelease or newer (pinned: $declared): " +
                "older releases add a Project-object dependency from PrepareKover, which Gradle " +
                "9.6 reports and Gradle 10 rejects",
            declared >= firstCleanRelease
        )
    }

    @Test
    fun `the coverage guideline names the pinned version and records no open upstream issue`() {
        val declared = declaredKoverVersion()
        val section = coverageSection()
        println("koverDeclared=$declared §7 states it: ${section.contains("Kover $declared")}")
        assertTrue(
            "guidelines/Build.md §7's engine table must state the pinned Kover version $declared",
            section.contains("Kover $declared")
        )
        assertFalse(
            "guidelines/Build.md §7 must not keep the Project-object deprecation as an open " +
                "upstream issue once the pinned version clears it",
            section.contains("Known upstream issue")
        )
    }

    /** The version pinned in the root build's plugins block. */
    private fun declaredKoverVersion(): KoverVersion {
        assertTrue("the root build script must exist at ${rootBuild.absolutePath}", rootBuild.isFile)
        val match = Regex("""id\("org\.jetbrains\.kotlinx\.kover"\)\s+version\s+"([0-9]+(?:\.[0-9]+)*)"""")
            .find(rootBuild.readText())
        assertTrue(
            "the root build script must pin the Kover plugin: ${rootBuild.absolutePath}",
            match != null
        )
        return KoverVersion.parse(match!!.groupValues[1])
    }

    /** `guidelines/Build.md` §7 — the coverage section, up to the next `## ` heading. */
    private fun coverageSection(): String {
        assertTrue("the guideline must exist at ${buildGuideline.absolutePath}", buildGuideline.isFile)
        val text = buildGuideline.readText()
        val start = text.indexOf("## 7. Code coverage")
        assertTrue(
            "guidelines/Build.md must carry the section \"## 7. Code coverage\": ${buildGuideline.absolutePath}",
            start >= 0
        )
        val end = text.indexOf("\n## ", start + 1)
        return if (end < 0) text.substring(start) else text.substring(start, end)
    }

    /** A dot-separated release number, compared component by component with missing parts as 0. */
    private data class KoverVersion(val major: Int, val minor: Int, val patch: Int) :
        Comparable<KoverVersion> {

        override fun compareTo(other: KoverVersion): Int =
            compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

        override fun toString(): String = "$major.$minor.$patch"

        companion object {
            fun parse(version: String): KoverVersion {
                val parts = version.split(".").map { it.toInt() }
                return KoverVersion(
                    parts.getOrElse(0) { 0 },
                    parts.getOrElse(1) { 0 },
                    parts.getOrElse(2) { 0 }
                )
            }
        }
    }
}
