package com.naviveylin.architecture

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The documentation cases of change `package-structure-as-five-kinds` (tasks 4.1–4.4 and 6.1–6.3): the
 * declaration is the single machine-readable copy of the structure, so the guideline section that carries the
 * structure names it and enumerates no package itself.
 *
 * The two documents are read as text, the same way the structure check reads the sources: no build, no test
 * classpath, and the failure names the token that restated the structure.
 */
class StructureDocumentationTest {

    /** `guidelines/Design.md` §1 is the section that carries the structure: it routes, it does not restate. */
    @Test
    fun docNamesDeclarationAndListsNoPackages() {
        val design = ProjectRoot.resolve("guidelines/Design.md").readText()
        val designSectionOne = section(design, "1.")

        assertTrue(
            "`guidelines/Design.md` §1 must name $DECLARATION_PATH, the single machine-readable copy of the structure",
            designSectionOne.contains(DECLARATION_PATH),
        )
        assertTrue(
            "`guidelines/Design.md` §1 must route to the document that states the five kinds, `guidelines/Structure.md`",
            designSectionOne.contains("Structure.md"),
        )
        assertNoPackageEnumerated("`guidelines/Design.md` §1", designSectionOne)

        val structure = ProjectRoot.resolve("guidelines/Structure.md").readText()
        assertTrue(
            "`guidelines/Structure.md` must name $DECLARATION_PATH, the declaration whose rules it states",
            structure.contains(DECLARATION_PATH),
        )
        assertNoPackageEnumerated("`guidelines/Structure.md` §1", section(structure, "1."))
    }

    /**
     * The text of the section a `## <number> ` heading introduces, through the line before the next `## `
     * heading. A section number is the document's own: `1.` finds the heading whose number is `1`.
     */
    internal fun section(document: String, number: String): String {
        val lines = document.lines()
        val start = lines.indexOfFirst { line -> headingNumber(line) == number }
        require(start >= 0) { "the document carries no `## $number ` section" }
        val end = lines.drop(start + 1).indexOfFirst { line -> line.startsWith("## ") }
        val stop = if (end < 0) lines.size else start + 1 + end
        return lines.subList(start, stop).joinToString("\n")
    }

    private fun headingNumber(line: String): String? =
        if (!line.startsWith("## ")) null else line.removePrefix("## ").trim().substringBefore(' ')

    /**
     * A section that states the structure states no package: a package is written either by its full name
     * (`com.naviveylin.<module>.<package>`) or as a path under its module. Either form is a second copy the
     * declaration exists to prevent, so it is reported with the token, not with a count.
     *
     * A path a measurement command quotes — `git ls-files app/src/main/java/…` — is a measurement and not an
     * enumeration, so those lines are read out before the paths are looked for: the requirement forbids a
     * section that lists the packages, not a section that measures them.
     */
    internal fun assertNoPackageEnumerated(where: String, text: String) {
        val packages = Regex("""com\.naviveylin\.[a-z]""")
        val paths = Regex("""\b(app|auto|core)/(main|src|[a-z]+)/""")
        val measured = Regex("""git ls-files|git rev-parse|git grep|\bfind """)
        val stated = text.lines().filterNot { measured.containsMatchIn(it) }.joinToString("\n")
        packages.find(stated)?.let { match ->
            fail(
                "$where enumerates the package `${match.value}…`; the structure is declared in $DECLARATION_PATH " +
                    "and stated by `guidelines/Structure.md` §1–§2, so this section routes instead of listing it",
            )
        }
        paths.find(stated)?.let { match ->
            fail(
                "$where enumerates the package path `${match.value}…`; the structure is declared in $DECLARATION_PATH " +
                    "and stated by `guidelines/Structure.md` §1–§2, so this section routes instead of listing it",
            )
        }
    }
}
