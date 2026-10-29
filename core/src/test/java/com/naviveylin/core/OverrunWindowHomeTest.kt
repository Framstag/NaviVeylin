package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the overrun-window shift to its own home (issue #73, spec: `canvas-overrun` — Overrun window shift
 * for pan; spec: `smooth-follow` — The follow blit offset is computed by one seam that owns the anchor).
 *
 * Why the sources are the surface. The rule has three callers and only one of them predicts anything —
 * follow drift, the pan window and the renderer's coverage predicate — and the pan/coverage callers use the
 * default center anchor, i.e. the absolute overrun-window shift. While the helper sat on
 * `FollowPrediction` a reader of the pan path had to know that a prediction class owned the pan's geometry,
 * and a new caller could not tell which of the two meanings it was getting. Geometry cases cannot see a
 * helper's home, so the guard reads the two files: the prediction class carries no shift helper, and
 * `OverrunWindow` carries the one the caller paths use.
 *
 * The needles are assembled from parts on purpose, the way `FollowBlitCallSiteTest` does: the strings are
 * quoted in this file's own doc and body, and a scan surface that names them literally would find its own
 * text.
 */
class OverrunWindowHomeTest {

    private val repoRoot = File("..").canonicalFile
    private val corePackage = "core/src/main/java/com/naviveylin/core"
    private val predictionFile = File(repoRoot, "$corePackage/FollowPrediction.kt")
    private val windowFile = File(repoRoot, "$corePackage/OverrunWindow.kt")

    private val shiftCall = "fun " + "shiftPx("
    private val oldHelper = "display" + "OffsetPx"
    private val offsetType = "Display" + "Offset"
    private val offsetTypeDeclaration = "data class " + "DisplayOffset("
    /** A declaration the prediction class must keep: the scan reads the file it is meant to read. */
    private val predictionKeeps = "fun " + "predictedPosition("

    /**
     * The file's **code** lines: comments may name a needle freely (this guard's own doc explains the move),
     * so only a statement counts as a declaration.
     */
    private fun codeOnly(text: String): String = text.lines()
        .filterNot { line ->
            val t = line.trimStart()
            t.startsWith("//") || t.startsWith("/*") || t.startsWith("*") || t.startsWith("*/")
        }
        .joinToString("\n")

    private fun occurrences(text: String, needle: String): Int = text.split(needle).size - 1

    @Test
    fun `the prediction class carries no overrun window shift`() {
        assertTrue("the prediction class must exist at ${predictionFile.absolutePath}", predictionFile.isFile)
        val prediction = codeOnly(predictionFile.readText())
        println(
            "overrunWindowHome predictionKeeps=${occurrences(prediction, predictionKeeps)} " +
                "helper=${occurrences(prediction, shiftCall)} " +
                "oldHelper=${occurrences(prediction, oldHelper)} " +
                "type=${occurrences(prediction, offsetType)}"
        )

        assertEquals(
            "the scan must read the prediction class itself (spec: smooth-follow — Prediction between fixes)",
            1,
            occurrences(prediction, predictionKeeps)
        )
        assertEquals(
            "the overrun-window shift must not live on the prediction class: the pan window and the coverage " +
                "predicate use it without predicting anything (issue #73)",
            0,
            occurrences(prediction, shiftCall)
        )
        assertEquals(
            "the prediction class must not declare the shift helper under its old name either",
            0,
            occurrences(prediction, oldHelper)
        )
        assertEquals(
            "the shift result type must not live on the prediction class",
            0,
            occurrences(prediction, offsetType)
        )
    }

    @Test
    fun `the overrun window is the shift's home`() {
        assertTrue("the shift's home must exist at ${windowFile.absolutePath}", windowFile.isFile)
        val window = codeOnly(windowFile.readText())
        println(
            "overrunWindowHome shift=${occurrences(window, shiftCall)} " +
                "type=${occurrences(window, offsetTypeDeclaration)}"
        )

        assertEquals(
            "the neutral home must declare the shift helper the callers use (`OverrunWindow.$shiftCall`)",
            1,
            occurrences(window, shiftCall)
        )
        assertEquals(
            "the neutral home must declare the shift result type once",
            1,
            occurrences(window, offsetTypeDeclaration)
        )
    }
}
