package com.naviveylin.auto

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Pure Canvas drawing of the free-driving street-name label (spec:
 * auto/free-driving — "Current street name shown").
 *
 * Drawn horizontally centered at the bottom of the map surface, inside the
 * host stable area (anchored to its bottom edge when known, else the surface
 * bottom). Blank names draw nothing — no stale label on unnamed roads (spec:
 * "No street name when unnamed").
 */
object StreetNameLabel {

    private const val BOTTOM_MARGIN_DP = 16f
    private const val HEIGHT_DP = 52f
    private const val MAX_WIDTH_DP = 520f
    private const val PADDING_DP = 16f
    private const val CORNER_DP = 10f
    private const val TEXT_SIZE_DP = 20f

    private const val BG = 0xCC1C1B1F.toInt()
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()

    /** Paint used for text measurement and drawing (shared config). */
    private fun textPaint(density: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        textSize = TEXT_SIZE_DP * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    /**
     * Label rect in surface pixels; null when [name] is blank (nothing to
     * draw). Horizontally centered, width fits the text capped at
     * [MAX_WIDTH_DP]; bottom sits above the stable-area/surface bottom.
     */
    fun geometry(
        usableBounds: Rect,
        surfaceWidth: Int,
        surfaceHeight: Int,
        density: Float,
        name: String
    ): Rect? {
        if (name.isBlank()) return null
        val textW = textPaint(density).measureText(name)
        val width = ((textW + 2f * PADDING_DP * density).toInt())
            .coerceAtMost((MAX_WIDTH_DP * density).toInt())
        val left = (surfaceWidth - width) / 2

        val bottomEdge = if (usableBounds.isEmpty()) {
            surfaceHeight
        } else {
            usableBounds.bottom
        }
        val bottom = bottomEdge - (BOTTOM_MARGIN_DP * density).toInt()
        return Rect(left, bottom - (HEIGHT_DP * density).toInt(), left + width, bottom)
    }

    /** Draw the label; no-op when [name] is blank. */
    fun draw(
        canvas: Canvas,
        surfaceWidth: Int,
        surfaceHeight: Int,
        density: Float,
        usableBounds: Rect,
        name: String
    ) {
        val rect = geometry(usableBounds, surfaceWidth, surfaceHeight, density, name) ?: return

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BG
            style = Paint.Style.FILL
        }
        val corner = CORNER_DP * density
        canvas.drawRoundRect(
            RectF(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat()),
            corner, corner, bg
        )

        val text = textPaint(density)
        canvas.drawText(
            name,
            rect.exactCenterX(),
            rect.exactCenterY() - (text.ascent() + text.descent()) / 2f,
            text
        )
    }
}
