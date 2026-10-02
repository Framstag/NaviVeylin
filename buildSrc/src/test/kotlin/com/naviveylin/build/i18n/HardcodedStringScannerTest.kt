package com.naviveylin.build.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The i18n gate's rule (spec: i18n-l10n — All user-facing text is translatable):
 * one case per shape the replaced inline tasks missed, plus the exemptions that
 * must not turn into false positives.
 */
class HardcodedStringScannerTest {

    private fun findings(source: String): List<HardcodedStringFinding> =
        HardcodedStringScanner.scan("app/src/main/java/Fixture.kt", source)

    private fun literals(source: String): List<String> = findings(source).map { it.literal }

    // --- flagged: the three blind spots -------------------------------------

    @Test
    fun interpolatedDisplayTextIsFlagged() {
        // The favorites group card (TODO.md §104): skipping every literal that
        // contains ${ let hand-rolled English pluralization through.
        val source = """Text(text = "${'$'}favCount favorite${'$'}{if (favCount != 1) "s" else ""}")"""
        val finding = findings(source).single()
        // The nested quotes of the template cut the captured literal at "s"; what
        // matters is that the shape is reported, not skipped.
        assertTrue(finding.literal, finding.literal.startsWith("${'$'}favCount favorite"))
        assertEquals("interpolated display text", finding.reason)
    }

    @Test
    fun hardcodedTemplateTitleIsFlagged() {
        // The car favorites screen (TODO.md §105) passed this into .setTitle(…).
        assertEquals(
            listOf("Starred favorites"),
            literals(""".setTitle(if (starredOnly) "Starred favorites" else "Favorites")""")
        )
        assertEquals(listOf("Favorites"), literals(""".setTitle("Favorites")"""))
    }

    @Test
    fun notificationChannelNameAndDescriptionAreFlagged() {
        val source =
            """NotificationChannel(CHANNEL_ID, "Map Download", NotificationManager.IMPORTANCE_LOW)"""
        assertEquals(listOf("Map Download"), literals(source))
        assertEquals("notification-channel name/description", findings(source).single().reason)

        // The id argument is not display text, so a literal there is left alone.
        assertEquals(
            emptyList<String>(),
            literals("""NotificationChannel("map_download", CHANNEL_NAME, IMPORTANCE_LOW)""")
        )
    }

    @Test
    fun channelDescriptionAssignmentIsFlagged() {
        assertEquals(
            listOf("Shows map download progress"),
            literals("""description = "Shows map download progress"""")
        )
    }

    @Test
    fun conditionalAssignmentIsFlaggedOnce() {
        assertEquals(
            listOf("A"),
            literals("""contentDescription = if (visible) "A" else "B"""")
        )
    }

    @Test
    fun everyTextPositionIsCovered() {
        val positions = listOf(
            """Text("Hello")""",
            """text = "Hello"""",
            """label = "Hello"""",
            """title = "Hello"""",
            """placeholder = "Hello"""",
            """hint = "Hello"""",
            """.setText("Hello")""",
            """.addText("Hello")""",
            """.setContentTitle("Hello")""",
            """.setContentText("Hello")"""
        )
        positions.forEach { assertEquals("missed: $it", listOf("Hello"), literals(it)) }
    }

    @Test
    fun lineNumberIsReported() {
        val source = "package x\n\nfun f() {\n    Text(\"Hello\")\n}\n"
        assertEquals(4, findings(source).single().line)
    }

    // --- exempt: values, formats and separators ----------------------------

    @Test
    fun pureTemplateIsExempt() {
        assertEquals(emptyList<String>(), literals("""text = "${'$'}zoomLevel""""))
        assertEquals(emptyList<String>(), literals("""text = "${'$'}{a}-${'$'}{b}""""))
    }

    @Test
    fun templateCarryingAWordIsFlagged() {
        // A substituted value next to words of its own is display text an English
        // suffix cannot translate — the plural must come from a resource.
        assertEquals(listOf("${'$'}n favorite"), literals("""text = "${'$'}n favorite""""))
        assertEquals(listOf("${'$'}a km"), literals("""text = "${'$'}a km""""))
    }

    @Test
    fun formatTemplatesAreExempt() {
        assertEquals(emptyList<String>(), literals("""text = "%.5f, %.5f""""))
        assertEquals(emptyList<String>(), literals("""text = "%1${'$'}s km""""))
    }

    @Test
    fun symbolSeparatorsAreExempt() {
        assertEquals(emptyList<String>(), literals("""text = "|""""))
        assertEquals(emptyList<String>(), literals("""text = " · """"))
    }

    @Test
    fun camelCaseIdentifierIsExempt() {
        assertEquals(emptyList<String>(), literals("""label = "compassRotation""""))
    }

    @Test
    fun resourcesAndComputedTextAreNotFlagged() {
        assertEquals(emptyList<String>(), literals("""title = stringResource(R.string.favorites)"""))
        assertEquals(emptyList<String>(), literals("""text = carContext.getString(R.string.loading)"""))
        assertEquals(
            emptyList<String>(),
            literals("""text = pluralStringResource(R.plurals.favorite_count, n, n)""")
        )
    }

    @Test
    fun proseAndLogsAreNotFlagged() {
        assertEquals(emptyList<String>(), literals("""Log.d(TAG, "Favorite selected")"""))
        assertEquals(emptyList<String>(), literals("""val name = "Favorite""""))
    }

    // --- report ------------------------------------------------------------

    @Test
    fun reportNamesFileLineAndLiteral() {
        val source = """Text("Hello")"""
        val report = HardcodedStringScanner.report(findings(source))
        assertTrue(report, report.contains("app/src/main/java/Fixture.kt:1"))
        assertTrue(report, report.contains("\"Hello\""))
        assertTrue(report, report.contains("i18n-l10n"))
    }
}
