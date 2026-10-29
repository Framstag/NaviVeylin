package com.naviveylin.auto

import com.naviveylin.core.OverrunWindow
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.anchorCenter
import io.mockk.every
import java.util.Locale
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [AutoMapRenderer]'s follow re-engage and its resolved follow anchor: the frame a re-engage
 * re-anchored, the leading edge a host pane inset clamps, the bottom row above the host's chrome,
 * and the locale-independent diagnostic numbers — one group of the cases that were a single
 * 2 099-line `AutoMapRendererTest` until the file crossed the `source-size` row's bound (item #300).
 */
@RunWith(RobolectricTestRunner::class)
class AutoMapRendererFollowAnchorTest : AutoMapRendererTestSupport() {

    @Test
    fun reengageFollowReengagesWithoutSnapping() {
        // reengageFollow re-engages follow mode without snapping the display
        // to the fix (the extrapolation loop eases there); reCenter snaps.
        // Screens that disengage follow transiently (heading-up rotation) use
        // reengageFollow so the map does not jump back per fix.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 10.0)

        renderer.setViewport(51.5142273, 7.4652789, 12, 0.0)
        assertFalse(renderer.isFollowMode())
        renderer.reengageFollow()
        assertTrue(renderer.isFollowMode())

        // reCenter snaps the display to the fix.
        renderer.setViewport(51.5142273, 7.4652789, 12, 0.0)
        renderer.reCenter()
        assertEquals(51.5142273, renderer.markerPosition().first, 1e-9)
        assertEquals(7.4652789, renderer.markerPosition().second, 1e-9)
    }

    @Test
    fun fullRenderPreservesDisplayInFollowMode() {
        // Regression test for the "pumping" bug: a render triggered while
        // follow mode stays engaged (heading-up setViewport + reengageFollow)
        // must NOT yank the loop-owned eased display back to the render
        // target — that made the vehicle marker oscillate every fix.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)

        // The extrapolation loop eased the display ahead of the fix.
        val now = System.currentTimeMillis()
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 10.0, timeMs = now)
        renderer.extrapolationTick(now + 500, 0.1)
        val displayBefore = renderer.markerPosition()
        assertTrue(abs(displayBefore.first - 51.5142273) > 1e-7)

        // Simulate the heading-up flow: disengage + re-engage, then the
        // pending render completes in follow mode.
        renderer.setViewport(51.5142273, 7.4652789, 12, 0.0)
        renderer.reengageFollow()
        renderer.renderFrame()

