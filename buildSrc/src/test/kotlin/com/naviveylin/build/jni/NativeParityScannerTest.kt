package com.naviveylin.build.jni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the JNI native parity gate (change `fix-jni-native-parity-guard`, spec `osmscout-jni` — "The
 * override mirrors the submodule's natives, except the recorded deviations").
 *
 * The fixtures are the real shapes of the two sides — a `public native` declaration in the submodule, the
 * Android override's own form of the same method, and a javadoc that *shows* a signature without declaring
 * one — plus the four ways the rule can silently stop being enforced: a submodule native the override does not
 * reach, an override native the submodule never had, a recorded deviation that has since disappeared, and a
 * list line whose shape is wrong (which would otherwise drop an allowance without a word).
 */
class NativeParityScannerTest {

    private val submodule = """
        package com.framstag.libosmscout.client;

        public class OSMScoutClient {
            /** See {@link #getRegion(double, double)} — a signature in prose, not a declaration. */
            public native String getRegion(double lat, double lon);

            native NavigationController startNavigationWithVehicle(long routeHandle,
                                                                  Vehicle vehicle,
                                                                  NavigationListener listener);

            public native double[] renderInto(int width, int height,
                                              java.nio.ByteBuffer target);
        }
    """.trimIndent()

    private val override = """
        package com.framstag.libosmscout.client;

        public class OSMScoutClient {
            public native NavigationController startNavigationWithVehicle(long routeHandle,
                                                                         Vehicle vehicle,
                                                                         NavigationListener listener);

            // A comment that mentions setGpsMarker(double, double, double, double) declares nothing.
            public native void reloadBasemap();

            public native double[] renderInto(int width, int height,
                                              ByteBuffer target);
        }
    """.trimIndent()

    private val recorded = """
        # file | direction | signature | reason
        OSMScoutClient.java | submodule-only | getRegion(double,double) | the Android surfaces take regions from search results, not from a coordinate lookup
        OSMScoutClient.java | override-only | reloadBasemap() | the app reloads the basemap after a download, which the submodule does on database open
    """.trimIndent()

    @Test
    fun `a new submodule native without a record is a finding`() {
        val drifting = fixture().submodule + ("OSMScoutClient.java" to submodule.replace(
            "public native String getRegion(double lat, double lon);",
            "public native String getRegion(double lat, double lon);\n\n" +
                "    public native void setGpsMarker(double lat, double lon, double bearing, double accuracy);"
        ))
        val withRecord = recorded + "\n" +
            "OSMScoutClient.java | submodule-only | setGpsMarker(double,double,double,double) | fixture\n"

        val recordedFindings = NativeParityScanner.check(drifting, fixture().override, withRecord)
        println("recordedFindings=$recordedFindings")
        assertEquals(
            "a submodule native the override does not reach, recorded in the list, must not be a finding",
            emptyList<NativeParityFinding>(),
            recordedFindings
        )

        val unrecorded = NativeParityScanner.check(drifting, fixture().override, recorded)
        println("unrecordedFindings=$unrecorded")
        assertEquals("exactly one unrecorded drift is reported", 1, unrecorded.size)
        val finding = unrecorded.single()
        assertTrue(
            "the finding must name the native and the file that lacks it: $finding",
            finding.reason.contains("setGpsMarker(double,double,double,double)") &&
                finding.path.contains(NativeParityScanner.OVERRIDE_DIR)
        )
    }

    @Test
    fun `a new override native without a record is a finding`() {
        val drifting = fixture().override + ("OSMScoutClient.java" to override.replace(
            "public native void reloadBasemap();",
            "public native void reloadBasemap();\n\n    public native double getMaxSpeedAt(double lat, double lon);"
        ))

        val findings = NativeParityScanner.check(fixture().submodule, drifting, recorded)
        println("overrideAdditionFindings=$findings")
        assertEquals("exactly one unrecorded Android addition is reported", 1, findings.size)
        val finding = findings.single()
        assertTrue(
            "the finding must name the added native and the submodule file that would lose it: $finding",
            finding.reason.contains("getMaxSpeedAt(double,double)") &&
                finding.path.contains(NativeParityScanner.SUBMODULE_DIR)
        )
    }

    @Test
    fun `a recorded deviation that no longer deviates is a finding`() {
        val stale = recorded + "\n" +
            "OSMScoutClient.java | submodule-only | searchLocations(String,int,String,long) | fixture\n"
        val findings = NativeParityScanner.check(fixture().submodule, fixture().override, stale)
        println("staleFindings=$findings")
        assertEquals("the list may not carry a deviation the trees no longer have", 1, findings.size)
        assertTrue(
            "the finding must name the stale line and say to remove it: ${findings.single().reason}",
            findings.single().reason.contains("searchLocations(String,int,String,long)") &&
                findings.single().reason.contains("no longer exists")
        )
    }

    @Test
    fun `a malformed deviation line is a finding`() {
        val malformed = listOf(
            "OSMScoutClient.java | submodule-only | getRegion(double,double)",
            "OSMScoutClient.java | both | getRegion(double,double) | fixture",
            "Other.java | submodule-only | getRegion(double,double) | fixture",
            "OSMScoutClient.java | submodule-only | getRegion | fixture",
            "OSMScoutClient.java | submodule-only | getRegion(double,double) |"
        )
        val findings = malformed.map { line ->
            NativeParityScanner.check(fixture().submodule, fixture().override, recorded + "\n" + line)
        }
        println("malformedFindings=$findings")
        assertEquals(
            "one finding per malformed line, naming its line number — no allowance may drop silently",
            List(malformed.size) { 1 },
            findings.map { it.size }
        )
        assertTrue(
            "each finding must name the list's line: $findings",
            findings.all { it.single().path.startsWith("${NativeParityScanner.DEVIATION_LIST_PATH}:") }
        )
    }

    @Test
    fun `package qualifiers and prose are not divergences`() {
        assertEquals(
            "the same declaration with an imported and a qualified type is one signature",
            NativeParityScanner.nativesIn(submodule).filter { it.startsWith("renderInto") },
            NativeParityScanner.nativesIn(override).filter { it.startsWith("renderInto") }
        )
        assertTrue(
            "a signature named in a javadoc or a line comment is not a declaration: " +
                NativeParityScanner.nativesIn(submodule),
            "getRegion(double,double)" !in NativeParityScanner.nativesIn(override) &&
                "setGpsMarker(double,double,double,double)" !in NativeParityScanner.nativesIn(override)
        )
    }

    /** The fixture's two sides; the four files the cases do not vary are empty, so only one pair differs. */
    private fun fixture(): Fixture = Fixture(
        submodule = emptySources() + ("OSMScoutClient.java" to submodule),
        override = emptySources() + ("OSMScoutClient.java" to override)
    )

    private fun emptySources(): Map<String, String> =
        NativeParityScanner.OVERRIDDEN_FILES.associateWith { "package com.framstag.libosmscout.client;\n" }

    private data class Fixture(
        val submodule: Map<String, String>,
        val override: Map<String, String>
    )
}
