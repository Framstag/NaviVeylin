package com.naviveylin.build.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the native coordinate gate (issue #125, change `fix-native-coordinate-log-gate`;
 * spec `auto-diagnostics` — Diagnostics carry no coordinates). The fixtures are the statement
 * shapes the rule exists for — the four trace lines the legacy JNI route entry used to print,
 * the bounding-box line beside them, and a component accessor — plus the shapes it must leave
 * alone: a position-free line, a commented-out line, and a statement whose message contains the
 * characters a naive scanner would mistake for its end.
 */
class NativeCoordinateLogScannerTest {

    private fun scan(source: String): List<NativeCoordinateLogFinding> =
        NativeCoordinateLogScanner.scan("app/src/main/cpp/Fixture.cpp", source)

    @Test
    fun flagsTheLegacyRouteEntryLines() {
        val source = """
            void calculateRouteWithObjectsAsync() {
              osmscout::log.Warn() << "calculateRouteAsync: resolving start at "
                                   << start.GetDisplayText();
              auto startPosOpt = ResolveRoutePosition(start);
              osmscout::log.Warn() << "calculateRouteAsync: FAILED to find routable node near start "
                                   << start.GetDisplayText();
              osmscout::log.Warn() << "calculateRouteAsync: resolving dest at "
                                   << dest.GetDisplayText();
              osmscout::log.Warn() << "calculateRouteAsync: FAILED to find routable node near dest "
                                   << dest.GetDisplayText();
            }
        """.trimIndent()

        val findings = scan(source)

        assertEquals("all four legacy trace lines must be caught", 4, findings.size)
        assertEquals(
            "the statement's opening line is reported: ${findings.map { it.line }}",
            listOf(2, 5, 7, 9),
            findings.map { it.line }
        )
        assertTrue(
            "the reason names the accessor: ${findings.first().reason}",
            findings.first().reason.contains("GetDisplayText")
        )
    }

    @Test
    fun flagsTheBoundingBoxAndComponentAccessors() {
        val source = """
            osmscout::log.Debug() << "[JNI] checking db box: " << dbBox.GetDisplayText();
            osmscout::log.Info() << "center " << renderer->GetCenter().GetLat() << "," << renderer->GetCenter().GetLon();
        """.trimIndent()

        val findings = scan(source)

        assertEquals(2, findings.size)
        assertTrue(findings[0].reason.contains("GetDisplayText"))
        assertTrue(findings[1].reason.contains("GetLat"))
    }

    @Test
    fun leavesAPositionFreeStatementAlone() {
        val source = """
            osmscout::log.Warn() << "calculateRouteAsync: resolving start";
            osmscout::log.Debug() << "[JNI] checking db box of " << db->path;
            osmscout::log.Warn() << "calculateRouteAsync: " << dbMapping.size() << " databases";
        """.trimIndent()

        assertTrue("no position accessor, no finding", scan(source).isEmpty())
    }

    @Test
    fun ignoresACommentedOutStatement() {
        val source = """
            //osmscout::log.Debug() << "box " << lineBox.GetDisplayText();
            /* osmscout::log.Debug() << "box " << lineBox.GetDisplayText(); */
            osmscout::log.Debug() << "start resolved OK";
        """.trimIndent()

        assertTrue("commented-out lines are not built, so not gated", scan(source).isEmpty())
    }

    @Test
    fun readsThroughAMessageThatLooksLikeAStatementEnd() {
        val source = """
            osmscout::log.Debug() << "found ); // not the end" << coord.GetDisplayText();
        """.trimIndent()

        val findings = scan(source)

        assertEquals(1, findings.size)
        assertTrue(
            "the whole statement is reported: ${findings.single().statement}",
            findings.single().statement.endsWith("coord.GetDisplayText();")
        )
    }

    @Test
    fun reportsThePathAndReasonForABuildFailure() {
        val report = NativeCoordinateLogScanner.report(
            scan("""osmscout::log.Debug() << coord.GetLat();""")
        )

        assertTrue("the report names the file and line: $report", report.contains("Fixture.cpp:1:"))
        assertTrue("the report says what matched: $report", report.contains("GetLat"))
        assertTrue("the report names the owning spec: $report", report.contains("auto-diagnostics"))
    }
}
