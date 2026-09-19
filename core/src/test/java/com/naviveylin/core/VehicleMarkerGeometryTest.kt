package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec compliance for the unified vehicle marker geometry (changes
 * `unified-vehicle-marker`; specs `gps-location-marker`,
 * `auto-map-renderer`, `cross-variant-ui-parity`).
 *
 * Parity guarantee: BOTH the phone overlay and the Android Auto renderer
 * build their platform `Path` from [VehicleMarkerGeometry.outlineVertices]
 * (rounded cap via quad, then straight edges) — this test pins the contract
 * so the two renderers cannot drift apart. Task 4.2 of the change.
 */
class VehicleMarkerGeometryTest {

    @Test
    fun sizeIs38dpDensityAwareAnchor() {
        assertEquals("Marker footprint anchor for both surfaces", 38f, VehicleMarkerGeometry.SIZE_DP, 1e-6f)
        assertTrue("casing must enlarge the core", VehicleMarkerGeometry.CASING_SCALE > 1f)
        assertTrue("rim must be visible", VehicleMarkerGeometry.RIM_WIDTH_H > 0f)
    }

    @Test
    fun outlineHasSevenVerticesForPathContract() {
        // moveTo(v0); quadTo(0,-H, v1); lineTo v2..v6; close — the exact path
        // both renderers build.
        val v = VehicleMarkerGeometry.outlineVertices()
        assertEquals(7, v.size)
    }

    @Test
    fun roundedCapIsSymmetricAndSetBackFromTip() {
        val (p1x, p1y) = VehicleMarkerGeometry.outlineVertices()[0]
        val (p2x, p2y) = VehicleMarkerGeometry.outlineVertices()[1]
        assertEquals("cap left/right symmetric", p1x, -p2x, 1e-6f)
        assertEquals("cap endpoints at same height", p1y, p2y, 1e-6f)
        assertTrue("cap sits below the tip (0,-1)", p1y > -1f)
        // Rounded cap width grows with the rounding fraction.
        assertEquals("cap x spans +-TIP_ROUND_T of body width",
            VehicleMarkerGeometry.BODY_HALF_W * VehicleMarkerGeometry.TIP_ROUND_T,
            p2x, 1e-6f)
    }

    @Test
    fun bodyTailExtentMatchesConstants() {
        val v = VehicleMarkerGeometry.outlineVertices()
        // v[2] = (BODY_HALF_W, 0); v[3] = (TAIL_HALF_W, TAIL_Y); v[4] = (0, TAIL_Y);
        assertEquals(VehicleMarkerGeometry.BODY_HALF_W, v[2].first, 1e-6f)
        assertEquals(0f, v[2].second, 1e-6f)
        assertEquals(VehicleMarkerGeometry.TAIL_HALF_W, v[3].first, 1e-6f)
        assertEquals(VehicleMarkerGeometry.TAIL_Y, v[3].second, 1e-6f)
        assertEquals(0f, v[4].first, 1e-6f)
        assertEquals(VehicleMarkerGeometry.TAIL_Y, v[4].second, 1e-6f)
        assertEquals(-VehicleMarkerGeometry.TAIL_HALF_W, v[5].first, 1e-6f)
        assertEquals(-VehicleMarkerGeometry.BODY_HALF_W, v[6].first, 1e-6f)
        assertEquals(0f, v[6].second, 1e-6f)
    }

    @Test
    fun scaledVerticesMultiplyByH() {
        val h = 16f
        val px = VehicleMarkerGeometry.outlineVerticesPx(h)
        val v = VehicleMarkerGeometry.outlineVertices()
        assertEquals(v.size, px.size)
        for (i in v.indices) {
            assertEquals(v[i].first * h, px[i].first, 1e-4f)
            assertEquals(v[i].second * h, px[i].second, 1e-4f)
        }
    }

    @Test
    fun gradientIsVerticalLightFromAbove() {
        val top = VehicleMarkerGeometry.COLOR_GRADIENT_TOP
        val bottom = VehicleMarkerGeometry.COLOR_GRADIENT_BOTTOM
        assertTrue("gradient needs two distinct stops", top != bottom)
        // Light from above: top stop is lighter (higher HSL lightness) than bottom.
        assertTrue("top must be lighter than bottom", top.lightness() > bottom.lightness())
    }

    @Test
    fun paletteConsistent() {
        assertTrue(VehicleMarkerGeometry.COLOR_CASING and 0xFF000000L == 0xFF000000L)
        assertEquals("rim doubles as the gradient's dark stop",
            VehicleMarkerGeometry.COLOR_GRADIENT_BOTTOM, VehicleMarkerGeometry.COLOR_RIM)
        assertTrue("soft shadow is translucent black",
            (VehicleMarkerGeometry.COLOR_SHADOW ushr 24) in 0x10..0x80)
    }

    @Test
    fun darkCasingIsDarkAndDarkerThanDayCasing() {
        // Review feedback: a fixed white casing shows as a stencil halo on dark
        // land; dark presentation uses deep blue-black instead (no bright ring).
        val darkCasing = VehicleMarkerGeometry.COLOR_CASING_DARK
        assertTrue("dark casing must be much darker than the white day casing",
            VehicleMarkerGeometry.COLOR_CASING.lightness() - darkCasing.lightness() > 0.5)
        assertTrue("dark casing still reads on dark land", darkCasing.lightness() < 0.15)
    }

