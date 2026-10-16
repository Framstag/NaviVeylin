package com.naviveylin.core

import android.graphics.Bitmap
import android.graphics.Canvas
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

/**
 * Tests for [ManeuverSymbols] — the single source of the maneuver artwork
 * (spec: auto-navigation-hints — "Car hint content" large icon, and
 * auto/navigation-view — host-maneuver icons reuse the same arrow geometry,
 * and roundabout-renderer — the numbered roundabout-exit glyph).
 *
 * Per AGENTS.md these stay in the default Robolectric sandbox with no `@GraphicsMode`, so the
 * canvas discards the draws: the numbered glyph's number is pinned as arithmetic
 * ([ManeuverSymbols.exitLabel], [ManeuverSymbols.fittedTextSize]) and as bitmap identity, the
 * absence of per-exit markers as the drawn-circle count, and how the digits look is the
 * on-device `pixel-check` (change `fix-roundabout-exit-number`, task 7.4).
 */
@RunWith(RobolectricTestRunner::class)
class ManeuverSymbolsTest {

    @Test
    fun allTurnTypesDrawWithoutError() {
        val canvas = testCanvas()
        for (type in TurnType.values()) {
            ManeuverSymbols.drawTurnSymbol(canvas, type, 96f, 0xFFFFFFFF.toInt(), 0f, 0f)
        }
        ManeuverSymbols.drawTurnSymbol(canvas, null, 96f, 0xFFFFFFFF.toInt(), 0f, 0f)
    }

    @Test
    fun allLaneTurnsDrawWithoutError() {
        val canvas = testCanvas()
        for (turn in LaneTurn.values()) {
            ManeuverSymbols.drawLaneSymbol(canvas, turn, 56f, 0xFFFFFFFF.toInt(), 0f, 0f)
        }
    }

    @Test
    fun bitmapRenderedForEveryTurnType() {
        for (type in TurnType.values()) {
            val bitmap = ManeuverSymbols.bitmapForTurnType(type)
            assertNotNull("maneuver bitmap for $type", bitmap)
            assertEquals("bitmap width for $type", ManeuverSymbols.BITMAP_SIZE, bitmap.width)
            assertEquals("bitmap height for $type", ManeuverSymbols.BITMAP_SIZE, bitmap.height)
        }
    }

    @Test
    fun bitmapAvailableForUnknownTurnType() {
        val bitmap = ManeuverSymbols.bitmapForTurnType(null)
        assertNotNull(bitmap)
        assertEquals(ManeuverSymbols.BITMAP_SIZE, bitmap.width)
    }

    @Test
    fun bitmapsAreCachedPerTurnType() {
        assertSame(
            ManeuverSymbols.bitmapForTurnType(TurnType.LEFT),
            ManeuverSymbols.bitmapForTurnType(TurnType.LEFT)
        )
        assertSame(
            ManeuverSymbols.bitmapForTurnType(null),
            ManeuverSymbols.bitmapForTurnType(null)
        )
    }

    // --- the numbered roundabout-exit glyph (spec: roundabout-renderer) ---
    //
    // Robolectric's canvas discards draws, so the number itself is asserted as arithmetic and
    // as bitmap identity, and how it looks is the on-device `pixel-check` (task 7.4).

    private fun roundaboutLeave(exitNumber: Int) = RouteInstruction(
        350.0, 12.0, 350.0, TurnType.ROUNDABOUT_LEAVE, "Bahnhofstraße",
        "Take exit $exitNumber onto Bahnhofstraße", "Exit $exitNumber",
        0.0, TurnType.STRAIGHT_ON, "", "",
        exitNumber, true
    )

    @Test
    fun `the roundabout exit glyph carries the exit number`() {
        assertEquals("3", ManeuverSymbols.exitLabel(3))
        // The number is not decoration on an unchanged arrow: the numbered glyph is its own
        // artwork, distinct from the plain leave arrow.
        assertNotSame(
            ManeuverSymbols.bitmapForInstruction(roundaboutLeave(0)),
            ManeuverSymbols.bitmapForInstruction(roundaboutLeave(3))
        )
    }

    @Test
    fun `the roundabout exit glyph differs per ordinal`() {
        val first = ManeuverSymbols.bitmapForInstruction(roundaboutLeave(1))
        val third = ManeuverSymbols.bitmapForInstruction(roundaboutLeave(3))
        val plain = ManeuverSymbols.bitmapForInstruction(roundaboutLeave(0))

        assertNotSame(first, third)
        assertNotSame(first, plain)
        assertNotSame(third, plain)
    }

    @Test
    fun `a two-digit exit ordinal is drawn inside the ring`() {
        assertEquals("10", ManeuverSymbols.exitLabel(10))
        // A two-digit number that overruns the ring is shrunk to fit, not clipped and not dropped:
        // 12 px of text 30 px wide inside a 10 px ring becomes 12 * 10/30 = 4 px.
        assertEquals(4f, ManeuverSymbols.fittedTextSize(12f, 30f, 10f), 0.001f)
        // A number that already fits keeps the size the glyph asked for.
        assertEquals(12f, ManeuverSymbols.fittedTextSize(12f, 8f, 10f), 0.001f)
        assertNotSame(
            ManeuverSymbols.bitmapForInstruction(roundaboutLeave(1)),
            ManeuverSymbols.bitmapForInstruction(roundaboutLeave(10))
        )
    }

    @Test
    fun `the exit ordinal symbol carries no per-exit markers`() {
        // An ordinal is "which exit you take", not "how many exits the ring has": drawing a
        // marker per exit would grow the ring's circles with the ordinal.
        val one = canvasWithCirclesDrawn(1)
        val six = canvasWithCirclesDrawn(6)

        assertEquals(
            "the numbered glyph must draw the same circles whatever the ordinal",
            Shadows.shadowOf(one).circlePaintHistoryCount,
            Shadows.shadowOf(six).circlePaintHistoryCount
        )
    }

    @Test
    fun `the exit glyph is cached per ordinal`() {
        assertSame(
            ManeuverSymbols.bitmapForInstruction(roundaboutLeave(3)),
            ManeuverSymbols.bitmapForInstruction(roundaboutLeave(3))
        )
        assertNotSame(
            ManeuverSymbols.bitmapForInstruction(roundaboutLeave(3)),
            ManeuverSymbols.bitmapForInstruction(roundaboutLeave(4))
        )
    }

    @Test
    fun `an ordinal on a non-roundabout instruction draws no number`() {
        // The ordinal is only meaningful on a roundabout exit; anywhere else it must not become
        // its own cache entry nor change the artwork.
        val plainLeft = RouteInstruction(100.0, TurnType.LEFT, "Main St", "Turn left", "Turn left")

        assertSame(
            ManeuverSymbols.bitmapForTurnType(TurnType.LEFT),
            ManeuverSymbols.bitmapForInstruction(plainLeft)
        )
    }

    private fun canvasWithCirclesDrawn(exitNumber: Int): Canvas {
        val canvas = testCanvas()
        ManeuverSymbols.drawTurnSymbol(
            canvas, TurnType.ROUNDABOUT_LEAVE, 96f, 0xFFFFFFFF.toInt(), 0f, 0f, exitNumber
        )
        return canvas
    }

    private fun testCanvas(): Canvas {
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        return Canvas(bitmap)
    }
}
