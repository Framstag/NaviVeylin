package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [StreetNameLabel] (spec: auto/free-driving — "Current street name
 * shown"; "No street name when unnamed"). Pure Canvas geometry + drawing.
 */
@RunWith(RobolectricTestRunner::class)
class StreetNameLabelTest {

    private val density = 2.0f

    @Test
    fun blankNameDrawsNothing() {
        // Spec: "No street name when unnamed" — no stale label.
        assertNull(StreetNameLabel.geometry(Rect(), 1920, 1080, density, ""))
        assertNull(StreetNameLabel.geometry(Rect(), 1920, 1080, density, "   "))
    }

    @Test
    fun namedStreetCenteredAtBottom() {
        val rect = StreetNameLabel.geometry(Rect(), 1920, 1080, density, "Hauptstraße")!!
        // Horizontally centered on the surface.
        assertEquals(1920f / 2f, rect.exactCenterX(), 1f)
        // Bottom-anchored: sits above the surface bottom margin.
        assertTrue(rect.bottom <= 1080)
        assertTrue(rect.bottom >= 1080 - 2 * 16 * density)
    }

    @Test
    fun labelAnchorsToStableAreaBottomWhenKnown() {
        val stable = Rect(320, 240, 1600, 900)
        val rect = StreetNameLabel.geometry(stable, 1920, 1080, density, "Musterweg")!!
        assertTrue(rect.bottom <= stable.bottom)
        // Still centered horizontally (stable area horizontal span).
        assertEquals(1920f / 2f, rect.exactCenterX(), 1f)
    }

    @Test
    fun widthFitsTextAndIsCapped() {
        val short = StreetNameLabel.geometry(Rect(), 1920, 1080, density, "A")
        val long = StreetNameLabel.geometry(Rect(), 1920, 1080, density, "A".repeat(500))
        assertTrue(short!!.width() < long!!.width())
        assertTrue(long!!.width() <= (520f * density).toInt())
    }

    @Test
    fun drawDoesNotFailWithAndWithoutName() {
        val canvas = Canvas(Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888))
        StreetNameLabel.draw(canvas, 1920, 1080, density, Rect(), "Hauptstraße")
        StreetNameLabel.draw(canvas, 1920, 1080, density, Rect(), "") // no-op, must not throw
    }
}
