package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Visible-area anchor resolution (spec: smooth-follow — visible-area scenarios;
 * change `anchor-per-surface-visible-area`): a preset fraction is mapped into the
 * part of the surface the app's own overlays do not cover, so an outer preset
 * (bottom-center during phone navigation) stays visible above the routing-status
 * card instead of behind it.
 *
 * Plain JUnit: pure math, no Android/JNI dependencies.
 */
class AnchorVisibleAreaTest {

    private val screenW = 1080
    private val screenH = 2400

    /** Typical phone navigation coverage: turn card 0.19H, routing status 0.18H, widget column 0.07W. */
    private val navTop = (screenH * 0.19).toInt()
    private val navBottom = (screenH * 0.18).toInt()
    private val navRight = (screenW * 0.07).toInt()

    private fun resolve(
        anchor: VehicleAnchorPosition,
        left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0
    ) = resolveAnchorFraction(anchor, left, top, right, bottom, screenW, screenH)

    @Test
    fun `no measured overlay resolves to the preset itself`() {
        VehicleAnchorPosition.entries.forEach { anchor ->
            val resolved = resolve(anchor)
            assertEquals("preset ${anchor.id} fx", anchor.fx, resolved.fx, 1e-12)
            assertEquals("preset ${anchor.id} fy", anchor.fy, resolved.fy, 1e-12)
        }
    }

    @Test
    fun `zero insets on one axis leave that axis untouched`() {
        val resolved = resolve(VehicleAnchorPosition.BOTTOM_LEFT, bottom = 400)
        assertEquals(VehicleAnchorPosition.BOTTOM_LEFT.fx, resolved.fx, 1e-12)
        assertTrue("bottom preset must move up with a bottom inset", resolved.fy < VehicleAnchorPosition.BOTTOM_LEFT.fy)
    }

    @Test
    fun `bottom anchor resolves above the routing status card`() {
        val resolved = resolve(VehicleAnchorPosition.BOTTOM_CENTER, top = navTop, bottom = navBottom)
        // The resolved anchor sits inside the visible band: below the turn card, above
        // the status card, and inside the overrun band (0.1..0.9).
        val visibleBottom = 1.0 - navBottom.toDouble() / screenH
        assertTrue(
            "resolved fy ${resolved.fy} must stay above the status card ($visibleBottom)",
            resolved.fy < visibleBottom
        )
        assertTrue("resolved fy ${resolved.fy} must stay below the turn card", resolved.fy > navTop.toDouble() / screenH)
        assertEquals(0.5, resolved.fx, 1e-12)
    }

    @Test
    fun `top anchor resolves below the turn card`() {
        val resolved = resolve(VehicleAnchorPosition.TOP_CENTER, top = navTop, bottom = navBottom)
        assertTrue(
            "resolved fy ${resolved.fy} must stay below the turn card",
            resolved.fy > navTop.toDouble() / screenH
        )
    }

    @Test
    fun `right anchor resolves clear of the widget column`() {
        val resolved = resolve(VehicleAnchorPosition.MIDDLE_FAR_RIGHT, right = navRight)
        val visibleRight = 1.0 - navRight.toDouble() / screenW
        assertTrue(
            "resolved fx ${resolved.fx} must stay left of the widget column ($visibleRight)",
            resolved.fx < visibleRight + 1e-9
        )
    }

    @Test
    fun `host pane clamp keeps the default and moves only the covered presets`() {
        // Android Auto semantics: the host panel is a forbidden band, not a remap —
        // the default preset must keep the surface center (the AA "default reproduces
        // today's framing" contract).
        val center = clampAnchorOutOfPane(VehicleAnchorPosition.CENTER, paneLeftPx = 400, screenW = 1000, screenH = 1000)
        assertEquals(0.5, center.fx, 1e-12)
        assertEquals(0.5, center.fy, 1e-12)

        val farRight = clampAnchorOutOfPane(
            VehicleAnchorPosition.BOTTOM_RIGHT, paneLeftPx = 400, screenW = 1000, screenH = 1000
        )
        assertEquals(0.7, farRight.fx, 1e-12)
        assertEquals(0.9, farRight.fy, 1e-12)

        // A preset inside the covered band moves just inside the visible strip.
        val farLeft = clampAnchorOutOfPane(
            VehicleAnchorPosition.TOP_FAR_LEFT, paneLeftPx = 400, screenW = 1000, screenH = 1000
        )
        assertEquals(0.4 + VehicleAnchorPosition.MIN_FRACTION * 0.6, farLeft.fx, 1e-12)
        assertEquals(VehicleAnchorPosition.TOP_FAR_LEFT.fy, farLeft.fy, 1e-12)

        // RTL: the band is on the right, so the far-right preset is the clamped one.
        val rtl = clampAnchorOutOfPane(
            VehicleAnchorPosition.MIDDLE_FAR_RIGHT, paneRightPx = 400, screenW = 1000, screenH = 1000
        )
        assertEquals(1.0 - 0.4 - VehicleAnchorPosition.MIN_FRACTION * 0.6, rtl.fx, 1e-12)
        val rtlLeft = clampAnchorOutOfPane(
            VehicleAnchorPosition.MIDDLE_FAR_LEFT, paneRightPx = 400, screenW = 1000, screenH = 1000
        )
        assertEquals(0.1, rtlLeft.fx, 1e-12)

        // No pane (browse, phone surfaces without a panel) = identity for all presets.
        VehicleAnchorPosition.entries.forEach { anchor ->
            val resolved = clampAnchorOutOfPane(anchor, screenW = 1000, screenH = 1000)
            assertEquals(anchor.fx, resolved.fx, 1e-12)
            assertEquals(anchor.fy, resolved.fy, 1e-12)
        }
    }

