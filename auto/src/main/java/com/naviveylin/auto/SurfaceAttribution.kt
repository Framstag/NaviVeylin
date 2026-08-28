package com.naviveylin.auto

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Pure Canvas drawing of the OpenStreetMap attribution notice (spec:
 * osm-attribution — "Attribution shown in Android Auto variant").
 *
 * Drawn at the bottom-right of the map surface, inside the host stable area
 * (anchored to its bottom edge when known, else the surface bottom). The
 * notice is always visible; the OSMF Attribution Guidelines require a way to
 * reach the licence information, which the car About screen provides (licence
 * section + link action).
 */
object SurfaceAttribution {

    private const val TEXT = "© OpenStreetMap contributors"
    private const val BOTTOM_MARGIN_DP = 8f
    private const val RIGHT_MARGIN_DP = 8f
    private const val PADDING_DP = 8f
    private const val CORNER_DP = 6f
    private const val TEXT_SIZE_DP = 12f

    private const val BG = 0xCC1C1B1F.toInt()
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()

    private fun textPaint(density: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        textSize = TEXT_SIZE_DP * density
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.LEFT
    }

    /**
     * Attribution rect in surface pixels; null when the surface is too small
     * to fit the notice. Bottom-right corner, above the stable-area/surface
     * bottom edge.
     */
    fun geometry(
        usableBounds: Rect,
        surfaceWidth: Int,
        surfaceHeight: Int,
        density: Float
    ): Rect? {
        val textW = textPaint(density).measureText(TEXT)
        val width = (textW + 2f * PADDING_DP * density).toInt()
        val height = (TEXT_SIZE_DP * density + 2f * PADDING_DP * density).toInt()
        if (width <= 0 || height <= 0) return null
        if (width > surfaceWidth || height > surfaceHeight) return null

        val bottomEdge = if (usableBounds.isEmpty()) surfaceHeight else usableBounds.bottom
        val right = surfaceWidth - (RIGHT_MARGIN_DP * density).toInt()
        val bottom = bottomEdge - (BOTTOM_MARGIN_DP * density).toInt()
        return Rect(right - width, bottom - height, right, bottom)
    }

    /** Draw the attribution notice; no-op when the surface is too small. */
    fun draw(
        canvas: Canvas,
        surfaceWidth: Int,
        surfaceHeight: Int,
        density: Float,
        usableBounds: Rect
    ) {
        val rect = geometry(usableBounds, surfaceWidth, surfaceHeight, density) ?: return

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
            TEXT,
            rect.left + PADDING_DP * density,
            rect.exactCenterY() - (text.ascent() + text.descent()) / 2f,
            text
        )
    }
}
