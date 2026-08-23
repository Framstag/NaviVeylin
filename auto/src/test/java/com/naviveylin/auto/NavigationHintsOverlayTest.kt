package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [NavigationHintsOverlay] symbol rendering (spec:
 * auto/navigation-view — host-maneuver icons reuse the same arrow geometry).
 * The surface hint panel was removed when the host instruction panel took
 * over (see `auto-map-layout` delta).
 */
@RunWith(RobolectricTestRunner::class)
class NavigationHintsOverlayTest {

    @Test
    fun formatDistanceHandlesMetersAndKilometers() {
        assertEquals("350 m", NavigationHintsOverlay.formatDistance(350.0))
        assertEquals("1.2 km", NavigationHintsOverlay.formatDistance(1200.0))
        assertEquals("0 m", NavigationHintsOverlay.formatDistance(0.0))
    }

    @Test
    fun formatDistanceRoundsForDisplay() {
        assertEquals("45 m", NavigationHintsOverlay.formatDistance(45.0))
        assertEquals("150 m", NavigationHintsOverlay.formatDistance(137.0))
        assertEquals("100 m", NavigationHintsOverlay.formatDistance(124.0))
        assertEquals("1.4 km", NavigationHintsOverlay.formatDistance(1350.0))
        assertEquals("1.2 km", NavigationHintsOverlay.formatDistance(1234.0))
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
