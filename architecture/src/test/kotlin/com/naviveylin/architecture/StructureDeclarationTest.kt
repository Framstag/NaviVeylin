package com.naviveylin.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The declaration cases of change `package-structure-as-five-kinds` (tasks 1.3, 1.4 and 1.11). Every case hands the
 * check the tree it means — the real one plus the one row or one file that makes the point — so no case writes
 * a package into the tree to watch it appear.
 */
class StructureDeclarationTest {

    private val declarationText: String = ProjectRoot.declarationText()
    private val declaration: StructureDeclaration = StructureDeclaration.parse(declarationText)
    private val treeFiles: List<SourceFile> = ProjectSources.sourceFiles()

    @Test
    fun structureCheckFailsOnRowWithoutPackage() {
        assertEquals(
            "the declaration the tree ships must report no violation on the tree it was measured against",
            emptyList<StructureViolation>(),
            StructureCheck(declaration).violations(treeFiles),
        )

        val rowForGone = declarationText.trimEnd('\n') + "\napp\tcom.naviveylin.gone\tfeature\tgone\tconcept,port\n"
        val goneRow = StructureDeclaration.parse(rowForGone).rows.last()
        val violations = StructureCheck(StructureDeclaration.parse(rowForGone)).violations(treeFiles)

        assertEquals("one row names a package the tree lacks, so one violation: $violations", 1, violations.size)
        assertEquals(StructureRule.ROW_WITHOUT_PACKAGE, violations.single().rule)
        assertTrue(
            "the report names the row by its line: ${violations.single().message}",
            violations.single().message.contains("$DECLARATION_PATH:${goneRow.line}"),
        )
        assertTrue(
            "the report names the package: ${violations.single().message}",
            violations.single().message.contains("com.naviveylin.gone"),
        )

        assertEquals(
            "the row removed, the same tree passes again",
            emptyList<StructureViolation>(),
            StructureCheck(StructureDeclaration.parse(declarationText)).violations(treeFiles),
        )
    }

    @Test
    fun structureCheckFailsOnDuplicateRow() {
        val duplicated = declarationText.trimEnd('\n') + "\ncore\tcom.naviveylin.core\tconcept\t-\t-\n"
        val parsed = StructureDeclaration.parse(duplicated)
        val firstRow = parsed.rowFor("com.naviveylin.core")!!
        val secondRow = parsed.rows.last()

        val violations = StructureCheck(parsed).violations(treeFiles)

        assertEquals("one package is claimed twice, so one violation: $violations", 1, violations.size)
        assertEquals(StructureRule.ONE_ROW_PER_PACKAGE, violations.single().rule)
        assertTrue(
            "the report names both rows: ${violations.single().message}",
            violations.single().message.contains("$DECLARATION_PATH:${firstRow.line}") &&
                violations.single().message.contains("$DECLARATION_PATH:${secondRow.line}"),
        )

        assertEquals(
            "the duplicate removed, the same tree passes again",
            emptyList<StructureViolation>(),
            StructureCheck(declaration).violations(treeFiles),
        )
    }

    @Test
    fun structureCheckFailsOnUndeclaredPackage() {
        assertEquals(
            "every source file of the three modules sits in a declared package",
            emptyList<StructureViolation>(),
            StructureCheck(declaration).violations(treeFiles),
        )

        val stranger = SourceFile(
            path = "app/src/main/java/com/naviveylin/undeclared/Stranger.kt",
            packageName = "com.naviveylin.undeclared",
        )
        val violations = StructureCheck(declaration).violations(treeFiles + stranger)

        assertEquals("one file sits in no declared package: $violations", 1, violations.size)
        assertEquals(StructureRule.UNDECLARED_PACKAGE, violations.single().rule)
        assertTrue(
            "the report names the file: ${violations.single().message}",
            violations.single().message.contains(stranger.path),
        )
        assertTrue(
            "the report names the package: ${violations.single().message}",
            violations.single().message.contains(stranger.packageName),
        )
    }

    @Test
    fun defaultDenyFailsForANewPackage() {
        val newPackage = SourceFile(
            path = "auto/src/main/java/com/naviveylin/newthing/NewThing.kt",
            packageName = "com.naviveylin.newthing",
        )

        val violations = StructureCheck(declaration).violations(treeFiles + newPackage)

        assertEquals("a package no row claims fails by default: $violations", 1, violations.size)
        assertEquals(StructureRule.UNDECLARED_PACKAGE, violations.single().rule)
        assertTrue(
            "the report names the new package: ${violations.single().message}",
            violations.single().message.contains(newPackage.packageName),
        )

        val claimed = declarationText.trimEnd('\n') + "\nauto\tcom.naviveylin.newthing\tfeature\tnewthing\tconcept,port\n"
        assertEquals(
            "the row that claims the package is the whole fix",
            emptyList<StructureViolation>(),
            StructureCheck(StructureDeclaration.parse(claimed)).violations(treeFiles + newPackage),
        )
    }

    /** Task 1.11, second half: a kind no rule resolves is refused, not carried along as an exemption. */
    @Test
    fun unknownKindFails() {
        val withUnknownKind = declarationText.trimEnd('\n') +
            "\ncore\tcom.naviveylin.core.probe\twidget\t-\t-\n"

        val refusal = runCatching { StructureDeclaration.parse(withUnknownKind) }.exceptionOrNull()

        assertTrue(
            "a row whose kind is not one of the five must be refused at its line, not parsed",
            refusal is DeclarationParseError,
        )
        assertTrue(
            "the refusal names the kind and the row: $refusal",
            refusal?.message?.contains("widget") == true &&
                refusal?.message?.contains("$DECLARATION_PATH:") == true,
        )
    }

    /** Task 1.11, first half: a declaration the reader cannot open is a failure, never an empty rule set. */
    @Test
    fun missingDeclarationFails() {
        val missing = "architecture/no-such-structure.tsv"
        val refusal = runCatching { ProjectRoot.declarationText(missing) }.exceptionOrNull()

        assertTrue(
            "a missing declaration must be refused as a declaration error, and the tree's own must read: " +
                "$refusal",
            refusal is DeclarationParseError,
        )
        assertTrue(
            "the refusal names the file a reader has to restore: $refusal?.message",
            refusal?.message?.contains(missing) == true,
        )
    }

    /** Task 1.11, third half: a declaration that holds no row is refused, never carried as an empty rule set. */
    @Test
    fun emptyDeclarationFails() {
        val noRow = runCatching { StructureDeclaration.parse("# the declaration holds no rows\n") }
            .exceptionOrNull()

        assertTrue(
            "a declaration with no row must be refused as a declaration error: $noRow",
            noRow is DeclarationParseError,
        )
        assertTrue(
            "the refusal names the declaration a reader has to fill: ${noRow?.message}",
            noRow?.message?.contains(DECLARATION_PATH) == true,
        )

        val report = StructureReport.of(
            StructureDeclaration(emptyList()),
            treeFiles,
            Baseline(emptyList()),
        )

        assertFalse("an empty rule set must fail, not report zero violations: ${report.summary()}", report.passed)
        assertTrue(
            "the refusal names the declaration: ${report.summary()}",
            report.summary().contains(DECLARATION_PATH),
        )
    }
}
