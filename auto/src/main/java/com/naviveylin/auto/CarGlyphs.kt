package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat

/**
 * Generated glyphs for host action-strip actions (the car-app map action
 * strip only accepts icon-only actions — no custom titles). White on
 * transparent; the host tints per theme. Bitmaps are created once and cached:
 * buildTemplate() runs on every invalidation.
 */
object CarGlyphs {

    val menu: CarIcon by lazy { glyph { c, p ->
        val w = 22f
        val h = 3.5f
        val x = 13f
        val y = 14f
        val gap = 8f
        c.drawRoundRect(x, y, x + w, y + h, 2f, 2f, p)
        c.drawRoundRect(x, y + gap, x + w, y + gap + h, 2f, 2f, p)
        c.drawRoundRect(x, y + 2 * gap, x + w, y + 2 * gap + h, 2f, 2f, p)
    } }

    val search: CarIcon by lazy { glyph { c, p ->
        val stroke = Paint(p).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
            strokeCap = Paint.Cap.ROUND
        }
        c.drawCircle(20f, 20f, 9.5f, stroke)
        c.drawLine(27f, 27f, 34f, 34f, stroke)
    } }

    val settings: CarIcon by lazy { glyph { c, p ->
        // Sliders: three lines with offset knobs.
        val y0 = 15f
        val gap = 9f
        val left = 12f
        val right = 36f
        val stroke = Paint(p).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            strokeCap = Paint.Cap.ROUND
        }
        repeat(3) { i ->
            val y = y0 + i * gap
            c.drawLine(left, y, right, y, stroke)
            val knobX = if (i % 2 == 0) 24f else 18f
            c.drawCircle(knobX, y, 4.2f, p)
        }
    } }

    /** Exit free driving: an "x" (close) — stops free driving, back to the map view. */
    val exit: CarIcon by lazy { glyph { c, p ->
        val stroke = Paint(p).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5.0f
            strokeCap = Paint.Cap.ROUND
        }
        // Two crossing diagonal lines.
        c.drawLine(15f, 15f, 33f, 33f, stroke)
        c.drawLine(33f, 15f, 15f, 33f, stroke)
    } }

    /** Route description: three stacked lines with leading bullets. */
    val routeList: CarIcon by lazy { glyph { c, p ->
        val stroke = Paint(p).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            strokeCap = Paint.Cap.ROUND
        }
        val left = 16f
        val right = 34f
        val y0 = 15f
        val gap = 9f
        repeat(3) { i ->
            val y = y0 + i * gap
            c.drawCircle(11f, y, 2.6f, p)
            c.drawLine(left, y, right, y, stroke)
        }
    } }

    private fun glyph(draw: (Canvas, Paint) -> Unit): CarIcon {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        draw(canvas, paint)
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }
}
