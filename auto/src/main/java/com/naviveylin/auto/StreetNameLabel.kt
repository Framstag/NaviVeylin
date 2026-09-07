package com.naviveylin.auto

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint

/**
 * Pure Canvas drawing of the street-name label (spec: auto/free-driving —
 * "Current street name shown"; auto/navigation-view — "Current street name
 * shown during navigation").
 *
 * Drawn horizontally centered at the bottom of the map surface, anchored to
 * the more conservative of the host's stable and visible area bottoms (the
 * visible area excludes the host ETA card while it is shown; the stable area
 * excludes it "as if always present" — see `SurfaceCallback`). A
 * [bottomReserveDp] safety margin lifts the label further while navigating.
 * Blank names draw nothing — no stale label on unnamed roads (spec: "No
 * street name when unnamed").
 */
object StreetNameLabel {

    private const val BOTTOM_MARGIN_DP = 16f
    private const val HEIGHT_DP = 52f

    /**
     * Pill width cap. Keeps the label in the center region between the host
     * ETA card (bottom-left) and the map action strip (bottom-right) on
     * typical ~800 dp screens (design D2).
     */
    private const val MAX_WIDTH_DP = 360f

    /**
     * Minimum clearance (dp) between a delivered stable/visible area bottom
     * and the surface bottom for the map label to be considered safe (design
     * D5). A host that excludes the ETA card delivers a bottom at least this
     * far above the surface bottom; a full-surface or empty area means the
     * card may cover the label and the street name must go into the host ETA
     * card ([TravelEstimate.setTripText]) instead.
     */
    private const val SAFE_MARGIN_DP = 48f
    private const val PADDING_DP = 16f
    private const val CORNER_DP = 10f
    private const val TEXT_SIZE_DP = 20f

    private const val BG = 0xCC1C1B1F.toInt()
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()

    /** Paint used for text measurement and drawing (shared config). */
    private fun textPaint(density: Float): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        textSize = TEXT_SIZE_DP * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    /**
     * Whether the map-surface label can be drawn safely: at least one of the
     * host's stable/visible areas clears the surface bottom by [SAFE_MARGIN_DP]
     * — i.e. the host excluded the ETA card (or similar bottom occlusion).
     * When false, the caller renders the street name via the host ETA card
     * ([TravelEstimate.setTripText]) instead of the map surface (design D5):
     * the host positions the card, so the name is never covered.
     */
    fun isMapLabelSafe(
        stableBounds: Rect,
        visibleBounds: Rect,
        surfaceHeight: Int,
        density: Float
    ): Boolean {
        val margin = (SAFE_MARGIN_DP * density).toInt()
        val bottoms = listOfNotNull(
            stableBounds.takeIf { !it.isEmpty() }?.bottom,
            visibleBounds.takeIf { !it.isEmpty() }?.bottom
        )
        if (bottoms.isEmpty()) return false
        return bottoms.min() < surfaceHeight - margin
    }

    /**
     * Bottom edge of the label area in surface pixels: the smaller of the
     * stable/visible area bottoms (whichever is known), minus the reserve;
     * the raw surface bottom only when both areas are unknown.
     */
    fun resolveBottomEdge(
        stableBounds: Rect,
        visibleBounds: Rect,
        surfaceHeight: Int,
        density: Float,
        bottomReserveDp: Float
    ): Int {
        val bottoms = listOfNotNull(
            stableBounds.takeIf { !it.isEmpty() }?.bottom,
            visibleBounds.takeIf { !it.isEmpty() }?.bottom
        )
        val base = if (bottoms.isEmpty()) surfaceHeight else bottoms.min()
        return base - (bottomReserveDp * density).toInt()
    }

    /**
     * Label rect in surface pixels; null when [name] is blank (nothing to
     * draw). Horizontally centered, width fits the text capped at
     * [MAX_WIDTH_DP]; bottom sits above the resolved bottom edge (see
     * [resolveBottomEdge]) minus the margin.
     */
    fun geometry(
        stableBounds: Rect,
        visibleBounds: Rect = Rect(),
        surfaceWidth: Int,
        surfaceHeight: Int,
        density: Float,
        name: String,
        bottomReserveDp: Float = 0f
    ): Rect? {
        if (name.isBlank()) return null
        val textW = textPaint(density).measureText(name)
        val width = ((textW + 2f * PADDING_DP * density).toInt())
            .coerceAtMost((MAX_WIDTH_DP * density).toInt())
        val left = (surfaceWidth - width) / 2

        val bottom = resolveBottomEdge(stableBounds, visibleBounds, surfaceHeight, density, bottomReserveDp) -
            (BOTTOM_MARGIN_DP * density).toInt()
        return Rect(left, bottom - (HEIGHT_DP * density).toInt(), left + width, bottom)
    }

    /**
     * Text to draw: ellipsized to the pill's inner width so a long name
     * cannot overflow the pill into the ETA card zone (design D2). Manual
     * ellipsize (measure + truncate + "…") instead of `TextUtils.ellipsize`:
     * Robolectric's implementation is a no-op that returns the input
     * unchanged, so the truncation would be untestable in unit tests.
     */
    fun ellipsizedText(name: String, width: Int, density: Float): String {
        val available = width - (2f * PADDING_DP * density).toInt()
        val paint = textPaint(density)
        if (paint.measureText(name) <= available) return name
        val ellipsis = "\u2026"
        var end = name.length
        while (end > 0 && paint.measureText(name.substring(0, end) + ellipsis) > available) {
            end--
        }
        return name.substring(0, end) + ellipsis
    }

    /** Draw the label; no-op when [name] is blank. */
    fun draw(
        canvas: Canvas,
        surfaceWidth: Int,
        surfaceHeight: Int,
        density: Float,
        stableBounds: Rect,
        visibleBounds: Rect = Rect(),
        name: String,
        bottomReserveDp: Float = 0f
    ) {
        val rect = geometry(stableBounds, visibleBounds, surfaceWidth, surfaceHeight, density, name, bottomReserveDp)
            ?: return

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
            ellipsizedText(name, rect.width(), density),
            rect.exactCenterX(),
            rect.exactCenterY() - (text.ascent() + text.descent()) / 2f,
            text
        )
    }
}
