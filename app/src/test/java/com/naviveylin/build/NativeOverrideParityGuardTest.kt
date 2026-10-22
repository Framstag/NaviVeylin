package com.naviveylin.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the JNI bridge's two Java sides together, to `osmscout-jni`'s requirement "The override mirrors the
 * submodule's natives, except the recorded deviations" (scenarios "The build refuses an unrecorded deviation"
 * and "The recorded deviations are the tree's own").
 *
 * `:osmscout-client-java` compiles the libosmscout submodule's `libosmscout-client-java/java` sources except five
 * overridden files (`guidelines/Design.md` §5). A native the submodule declares and the override does not is a
 * `NoSuchMethodError` at the first call — no compiler error, no failing test — and the same holds in reverse for
 * an override native the submodule later renames. Nothing in the tree asserted the pair, and a blanket
 * "submodule ⊆ override" check is *false even today*, because the override diverges on purpose in both
 * directions (`searchLocationByForm` and `reloadBasemap` are Android additions; the region-name search and
 * `getRegion` are submodule natives the Android port does not carry): issue #174.
 *
 * Two surfaces assert the rule and this class is the host-side one — the list is recorded and the build runs the
 * scan (cases below), while the scan itself is `buildSrc`'s `NativeParityScanner`, unit-tested against fixtures
 * there. A unit test cannot run a Gradle task, so this case reads the build script the way
 * `VersionedGradleJavaHomeTest` reads the workflow.
 *
 * The case reads the repository's own files rather than a Gradle log: the working directory of an `:app` unit
 * test is the module directory, so the repository root is `File("..")`. Counts and lines are printed before the
 * assertion, so the JUnit XML's `system-out` carries the measurement of the run that made it.
 */
class NativeOverrideParityGuardTest {

    private val repoRoot = File("..").canonicalFile
    private val buildScript = File(repoRoot, "app/build.gradle.kts")
    private val deviationList = File(repoRoot, "osmscout-client-java/native-parity.txt")

    @Test
    fun `the build refuses an unrecorded deviation`() {
        assertTrue(
            "the build script must exist at ${buildScript.absolutePath}",
            buildScript.isFile
        )
        val script = buildScript.readText()
        println("buildScript=${buildScript.name} chars=${script.length}")

        assertTrue(
            "the app build must register the native parity gate " +
                "(`tasks.register(\"checkNativeParity\")`), which compares every overridden bridge file with " +
                "the submodule's copy (issue #174, guidelines/Design.md §5)",
            script.contains("tasks.register(\"checkNativeParity\")")
        )
        assertTrue(
            "the app build must wire the native parity gate into `preBuild`, so an unrecorded deviation fails " +
                "a build instead of waiting for the call site's NoSuchMethodError (issue #174)",
            script.contains("tasks.named(\"preBuild\") { dependsOn(checkNativeParity) }")
        )
    }

    @Test
    fun `the recorded deviations are the tree's own`() {
        assertTrue(
            "the deviation list must exist at ${deviationList.absolutePath} — a blanket " +
                "\"submodule ⊆ override\" check is false, so the intentional divergences are recorded here " +
                "with a reason each (issue #174)",
            deviationList.isFile
        )
        val entries = deviationList.readLines()
            .withIndex()
            .filter { (_, line) -> line.isNotBlank() && !line.trimStart().startsWith("#") }
            .map { (index, line) -> index + 1 to line.split('|').map { it.trim() } }
        println("deviationLines=${entries.size}")
        entries.forEach { (line, fields) -> println("LINE $line fields=${fields.size} ${fields.firstOrNull()}") }

        val malformed = entries.filter { (_, fields) -> fields.size != 4 || fields[3].isEmpty() }
        assertEquals(
            "every deviation line must read `file | submodule-only|override-only | name(types) | reason`, so a " +
                "reader knows which side owns the divergence and why: $malformed",
            emptyList<Pair<Int, List<String>>>(),
            malformed
        )

        val byDirection = entries.map { (_, fields) -> fields[1] }.groupingBy { it }.eachCount()
        println("byDirection=$byDirection")
        assertEquals(
            "the list must record exactly the divergences the two trees carry today — five natives the " +
                "submodule declares and the Android override omits, five the override adds for Android",
            mapOf("submodule-only" to 5, "override-only" to 5),
            byDirection
        )
        val unknownFiles = entries.map { (_, fields) -> fields[0] }.filterNot { it in OVERRIDDEN_FILES }
        assertEquals(
            "a recorded deviation must name one of the five overridden bridge files " +
                "(guidelines/Design.md §5): $unknownFiles",
            emptyList<String>(),
            unknownFiles
        )
    }

    private companion object {
        /**
         * The five files `:osmscout-client-java` takes from its own `src/main/java` instead of the submodule
         * (`guidelines/Design.md` §5, "The bridge's two sides").
         */
        val OVERRIDDEN_FILES = setOf(
            "OSMScoutClient.java",
            "OSMScoutClientBuilder.java",
            "BasemapManager.java",
            "MapDownloadManager.java",
            "AvailableMapEntry.java"
        )
    }
}
