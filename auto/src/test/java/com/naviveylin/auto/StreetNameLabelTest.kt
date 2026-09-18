package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import com.naviveylin.core.VehicleAnchorPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [StreetNameLabel] (spec: auto/free-driving — "Current street name
 * shown"; auto/browse — "Current street name shown while browsing"; "No
 * street name when unnamed"). Pure Canvas geometry + drawing.
 *
 * Row rule (street-name-host-views design D3/D4): the label anchors to the
 * REAL surface edges — a bottom-row anchor preset (`fy == 0.9`) puts the
 * label at the top edge, every other row at the bottom edge; the geometry
 * never depends on host stable/visible-area rects.
 */
@RunWith(RobolectricTestRunner::class)
class StreetNameLabelTest {

    private val density = 2.0f

    @Test
    fun blankNameDrawsNothing() {
        // Spec: "No street name when unnamed" — no stale label.
        assertNull(StreetNameLabel.geometry(1920, 1080, density, ""))
        assertNull(StreetNameLabel.geometry(1920, 1080, density, "   "))
    }

    @Test
    fun placementIsTopForWholeBottomRowElseBottom() {
        // Row rule (design D3): every bottom-row preset (fy == 0.9) moves the
        // label to the top; top row and middle row keep the bottom placement.
        VehicleAnchorPosition.entries.forEach { anchor ->
            val expected = if (anchor.fy == 0.9) {
                StreetNameLabel.Placement.TOP
            } else {
                StreetNameLabel.Placement.BOTTOM
            }
            assertEquals("$anchor must map to $expected", expected, StreetNameLabel.placementFor(anchor))
        }
    }

    @Test
    fun bottomPlacementSitsAtRealSurfaceBottomEdge() {
        val rect = StreetNameLabel.geometry(1920, 1080, density, "Hauptstraße")!!
        // Horizontally centered on the surface.
        assertEquals(1920f / 2f, rect.exactCenterX(), 1f)
        // Real-edge anchored: bottom = surfaceBottom − 16.dp margin (32 px at
        // density 2), pill height 52.dp (104 px) — the host rects do not move it.
        assertEquals(1080 - 32, rect.bottom)
        assertEquals(1080 - 32 - 104, rect.top)
    }

    @Test
    fun topPlacementSitsAtRealSurfaceTopEdge() {
        val rect = StreetNameLabel.geometry(
            1920, 1080, density, "Musterweg",
            placement = StreetNameLabel.Placement.TOP
        )!!
        // top = 8.dp below the surface top (16 px at density 2).
        assertEquals(16, rect.top)
        assertEquals(16 + 104, rect.bottom)
        assertEquals(1920f / 2f, rect.exactCenterX(), 1f)
    }

    @Test
    fun topInsetPushesTopPlacementBelowTheBand() {
        // Design D8: with a top inset (host/system chrome over the surface
        // top, e.g. the AAOS status bar), the TOP placement anchors to the
        // band top — inset + 8.dp margin (16 px at density 2) — so the pill
        // is never hidden under the chrome band.
        val rect = StreetNameLabel.geometry(
            1920, 1080, density, "Hauptstraße",
            placement = StreetNameLabel.Placement.TOP,
            topInset = 80
        )!!
        assertEquals(80 + 16, rect.top)
        assertEquals(80 + 16 + 104, rect.bottom)
        assertEquals(1920f / 2f, rect.exactCenterX(), 1f)
    }

    @Test
    fun bottomInsetPushesBottomPlacementAboveTheBand() {
        // Design D8: with a bottom inset (host/system chrome over the surface
        // bottom, e.g. the AAOS task bar), the BOTTOM placement anchors to the
        // band bottom — surface bottom − inset − 16.dp margin (32 px).
        val rect = StreetNameLabel.geometry(
            1920, 1080, density, "Hauptstraße",
            placement = StreetNameLabel.Placement.BOTTOM,
            bottomInset = 120
        )!!
        assertEquals(1080 - 120 - 32, rect.bottom)
        assertEquals(1080 - 120 - 32 - 104, rect.top)
    }

    @Test
    fun bothInsetsKeepThePillInsideTheVisibleBand() {
        // Only the placement's own edge is used by the geometry; both insets
        // together must keep the pill inside the guaranteed-visible band.
        val top = StreetNameLabel.geometry(
            1920, 1080, density, "Hauptstraße",
            placement = StreetNameLabel.Placement.TOP,
            topInset = 80, bottomInset = 120
        )!!
        assertTrue("top-placed pill must sit below the top inset", top.top >= 80)
        val bottom = StreetNameLabel.geometry(
            1920, 1080, density, "Hauptstraße",
            placement = StreetNameLabel.Placement.BOTTOM,
            topInset = 80, bottomInset = 120
        )!!
        assertTrue("bottom-placed pill must sit above the bottom inset", bottom.bottom <= 1080 - 120)
    }

    @Test
    fun zeroInsetsMatchTheRawEdgeGeometry() {
        // Regression (design D8): no chrome insets -> the band IS the surface,
        // so the geometry must be byte-identical to the pre-band real-edge
        // anchoring (hosts without chrome keep today's behavior).
        val bandedTop = StreetNameLabel.geometry(
            1920, 1080, density, "Hauptstraße",
            placement = StreetNameLabel.Placement.TOP, topInset = 0, bottomInset = 0
        )!!
        val rawTop = StreetNameLabel.geometry(
            1920, 1080, density, "Hauptstraße",
            placement = StreetNameLabel.Placement.TOP
        )!!
        assertEquals(rawTop, bandedTop)
        val bandedBottom = StreetNameLabel.geometry(
            1920, 1080, density, "Hauptstraße",
            placement = StreetNameLabel.Placement.BOTTOM, topInset = 0, bottomInset = 0
        )!!
        val rawBottom = StreetNameLabel.geometry(
            1920, 1080, density, "Hauptstraße",
            placement = StreetNameLabel.Placement.BOTTOM
        )!!
        assertEquals(rawBottom, bandedBottom)
    }

    @Test
    fun widthFitsTextAndIsCapped() {
        val short = StreetNameLabel.geometry(1920, 1080, density, "A")
        val long = StreetNameLabel.geometry(1920, 1080, density, "A".repeat(500))
        assertTrue(short!!.width() < long!!.width())
        assertTrue(long!!.width() <= (360f * density).toInt())
    }

    @Test
    fun longNameIsEllipsizedToPillWidth() {
        // The text must not overflow the pill — a long name is truncated with
        // an ellipsis. Robolectric's Paint.measureText returns the character
        // count, so the name must exceed the pill's inner width (~656 px at
        // density 2) in length.
        val longName = "A".repeat(2000)
        val rect = StreetNameLabel.geometry(1920, 1080, density, longName)!!
        val text = StreetNameLabel.ellipsizedText(longName, rect.width(), density)
        assertTrue("long name must be truncated", text.length < longName.length)
        assertTrue("truncated text must end with an ellipsis", text.endsWith("\u2026"))
    }

    @Test
    fun shortNameNotEllipsized() {
        val rect = StreetNameLabel.geometry(1920, 1080, density, "Hauptstraße")!!
        val text = StreetNameLabel.ellipsizedText("Hauptstraße", rect.width(), density)
        assertEquals("Hauptstraße", text)
    }

    @Test
    fun drawDoesNotFailWithAndWithoutName() {
        val canvas = Canvas(Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888))
        StreetNameLabel.draw(canvas, 1920, 1080, density, "Hauptstraße")
        StreetNameLabel.draw(canvas, 1920, 1080, density, "Hauptstraße", StreetNameLabel.Placement.TOP)
        StreetNameLabel.draw(canvas, 1920, 1080, density, "") // no-op, must not throw
    }
}
