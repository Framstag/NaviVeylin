package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [StreetNameLabel] (spec: auto/free-driving — "Current street name
 * shown"; "No street name when unnamed"; auto/navigation-view — "Street name
 * not covered by host ETA card"). Pure Canvas geometry + drawing.
 */
@RunWith(RobolectricTestRunner::class)
class StreetNameLabelTest {

    private val density = 2.0f

    @Test
    fun blankNameDrawsNothing() {
        // Spec: "No street name when unnamed" — no stale label.
        assertNull(StreetNameLabel.geometry(Rect(), Rect(), 1920, 1080, density, ""))
        assertNull(StreetNameLabel.geometry(Rect(), Rect(), 1920, 1080, density, "   "))
    }

    @Test
    fun namedStreetCenteredAtBottom() {
        val rect = StreetNameLabel.geometry(Rect(), Rect(), 1920, 1080, density, "Hauptstraße")!!
        // Horizontally centered on the surface.
        assertEquals(1920f / 2f, rect.exactCenterX(), 1f)
        // Bottom-anchored: sits above the surface bottom margin.
        assertTrue(rect.bottom <= 1080)
        assertTrue(rect.bottom >= 1080 - 2 * 16 * density)
    }

    @Test
    fun labelAnchorsToStableAreaBottomWhenKnown() {
        val stable = Rect(320, 240, 1600, 900)
        val rect = StreetNameLabel.geometry(stable, Rect(), 1920, 1080, density, "Musterweg")!!
        assertTrue(rect.bottom <= stable.bottom)
        // Still centered horizontally (stable area horizontal span).
        assertEquals(1920f / 2f, rect.exactCenterX(), 1f)
    }

    @Test
    fun labelUsesVisibleAreaBottomWhenStableUnknown() {
        // Spec: "Street name stays clear when host geometry is unknown" — a
        // missing stable area must not push the label to the raw surface
        // bottom; the visible area bottom is used instead.
        val visible = Rect(0, 0, 1920, 900)
        val rect = StreetNameLabel.geometry(Rect(), visible, 1920, 1080, density, "Musterweg")!!
        assertTrue("label must sit above the visible area bottom", rect.bottom <= visible.bottom)
    }

    @Test
    fun labelUsesMinOfStableAndVisibleBottoms() {
        // The more conservative (higher) bottom wins: visible area excludes
        // the ETA card while shown, so its bottom is above the stable one.
        val stable = Rect(0, 0, 1920, 1000)
        val visible = Rect(0, 0, 1920, 800)
        val rect = StreetNameLabel.geometry(stable, visible, 1920, 1080, density, "Musterweg")!!
        assertTrue("label must use the smaller (higher) bottom", rect.bottom <= visible.bottom)
    }

    @Test
    fun labelFallsBackToSurfaceBottomWhenBothUnknown() {
        val rect = StreetNameLabel.geometry(Rect(), Rect(), 1920, 1080, density, "Musterweg")!!
        assertTrue(rect.bottom <= 1080)
        assertTrue(rect.bottom >= 1080 - 2 * 16 * density)
    }

    @Test
    fun reserveLiftsLabelAboveResolvedBottomEdge() {
        // Navigation reserve: the label sits above the resolved bottom edge
        // minus the reserve, so a wrong host area cannot push it under the
        // ETA card.
        val stable = Rect(0, 0, 1920, 900)
        val reserve = 24f
        val rect = StreetNameLabel.geometry(stable, Rect(), 1920, 1080, density, "Musterweg", reserve)!!
        assertTrue(rect.bottom <= 900 - reserve * density)
    }

    // ── isMapLabelSafe (design D5: map label vs host ETA card trip text) ──

    @Test
    fun mapLabelUnsafeWhenBothAreasUnknown() {
        // No host geometry at all: the ETA card may cover the bottom — the
        // street name must go into the host ETA card instead.
        assertFalse(StreetNameLabel.isMapLabelSafe(Rect(), Rect(), 1080, density))
    }

    @Test
    fun mapLabelUnsafeWhenBothAreasFullSurface() {
        // The user's head unit: both areas delivered as the full surface, so
        // the ETA card is not excluded — the map label would sit under it.
        val full = Rect(0, 0, 1920, 1080)
        assertFalse(StreetNameLabel.isMapLabelSafe(full, full, 1080, density))
    }

    @Test
    fun mapLabelSafeWhenVisibleAreaClearsBottom() {
        // A correct host excludes the ETA card from the visible area — the
        // map label is safe even when the stable area is the full surface.
        val full = Rect(0, 0, 1920, 1080)
        val visible = Rect(0, 0, 1920, 900)
        assertTrue(StreetNameLabel.isMapLabelSafe(full, visible, 1080, density))
    }

    @Test
    fun mapLabelSafeWhenStableAreaClearsBottom() {
        // The stable area excludes the card "as if always present" — safe
        // even when the visible area is unknown.
        val stable = Rect(0, 0, 1920, 900)
        assertTrue(StreetNameLabel.isMapLabelSafe(stable, Rect(), 1080, density))
    }

    @Test
    fun mapLabelUnsafeWhenClearanceBelowSafetyMargin() {
        // A delivered area that clears the bottom by less than the safety
        // margin is treated as unsafe: the ETA card may still cover the label.
        val almostFull = Rect(0, 0, 1920, 1080 - 20 * density.toInt())
        assertFalse(StreetNameLabel.isMapLabelSafe(almostFull, Rect(), 1080, density))
    }

    @Test
    fun widthFitsTextAndIsCapped() {
        val short = StreetNameLabel.geometry(Rect(), Rect(), 1920, 1080, density, "A")
        val long = StreetNameLabel.geometry(Rect(), Rect(), 1920, 1080, density, "A".repeat(500))
        assertTrue(short!!.width() < long!!.width())
        assertTrue(long!!.width() <= (360f * density).toInt())
    }

    @Test
    fun longNameIsEllipsizedToPillWidth() {
        // Design D2: the text must not overflow the pill into the ETA card
        // zone — a long name is truncated with an ellipsis. Robolectric's
        // Paint.measureText returns the character count, so the name must
        // exceed the pill's inner width (~656 px at density 2) in length.
        val longName = "A".repeat(2000)
        val rect = StreetNameLabel.geometry(Rect(), Rect(), 1920, 1080, density, longName)!!
        val text = StreetNameLabel.ellipsizedText(longName, rect.width(), density)
        assertTrue("long name must be truncated", text.length < longName.length)
        assertTrue("truncated text must end with an ellipsis", text.endsWith("\u2026"))
    }

    @Test
    fun shortNameNotEllipsized() {
        val rect = StreetNameLabel.geometry(Rect(), Rect(), 1920, 1080, density, "Hauptstraße")!!
        val text = StreetNameLabel.ellipsizedText("Hauptstraße", rect.width(), density)
        assertEquals("Hauptstraße", text)
    }

    @Test
    fun drawDoesNotFailWithAndWithoutName() {
        val canvas = Canvas(Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888))
        StreetNameLabel.draw(canvas, 1920, 1080, density, Rect(), Rect(), "Hauptstraße")
        StreetNameLabel.draw(canvas, 1920, 1080, density, Rect(), Rect(), "") // no-op, must not throw
    }
}
