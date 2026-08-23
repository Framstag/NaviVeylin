package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.TurnType

/**
 * Canvas-generated maneuver icons for the host instruction panel
 * (spec: auto/navigation-view — "Next turn maneuver in host instruction panel").
 *
 * A [androidx.car.app.navigation.model.Maneuver] requires a [CarIcon] image.
 * Each icon is a white arrow on transparent, rendered from the same
 * normalized geometry as the phone/previous surface arrows
 * ([NavigationHintsOverlay.drawTurnSymbol]), so the head-unit symbol matches
 * the phone symbol. Bitmaps are created once and cached per [TurnType] —
 * buildTemplate() runs on every invalidation (same pattern as [CarGlyphs]).
 */
object ManeuverGlyphs {

    private const val SIZE = 48

    /** Suggested-lane highlight (matches the phone lane hint color). */
    private const val SUGGESTED_COLOR = 0xFF2196F3.toInt()

    private val cache = HashMap<TurnType, CarIcon>()

    /** CarIcon for [type]; lazily rendered and cached. */
    fun forTurnType(type: TurnType): CarIcon = cache.getOrPut(type) { render(type) }

    /**
     * Lanes-strip image for the host instruction panel: one lane arrow per
     * row, suggested lanes highlighted (spec: auto/navigation-view — the
     * current step must carry a lanes image when lane information is set).
     */
    fun lanesImage(turns: List<LaneTurn>, recommended: IntRange): CarIcon {
        val laneSize = 32
        val gap = 8
        val width = turns.size * laneSize + (turns.size - 1) * gap
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), laneSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        turns.forEachIndexed { index, turn ->
            val color = if (index in recommended) SUGGESTED_COLOR else 0xFFFFFFFF.toInt()
            NavigationHintsOverlay.drawLaneSymbol(
                canvas,
                turn,
                laneSize.toFloat(),
                color,
                (index * (laneSize + gap)).toFloat(),
                0f
            )
        }
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }

    private fun render(type: TurnType): CarIcon {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        NavigationHintsOverlay.drawTurnSymbol(
            canvas = canvas,
            type = type,
            pxSize = SIZE.toFloat(),
            color = 0xFFFFFFFF.toInt(),
            left = 0f,
            top = 0f
        )
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }
}
