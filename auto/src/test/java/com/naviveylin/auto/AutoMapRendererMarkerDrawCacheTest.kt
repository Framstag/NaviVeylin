package com.naviveylin.auto

import android.graphics.Canvas
import android.view.Surface
import com.framstag.libosmscout.client.FakeAutoRenderClient
import com.naviveylin.core.GpsMarkerPalette
import com.naviveylin.core.VehicleMarkerGeometry
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The car renderer's marker draw objects are built once and reused across frames, and are
 * rebuilt only when the surface bounds, the density or the dark presentation changes
 * (spec: `auto-map-renderer` — Marker drawing allocates no per-frame objects; design D9).
 *
 * The shape and palette values are still asserted against [VehicleMarkerGeometry], so the
 * caching cannot quietly change what the marker looks like.
 */
@RunWith(RobolectricTestRunner::class)
class AutoMapRendererMarkerDrawCacheTest {

    @get:Rule
    val renderers = RendererTestRule()

    private lateinit var client: FakeAutoRenderClient

    @Before
    fun setUp() {
        client = FakeAutoRenderClient()
    }

    private fun mockSurface(): Surface = mockk<Surface>(relaxed = true).apply {
        every { lockCanvas(any()) } returns mockk<Canvas>(relaxed = true)
        every { isValid } returns true
    }

    /** Attached renderer at density 1.0 (160 dpi), loops off, both markers set. */
    private fun attachedRenderer(): AutoMapRenderer {
        val renderer = renderers.newRenderer(client, dpi = 160.0)
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(mockSurface(), 1920, 720)
        renderer.setGpsMarker(48.0, 11.0, 90.0, 5.0)
        renderer.setDestinationMarker(48.01, 11.01, "Ziel")
        return renderer
    }

    @Test
    fun consecutiveFramesReuseTheSameDrawObjects() {
        val renderer = attachedRenderer()

        renderer.renderFrame()
        val first = renderer.markerDrawObjectsForTest()
        assertNotNull("a drawn frame builds the marker objects", first)
        assertEquals(1, renderer.markerDrawRebuildsForTest())

        renderer.renderFrame()
        renderer.renderFrame()

        assertSame(
            "later frames reuse the same draw objects",
            first,
            renderer.markerDrawObjectsForTest()
        )
        assertEquals("unchanged inputs rebuild nothing", 1, renderer.markerDrawRebuildsForTest())
    }

    @Test
    fun aDarkPresentationChangeRebuildsThePalette() {
        val renderer = attachedRenderer()
        renderer.renderFrame()
        val day = renderer.markerDrawObjectsForTest()!!

        assertFalse(day.dark)
        assertEquals(
            "day casing is the standard white ring",
            VehicleMarkerGeometry.COLOR_CASING.toInt(),
            day.casingPaint.color
        )
        assertEquals(VehicleMarkerGeometry.COLOR_GRADIENT_TOP.toInt(), day.gradientTop)
        assertEquals(VehicleMarkerGeometry.COLOR_GRADIENT_BOTTOM.toInt(), day.gradientBottom)

        renderer.setDarkPresentation(true)
        renderer.renderFrame()

        val night = renderer.markerDrawObjectsForTest()!!
        assertNotSame("the palette objects are rebuilt for the new presentation", day, night)
        assertTrue(night.dark)
        assertEquals(
            "night casing is the deep blue-black ring (no white halo)",
            VehicleMarkerGeometry.COLOR_CASING_DARK.toInt(),
            night.casingPaint.color
        )
        assertEquals(VehicleMarkerGeometry.COLOR_GRADIENT_TOP_DARK.toInt(), night.gradientTop)
        assertEquals(VehicleMarkerGeometry.COLOR_GRADIENT_BOTTOM_DARK.toInt(), night.gradientBottom)
        assertEquals(2, renderer.markerDrawRebuildsForTest())

        // The new presentation then stays cached.
        renderer.renderFrame()
        assertSame(night, renderer.markerDrawObjectsForTest())
        assertEquals(2, renderer.markerDrawRebuildsForTest())
    }

