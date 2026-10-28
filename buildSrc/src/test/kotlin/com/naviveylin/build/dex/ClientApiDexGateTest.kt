package com.naviveylin.build.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for the client-API-in-dex gate (issue #691).
 *
 * The two halves are separated on purpose. [ClientApiClasses] and [ClientApiDexGate] are pure decisions and are
 * tested on their own sets; [DexClassDescriptors] is tested against a hand-built dex file, because the whole
 * finding was the difference between the table that lists a *referenced* type and the table that lists a
 * *defined* one — a reader that answered from the wrong table would pass a grep and fail the app.
 */
class ClientApiDexGateTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun tree(vararg paths: String): File {
        val root = temp.root
        paths.forEach { relative ->
            val file = File(root, relative)
            file.parentFile.mkdirs()
            file.writeText("\n")
        }
        return root
    }

    // ── the required side ────────────────────────────────────────────────

    @Test
    fun everyJavaFileIsItsOwnDescriptorWithItsPackage() {
        val root = tree(
            "com/framstag/libosmscout/client/MapProvider.java",
            "com/framstag/libosmscout/client/nested/LaneTurn.java",
            "com/framstag/libosmscout/client/notes.txt"
        )

        assertEquals(
            setOf(
                "Lcom/framstag/libosmscout/client/MapProvider;",
                "Lcom/framstag/libosmscout/client/nested/LaneTurn;"
            ),
            ClientApiClasses.descriptorsIn(root)
        )
    }

    @Test
    fun aMissingSourceRootRequiresNothing() {
        assertTrue(ClientApiClasses.descriptorsIn(File(temp.root, "absent")).isEmpty())
    }

    // ── the decision ─────────────────────────────────────────────────────

    @Test
    fun theMissingSetIsTheRequiredSetMinusTheDefinedOneSorted() {
        val missing = ClientApiDexGate.missing(
            required = setOf("Lb/B;", "La/A;", "Lc/C;"),
            defined = setOf("Lc/C;", "Lx/Unrelated;")
        )

        assertEquals(listOf("La/A;", "Lb/B;"), missing)
    }

    @Test
    fun theReportNamesEveryAbsentClassAndTheScannedDexFiles() {
        val report = ClientApiDexGate.report(
            missing = listOf("Lcom/framstag/libosmscout/client/MapProvider;"),
            dexPaths = listOf("app/build/intermediates/dex/mobileDebug/mergeProjectDexMobileDebug/0/classes.dex")
        )

        assertTrue(report, report.contains("com.framstag.libosmscout.client.MapProvider"))
        assertTrue(report, report.contains("mergeProjectDexMobileDebug/0/classes.dex"))
        assertTrue(report, report.contains("#691"))
    }

    // ── the dex reader ───────────────────────────────────────────────────

    /** A dex with [definedDescriptors] in `class_defs` and [referencedDescriptors] only in `type_ids`. */
    private fun dex(
        definedDescriptors: List<String>,
        referencedDescriptors: List<String> = emptyList()
    ): File {
        val strings = (definedDescriptors + referencedDescriptors).distinct()

        // Layout: header, string ids, type ids, class defs, then the string data blobs.
        val stringIdsOff = 112
        val stringIdsSize = strings.size * 4
        val typeIdsOff = stringIdsOff + stringIdsSize
        val typeIdsSize = strings.size * 4
        val classDefsOff = typeIdsOff + typeIdsSize
        val classDefsSize = definedDescriptors.size * 32
        val stringDataOff = classDefsOff + classDefsSize

        val stringData = ByteArray(strings.sumOf { 1 + it.toByteArray().size + 1 })
        val stringOffsets = IntArray(strings.size)
        var cursor = 0
        strings.forEachIndexed { index, value ->
            stringOffsets[index] = stringDataOff + cursor
            val bytes = value.toByteArray(Charsets.UTF_8)
            stringData[cursor] = bytes.size.toByte() // uleb128, one byte for every descriptor here
            bytes.copyInto(stringData, cursor + 1)
            stringData[cursor + 1 + bytes.size] = 0
            cursor += 1 + bytes.size + 1
        }

        val bytes = ByteArray(stringDataOff + stringData.size)
        "dex\n035\u0000".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        putUInt(bytes, 56, strings.size)
        putUInt(bytes, 60, stringIdsOff)
        putUInt(bytes, 64, strings.size)
        putUInt(bytes, 68, typeIdsOff)
        putUInt(bytes, 96, definedDescriptors.size)
        putUInt(bytes, 100, classDefsOff)

        stringOffsets.forEachIndexed { index, offset -> putUInt(bytes, stringIdsOff + index * 4, offset) }
        strings.indices.forEach { putUInt(bytes, typeIdsOff + it * 4, it) }
        definedDescriptors.forEachIndexed { index, descriptor ->
            putUInt(bytes, classDefsOff + index * 32, strings.indexOf(descriptor))
        }
        stringData.copyInto(bytes, stringDataOff)
        return temp.newFile("classes-${System.nanoTime()}.dex").also { it.writeBytes(bytes) }
    }

    private fun putUInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
        bytes[offset + 2] = (value ushr 16).toByte()
        bytes[offset + 3] = (value ushr 24).toByte()
    }

    @Test
    fun aTypeThatIsOnlyReferencedIsNotDefined() {
        val file = dex(
            definedDescriptors = listOf("Lcom/framstag/libosmscout/client/TrackPoint;"),
            referencedDescriptors = listOf("Lcom/framstag/libosmscout/client/MapProvider;")
        )

        val defined = DexClassDescriptors.definedIn(file)

        assertTrue(defined.toString(), defined.contains("Lcom/framstag/libosmscout/client/TrackPoint;"))
        assertFalse(defined.toString(), defined.contains("Lcom/framstag/libosmscout/client/MapProvider;"))
    }

    @Test
    fun theDefinedSetOfSeveralDexFilesIsTheUnionOfTheirClassDefs() {
        val first = dex(listOf("La/First;"))
        val second = dex(listOf("Lb/Second;"))

        assertEquals(
            setOf("La/First;", "Lb/Second;"),
            DexClassDescriptors.definedIn(listOf(first, second, File(temp.root, "merge.log")))
        )
    }

    @Test
    fun aFileThatIsNotADexIsRefusedByName() {
        val notDex = temp.newFile("classes.dex").also { it.writeBytes(ByteArray(200)) }

        val failure = runCatching { DexClassDescriptors.definedIn(notDex) }.exceptionOrNull()

        assertTrue(
            failure?.message.orEmpty(),
            failure?.message.orEmpty().contains("is not a standard dex file")
        )
    }
}
