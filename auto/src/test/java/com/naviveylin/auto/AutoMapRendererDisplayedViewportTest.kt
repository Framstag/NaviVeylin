package com.naviveylin.auto

import com.naviveylin.core.VehicleAnchorPosition
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The car's displayed viewport is ONE derivation (spec: `auto-smooth-follow` — "Single
 * resolved anchor in the AA follow blit"; issue #1043): the blit, the marker's draw offset
 * and the frame's own commit read one derived offset instead of each resolving the anchor
 * again.
 *
 * The shipped scenarios pin the resolved anchor's VALUE, so three agreeing sites keep them
 * green. This case pins the derivation's SINGULARITY — the reading the base cannot express —
 * so it is red there (three main-source sites compute the shift) and green once the call
 * sites read [displayedViewport].
 */
@RunWith(RobolectricTestRunner::class)
class AutoMapRendererDisplayedViewportTest : AutoMapRendererTestSupport() {

    @Test
    fun theCarOverlayOffsetIsTheFramesShift() {
        // 1. The derivation is singular: one main-source site computes the shift, so a
        // fourth site or a helper with its own branch has nowhere to hide.
        val sites = shiftSiteCount()
        assertEquals(
            "the overlay offset is derived in one place, not once per call site (found $sites)",
            1,
            sites
        )

        // 2. That one value is what the displayed frame is shifted by, and the marker's own
        // draw offset reads it: the marker rides the content it is drawn on.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setHostPaneRtl(false)
        renderer.setFollowAnchor(VehicleAnchorPosition.TOP_FAR_LEFT) // pane-band preset
        var now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        renderer.reCenter()
        renderer.renderFrame()

        now += 90
        renderer.extrapolationTick(now, 0.09)

        val (bx, by) = renderer.blitOffset()
        assertTrue(
            "a pane-band preset is still served by a blit, not a saturated clamp (dx=$bx dy=$by)",
            kotlin.math.abs(bx) < 8.0 && kotlin.math.abs(by) < 8.0
        )
        val (mx, my) = renderer.markerScreenPosition(100, 100)
        renderer.setBlitOffsetForTest(0.0, 0.0)
        val (mx0, my0) = renderer.markerScreenPosition(100, 100)
        assertEquals("the marker's draw offset is the frame's shift (x)", mx0 - bx, mx, 1e-9)
        assertEquals("the marker's draw offset is the frame's shift (y)", my0 - by, my, 1e-9)
    }

    /** Main-source `OverrunWindow.shiftPx` call sites — the derivation's singularity. */
    private fun shiftSiteCount(): Int {
        val roots = listOf(
            File("src/main/java/com/naviveylin/auto"),
            File("auto/src/main/java/com/naviveylin/auto")
        )
        val dir = roots.firstOrNull { it.isDirectory } ?: return -1
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sumOf { file -> Regex("OverrunWindow\\.shiftPx").findAll(file.readText()).count() }
    }
}