    @Test
    fun `resolved fraction stays inside the overrun band`() {
        // Extreme insets must not push the anchor outside the band the anchor-centered
        // frame can render without uncovering a strip.
        val cases = listOf(
            Triple(0, screenH / 2, 0),
            Triple(0, 0, screenW / 2),
            Triple(screenH / 3, screenH / 2, screenW / 3)
        )
        for ((top, bottom, right) in cases) {
            VehicleAnchorPosition.entries.forEach { anchor ->
                val resolved = resolve(anchor, top = top, bottom = bottom, right = right)
                assertTrue(
                    "preset ${anchor.id} fx ${resolved.fx} outside band (top=$top bottom=$bottom right=$right)",
                    resolved.fx >= VehicleAnchorPosition.MIN_FRACTION - 1e-9 &&
                        resolved.fx <= VehicleAnchorPosition.MAX_FRACTION + 1e-9
                )
                assertTrue(
                    "preset ${anchor.id} fy ${resolved.fy} outside band",
                    resolved.fy >= VehicleAnchorPosition.MIN_FRACTION - 1e-9 &&
                        resolved.fy <= VehicleAnchorPosition.MAX_FRACTION + 1e-9
                )
            }
        }
    }

    @Test
    fun `resolution is monotonic in each inset`() {
        // A larger bottom inset moves a bottom anchor further up; a larger top inset
        // moves a top anchor further down. (Monotonicity keeps the preset ordering
        // meaningful inside the visible area.)
        val small = resolve(VehicleAnchorPosition.BOTTOM_CENTER, bottom = 100)
        val large = resolve(VehicleAnchorPosition.BOTTOM_CENTER, bottom = 500)
        assertTrue("larger bottom inset must move the anchor up", large.fy < small.fy)

        val smallTop = resolve(VehicleAnchorPosition.TOP_CENTER, top = 100)
        val largeTop = resolve(VehicleAnchorPosition.TOP_CENTER, top = 500)
        assertTrue("larger top inset must move the anchor down", largeTop.fy > smallTop.fy)

        val smallRight = resolve(VehicleAnchorPosition.MIDDLE_FAR_RIGHT, right = 50)
        val largeRight = resolve(VehicleAnchorPosition.MIDDLE_FAR_RIGHT, right = 300)
        assertTrue("larger right inset must move the anchor left", largeRight.fx < smallRight.fx)
    }

    @Test
    fun `preset ordering is preserved inside the visible area`() {
        val top = resolve(VehicleAnchorPosition.TOP_CENTER, top = navTop, bottom = navBottom)
        val middle = resolve(VehicleAnchorPosition.CENTER, top = navTop, bottom = navBottom)
        val bottom = resolve(VehicleAnchorPosition.BOTTOM_CENTER, top = navTop, bottom = navBottom)
        assertTrue(top.fy < middle.fy)
        assertTrue(middle.fy < bottom.fy)
    }

    @Test
    fun `unknown canvas size falls back to the preset fraction`() {
        val resolved = resolveAnchorFraction(
            VehicleAnchorPosition.BOTTOM_RIGHT, 0, navTop, navRight, navBottom, 0, 0
        )
        assertEquals(VehicleAnchorPosition.BOTTOM_RIGHT.fx, resolved.fx, 1e-12)
        assertEquals(VehicleAnchorPosition.BOTTOM_RIGHT.fy, resolved.fy, 1e-12)
    }

    @Test
    fun `negative insets are treated as zero`() {
        val resolved = resolve(VehicleAnchorPosition.CENTER, top = -100, bottom = -100, right = -100)
        assertEquals(0.5, resolved.fx, 1e-12)
        assertEquals(0.5, resolved.fy, 1e-12)
    }
}
