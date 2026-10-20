package com.naviveylin.build

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the unit-test source sets to the warning-free build the gate's result evaluation requires
 * (spec: `build-test-gate` — "The unit-test source sets call no deprecated API"; the rule is
 * `guidelines/Build.md` §4 "Result evaluation", bullet "Warnings: the build must have no warnings —
 * report them, don't ignore").
 *
 * The guard exists because a deprecation is invisible in a green run: a test that calls a member its
 * library deprecated still passes, so nothing but a scan keeps the call sites from returning. Each
 * case asserts the absence of one deprecated call family **and** the presence of its supported
 * successor, so a scan that reached no source cannot pass vacuously.
 *
 * The four unit-test suites compile from three source roots (`:app`'s two flavors share
 * `app/src/test/java`), which is why the scan walks three. The repository is read relative to the
 * module directory, which is an `:app` unit test's working directory — the way
 * [KoverDeprecationBaselineTest] reads `build.gradle.kts`.
 */
class UnitTestSourceDeprecationBaselineTest {

    private val repoRoot = File("..").canonicalFile

    /** The module test roots the gate's four unit-test suites compile from. */
    private val unitTestRoots = listOf(
        "app/src/test/java",
        "auto/src/test/java",
        "core/src/test/java"
    )

    /**
     * This guard names the deprecated members it forbids, so it would match itself: it is the one
     * file the scan skips. Its own presence is what the other cases' positive controls rest on.
     */
    private val self = "app/src/test/java/com/naviveylin/build/" +
        "UnitTestSourceDeprecationBaselineTest.kt"

    @Test
    fun `no unit test registers a sensor through the deprecated type-and-sensor overload`() {
        val sources = unitTestSources()
        val deprecated = Regex("""addSensor\([^)]*,""")
        val supported = Regex("""addSensor\(\s*[A-Za-z_][\w.]*\s*\)""")

        val offenders = sources.filter { deprecated.containsMatchIn(it.code) }.map { it.relative }
        assertTrue(
            "ShadowSensorManager.addSensor(int, Sensor) is deprecated; register the sensor through " +
                "addSensor(Sensor), which keys the same map by Sensor.getType(): $offenders",
            offenders.isEmpty()
        )
        assertTrue(
            "no unit-test source calls the supported addSensor(Sensor) overload, so this scan " +
                "reached no sensor registration at all: ${sources.size} files scanned under " +
                "$repoRoot",
            sources.any { supported.containsMatchIn(it.code) }
        )
    }

    @Test
    fun `no unit test reads the pending-intent kind through the deprecated predicates`() {
        val sources = unitTestSources()
        val deprecated = Regex("""is(?:Broadcast|Activity|Service|ForegroundService)Intent\(\)""")
        val supported = Regex("""is(?:Broadcast|Activity|Service|ForegroundService)\(\)""")

        val offenders = sources.filter { deprecated.containsMatchIn(it.code) }.map { it.relative }
        assertTrue(
            "ShadowPendingIntent.isActivityIntent()/isBroadcastIntent()/…Intent() are deprecated; " +
                "isActivity()/isBroadcast()/…() read the same PendingIntent type: $offenders",
            offenders.isEmpty()
        )
        assertTrue(
            "no unit-test source reads a pending-intent kind through the supported predicates, so " +
                "this scan reached no pending-intent assertion at all: ${sources.size} files " +
                "scanned under $repoRoot",
            sources.any { supported.containsMatchIn(it.code) }
        )
    }

    @Test
    fun `no unit test asserts notification silence through the deprecated defaults bitmask`() {
        val sources = unitTestSources()
        val deprecated = Regex("""\.defaults\b""")

        assertTrue(
            "the scan must reach the notification test that asserts silence, or it proves nothing " +
                "about the deprecated bitmask: ${sources.size} files scanned under $repoRoot",
            sources.any { it.relative == "auto/src/test/java/com/naviveylin/auto/CarStyleLoadNotifierTest.kt" }
        )
        val offenders = sources.filter { deprecated.containsMatchIn(it.code) }.map { it.relative }
        assertTrue(
            "Notification.defaults is deprecated from API 26 — the concept moved to the channel, so " +
                "assert the channel the notice rides and its importance instead: $offenders",
            offenders.isEmpty()
        )
    }

    /**
     * One scanned source file: its repository-relative path, and its code with line comments removed —
     * a comment cannot call a deprecated member, so a comment that names one is not an offence.
     */
    private data class Source(val relative: String, val code: String)

    /** Every `*.kt` file under the module test roots, minus this guard. */
    private fun unitTestSources(): List<Source> =
        unitTestRoots.flatMap { root ->
            val dir = File(repoRoot, root)
            assertTrue("the unit-test source root must exist: ${dir.absolutePath}", dir.isDirectory)
            dir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") }
                .map { file ->
                    Source(
                        file.relativeTo(repoRoot).invariantSeparatorsPath,
                        file.readLines().joinToString("\n") { it.substringBefore("//") }
                    )
                }
                .toList()
        }.filterNot { it.relative == self }
}
