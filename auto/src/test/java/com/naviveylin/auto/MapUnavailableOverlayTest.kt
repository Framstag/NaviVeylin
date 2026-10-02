package com.naviveylin.auto

import android.graphics.Canvas
import android.graphics.Paint
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The app-drawn car notice for a map that cannot be drawn (spec: car-host-fault-isolation — A
 * repeatedly faulting car renderer recovers, then degrades visibly; design D5).
 *
 * The navigation and free-driving templates are `NavigationTemplate`s without a content slot, so
 * this overlay is the only way those screens can state the condition. It runs once per frame while
 * the state holds, hence the cached paints (spec `auto-map-renderer` — Marker drawing allocates no
 * per-frame objects).
 */
@RunWith(RobolectricTestRunner::class)
class MapUnavailableOverlayTest {

    @Before
    fun setUp() {
        MapUnavailableOverlay.resetForTest()
    }

    private fun canvasMock(): Canvas = mockk(relaxed = true)

    @Test
    fun theNoticeTextIsDrawnCenteredOnTheSurface() {
        val canvas = canvasMock()
        val y = slot<Float>()

        MapUnavailableOverlay.draw(canvas, 1000, 600, density = 2f, text = "Karte nicht verfügbar")

        verify(exactly = 1) { canvas.drawText("Karte nicht verfügbar", any(), capture(y), any()) }
        // Centered vertically, plus the baseline correction of a third of the text size.
        assertTrue("drawn around the surface center, was $y", y.captured > 300f && y.captured < 320f)
    }

    @Test
    fun anEmptyTextOrAnEmptySurfaceDrawsNothing() {
        val canvas = canvasMock()

        MapUnavailableOverlay.draw(canvas, 1000, 600, density = 2f, text = "")
        MapUnavailableOverlay.draw(canvas, 0, 0, density = 2f, text = "Map unavailable")

        verify(exactly = 0) { canvas.drawText(any(), any(), any(), any()) }
    }

    @Test
    fun theTextPaintIsReusedAcrossFrames() {
        // The frame path must not allocate per frame: one Paint instance serves every draw at the
        // same density (spec `auto-map-renderer` — Marker drawing allocates no per-frame objects).
        val canvas = canvasMock()
        val paints = mutableListOf<Paint>()

        MapUnavailableOverlay.draw(canvas, 1000, 600, density = 2f, text = "Map unavailable")
        MapUnavailableOverlay.draw(canvas, 1000, 600, density = 2f, text = "Map unavailable")

        verify(exactly = 2) { canvas.drawText(any(), any(), any(), capture(paints)) }
        assertSame("the text paint is cached per density", paints[0], paints[1])
    }

    @Test
    fun aDensityChangeRebuildsTheTextPaint() {
        val canvas = canvasMock()
        val paints = mutableListOf<Paint>()

        MapUnavailableOverlay.draw(canvas, 1000, 600, density = 2f, text = "Map unavailable")
        MapUnavailableOverlay.draw(canvas, 1000, 600, density = 3f, text = "Map unavailable")

        verify(exactly = 2) { canvas.drawText(any(), any(), any(), capture(paints)) }
        assertTrue("the density is part of the cache key", paints[0] !== paints[1])
        assertEquals(3f * 14f, paints[1].textSize, 0.01f)
    }
}
