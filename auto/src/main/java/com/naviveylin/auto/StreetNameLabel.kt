package com.naviveylin.auto

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.naviveylin.core.VehicleAnchorPosition

/**
 * Pure Canvas drawing of the street-name label (spec: auto/free-driving —
 * "Current street name shown"; auto/browse — "Current street name shown while
 * browsing").
 *
 * Drawn horizontally centered and anchored to the edge of the guaranteed-
 * visible map band — the surface minus the host/system chrome insets the host
 * reports through the stable area (design D8, street-name-host-views).
 * [Placement.BOTTOM] sits above the bottom inset, [Placement.TOP] below the
 * top inset; with no insets (empty stable area, or one spanning the full
 * surface) the band IS the surface, which reproduces the previous real-edge
 * anchoring exactly.
 * Vertical position follows the row rule: opposite the vehicle anchor row
 * ([Placement.BOTTOM] default for the top and middle rows, [Placement.TOP]
 * for the bottom row), with a fixed padding from the chosen band edge. Blank
 * names draw nothing — no stale label on unnamed roads (spec: "No street name
 * when unnamed").
 *
 * Navigation does not use this label: the routing street name lives in the
 * host travel-estimate card ([TravelEstimate.setTripText], spec:
 * auto/navigation-view). Free driving and browse draw it on the surface.
 */
object StreetNameLabel {

    /**
     * Where the label is anchored vertically (real surface edges, fixed
     * paddings).
     *
     * - [BOTTOM]: bottom-edge anchored ([BOTTOM_MARGIN_DP] above the surface
     *   bottom) — top-row and middle-row anchors.
     * - [TOP]: top-edge anchored ([TOP_MARGIN_DP] below the surface top) —
     *   bottom-row anchors, where the label would otherwise sit between the
     *   vehicle and the way ahead.
     */
    enum class Placement { BOTTOM, TOP }

    /**
     * Anchor preset → [Placement] (row rule, design D3): a bottom-row preset
     * (`fy == 0.9` — the vehicle sits at the bottom, the label must move to
     * the top so it never covers the marker nor lies in the travel corridor);
     * every other row keeps [Placement.BOTTOM] (spec: auto/free-driving,
     * auto/browse, current-road-info — shared rule on both surfaces).
     */
    fun placementFor(anchor: VehicleAnchorPosition): Placement =
        if (anchor.fy == 0.9) Placement.TOP else Placement.BOTTOM

    private const val TOP_MARGIN_DP = 8f
    private const val BOTTOM_MARGIN_DP = 16f
    private const val HEIGHT_DP = 52f

    /**
     * Pill width cap. Keeps the label in the center region of the surface on
     * typical ~800 dp screens. Unchanged from the pre-feature label.
     */
    private const val MAX_WIDTH_DP = 360f

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
     * Label rect in surface pixels; null when [name] is blank (nothing to
     * draw). Horizontally centered; width fits the text capped at
     * [MAX_WIDTH_DP]. Vertical anchoring per [placement], to the edges of the
     * guaranteed-visible band (design D8): [Placement.BOTTOM] sits
     * [BOTTOM_MARGIN_DP] above the band bottom; [Placement.TOP] sits
     * [TOP_MARGIN_DP] below the band top. The band is the surface minus
     * [topInset] (host chrome at the top, e.g. the AAOS status bar) and
     * [bottomInset] (host chrome at the bottom, e.g. the AAOS task bar); both
     * default to 0, in which case the band IS the surface (real-edge
     * anchoring, byte-identical to the pre-band geometry).
     */
    fun geometry(
        surfaceWidth: Int,
        surfaceHeight: Int,
        density: Float,
        name: String,
        placement: Placement = Placement.BOTTOM,
        topInset: Int = 0,
        bottomInset: Int = 0
    ): Rect? {
        if (name.isBlank()) return null
        val textW = textPaint(density).measureText(name)
        val width = ((textW + 2f * PADDING_DP * density).toInt())
            .coerceAtMost((MAX_WIDTH_DP * density).toInt())
        val left = (surfaceWidth - width) / 2

        val height = (HEIGHT_DP * density).toInt()
        return when (placement) {
            Placement.BOTTOM -> {
                val bottom = surfaceHeight - bottomInset - (BOTTOM_MARGIN_DP * density).toInt()
                Rect(left, bottom - height, left + width, bottom)
            }
            Placement.TOP -> {
                val top = topInset + (TOP_MARGIN_DP * density).toInt()
                Rect(left, top, left + width, top + height)
            }
        }
    }

    /**
     * Text to draw: ellipsized to the pill's inner width so a long name
     * cannot overflow the pill. Manual ellipsize (measure + truncate + "…")
     * instead of `TextUtils.ellipsize`: Robolectric's implementation is a
     * no-op that returns the input unchanged, so the truncation would be
     * untestable in unit tests.
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
        name: String,
        placement: Placement = Placement.BOTTOM,
        topInset: Int = 0,
        bottomInset: Int = 0
    ) {
        val rect = geometry(surfaceWidth, surfaceHeight, density, name, placement, topInset, bottomInset) ?: return

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
