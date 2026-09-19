package com.naviveylin.core

import android.graphics.Bitmap
import android.graphics.Canvas
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [ManeuverSymbols] — the single source of the maneuver artwork
 * (spec: auto-navigation-hints — "Car hint content" large icon, and
 * auto/navigation-view — host-maneuver icons reuse the same arrow geometry).
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

    private fun testCanvas(): Canvas {
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        return Canvas(bitmap)
    }
}
