package com.naviveylin.auto

import com.framstag.libosmscout.client.FakeAutoRenderClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [MapPanHandler] (spec: auto/map-pan) — pan-mode enter/exit,
 * scroll→viewport, pinch→zoom.
 */
@RunWith(RobolectricTestRunner::class)
class MapPanHandlerTest {

    private lateinit var renderer: AutoMapRenderer
    private lateinit var autoZoom: AutoZoomController
    private lateinit var handler: MapPanHandler

    @Before
    fun setUp() {
        renderer = AutoMapRenderer(FakeAutoRenderClient(), initialProjectionDpi = 240.0)
        autoZoom = AutoZoomController()
        handler = MapPanHandler({ renderer }, autoZoom) { 1920 to 1080 }
    }

    @Test
    fun panModeEnterDisengagesFollowAndSuspendsAutoZoom() {
        renderer.reCenter()
        assertTrue(renderer.isFollowMode())

        handler.onPanModeChanged(true)

        assertTrue(handler.panning)
        assertFalse(renderer.isFollowMode())
        assertTrue(autoZoom.isSuspended())
    }

    @Test
    fun panModeExitReengagesFollow() {
        handler.onPanModeChanged(true)
        assertFalse(renderer.isFollowMode())

        handler.onPanModeChanged(false)

        assertFalse(handler.panning)
        assertTrue(renderer.isFollowMode())
    }

    @Test
    fun scrollMovesViewportCenter() {
        handler.onPanModeChanged(true)
        val before = renderer.viewportState.value

        handler.onScroll(100f, 0f)

        val after = renderer.viewportState.value
        assertTrue("center must move", after.lat != before.lat || after.lon != before.lon)
    }

    @Test
    fun scrollIgnoredWhenNotPanning() {
        val before = renderer.viewportState.value

        handler.onScroll(100f, 0f)

        val after = renderer.viewportState.value
        assertEquals(before.lat, after.lat, 0.0)
        assertEquals(before.lon, after.lon, 0.0)
    }

    @Test
    fun pinchZoomsAroundFocus() {
        handler.onPanModeChanged(true)
        val before = renderer.viewportState.value

        handler.onScale(960f, 540f, 2f)

        val after = renderer.viewportState.value
        assertTrue("zoom must increase", after.zoom > before.zoom)
    }

    @Test
    fun jitterIgnored() {
        handler.onPanModeChanged(true)
        val before = renderer.viewportState.value

        handler.onScale(960f, 540f, 1.005f)

        val after = renderer.viewportState.value
        assertEquals(before.zoom, after.zoom)
    }

    @Test
    fun scaleIgnoredWhenNotPanning() {
        val before = renderer.viewportState.value

        handler.onScale(960f, 540f, 2f)

        val after = renderer.viewportState.value
        assertEquals(before.zoom, after.zoom)
    }

    // ── shouldCommitViewport / resolveCommittedAngle (spec: auto/map-pan —
    // follow suspended while panned; design D1 — shared by both screens;
    // spec: auto-smooth-follow — Heading commit deadband; design D2) ──

    @Test
    fun panningSuppressesViewportCommit() {
        // The band-crossing case: a zoom the auto-zoom controller would
        // return while panned (it re-engages on a speed-band change) must
        // never reach the commit block — that is the routing-mode jump bug.
        assertFalse(shouldCommitViewport(panning = true, angle = -1.0, committedAngle = -1.0, newZoom = 8.0))
        assertFalse(shouldCommitViewport(panning = true, angle = null, committedAngle = null, newZoom = null))
        // The deadband gate is pan-blind in the same way: heading-up is always
        // computed, so without the gate every fix would re-engage mid-pan.
        assertNull(resolveCommittedAngle(panning = true, heading = -1.0, committedAngle = -1.0))
    }

    @Test
    fun notPanningCommitsOnHeadingOrZoomChange() {
        // A heading far beyond the deadband (1 rad vs 0 committed) commits;
        // zoom changes commit regardless of the heading.
        assertTrue(shouldCommitViewport(panning = false, angle = -1.0, committedAngle = 0.0, newZoom = null))
        assertTrue(shouldCommitViewport(panning = false, angle = null, committedAngle = 0.0, newZoom = 8.0))
        assertFalse(shouldCommitViewport(panning = false, angle = null, committedAngle = 0.0, newZoom = null))
    }

