package com.naviveylin.auto

import com.framstag.libosmscout.client.FakeAutoRenderClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        handler = MapPanHandler(renderer, autoZoom) { 1920 to 1080 }
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

    // ── shouldCommitViewport (spec: auto/map-pan — follow suspended while
    // panned; design D1 — shared by both screens) ──

    @Test
    fun panningSuppressesViewportCommit() {
        // The band-crossing case: a zoom the auto-zoom controller would
        // return while panned (it re-engages on a speed-band change) must
        // never reach the commit block — that is the routing-mode jump bug.
        assertFalse(shouldCommitViewport(panning = true, angle = -1.0, newZoom = 8))
        assertFalse(shouldCommitViewport(panning = true, angle = null, newZoom = null))
    }

    @Test
    fun notPanningCommitsOnHeadingOrZoomChange() {
        assertTrue(shouldCommitViewport(panning = false, angle = -1.0, newZoom = null))
        assertTrue(shouldCommitViewport(panning = false, angle = null, newZoom = 8))
        assertFalse(shouldCommitViewport(panning = false, angle = null, newZoom = null))
    }
}