    @Test
    fun darkCoreIsLighterThanDarkCasingAndNotWhite() {
        // User feedback (map-marker-route-contrast): on dark land the near-black
        // casing plus the dark end of the day gradient made the marker read as a
        // dark blob. The dark core is lighter — but deliberately NOT white, so it
        // cannot reintroduce the stencil halo the dark casing exists to avoid.
        val (darkTop, darkBottom) = VehicleMarkerGeometry.gradientColors(dark = true)
        val darkCasing = VehicleMarkerGeometry.COLOR_CASING_DARK
        val white = VehicleMarkerGeometry.COLOR_CASING

        assertTrue("dark core top must be markedly lighter than the dark casing",
            darkTop.lightness() - darkCasing.lightness() > 0.5)
        assertTrue("dark core bottom must still read against the dark casing",
            darkBottom.lightness() - darkCasing.lightness() > 0.3)
        assertTrue("dark core must be lighter than the day core (spec: lighter on dark land)",
            darkTop.lightness() > VehicleMarkerGeometry.COLOR_GRADIENT_TOP.lightness())
        assertTrue("dark core top must not be white",
            darkTop != white && darkTop.lightness() < 0.9)
        assertTrue("dark core bottom must not be white",
            darkBottom != white && darkBottom.lightness() < 0.9)
        assertTrue("dark gradient stays light-from-above", darkTop.lightness() > darkBottom.lightness())
    }

    @Test
    fun gradientColorsSelectsPaletteOnly() {
        val (dayTop, dayBottom) = VehicleMarkerGeometry.gradientColors(dark = false)
        val (darkTop, darkBottom) = VehicleMarkerGeometry.gradientColors(dark = true)

        assertEquals("day palette unchanged", VehicleMarkerGeometry.COLOR_GRADIENT_TOP, dayTop)
        assertEquals("day palette unchanged", VehicleMarkerGeometry.COLOR_GRADIENT_BOTTOM, dayBottom)
        assertEquals("dark palette from the dark constants", VehicleMarkerGeometry.COLOR_GRADIENT_TOP_DARK, darkTop)
        assertEquals("dark palette from the dark constants", VehicleMarkerGeometry.COLOR_GRADIENT_BOTTOM_DARK, darkBottom)
        assertTrue("presentations must differ", dayTop != darkTop && dayBottom != darkBottom)
    }

    @Test
    fun dayPaletteValuesUnchanged() {
        // This change must not touch the daylight marker appearance.
        assertEquals(0xFF42A5F5L, VehicleMarkerGeometry.COLOR_GRADIENT_TOP)
        assertEquals(0xFF0D47A1L, VehicleMarkerGeometry.COLOR_GRADIENT_BOTTOM)
        assertEquals(0xFFFFFFFFL, VehicleMarkerGeometry.COLOR_CASING)
        assertEquals(0xFF0D47A1L, VehicleMarkerGeometry.COLOR_RIM)
        assertEquals(0xFF0E1622L, VehicleMarkerGeometry.COLOR_CASING_DARK)
        assertEquals(0xFFBBDEFBL, VehicleMarkerGeometry.COLOR_GRADIENT_TOP_DARK)
        assertEquals(0xFF1E88E5L, VehicleMarkerGeometry.COLOR_GRADIENT_BOTTOM_DARK)
    }

    @Test
    fun geometryIsPaletteIndependent() {
        // Only the palette branches on presentation; the geometry does not
        // (spec: gps-location-marker, scenario "Marker geometry identical in
        // both presentations").
        assertEquals(1.16f, VehicleMarkerGeometry.CASING_SCALE, 1e-6f)
        assertEquals(0.10f, VehicleMarkerGeometry.RIM_WIDTH_H, 1e-6f)
        assertEquals(3f, VehicleMarkerGeometry.SHADOW_OFFSET_DP, 1e-6f)
        assertEquals(3f, VehicleMarkerGeometry.SHADOW_BLUR_DP, 1e-6f)
        assertEquals(0.5f, VehicleMarkerGeometry.BODY_HALF_W, 1e-6f)
        assertEquals(0.35f, VehicleMarkerGeometry.TAIL_Y, 1e-6f)
        assertEquals(0.25f, VehicleMarkerGeometry.TAIL_HALF_W, 1e-6f)
        assertEquals(0.08f, VehicleMarkerGeometry.TIP_ROUND_T, 1e-6f)
        // Same vertex list regardless of which palette is selected: the
        // builder takes no presentation argument at all.
        val h = 19f
        assertEquals(7, VehicleMarkerGeometry.outlineVerticesPx(h).size)
        assertEquals(
            VehicleMarkerGeometry.outlineVertices().map { (x, y) -> x * h to y * h },
            VehicleMarkerGeometry.outlineVerticesPx(h)
        )
    }
}

private fun Long.lightness(): Double {
    val r = ((this ushr 16) and 0xFF) / 255.0
    val g = ((this ushr 8) and 0xFF) / 255.0
    val b = (this and 0xFF) / 255.0
    return (r.coerceAtLeast(g).coerceAtLeast(b) + r.coerceAtMost(g).coerceAtMost(b)) / 2.0
}
