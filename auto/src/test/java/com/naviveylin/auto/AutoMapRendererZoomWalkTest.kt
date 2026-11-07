package com.naviveylin.auto

import com.naviveylin.core.SpeedZoomTable
import io.mockk.every
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [AutoMapRenderer]'s auto-zoom walk: the displayed magnification steps toward its target between
 * frames, stays inside the blit window, retargets without passing the request, and ends on an exact
 * render — one group of the cases that were a single 2 099-line `AutoMapRendererTest` until the file
 * crossed the `source-size` row's bound (item #300).
 */
@RunWith(RobolectricTestRunner::class)
class AutoMapRendererZoomWalkTest : AutoMapRendererTestSupport() {

    // --- aa-entry-zoom-animation: zoom transition walk -------------------------------
    // (spec: auto-speed-zoom — Auto-zoom entry transition starts from the displayed
    // magnification / Entry transition is bounded in time and render requests;
    // change aa-entry-zoom-animation)

    /**
     * A renderer with one frame on the surface at [mag] and the background loops
     * disabled, so the test drives the walk and the renders itself.
     */
    private fun rendererWithFrame(mag: Double = 13.0) {
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 1080, 600)
        val now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        renderer.reCenter()
        renderer.setViewport(
            renderer.viewportState.value.lat, renderer.viewportState.value.lon,
            mag.toInt(), 0.0, mag
        )
        renderer.renderFrame()
    }

    /** One magnification request through the auto-zoom path (transition-eligible). */
    private fun requestAutoZoom(mag: Double, walkZoom: Boolean = true) {
        val vp = renderer.viewportState.value
        renderer.setViewport(vp.lat, vp.lon, mag.toInt(), vp.angle, mag, walkZoom = walkZoom)
    }

    /** Drive the walk to completion, rendering one frame per committed step. */
    private fun walkToCompletion(limit: Int = 64): List<Double> {
        val steps = mutableListOf<Double>()
        repeat(limit) {
            val stepped = renderer.advanceZoomWalk() ?: return steps
            steps += stepped
            renderer.renderFrame()
        }
        return steps
    }

    @Test
    fun farZoomCommitStartsAWalk() {
        // The reported defect: entering free driving snapped 13.0 -> 17.0 in ONE frame.
        // A request farther than the blit window must not be committed in one frame.
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)

        assertEquals(
            "a far auto-zoom request must not be committed in one frame",
            13.0, renderer.viewportState.value.zoomFraction, 1e-9
        )
        val first = renderer.advanceZoomWalk()
        assertNotNull("the walk commits the first step", first)
        assertTrue(
            "the first step stays inside the blit window",
            abs(first!! - 13.0) <= AutoMapRenderer.ZOOM_BLIT_LIMIT + 1e-9
        )
    }

    @Test
    fun walkStepsStayInsideTheBlitWindow() {
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)

        val steps = walkToCompletion()
        assertTrue("a 4-level entry walks in several steps (got ${steps.size})", steps.size >= 16)
        var previous = 13.0
        steps.forEach { step ->
            assertTrue(
                "step $step must stay inside the blit window (from $previous)",
                abs(step - previous) <= AutoMapRenderer.ZOOM_BLIT_LIMIT + 1e-9
            )
            previous = step
        }
        assertTrue(
            "the sequence must be monotonic toward the target (spec: no overshoot), got $steps",
            steps.zipWithNext().all { (a, b) -> b >= a }
        )
    }

    @Test
    fun speedUnknownTargetWalksFromTheDisplayedMagnification() {
        // spec: auto-speed-zoom — "Speed unknown while a magnification is displayed": the target
        // is computed from the default speed of 20 km/h and the displayed magnification moves to
        // it across display frames, not in one frame. 20 km/h is the spec's DEFAULT value; the
        // car derivation has no seed of its own (`unknownSpeedCommitsNoAutoZoomTarget`), so the
        // value is fed explicitly here.
        rendererWithFrame(13.0)
        val target = SpeedZoomTable.compute(20.0)
        assertEquals("20 km/h is the slow-city level", 16.0, target, 1e-9)

        requestAutoZoom(target)

        assertEquals(
            "the target must not land in one frame while a magnification is displayed",
            13.0, renderer.viewportState.value.zoomFraction, 1e-9
        )
        val steps = walkToCompletion()
        assertTrue("the 3-level difference is walked (got ${steps.size})", steps.size >= 12)
        assertEquals("and it ends exactly on the target", 16.0, steps.last(), 0.0)
    }

    @Test
    fun followLoopKeepsTickingWhileTheWalkPlays() {
        // spec: auto-speed-zoom — Entry transition is bounded in time and render requests:
        // "the follow display loop SHALL keep running while the transition plays". The walk owns
        // its own loop, so a pending walk must neither stall the follow tick nor be stalled by
        // it.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 1080, 600)
        val now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        renderer.reCenter()
        requestAutoZoom(13.0, walkZoom = false)
        renderer.renderFrame()
        requestAutoZoom(17.0)

        renderer.advanceZoomWalk()
        renderer.renderFrame()
        renderer.reengageFollow()
        val walkMag = renderer.viewportState.value.zoomFraction
        val (beforeLat, beforeLon) = renderer.markerPosition()

        // The follow loop ticks while the transition is pending.
        renderer.extrapolationTick(now + 200, 0.2)

        val (afterLat, afterLon) = renderer.markerPosition()
        assertTrue(
            "the follow display keeps gliding during the transition",
            afterLat != beforeLat || afterLon != beforeLon
        )
        assertEquals(
            "the follow tick commits no zoom step",
            walkMag, renderer.viewportState.value.zoomFraction, 1e-9
        )
        assertNotNull(
            "and the walk continues — it is not paced by the follow loop",
            renderer.advanceZoomWalk()
        )
    }

    @Test
    fun walkEndsOnAnExactTargetRender() {
        // The transition ends on a full native render at the EXACT requested value, not on an
        // accumulated arithmetic sum (spec: auto-speed-zoom — Entering free driving from a
        // browse magnification: "SHALL end on a frame rendered at exactly 17.0").
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)
        val rendersBefore = renderer.fullRenderCount

        val steps = walkToCompletion()

        assertEquals("the last step lands on the requested magnitude", 17.0, steps.last(), 0.0)
        assertEquals("and it is the committed one", 17.0, renderer.viewportState.value.zoomFraction, 0.0)
        assertEquals(
            "every step, the target included, was rendered",
            rendersBefore + steps.size, renderer.fullRenderCount
        )
    }

    @Test
    fun walkIsRenderSynchronous() {
        // A step is committed only once the previous step's frame has LANDED: otherwise the
        // walk outruns the renders and the committed value leads the displayed frame by more
        // than the blit window, which snaps the display.
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)

        assertNotNull("first step", renderer.advanceZoomWalk())
        assertNull("no second step before the frame landed", renderer.advanceZoomWalk())
        renderer.renderFrame()
        assertNotNull("the walk continues once the frame landed", renderer.advanceZoomWalk())
    }

    @Test
    fun walkWithNoDisplayedFrameLandsDirectlyInsteadOfStepping() {
        // Cold start (no frame on the surface): there is nothing to transition from, so the
        // request lands directly — the spec's "jumps directly to the target instead of
        // smoothing from the default map zoom".
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 1080, 600)

        requestAutoZoom(17.0)

        assertEquals("no frame -> the request lands directly", 17.0, renderer.advanceZoomWalk() ?: Double.NaN, 0.0)
        assertEquals(17.0, renderer.viewportState.value.zoomFraction, 0.0)
    }

    @Test
    fun noRenderRequestAfterSurfaceLoss() {
        // The walk advances only while the surface is usable (design D2): with the surface gone
        // the loop must neither lock it, nor commit a step, nor request a render — a lost surface
        // does not get to snap the map to the target behind the driver's back. (The overrun buffer
        // is dropped with the surface, so the walk's cold-start branch would land the far target
        // in ONE step if the gate were missing.)
        //
        // Deterministic by construction: the loops are OFF while the frame and the pending walk
        // are set up — no render is in flight, no step can be taken — and are switched ON only
        // after the surface is gone, which is exactly the window under test.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 1080, 600)
        val now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        renderer.reCenter()
        requestAutoZoom(13.0, walkZoom = false)
        renderer.renderFrame() // one frame on the surface at 13.0
        requestAutoZoom(19.0)
        assertEquals(
            "the far request starts a walk, it does not land in one frame",
            13.0, renderer.viewportState.value.zoomFraction, 1e-9
        )

        renderer.onSurfaceDestroyed()
        val renders = renderer.fullRenderCount
        renderer.asyncLoopsEnabled = true // the walk loop now runs with the surface gone
        // Give the loop a chance to act: await its turns instead of pausing on the wall clock
        // (spec `unit-test-suite-runtime` — Awaiting state, not a deadline).
        val turns = renderer.zoomWalkLoopTurns
        runBlocking {
            withTimeoutOrNull(2_000) {
                while (renderer.zoomWalkLoopTurns < turns + 3) yield()
            }
        }

        assertEquals(
            "the walk must not land the target without a surface",
            13.0, renderer.viewportState.value.zoomFraction, 1e-9
        )
        assertEquals("and no render is requested", renders, renderer.fullRenderCount)
        // This is the one walk case that runs with the background loops ENABLED: shut the
        // renderer down so its three loops do not outlive the test (the unit-test fork has a
        // small heap).
        renderer.shutdown()
    }

    @Test
    fun walkCompletesWhileParked() {
        // The extrapolation loop is movement-gated; the walk is not, otherwise entering free
        // driving at standstill would keep the old magnification until the vehicle moves.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 1080, 600)
        val now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 0.0, timeMs = now)
        renderer.reCenter()
        requestAutoZoom(13.0, walkZoom = false)
        renderer.renderFrame()

        assertFalse(
            "the extrapolation loop is closed while the vehicle stands",
            renderer.extrapolationGateActive(now + 1000)
        )

        requestAutoZoom(17.0)
        val steps = walkToCompletion()
        assertTrue("the walk runs with the follow loop closed", steps.isNotEmpty())
        assertEquals(17.0, renderer.viewportState.value.zoomFraction, 0.0)
    }

    @Test
    fun walkKeepsTheVehicleOnTheAnchor() {
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)
        renderer.reengageFollow()

        repeat(3) {
            renderer.advanceZoomWalk()
            renderer.renderFrame()
        }

        val resolved = renderer.resolvedFollowAnchor()
        val (mx, my) = renderer.markerScreenPosition(1080, 600)
        assertEquals("marker x during the walk", resolved.fx * 1080, mx, 2.5)
        assertEquals("marker y during the walk", resolved.fy * 600, my, 2.5)
    }

    @Test
    fun walkRetargetsWithoutPassingTheRequestedMagnification() {
        // Mid-transition requests (the auto-zoom convergence steps down while the display is
        // still walking up) must be taken from the current value and never passed.
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)
        repeat(2) {
            renderer.advanceZoomWalk()
            renderer.renderFrame()
        }
        assertTrue(
            "the upward transition is in progress before the re-target",
            renderer.viewportState.value.zoomFraction < 17.0
        )

        requestAutoZoom(16.5)
        val rest = walkToCompletion()

        assertTrue(
            "the sequence must never pass the newest request",
            rest.all { it <= 16.5 + 1e-9 }
        )
        assertEquals("and it ends exactly on it", 16.5, renderer.viewportState.value.zoomFraction, 0.0)
    }

    @Test
    fun smallZoomDeltaNeedsNoExtraRender() {
        rendererWithFrame(15.0)
        val renders = renderer.fullRenderCount

        requestAutoZoom(15.2)

        assertEquals(
            "a delta inside the blit window commits directly",
            15.2, renderer.viewportState.value.zoomFraction, 1e-9
        )
        assertNull("and starts no walk", renderer.advanceZoomWalk())
        assertEquals(
            "and the transition initiates no full native render of its own",
            renders, renderer.fullRenderCount
        )
        renderer.renderFrame()
        assertEquals("the single commit frame is the only render", renders + 1, renderer.fullRenderCount)
    }

    @Test
    fun identicalRecommitKeepsTheWalk() {
        // The car screens re-commit the fraction they read back from the viewport state on
        // every fix; a fix with no new zoom target must not cancel a pending walk.
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)
        val committed = renderer.viewportState.value.zoomFraction

        requestAutoZoom(committed)

        assertNotNull("a re-commit must not cancel the walk", renderer.advanceZoomWalk())
    }

    @Test
    fun nearZoomRequestSupersedesTheWalk() {
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)

        requestAutoZoom(13.2, walkZoom = false)

        assertEquals("a near request commits directly", 13.2, renderer.viewportState.value.zoomFraction, 1e-9)
        assertNull("and clears the walk target", renderer.advanceZoomWalk())
    }

    @Test
    fun shutdownClearsTheWalk() {
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)

        renderer.shutdown()
        val renders = renderer.fullRenderCount

        assertNull(renderer.advanceZoomWalk())
        assertEquals("no render is requested after shutdown", renders, renderer.fullRenderCount)
    }
}
