package com.naviveylin.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds `guidelines/Build.md` §6's test-scheduling rule to the test sources: a test that builds
 * [com.naviveylin.data.SearchHistoryRepository] MUST point the repository's I/O hop at a dispatcher it
 * drives, because `record()` and `load()` run — including the `history` publication — inside
 * `withContext(defaultDispatcher)`, and a test that leaves the seam at its production value
 * (`Dispatchers.Default`) hands that work to a real thread which `advanceUntilIdle()` cannot wait for.
 *
 * The rule's failure mode is a race, which is why it survived for months: with a short write the assertion
 * after a result selection read the pre-selection state and passed; once the repository had to rewrite a
 * collapsed history file it lost the race, and the failure looked like a defect in the change under test
 * (`assertion: query='Bochum' results=[Bochum] details=false`, item §123). Nothing compared the rule to the
 * sources until this case: 96 construction sites in 65 test classes left the seam unwired.
 *
 * The detector reads **the sources**, the way `JniStubSandboxRuleTest` reads them, so no copy of the rule is
 * written down twice. Two forms count as wired: a chained `.also { … }`/`.apply { … }` on the construction —
 * on the same line or the next, which is how the classes this rule was written from spell it — and a
 * construction assigned to a name that the same file points at the seam later (a `historyRepo` property
 * assigned in setup). Each
 * allowed form is measured by its own control, because a branch nothing exercises is a sentence, not a rule.
 *
 * Scope, stated rather than implied: the scan reads `*.kt` under each test-bearing module's `src/test/java`,
 * so a test helper inside `src/main` (the `@VisibleForTesting` seam itself) and a repository built outside a
 * test module are not findings; and the guard lives in `:app`, so a `./gradlew :auto:test` run enforces
 * nothing by itself.
 *
 * The needles are assembled from parts on purpose: this file quotes the constructor and the seam in its own
 * text, and an assembled needle keeps it out of its own finding list.
 */
class SearchHistoryDispatcherRuleTest {

    /** One finding per construction whose I/O hop stays on the production dispatcher, as `<path>:<line>: <text>`. */
    private fun findingsIn(path: String, text: String): List<String> =
        unwiredSites(text).map { (line, text) -> "$path:$line: $text" }

    private fun unwiredSites(text: String): List<Pair<Int, String>> {
        val findings = mutableListOf<Pair<Int, String>>()
        var from = 0
        while (true) {
            val start = text.indexOf(CONSTRUCTOR, from)
            if (start < 0) return findings
            from = start + CONSTRUCTOR.length
            val lineStart = text.lastIndexOf('\n', start - 1) + 1
            val lineEnd = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            val line = text.substring(lineStart, lineEnd)
            val trimmedLine = line.trimStart()
            if (trimmedLine.startsWith("//") || trimmedLine.startsWith("*") || isInsideBlockComment(text, start)) continue

            val end = expressionEnd(text, start)
            val expression = text.substring(start, end)
            if (expression.contains(WIRING)) continue
            if (isWiredThroughItsName(text, lineStart, start)) continue

            findings += (text.count { it == '\n' } - text.substring(start).count { it == '\n' } + 1) to
                line.trim()
        }
    }

