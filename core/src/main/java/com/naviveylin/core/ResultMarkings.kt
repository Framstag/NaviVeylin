package com.naviveylin.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.naviveylin.core.search.ResultMarking

/**
 * Canvas rendering of the search-result row markings — the single source of the
 * marking artwork (same pattern as [ManeuverSymbols]): the phone result row and
 * the Android Auto result row draw the same bitmaps, so the two surfaces cannot
 * drift.
 *
 * The marking is *composite*: a row can be a favorite and a perfect match at the
 * same time, and the car `Row` offers a single image slot, so both facts are
 * drawn into one bitmap instead of one replacing the other (spec:
 * search-result-ranking — "Perfect-match marking and cross-surface parity").
 *
 * White on transparent; the host tints per theme. Bitmaps are created once and
 * cached — car templates are rebuilt on every invalidation.
 */
object ResultMarkings {

    /** Edge length of a rendered marking bitmap, in pixels. */
    const val BITMAP_SIZE = 48

    private val cache = HashMap<ResultMarking, Bitmap>()

    /**
     * Marking bitmap for [marking], cached per marking. [ResultMarking.NONE]
     * yields a fully transparent bitmap so callers can draw unconditionally.
     */
    fun bitmapFor(marking: ResultMarking): Bitmap = cache.getOrPut(marking) { render(marking) }

    private fun render(marking: ResultMarking): Bitmap {
        val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        when (marking) {
            ResultMarking.NONE -> Unit
            ResultMarking.FAVORITE -> drawHeart(canvas, fill, centerX = 24f, centerY = 24f, radius = 9f)
            ResultMarking.PERFECT -> drawCheck(canvas, stroke, centerX = 24f, centerY = 24f, size = 15f)
            ResultMarking.FAVORITE_AND_PERFECT -> {
                drawHeart(canvas, fill, centerX = 13f, centerY = 25f, radius = 7f)
                drawCheck(canvas, stroke, centerX = 34f, centerY = 24f, size = 10f)
            }
        }

        return bitmap
    }

    /** Filled heart of [radius] centered on the given point. */
    private fun drawHeart(canvas: Canvas, paint: Paint, centerX: Float, centerY: Float, radius: Float) {
        val lobe = radius * 0.55f
        canvas.drawCircle(centerX - radius * 0.5f, centerY - radius * 0.45f, lobe, paint)
        canvas.drawCircle(centerX + radius * 0.5f, centerY - radius * 0.45f, lobe, paint)
        val path = Path().apply {
            moveTo(centerX - radius, centerY - radius * 0.2f)
            lineTo(centerX + radius, centerY - radius * 0.2f)
            lineTo(centerX, centerY + radius * 1.1f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    /** Stroked tick mark of [size] centered on the given point. */
    private fun drawCheck(canvas: Canvas, paint: Paint, centerX: Float, centerY: Float, size: Float) {
        paint.strokeWidth = size * 0.22f
        val path = Path().apply {
            moveTo(centerX - size * 0.5f, centerY)
            lineTo(centerX - size * 0.1f, centerY + size * 0.42f)
            lineTo(centerX + size * 0.5f, centerY - size * 0.42f)
        }
        canvas.drawPath(path, paint)
    }
}
