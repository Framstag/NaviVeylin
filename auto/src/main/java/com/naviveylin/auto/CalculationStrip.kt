package com.naviveylin.auto

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Locale

/**
 * Percentage step the strip (and the not-navigating wait notice) displays. A value inside the same
 * step cannot change what the driver sees, so it must not cost a render request — the same rule
 * `NavigationTemplateMapper.displayedDistanceBucket` applies to distances
 * (spec: `car-host-fault-isolation` — Bounded host-facing traffic).
 */
internal const val CALCULATION_PERCENT_STEP = 5

/**
 * The percentage the strip displays for a reported [percent], or null while the calculation has not
 * reported one.
 */
internal fun displayedCalculationPercent(percent: Int?): Int? =
    percent?.let { (it / CALCULATION_PERCENT_STEP) * CALCULATION_PERCENT_STEP }

/**
 * The app-drawn strip that reports a route calculation in progress on the navigation surface
 * (spec: `route-calculation-feedback` — A route calculation during navigation is drawn on the map
 * surface; `auto-map-layout` — The route-calculation strip has one place on the navigation surface).
 *
 * A reroute runs with guidance live, and the Car App Library has no overlay layer: a pushed screen
 * *is* the displayed template, so the wait was taking the map away. The strip is drawn on the map
 * surface the session already owns — the same seam `SurfaceIndicators`, `SurfaceAttribution` and
 * `MapUnavailableOverlay` use — so the map stays drawn and interactive underneath it.
 *
 * Pure Canvas drawing with no host call and no ViewModel: [geometry] and [draw] are asserted without
 * a host (`CalculationStripTest`).
 */
internal object CalculationStrip {

    private const val MARGIN_DP = 8f
    private const val PADDING_DP = 8f
    private const val CORNER_DP = 6f
    private const val TEXT_SIZE_DP = 13f

    /** Which wait the strip states; [IDLE] draws nothing. */
    internal enum class Phase { IDLE, WAITING, FAILED }

    /**
     * The strip's small state value: never the ViewModel, only what the drawer needs. [percent] is a
     * reported percentage, displayed in its [CALCULATION_PERCENT_STEP] step.
     */
    internal data class Snapshot(
        val phase: Phase = Phase.IDLE,
        val percent: Int? = null
    )

    /** One presentation's strip colours. */
    internal data class Palette(val chip: Int, val text: Int)

    /**
     * The strip's wording for [snapshot]: [wait] when a calculation runs without a reported
     * percentage, [waitPercent] (a `%1$d` format) in [CALCULATION_PERCENT_STEP] steps once one is
     * reported, [failed] for a failed reroute, and empty while [Phase.IDLE] (nothing to draw).
     * The wording is the caller's resource — this drawer owns none — like the attribution and the
     * map-unavailable notice.
     */
    internal fun label(snapshot: Snapshot, wait: String, waitPercent: String, failed: String): String =
        when (snapshot.phase) {
            Phase.IDLE -> ""
            Phase.FAILED -> failed
            Phase.WAITING -> snapshot.percent
                ?.let { String.format(Locale.getDefault(), waitPercent, displayedCalculationPercent(it)) }
                ?: wait
        }

    /**
     * Palette for the presentation actually applied to the surface (the map's own variant), and for
     * the failed report. The geometry never depends on this
     * (spec: `auto-map-layout` — The strip follows the applied presentation).
     */
    internal fun palette(darkPresentation: Boolean, failed: Boolean): Palette = when {
        failed -> Palette(chip = 0xE6B3261E.toInt(), text = 0xFFFFFFFF.toInt())
        darkPresentation -> Palette(chip = 0xCC1C1B1F.toInt(), text = 0xFFFFFFFF.toInt())
        else -> Palette(chip = 0xE6FFFFFF.toInt(), text = 0xFF1C1B1F.toInt())
    }

