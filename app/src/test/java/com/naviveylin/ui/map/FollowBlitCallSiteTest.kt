package com.naviveylin.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the phone follow blit offset to spec `smooth-follow` — "Single resolved anchor across render,
 * blit and marker": the blit anchor is the **resolved** fraction, and nothing observes which anchor the
 * **call site** hands over.
 *
 * Why the sources are the surface. `FollowAnchorFramingTest` proves the framing contract with the fractions
 * it is *handed*, so it stayed green while `MapCanvasScreen`'s follow display block passed
 * `ui.activeFollowAnchor` (raw preset ≈ 0.9) to the shift helper while the render target
 * and the marker used `state.resolvedAnchor` (≈ 0.76) — a `(0.9 − 0.76)·H` content offset plus per-500 ms
 * re-render churn, invisible to the suite (issue #18, `§111`; the same day's `fix-follow-vehicle-jumps`
 * reworked the same pipeline without catching it). Its own regression case asserts the *defect geometry* by
 * handing the raw preset in, so a second regression at the call site would leave every case green.
 *
 * The seam is the fix: `FollowBlitRules.offsetFor` takes the whole `MapCanvasUiState` and reads
 * `resolvedAnchor` itself, so the anchor is not a parameter a caller can choose, and the screen can no
 * longer name the raw preset on this path. The call site is in the follow display loop, which
 * `2026-10-25-fix-map-canvas-screen-file-size` moved out of `MapCanvasScreen.kt` into
 * `MapCanvasFollowDisplay.kt` word for word; the case reads that file, the way
 * `QualifierUseSiteTargetTest` reads the module's sources (a unit test cannot compile the module it runs in,
 * and the offset is a local of a `withFrameNanos` loop in a composable — no published state to assert), and
 * the seam's own geometry is driven by
 * `MapCanvasViewModelVehicleAnchorTest#followBlitOffsetTakesTheResolvedAnchorNotTheRawPreset`.
 *
 * The needles are assembled from parts on purpose: the strings are quoted in this file's own doc and body,
 * and a scan surface that names them literally would find its own text the moment it grew to cover tests.
 */
class FollowBlitCallSiteTest {

    private companion object {
        /** Code lines on either side of the seam call that make up the offset call site. */
        const val CALL_SITE_RADIUS = 20
    }

    private val repoRoot = File("..").canonicalFile
    private val mapPackage = "app/src/main/java/com/naviveylin/ui/map"
    private val screenFile = File(repoRoot, "$mapPackage/MapCanvasFollowDisplay.kt")
    private val seamFile = File(repoRoot, "$mapPackage/FollowBlitRules.kt")

    private val seamCall = "FollowBlitRules" + ".offsetFor("
    private val inlineCall = "OverrunWindow" + ".shiftPx("
    private val rawPreset = "active" + "FollowAnchor"
    private val resolvedAnchor = "resolved" + "Anchor"

    private fun occurrences(text: String, needle: String): Int =
        text.split(needle).size - 1

    /**
     * The file's **code** lines: comments may name a needle freely (this seam's KDoc explains why the raw
     * preset must not be read), so only a statement counts as a read or a call.
     */
    private fun codeOnly(text: String): String = text.lines()
        .filterNot { line ->
            val t = line.trimStart()
            t.startsWith("//") || t.startsWith("/*") || t.startsWith("*") || t.startsWith("*/")
        }
        .joinToString("\n")

    /** The [radius] code lines on either side of the first [needle] — the call site that matters. */
    private fun windowAround(text: String, needle: String, radius: Int): String {
        val at = text.indexOf(needle)
        if (at < 0) return ""
        val lines = text.lines()
        val line = text.substring(0, at).count { it == '\n' }
        return lines.subList(maxOf(0, line - radius), minOf(lines.size, line + radius + 1))
            .joinToString("\n")
    }

    @Test
    fun `the follow blit offset is computed by the seam and not inline in the frame loop`() {
        assertTrue("the follow display block must exist at ${screenFile.absolutePath}", screenFile.isFile)
        assertTrue(
            "the follow blit seam must exist at ${seamFile.absolutePath}",
            seamFile.isFile
        )
        val screen = codeOnly(screenFile.readText())
        val seamCalls = occurrences(screen, seamCall)
        val inlineCalls = occurrences(screen, inlineCall)
        // The screen legitimately reads the raw preset elsewhere (the settings sheet mirrors it), so the
        // guard is scoped to the statement that calls the seam: a caller that reconstructs the state with
        // `ui.copy(resolvedAnchor = ui.activeFollowAnchor)` would otherwise reintroduce the old anchor
        // without touching the seam file.
        val callSite = windowAround(screen, seamCall, CALL_SITE_RADIUS)
        val callSiteRawReads = occurrences(callSite, rawPreset)
        println(
            "followBlitCallSite seamCalls=$seamCalls inlineCalls=$inlineCalls " +
                "callSiteRawReads=$callSiteRawReads"
        )

        assertEquals(
            "the follow display block must get its offset from the seam, which owns the anchor " +
                "choice ($seamCall)",
            1,
            seamCalls
        )
        assertEquals(
            "the screen must not compute the follow blit offset itself: an inline $inlineCall can be " +
                "handed any anchor, which is how the raw preset reached it before (issue #18)",
            0,
            inlineCalls
        )
        assertEquals(
            "the offset call site must not reconstruct the state with the raw preset " +
                "(`ui.copy(resolvedAnchor = ui.$rawPreset)`) — the seam's state parameter is the guard",
            0,
            callSiteRawReads
        )
    }

    @Test
    fun `the follow offset path cannot read the raw preset`() {
        assertTrue(
            "the follow blit seam must exist at ${seamFile.absolutePath}",
            seamFile.isFile
        )
        val seam = codeOnly(seamFile.readText())
        println(
            "followBlitSeam readsResolved=${occurrences(seam, resolvedAnchor)} " +
                "readsRawPreset=${occurrences(seam, rawPreset)} " +
                "callsOffset=${occurrences(seam, inlineCall)}"
        )

        assertEquals(
            "the follow offset path must not read the raw preset: it differs from the resolved anchor " +
                "whenever an overlay moves it, and the blit would sit outside the overrun margin",
            0,
            occurrences(seam, rawPreset)
        )
        assertEquals(
            "the follow offset path must take the resolved anchor the render target and the marker use",
            1,
            occurrences(seam, resolvedAnchor)
        )
        assertEquals(
            "the seam must still compute the drift with the shared helper",
            1,
            occurrences(seam, inlineCall)
        )
    }
}