    /**
     * The accuracy ring is the overlay this change exists for: it must dim with the presentation
     * (spec: gps-location-marker — "Accuracy circle follows the resolved presentation", scenario
     * "Dark presentation draws the dark ring"). Red before the shared palette exists, because both
     * presentations carried the same `0x1A2196F3` / `0x662196F3` pair.
     */
    @Test
    fun theAccuracyRingBranchesOnTheDarkPresentation() {
        val renderer = attachedRenderer()
        renderer.renderFrame()
        val day = renderer.markerDrawObjectsForTest()!!
        val dayFill = day.accuracyFillPaint.color
        val dayBorder = day.accuracyBorderPaint.color

        renderer.setDarkPresentation(true)
        renderer.renderFrame()
        val night = renderer.markerDrawObjectsForTest()!!

        assertNotEquals(
            "the accuracy ring's fill must follow the presentation",
            dayFill,
            night.accuracyFillPaint.color
        )
        assertNotEquals(
            "the accuracy ring's border must follow the presentation",
            dayBorder,
            night.accuracyBorderPaint.color
        )
    }

    /**
     * The car's ring paints read the one shared palette the phone overlay reads, so the two surfaces
     * cannot drift apart again (spec: gps-location-marker — scenario "Phone and car draw the same
     * ring"; spec `cross-variant-ui-parity`).
     */
    @Test
    fun theRingPaintsTakeTheSharedPaletteValues() {
        val renderer = attachedRenderer()
        renderer.renderFrame()
        val day = renderer.markerDrawObjectsForTest()!!
        assertEquals(
            "the daylight ring fill is the shared palette's",
            GpsMarkerPalette.accuracyFill(dark = false).toInt(),
            day.accuracyFillPaint.color
        )
        assertEquals(
            "the daylight ring border is the shared palette's",
            GpsMarkerPalette.accuracyBorder(dark = false).toInt(),
            day.accuracyBorderPaint.color
        )

        renderer.setDarkPresentation(true)
        renderer.renderFrame()
        val night = renderer.markerDrawObjectsForTest()!!
        assertEquals(
            "the dark ring fill is the shared palette's",
            GpsMarkerPalette.accuracyFill(dark = true).toInt(),
            night.accuracyFillPaint.color
        )
        assertEquals(
            "the dark ring border is the shared palette's",
            GpsMarkerPalette.accuracyBorder(dark = true).toInt(),
            night.accuracyBorderPaint.color
        )
        assertEquals(
            "the ring's stroke width stays this surface's own geometry",
            1.5f,
            night.accuracyBorderPaint.strokeWidth,
            0.001f
        )
    }

    @Test
    fun aDensityChangeRebuildsTheGeometryAtTheNewDensity() {
        val renderer = attachedRenderer()
        renderer.renderFrame()
        val atOne = renderer.markerDrawObjectsForTest()!!

        assertEquals(1.0f, atOne.density, 0.001f)
        assertEquals("half the 38 dp marker at density 1.0", 19f, atOne.hPx, 0.001f)
        assertEquals(9f, atOne.pinRadius, 0.001f)
        assertEquals(VehicleMarkerGeometry.RIM_WIDTH_H * 19f, atOne.rimPaint.strokeWidth, 0.001f)

        renderer.updateProjectionDpi(320.0)
        renderer.renderFrame()

        val atTwo = renderer.markerDrawObjectsForTest()!!
        assertNotSame("the geometry is rebuilt for the new density", atOne, atTwo)
        assertEquals(2.0f, atTwo.density, 0.001f)
        assertEquals("the marker keeps its 38 dp size", 38f, atTwo.hPx, 0.001f)
        assertEquals("pin scaled with density", 18f, atTwo.pinRadius, 0.001f)
        assertEquals(VehicleMarkerGeometry.RIM_WIDTH_H * 38f, atTwo.rimPaint.strokeWidth, 0.001f)
        assertEquals(2, renderer.markerDrawRebuildsForTest())
    }

    @Test
    fun aSurfaceBoundsChangeRebuildsTheDrawObjects() {
        val renderer = attachedRenderer()
        renderer.renderFrame()
        val beforeResize = renderer.markerDrawObjectsForTest()!!
        assertEquals(1920, beforeResize.width)
        assertEquals(720, beforeResize.height)

        renderer.onSurfaceCreated(mockSurface(), 1000, 500)
        renderer.renderFrame()

        val resized = renderer.markerDrawObjectsForTest()!!
        assertNotSame("a resize rebuilds the draw objects", beforeResize, resized)
        assertEquals(1000, resized.width)
        assertEquals(500, resized.height)
        assertEquals(2, renderer.markerDrawRebuildsForTest())
    }
}
