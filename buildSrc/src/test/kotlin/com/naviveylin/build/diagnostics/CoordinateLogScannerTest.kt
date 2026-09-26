package com.naviveylin.build.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the diagnostics coordinate gate (change
 * `fix-diagnostics-coordinate-redaction`, spec: auto-diagnostics — Diagnostics carry
 * no coordinates). The fixtures are the real source shapes the gate has to catch,
 * including the multi-line ones a line-based regex would miss, plus the legitimate
 * shapes it must leave alone.
 */
class CoordinateLogScannerTest {

    private fun scan(source: String): List<CoordinateLogFinding> =
        CoordinateLogScanner.scan("src/Fixture.kt", source)

    @Test
    fun flagsTheMultiLineFixLine() {
        val source = """
            if (logCount++ % 30 == 0) {
                Log.d(TAG, "GPS loc=${'$'}{"%.6f".format(fix.lat)},${'$'}{"%.6f".format(fix.lon)} " +
                        "bearing=${'$'}{"%.1f".format(fix.markerBearing)} " +
                        "follow=${'$'}{_uiState.value.followMode}")
            }
        """.trimIndent()

        val findings = scan(source)

        assertEquals("the three-line concatenated fix line must be caught", 1, findings.size)
        assertEquals(2, findings.single().line)
        assertTrue(
            "the reason names what matched: ${findings.single().reason}",
            findings.single().reason.contains("lat")
        )
    }

    @Test
    fun flagsAMultiLineDiagnosticsPayload() {
        val source = """
            DiagnosticsLog.log(
                "LONGPRESS",
                "lat=${'$'}lat lon=${'$'}lon mag=${'$'}{_uiState.value.viewport.magnification}"
            )
        """.trimIndent()

        val findings = scan(source)

        assertEquals(1, findings.size)
        assertEquals("the call's opening line is reported", 1, findings.single().line)
    }

    @Test
    fun leavesALabelOnlyMessageAlone() {
        val source = """Log.d(TAG, "onFavoriteSelected: name='${'$'}{fav.name}', source=favourites")"""

        assertTrue("no position, no finding", scan(source).isEmpty())
    }

    @Test
    fun leavesCoordinateComputationOutsideLoggingAlone() {
        val source = """
            val meters = distanceMeters(lat, lon, other.lat, other.lon)
            val bbox = doubleArrayOf(minLat, maxLat, minLon, maxLon)
        """.trimIndent()

        assertTrue("only log calls are inspected", scan(source).isEmpty())
    }

    @Test
    fun handlesNestedParenthesesInALogCall() {
        val source = """Log.d(TAG, "center=" + format(nested(lat, 2), lon) + " done")"""

        val findings = scan(source)

        assertEquals(1, findings.size)
        assertTrue("the whole call is reported: ${findings.single().call}", findings.single().call.endsWith("done\")"))
    }

    @Test
    fun flagsACoordinateShapedFormatWithoutAnIdentifier() {
        val source = """Log.d(TAG, "position " + "%.5f".format(value))"""

        val findings = scan(source)

        assertEquals(1, findings.size)
        assertTrue(findings.single().reason.contains("%.5f"))
    }

    @Test
    fun doesNotFlagWordsThatMerelyStartLikeCoordinates() {
        val source = """
            Log.d(TAG, "latency=5ms long=3")
            Log.w(TAG, "belongs to the previous session")
        """.trimIndent()

        assertTrue("word boundaries matter", scan(source).isEmpty())
    }

    @Test
    fun flagsDiagnosticsLogThrowableToo() {
        val source = """DiagnosticsLog.logThrowable("CRASH", "at ${'$'}lat,${'$'}lon", t)"""

        assertEquals(1, scan(source).size)
    }

    @Test
    fun reportNamesTheFileLineReasonAndCall() {
        val findings = scan("""Log.d(TAG, "onLongPress: lat=${'$'}lat, lon=${'$'}lon")""")

        val report = CoordinateLogScanner.report(findings)

        assertTrue(report.contains("src/Fixture.kt:1"))
        assertTrue(report.contains("coordinate identifier"))
        assertTrue(report.contains("onLongPress"))
    }

    @Test
    fun aCleanFileReportsNothing() {
        val source = """
            Log.d(TAG, "GPS fix accuracy=${'$'}accuracy bearing=${'$'}bearing")
            DiagnosticsLog.log("LONGPRESS", "mag=${'$'}mag candidates=${'$'}count")
        """.trimIndent()

        assertTrue(scan(source).isEmpty())
    }
}