    /** True when [index] sits inside a `/* … */` block, where a doc-comment mention is prose, not a call. */
    private fun isInsideBlockComment(text: String, index: Int): Boolean {
        val opened = text.lastIndexOf("/*", index)
        if (opened < 0) return false
        val closed = text.indexOf("*/", opened)
        return closed < 0 || closed > index
    }

    /**
     * End of the repository expression: the constructor call's closing parenthesis, extended over a chained
     * `.call { … }` / `.call(…)` suffix that carries the wiring — the suffix may start on the next line.
     */
    private fun expressionEnd(text: String, start: Int): Int {
        var index = enclosingGroup(text, start)
        while (true) {
            var next = index
            while (next < text.length && text[next].isWhitespace()) next++
            if (next >= text.length || text[next] != '.') return index
            var name = next + 1
            while (name < text.length && (text[name].isLetterOrDigit() || text[name] == '_')) name++
            var open = name
            while (open < text.length && text[open] == ' ') open++
            if (open >= text.length || (text[open] != '{' && text[open] != '(')) return index
            index = enclosingGroup(text, open)
        }
    }

    /** Index just past the group that opens at [open] (or at [start]'s first opening bracket). */
    private fun enclosingGroup(text: String, start: Int): Int {
        var index = start
        while (index < text.length && "[{(".indexOf(text[index]) < 0) index++
        var depth = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '(' || char == '[' || char == '{') depth++
            if (char == ')' || char == ']' || char == '}') {
                depth--
                if (depth == 0) return index + 1
            }
            index++
        }
        return text.length
    }

    /** The file's own name for it — `<name>.defaultDispatcher = …` anywhere after the assignment. */
    private fun isWiredThroughItsName(text: String, lineStart: Int, start: Int): Boolean {
        val statement = text.substring(lineStart, expressionEnd(text, start))
        val name = ASSIGNED_CONSTRUCTOR.find(statement)?.groupValues?.get(1) ?: return false
        return Regex(Regex.escape(name) + """\s*\.\s*""" + WIRING + """\s*=""").containsMatchIn(text.substring(start))
    }

    private fun scan(root: File): Pair<Int, List<String>> {
        val files = root.walkTopDown().filter { it.extension == "kt" }.toList()
        val findings = files.flatMap { file -> findingsIn(file.relativeTo(ROOT).path, file.readText()) }
        return files.size to findings
    }

    @Test
    fun noTestLeavesTheSearchHistoryRepositoryIoHopOnAProductionDispatcher() {
        val scanned = TEST_SOURCES.map { it to scan(it) }
        val files = scanned.sumOf { (_, result) -> result.first }
        val findings = scanned.flatMap { (_, result) -> result.second }
        println("searchHistoryDispatcher roots=${TEST_SOURCES.size} files=$files findings=${findings.size}")
        findings.forEach { println("FINDING $it") }

        assertTrue("the test sources of the modules must be found (found $files files)", files > 0)
        assertEquals(
            "a test that builds the search history repository must point its I/O hop at the test's own " +
                "dispatcher (`guidelines/Build.md` §6, test-scheduling traps) — these leave it on a real " +
                "thread: $findings",
            emptyList<String>(),
            findings
        )
    }

    @Test
    fun everyTestBearingModuleIsScanned() {
        val missing = TEST_SOURCES.filterNot { it.isDirectory }
        println("searchHistoryDispatcher missingRoots=$missing")

        assertEquals("every declared test-source root must exist — a renamed module would stop the scan", emptyList<File>(), missing)
        assertTrue("`:app` carries the repository, so its sources must be read", scan(APP_TEST_SOURCES).first > 0)
    }

    @Test
    fun anUnwiredConstructionIsReported() {
        val fixture = listOf(
            "package fixture",
            "",
            "class UnwiredTest {",
            "    private val repository = " + CONSTRUCTOR + "context),",
            "}"
        ).joinToString("\n")

        val findings = findingsIn("fixture/UnwiredTest.kt", fixture)
        println("searchHistoryDispatcher positiveControl=$findings")

        assertEquals("the detector must report a construction that never names the seam", 1, findings.size)
        assertTrue("the finding names the line", findings.single().endsWith("4: private val repository = " +
            CONSTRUCTOR + "context),"))
    }

    @Test
    fun aChainedWiringIsAllowed() {
        val fixture = listOf(
            "package fixture",
            "",
            "class ChainedTest {",
            "    private val repository = " + CONSTRUCTOR + "context)",
            "        .also { it." + WIRING + " = rule.dispatcher }",
            "}"
        ).joinToString("\n")

        val findings = findingsIn("fixture/ChainedTest.kt", fixture)
        println("searchHistoryDispatcher chainedControl=$findings")

        assertTrue("a chained `.also { … }` wiring is the refused form's cure: $findings", findings.isEmpty())
    }

    @Test
    fun aNameWiredLaterInTheFileIsAllowed() {
        val fixture = listOf(
            "package fixture",
            "",
            "class NamedTest {",
            "    private val repository = " + CONSTRUCTOR + "context)",
            "",
            "    fun setUp() {",
            "        repository." + WIRING + " = rule.dispatcher",
            "    }",
            "}"
        ).joinToString("\n")

        val findings = findingsIn("fixture/NamedTest.kt", fixture)
        println("searchHistoryDispatcher namedControl=$findings")

        assertTrue("assigning the property and wiring it afterwards is allowed: $findings", findings.isEmpty())
    }

    private companion object {
        /** `:app` unit tests run with the module directory as their working directory. */
        val ROOT = File("..").absoluteFile.normalize()
        val APP_TEST_SOURCES = File("src/test/java").absoluteFile.normalize()
        val TEST_SOURCES = listOf(
            APP_TEST_SOURCES,
            File("../auto/src/test/java").absoluteFile.normalize(),
            File("../core/src/test/java").absoluteFile.normalize()
        )

        val CONSTRUCTOR = "SearchHistory" + "Repository("
        val WIRING = "default" + "Dispatcher"
        val ASSIGNED_CONSTRUCTOR = Regex(
            """^\s*(?:(?:private|internal|protected)\s+)?(?:(?:val|var)\s+)?(\w+)\s*=\s*""" +
                Regex.escape(CONSTRUCTOR)
        )
    }
}
