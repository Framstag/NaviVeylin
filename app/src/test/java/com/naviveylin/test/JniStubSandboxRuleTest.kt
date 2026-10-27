package com.naviveylin.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds `guidelines/Build.md` §6's JNI-stub classloader rule to the test sources: a class that loads the
 * host stub (`app/src/test/jniLibs/libosmscout_client_java.so`, loaded by `OSMScoutClient`'s static
 * initializer) MUST run in the DEFAULT Robolectric sandbox, so it must not pin one with `@Config(sdk = …)`
 * or `@GraphicsMode(…)`.
 *
 * The rule's failure mode is "already loaded in another classloader" in a full-suite run, which depends on
 * which fork executes which class — so a violating class can be green for weeks and then take the suite
 * down. Nothing compared the rule to the sources until this case, and
 * `app/src/test/…/NavigationEngineAcquisitionTest.kt` carried `@Config(sdk = [34])` while instantiating
 * `FakeOSMScoutClient` (item §128).
 *
 * The detector reads **the sources**, the way `ReroutePolicyGuidelineParityTest` reads a guideline, so no
 * copy of the rule is written down twice. The rule reaches the two Robolectric modules that ship the stub in
 * `src/test/jniLibs` (`:app`, `:auto`) — `:osmscout-client-java` ships the same stub and runs plain JUnit 5,
 * one classloader for the whole suite, so no second sandbox can arise there — and a file counts as
 * stub-loading when it names the client package.
 * Only an SDK pin and a graphics mode are refused — a `@Config(qualifiers = …)` is applied per test and
 * starts no second sandbox, which is why the locale cases are not violations. Each refused form is
 * measured by its own control, because a branch nothing exercises is a sentence, not a rule.
 *
 * Scope, stated rather than implied: the scan reads `*.kt` under each module's `src/test/java`, so a
 * module-wide pin in `src/test/resources/robolectric.properties` (`:auto` carries `sdk=34` there) is not
 * part of this rule, and the guard lives in `:app`, so a `./gradlew :auto:test` run enforces nothing.
 *
 * The needles are assembled from parts on purpose: this file quotes the annotations and the client package
 * in its own text, and an assembled needle keeps it out of its own finding list.
 */
class JniStubSandboxRuleTest {

    /** A refused sandbox pin: the annotation at the start of a line, with the argument that pins the sandbox. */
    private fun sandboxPinHits(text: String): List<String> =
        text.lines().mapIndexedNotNull { index, line ->
            val trimmed = line.trimStart()
            val pinnedSdk = trimmed.startsWith(SDK_PIN) && trimmed.contains("sdk")
            val graphicsMode = trimmed.startsWith(GRAPHICS_PIN)
            if (pinnedSdk || graphicsMode) "${index + 1}: $trimmed" else null
        }

    /** A file that loads the host stub: it names the client package the stub's `.so` is loaded for. */
    private fun loadsStub(text: String): Boolean = text.contains(CLIENT_PACKAGE)

    private fun findingsIn(path: String, text: String): List<String> =
        if (loadsStub(text)) sandboxPinHits(text).map { "$path:$it" } else emptyList()

    private fun scan(root: File): Pair<Int, List<String>> {
        val files = root.walkTopDown().filter { it.extension == "kt" }.toList()
        val findings = files.flatMap { file ->
            findingsIn(file.relativeTo(APP_MODULE).path, file.readText())
        }
        return files.size to findings
    }

    @Test
    fun noTestClassThatLoadsTheStubPinsTheSandbox() {
        val (appFiles, appFindings) = scan(APP_TEST_SOURCES)
        val (autoFiles, autoFindings) = scan(AUTO_TEST_SOURCES)
        val findings = appFindings + autoFindings
        println("jniStubSandbox scanned app=$appFiles auto=$autoFiles findings=$findings")

        assertTrue(
            "a class that loads the host stub must run in the default Robolectric sandbox " +
                "(guidelines/Build.md §6) — these pin one: $findings",
            findings.isEmpty()
        )
    }

    @Test
    fun everyStubModuleIsScanned() {
        val (appFiles, _) = scan(APP_TEST_SOURCES)
        val (autoFiles, _) = scan(AUTO_TEST_SOURCES)
        println("jniStubSandbox roots app=$appFiles auto=$autoFiles")

        assertTrue("the :app test sources must be scanned at ${APP_TEST_SOURCES.absolutePath}", appFiles > 0)
        assertTrue("the :auto test sources must be scanned at ${AUTO_TEST_SOURCES.absolutePath}", autoFiles > 0)
    }

    @Test
    fun aPinnedStubLoadingClassIsReported() {
        val fixture = listOf(
            "package fixture",
            "",
            "import ${CLIENT_PACKAGE}FakeOSMScoutClient",
            "",
            SDK_PIN + "(sdk = [34])",
            "class PinnedStubClientTest"
        ).joinToString("\n")

        val findings = findingsIn("fixture/PinnedStubClientTest.kt", fixture)
        println("jniStubSandbox positiveControl=$findings")

        assertEquals("the detector must report a pinned stub-loading class", 1, findings.size)
        assertTrue("the finding names the line", findings.single().endsWith("5: " + SDK_PIN + "(sdk = [34])"))
    }

    @Test
    fun aGraphicsModeOnAStubLoadingClassIsReported() {
        val fixture = listOf(
            "package fixture",
            "",
            "import ${CLIENT_PACKAGE}FakeOSMScoutClient",
            "",
            GRAPHICS_PIN + "(GraphicsMode.Mode.NATIVE)",
            "class NativeGraphicsStubTest"
        ).joinToString("\n")

        val findings = findingsIn("fixture/NativeGraphicsStubTest.kt", fixture)
        println("jniStubSandbox graphicsModeControl=$findings")

        assertEquals("the detector must report a stub-loading class that pins a graphics mode", 1, findings.size)
        assertTrue(
            "the finding names the line",
            findings.single().endsWith("5: " + GRAPHICS_PIN + "(GraphicsMode.Mode.NATIVE)")
        )
    }

    @Test
    fun aPinnedClassThatLoadsNoStubIsAllowed() {
        val fixture = listOf(
            "package fixture",
            "",
            SDK_PIN + "(sdk = [34])",
            "class PlainTest"
        ).joinToString("\n")

        val findings = findingsIn("fixture/PlainTest.kt", fixture)
        println("jniStubSandbox negativeControl=$findings")

        assertTrue("a class that loads no stub may pin a sandbox: $findings", findings.isEmpty())
    }

    @Test
    fun aQualifierOnAStubLoadingClassIsAllowed() {
        val fixture = listOf(
            "package fixture",
            "",
            "import ${CLIENT_PACKAGE}FakeOSMScoutClient",
            "",
            SDK_PIN + "(qualifiers = \"de\")",
            "class GermanEngineTest"
        ).joinToString("\n")

        val findings = findingsIn("fixture/GermanEngineTest.kt", fixture)
        println("jniStubSandbox qualifierControl=$findings")

        assertTrue("a qualifier starts no second sandbox: $findings", findings.isEmpty())
    }

    private companion object {
        /** `:app` unit tests run with the module directory as their working directory. */
        val APP_MODULE = File("..").absoluteFile.normalize()
        val APP_TEST_SOURCES = File("src/test/java").absoluteFile.normalize()
        val AUTO_TEST_SOURCES = File("../auto/src/test/java").absoluteFile.normalize()

        val SDK_PIN = "@" + "Conf" + "ig"
        val GRAPHICS_PIN = "@" + "Graphics" + "Mode"
        val CLIENT_PACKAGE = "com.framstag." + "libosmscout.client."
    }
}