        val displayAfter = renderer.markerPosition()
        assertEquals(displayBefore.first, displayAfter.first, 1e-12)
        assertEquals(displayBefore.second, displayAfter.second, 1e-12)
    }

    @Test
    fun defaultFollowAnchorKeepsCenterFraming() {
        // Default anchor (center/center) must reproduce the pre-feature
        // framing: the follow render target equals the vehicle position.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0)
        renderer.reCenter()
        assertEquals(51.5142273, renderer.markerViewport().first, 1e-9)
        assertEquals(7.4652789, renderer.markerViewport().second, 1e-9)
    }

    @Test
    fun offCenterAnchorShiftsFollowRenderTarget() {
        // Bottom-right anchor: after reCenter the render target must be the
        // anchor center of the fix — projecting the fix against it lands on
        // the 70%/90% fractions (spec: auto/free-driving — Follow mode
        // activated; auto/navigation-view — Vehicle anchor during navigation).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_RIGHT)
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0)
        renderer.reCenter()

        val vp = renderer.markerViewport()
        assertFalse(vp.first == 51.5142273 && vp.second == 7.4652789)
        val proj = ProjectionUtils.viewport(
            vp.first, vp.second, renderer.fractionalZoom(), 100, 100, 240.0,
            renderer.viewportState.value.angle
        )
        val (x, y) = proj.geoToScreen(51.5142273, 7.4652789)
        assertEquals(0.7 * 100, x, 0.5)
        assertEquals(0.9 * 100, y, 0.5)
    }

    @Test
    fun reengageFollowAnchorsViewportToFix() {
        // Re-engage after a pan must return the marker to the anchor, not the
        // surface center (spec: auto/navigation-view — Anchor restored after
        // manual pan).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0)
        renderer.setViewport(51.5142273, 7.4652789, 12, 0.0)
        renderer.reengageFollow()

        val expected = anchorCenter(
            51.5142273, 7.4652789, VehicleAnchorPosition.BOTTOM_CENTER,
            renderer.fractionalZoom(), 100, 100, 240.0, renderer.viewportState.value.angle
        )
        assertEquals(expected.first, renderer.markerViewport().first, 1e-9)
        assertEquals(expected.second, renderer.markerViewport().second, 1e-9)
    }

    // --- A re-engage owns the frame it re-anchored -------------------------------
    // (spec: auto-smooth-follow — A follow re-engage requests the frame it re-anchored;
    // change fix-car-follow-reengage-render)

    @Test
    fun reengageFollowSchedulesTheFrameItReanchored() {
        // A pan release (MapPanHandler.onPanModeChanged(false)) and a screen start/resume
        // re-engage follow with NO pending commit behind them. Before this change the
        // re-engage only re-anchored the target, so the surface kept the pan's frame until
        // the next fix, commit or tick requested one.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0)
        renderer.reCenter()
        renderer.frameIteration()

        // Pan away from the vehicle: follow disengages and the pan commits its own frame.
        renderer.setViewport(fixLat + 0.05, fixLon + 0.05, 12, 0.0)
        renderer.frameIteration()
        val requestsBefore = renderer.renderRequestCount
        val framesBefore = renderer.fullRenderCount + renderer.blitCount

        renderer.reengageFollow()

        assertEquals(
            "the re-engage must schedule a frame of its own",
            requestsBefore + 1, renderer.renderRequestCount
        )
        assertEquals(
            "nothing is drawn until the scheduled frame is consumed",
            framesBefore, renderer.fullRenderCount + renderer.blitCount
        )

        val target = renderer.viewportState.value
        assertTrue(
            "the re-engage must re-anchor on the vehicle, not keep the pan's center",
            abs(target.lat - (fixLat + 0.05)) > 1e-5
        )

        renderer.frameIteration()

        assertEquals(
            "one re-engage draws one frame",
            framesBefore + 1, renderer.fullRenderCount + renderer.blitCount
        )
        val rendered = client.renderCalls.last()
        assertEquals("the frame is rendered at the re-anchored target", target.lat, rendered[0], 1e-9)
        assertEquals(target.lon, rendered[1], 1e-9)
        assertEquals(
            "the committed frame carries the re-anchored center",
            target.lat, renderer.markerViewport().first, 1e-9
        )

        renderer.frameIteration()
        assertEquals(
            "no frame beyond the one the re-engage scheduled",
            framesBefore + 1, renderer.fullRenderCount + renderer.blitCount
        )
    }

    @Test
    fun reengageFollowKeepsTheReanchoredCommitBlitEligible() {
        // A re-engage changes neither magnification nor rotation, and it anchors on the
        // displayed position, so its commit is the center-only change a blit can serve —
        // even when the last commit before it was a rotation (blit-ineligible). A full
        // native render here would be a per-pan-release cost the overrun blit exists for.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        // Center anchor: a rotation commit moves no center, so the re-anchor stays inside
        // the overrun margin even after the frame's angle changed.
        renderer.setFollowAnchor(VehicleAnchorPosition.CENTER)
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0)
        renderer.reCenter()
        renderer.frameIteration()

        renderer.setViewport(renderer.viewportState.value.lat, renderer.viewportState.value.lon, 12, 0.5)
        renderer.frameIteration()
        val fullsBefore = renderer.fullRenderCount
        val blitsBefore = renderer.blitCount

        renderer.reengageFollow()
        renderer.frameIteration()

        assertEquals(
            "a re-anchored commit must not force a full native render",
            fullsBefore, renderer.fullRenderCount
        )
        assertTrue(
            "the re-anchored commit is served by an overrun blit",
            renderer.blitCount > blitsBefore
        )
    }

    // --- Locale-independent diagnostic numbers -----------------------------------
    // (spec: auto-diagnostics — Diagnostic numbers are locale-independent;
    // change fix-car-follow-reengage-render)

    @Test
    fun followDiagnosticLineKeepsNumbersLocaleIndependent() {
        // The line is the on-device recipe's frame-vs-pending evidence
        // (guidelines/Build.md §10), so it has to be parseable on any device locale: with
        // the default-locale formatter a German build printed `off=3,4,-1,5` and
        // `frameMag=15,00`, where the decimal comma reads as a field separator.
        val now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        val offset = OverrunWindow.DisplayOffset(
            rawX = 3.42, rawY = -1.44, clampedX = 3.42, clampedY = -1.44
        )

        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val dotLocale = renderer.followDiagnosticLine(now, offset)
            Locale.setDefault(Locale.GERMANY)
            val commaLocale = renderer.followDiagnosticLine(now, offset)

            assertEquals("a decimal-comma locale must not change the line", dotLocale, commaLocale)
            assertTrue("the decimal offset must print with a dot: $dotLocale", dotLocale.contains("off=3.4,-1.4"))
            assertFalse(
                "no decimal comma may appear in the numbers: $dotLocale",
                dotLocale.contains("3,4") || dotLocale.contains("-1,4") || dotLocale.contains("15,00")
            )
            for (field in listOf(
                " spd=", " eff=", " dec=", " stp=", " off=", " clamped=",
                " frameMag=", " frameAng=", " pendingMag=", " pendingAng=",
                " dAng=", " dMag=", " last=", " renders=", " blits="
            )) {
                assertTrue("the recipe field $field is missing in: $dotLocale", dotLocale.contains(field))
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    // --- Host pane insets + marker/content alignment -------------------------
    // (spec: auto/navigation-view — "Panel clearance via side anchor"; change
    // anchor-per-surface-visible-area)

    @Test
    fun hostPaneClampsTheLeadingEdgeAnchorOnly() {
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)

        // Default (center) keeps its exact fraction on both pane sides: the AA default
        // framing contract is unchanged.
        assertEquals(0.5, renderer.resolvedFollowAnchor().fx, 1e-9)
        renderer.setHostPaneRtl(true)
        assertEquals(0.5, renderer.resolvedFollowAnchor().fx, 1e-9)
        renderer.setHostPaneRtl(false)

        // A preset inside the host's 40% leading band moves to the nearest free
        // position (band edge + the grid's own 10% margin), not behind the panel.
        renderer.setFollowAnchor(VehicleAnchorPosition.TOP_FAR_LEFT)
        val clamped = renderer.resolvedFollowAnchor()
        assertEquals(VehicleAnchorPosition.TOP_FAR_LEFT.fy, clamped.fy, 1e-9)
        assertTrue("far-left must move out of the 40% band (was ${clamped.fx})", clamped.fx >= 0.4)

        // A preset already clear of the band is untouched.
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_RIGHT)
        assertEquals(VehicleAnchorPosition.BOTTOM_RIGHT.fx, renderer.resolvedFollowAnchor().fx, 1e-9)

        // RTL hosts mirror the panel: the far-RIGHT preset is the clamped one.
        renderer.setHostPaneRtl(true)
        renderer.setFollowAnchor(VehicleAnchorPosition.TOP_FAR_RIGHT)
        val rtlClamped = renderer.resolvedFollowAnchor()
        assertTrue("far-right must move out of the band in RTL", rtlClamped.fx <= 0.6 + 1e-9)
        renderer.setFollowAnchor(VehicleAnchorPosition.TOP_FAR_LEFT)
        assertEquals(
            VehicleAnchorPosition.TOP_FAR_LEFT.fx,
            renderer.resolvedFollowAnchor().fx, 1e-9
        )
    }

    @Test
    fun bottomRowAnchorClampsAboveHostBottomChrome() {
        // AAOS bottom bar (host chrome over the surface bottom, not part of
        // the granted surface): a bottom-row preset must move up out of the
        // covered band (design: anchor-per-surface-visible-area, AA vertical
        // clamp — the AA counterpart of the phone overlay-inset rule). Center
        // stays exact; no inset = identity.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)

        renderer.setHostBottomInset(30)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)
        val clamped = renderer.resolvedFollowAnchor()
        assertTrue(
            "bottom-center must move above the bottom band (was ${clamped.fy})",
            clamped.fy < VehicleAnchorPosition.BOTTOM_CENTER.fy
        )
        assertTrue("clamped anchor must stay inside the visible band", clamped.fy >= 0.5)

        renderer.setFollowAnchor(VehicleAnchorPosition.CENTER)
        assertEquals(0.5, renderer.resolvedFollowAnchor().fy, 1e-9)

        renderer.setHostBottomInset(0)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)
        assertEquals(VehicleAnchorPosition.BOTTOM_CENTER.fy, renderer.resolvedFollowAnchor().fy, 1e-9)
    }

    @Test
    fun hostTopInsetStoredClampedAndNoopsOnRepeat() {
        // Mirror of setHostBottomInset (design D8, street-name-host-views):
        // the renderer stores the host-covered top band (the AAOS status bar)
        // for the street-pill band anchoring; it must clamp negatives, guard
        // repeats, and leave the follow-anchor resolution untouched (the top
        // anchor clamp is a recorded follow-up).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)

        assertEquals(0, renderer.hostTopInset())
        renderer.setHostTopInset(56)
        assertEquals(56, renderer.hostTopInset())
        renderer.setHostTopInset(-10) // negative clamped to 0
        assertEquals(0, renderer.hostTopInset())
        renderer.setHostTopInset(56)
        renderer.setHostTopInset(56) // repeat: no-op guard, no render churn
        assertEquals(56, renderer.hostTopInset())

        // The top band does not shift bottom/clamped anchors (follow-up note).
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)
        assertEquals(VehicleAnchorPosition.BOTTOM_CENTER.fy, renderer.resolvedFollowAnchor().fy, 1e-9)
    }
}