    // ── Heading commit deadband (design D2; task 2.1/2.3) ──

    @Test
    fun subDeadbandHeadingChangeDoesNotCommitRotation() {
        // 0.4° < 1.5° deadband: the rotation must NOT be re-committed (the
        // screen then passes `commitAngle ?: vp.angle` = the committed angle,
        // so the fix is a centre/zoom-only change the overrun blit serves).
        val committed = -Math.toRadians(90.0)
        val subDeadband = committed + Math.toRadians(0.4)
        assertNull(resolveCommittedAngle(panning = false, heading = subDeadband, committedAngle = committed))
        // Same just below the threshold on both sides of the wrap (179.7° → −180.3°):
        val nearWrap = Math.toRadians(179.7)
        assertNull(resolveCommittedAngle(panning = false, heading = -nearWrap, committedAngle = Math.toRadians(180.0)))
    }

    @Test
    fun headingChangeBeyondTheDeadbandCommitsRotation() {
        val committed = -Math.toRadians(90.0)
        // 2° > 1.5° deadband: the rotation is re-committed with the fix.
        val beyond = committed - Math.toRadians(2.0)
        assertEquals(beyond, resolveCommittedAngle(panning = false, heading = beyond, committedAngle = committed))
        // First rotation with no committed angle commits as today.
        assertEquals(beyond, resolveCommittedAngle(panning = false, heading = beyond, committedAngle = null))
        // The ±180° wrap is handled: 179° off from +179° committed = +1.7° delta.
        val wrapped = resolveCommittedAngle(
            panning = false,
            heading = Math.toRadians(179.0),
            committedAngle = -Math.toRadians(179.0)
        )
        assertEquals(Math.toRadians(179.0), wrapped)
    }

    @Test
    fun headingRampLagStaysInsideTheDeadband() {
        // Heading-up semantics unchanged (task 2.3): driving a heading ramp, the
        // committed rotation trails the smoothed heading by at most the deadband,
        // and a real turn keeps re-committing so the lag never accumulates.
        var committed: Double? = null
        var heading = -Math.toRadians(0.0)
        val step = Math.toRadians(0.5) // 0.5°/fix, below the deadband
        repeat(400) {
            heading -= step // a long sweeping turn, 200° total
            val resolved = resolveCommittedAngle(panning = false, heading, committed)
            if (resolved != null) committed = resolved
            // Lag bound: |heading − committed| ≤ deadband whenever a committed
            // rotation exists.
            if (committed != null) {
                assertTrue(
                    "heading=$heading committed=$committed lag=${angleDeltaRadians(heading, committed!!)}",
                    Math.abs(angleDeltaRadians(heading, committed!!)) <= HEADING_DEADBAND_RAD + 1e-12
                )
            }
        }
        // After the turn the committed rotation is within the deadband of the heading.
        assertTrue(Math.abs(angleDeltaRadians(heading, committed!!)) <= HEADING_DEADBAND_RAD + 1e-12)
        // The map did follow the turn: committed ≈ heading for a sustained ramp
        // (the 0.5° steps commit every ~3rd fix, so the lag is a fraction of the
        // deadband, never a full 180° hold).
        assertTrue(Math.abs(angleDeltaRadians(heading, committed!!)) < HEADING_DEADBAND_RAD)
    }

    @Test
    fun subDeadbandFixIsServedByABlitInsteadOfAFullRender() {
        // Task 2.2 (gate half): a sub-deadband heading change must not force a
        // rotation commit — the caller keeps `vp.angle`, so the viewport change
        // below is centre-only (blit-able). Assert the gate never reports the
        // sub-deadband heading as a commit-able rotation and that a zoom-less
        // fix with no heading change is not a viewport change at all.
        val committed = -Math.toRadians(45.0)
        assertFalse(shouldCommitViewport(panning = false, angle = committed + Math.toRadians(0.5), committedAngle = committed, newZoom = null))
        assertTrue(shouldCommitViewport(panning = false, angle = committed + Math.toRadians(2.5), committedAngle = committed, newZoom = null))
    }
}