    /**
     * The strip's rect in surface pixels, or null when the surface is too small or the wait leaves
     * no room beside the right-edge indicator column. Top-left of the host's stable area when one is
     * known (the surface's own top edge otherwise), clear of the compass/badge/speed-limit column and
     * of the attribution.
     */
    internal fun geometry(
        label: String,
        stableBounds: Rect,
        surfaceWidth: Int,
        surfaceHeight: Int,
        density: Float
    ): Rect? {
        if (label.isEmpty() || surfaceWidth <= 0 || surfaceHeight <= 0) return null
        val textW = textPaint(density).measureText(label)
        val width = (textW + 2f * PADDING_DP * density).toInt()
        val height = (TEXT_SIZE_DP * density + 2f * PADDING_DP * density).toInt()
        if (width <= 0 || height <= 0) return null
        if (width > surfaceWidth || height > surfaceHeight) return null

        val inside = !stableBounds.isEmpty()
        val left = (if (inside) stableBounds.left.toFloat() else 0f) + MARGIN_DP * density
        val top = (if (inside) stableBounds.top.toFloat() else 0f) + MARGIN_DP * density
        val right = left + width
        val bottom = top + height
        if (inside && (right > stableBounds.right || bottom > stableBounds.bottom)) return null

        // The right-edge column the indicator block occupies (compass rose is its widest element,
        // centred over the speed badge).
        val indicators = SurfaceIndicators.geometry(
            usableBounds = stableBounds,
            surfaceWidth = surfaceWidth,
            density = density,
            showSpeed = true,
            showSpeedLimit = true
        )
        val columnLeft = indicators.compassCenterX - indicators.compassRadius - MARGIN_DP * density
        if (right > columnLeft) return null

        val rect = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
        val attribution = SurfaceAttribution.geometry(
            usableBounds = stableBounds,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            density = density,
            text = ATTRIBUTION_PROBE
        )
        if (attribution != null && Rect.intersects(rect, attribution)) return null
        return rect
    }

    /**
     * Draw [snapshot]'s [label] on the surface. A no-op while [Phase.IDLE] (the wait is over) or when
     * the strip has no room — the map is never covered by an empty or oversized chip.
     */
    internal fun draw(
        canvas: Canvas,
        surfaceWidth: Int,
        surfaceHeight: Int,
        density: Float,
        stableBounds: Rect,
        darkPresentation: Boolean,
        snapshot: Snapshot,
        label: String
    ) {
        if (snapshot.phase == Phase.IDLE || label.isEmpty()) return
        val rect = geometry(label, stableBounds, surfaceWidth, surfaceHeight, density) ?: return
        val palette = palette(darkPresentation, failed = snapshot.phase == Phase.FAILED)

        val chip = chipPaintFor(density)
        chip.color = palette.chip
        val corner = CORNER_DP * density
        canvas.drawRoundRect(
            RectF(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat()),
            corner,
            corner,
            chip
        )

        val text = textPaint(density)
        text.color = palette.text
        val baseline = rect.top + PADDING_DP * density +
            (text.descent() - text.ascent()) / 2f - text.descent()
        canvas.drawText(label, rect.left + PADDING_DP * density, baseline, text)
    }

    // Attribution's width depends on its own wording; the strip only has to know whether a
    // realistic attribution box would reach it. A fixed, generous probe is enough: the strip lives
    // at the top edge, the attribution at the bottom edge.
    private const val ATTRIBUTION_PROBE = "© OpenStreetMap contributors"

    private var cachedDensity = Float.NaN
    private var chipPaint: Paint? = null
    private var textPaint: Paint? = null

    private fun chipPaintFor(density: Float): Paint {
        val cached = chipPaint
        if (cached != null && cachedDensity == density) return cached
        val fresh = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        chipPaint = fresh
        return fresh
    }

    private fun textPaint(density: Float): Paint {
        val cached = textPaint
        if (cached != null && cachedDensity == density) return cached
        val fresh = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.DEFAULT_BOLD
            textSize = TEXT_SIZE_DP * density
        }
        textPaint = fresh
        cachedDensity = density
        return fresh
    }

    /** Test seam: the cached paints belong to one surface density. */
    internal fun resetForTest() {
        cachedDensity = Float.NaN
        chipPaint = null
        textPaint = null
    }
}
