package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.ManeuverSymbols

/**
 * Canvas-generated maneuver icons for the host instruction panel
 * (spec: auto/navigation-view — "Next turn maneuver in host instruction panel").
 *
 * A [androidx.car.app.navigation.model.Maneuver] requires a [CarIcon] image.
 * The artwork comes from [ManeuverSymbols] in `:core` — the single source of
 * the maneuver geometry, shared with the car large icon of the ongoing
 * notification (spec: auto-navigation-hints) — so the head-unit symbol, the
 * rail-widget arrow and the phone symbol stay identical. [CarIcon]s are
 * created once and cached per [TurnType] — buildTemplate() runs on every
 * invalidation (same pattern as [CarGlyphs]).
 */
object ManeuverGlyphs {

    /** Suggested-lane highlight (matches the phone lane hint color). */
    private const val SUGGESTED_COLOR = 0xFF2196F3.toInt()

    private val cache = HashMap<TurnType, CarIcon>()

    /** CarIcon for [type]; lazily rendered and cached. */
    fun forTurnType(type: TurnType): CarIcon = cache.getOrPut(type) {
        CarIcon.Builder(IconCompat.createWithBitmap(ManeuverSymbols.bitmapForTurnType(type))).build()
    }

    /**
     * Lanes-strip image for the host instruction panel: one lane arrow per
     * row, suggested lanes highlighted (spec: auto/navigation-view — the
     * current step must carry a lanes image when lane information is set).
     *
     * The image is reused while the lane state is unchanged (spec:
     * car-host-fault-isolation — Bounded host-facing traffic while not visible): the template is
     * rebuilt on navigation-state updates, and a fresh bitmap per rebuild is a fresh
     * bitmap sent to the host over IPC every time. The cache holds the last state only —
     * the host panel shows one step's lanes at a time — so it stays bounded by the lane
     * count. Main-thread use (the template build).
     */
    fun lanesImage(turns: List<LaneTurn>, recommended: IntRange): CarIcon {
        val cached = cachedLanes
        if (cached != null && cached.turns == turns && cached.recommended == recommended) {
            return cached.icon
        }
        val icon = buildLanesImage(turns, recommended)
        cachedLanes = LaneCache(turns, recommended, icon)
        return icon
    }

    /** Last lane state whose image was built, so an unchanged state reuses the image. */
    @Volatile
    private var cachedLanes: LaneCache? = null

    private data class LaneCache(
        val turns: List<LaneTurn>,
        val recommended: IntRange,
        val icon: CarIcon
    )

    private fun buildLanesImage(turns: List<LaneTurn>, recommended: IntRange): CarIcon {
        val laneSize = 32
        val gap = 8
        val width = turns.size * laneSize + (turns.size - 1) * gap
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), laneSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        turns.forEachIndexed { index, turn ->
            val color = if (index in recommended) SUGGESTED_COLOR else 0xFFFFFFFF.toInt()
            ManeuverSymbols.drawLaneSymbol(
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
}
