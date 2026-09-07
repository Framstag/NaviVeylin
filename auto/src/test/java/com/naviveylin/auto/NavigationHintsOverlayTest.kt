package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.distanceUsesKilometers
import com.naviveylin.core.formatDistanceNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Tests for [NavigationHintsOverlay] symbol rendering (spec:
 * auto/navigation-view — host-maneuver icons reuse the same arrow geometry).
 * The surface hint panel was removed when the host instruction panel took
 * over (see `auto-map-layout` delta).
 */
@RunWith(RobolectricTestRunner::class)
class NavigationHintsOverlayTest {

    @Test
    fun formatDistanceNumberHandlesMetersAndKilometers() {
        assertEquals("350", formatDistanceNumber(350.0, Locale.US))
        assertEquals("1.2", formatDistanceNumber(1200.0, Locale.US))
        assertEquals("0", formatDistanceNumber(0.0, Locale.US))
    }

    @Test
    fun formatDistanceNumberRoundsForDisplay() {
        assertEquals("45", formatDistanceNumber(45.0, Locale.US))
        assertEquals("150", formatDistanceNumber(137.0, Locale.US))
        assertEquals("100", formatDistanceNumber(124.0, Locale.US))
        assertEquals("1.4", formatDistanceNumber(1350.0, Locale.US))
        assertEquals("1.2", formatDistanceNumber(1234.0, Locale.US))
    }

    @Test
    fun formatDistanceNumberUsesLocaleDecimalSeparator() {
        assertEquals("1,2", formatDistanceNumber(1200.0, Locale.GERMANY))
    }

    @Test
    fun distanceUsesKilometersReflectsUnit() {
        assertFalse(distanceUsesKilometers(350.0))
        assertTrue(distanceUsesKilometers(1200.0))
        assertFalse(distanceUsesKilometers(0.0))
    }

    @Test
    fun allTurnTypesDrawWithoutError() {
        val canvas = testCanvas()
        for (type in TurnType.values()) {
            NavigationHintsOverlay.drawTurnSymbol(canvas, type, 96f, 0xFFFFFFFF.toInt(), 0f, 0f)
        }
        NavigationHintsOverlay.drawTurnSymbol(canvas, null, 96f, 0xFFFFFFFF.toInt(), 0f, 0f)
    }

    @Test
    fun allLaneTurnsDrawWithoutError() {
        val canvas = testCanvas()
        for (turn in LaneTurn.values()) {
            NavigationHintsOverlay.drawLaneSymbol(canvas, turn, 56f, 0xFFFFFFFF.toInt(), 0f, 0f)
        }
    }

    private fun testCanvas(): Canvas {
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        return Canvas(bitmap)
    }
}
