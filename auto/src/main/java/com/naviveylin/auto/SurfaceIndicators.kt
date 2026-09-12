package com.naviveylin.auto

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.naviveylin.core.ProjectionUtils
import kotlin.math.roundToInt

/**
 * Pure Canvas drawing of the right-edge visualisation indicators: a compass
 * rose that rotates with the map and (during navigation) a speed-limit badge.
 * Host action strips only support static icons, so animated indicators are
 * drawn on the map surface via the renderer's overlay hook.
 */
object SurfaceIndicators {

    private const val ROSE_DIAMETER_DP = 56f // larger than the host strip buttons (48dp)
    private const val BADGE_WIDTH_DP = 128f
    private const val BADGE_HEIGHT_DP = 52f
    // Inset from the surface right edge (mirrors the phone's 8.dp end padding)
    // so the rose is not flush against the map border.
    private const val RIGHT_MARGIN_DP = 8f
    private const val TOP_MARGIN_DP = 12f
    private const val BADGE_GAP_DP = 8f

    // Speed-limit sign (free driving): standard EU sign — white circle with a
    // big red border and black text, larger than the compass rose.
    private const val LIMIT_DIAMETER_DP = 56f
    private const val LIMIT_RING_DP = 7f
    private const val LIMIT_TEXT_DP = 24f
    private const val LIMIT_GAP_DP = 8f

    private const val ROSE_BG = 0xCC1C1B1F.toInt()
    private const val ROSE_FG = 0xFFFFFFFF.toInt()
    private const val ROSE_NORTH = 0xFFE53935.toInt()
    private const val BADGE_BG = 0xCC1C1B1F.toInt()
    private const val BADGE_FG = 0xFFFFFFFF.toInt()
    private const val BADGE_WARN = 0xFFE53935.toInt()

    /** Geometry of the indicator block at the top-right of the usable area. */
    data class IndicatorGeometry(
        val compassCenterX: Float,
        val compassCenterY: Float,
        val compassRadius: Float,
        val badgeRect: Rect?,
        val speedLimitRect: Rect?
    )

    /**
     * Compute indicator positions. The speed badge sits at the surface right
     * edge (next to the host's right-edge controls); the compass rose is
     * horizontally centered above the badge. The top stays inside
     * [usableBounds] when known.
     */
    fun geometry(
        usableBounds: Rect,
        surfaceWidth: Int,
        density: Float,
        showSpeed: Boolean,
        showSpeedLimit: Boolean = false
    ): IndicatorGeometry {
        val size = ROSE_DIAMETER_DP * density
        val radius = size / 2f
        val gap = BADGE_GAP_DP * density
        val rightMargin = RIGHT_MARGIN_DP * density
        val topMargin = TOP_MARGIN_DP * density

        val rightLimit = surfaceWidth.toFloat()
        val topLimit = if (usableBounds.isEmpty()) 0f else usableBounds.top.toFloat()

        val badgeW = BADGE_WIDTH_DP * density
        val badgeH = BADGE_HEIGHT_DP * density
        val badgeLeft = rightLimit - rightMargin - badgeW

        // Rose horizontally centered over the badge slot (also when the badge
        // is hidden, so the position does not jump).
        val cx = badgeLeft + badgeW / 2f
        val cy = topLimit + topMargin + radius

        val badge = if (showSpeed) {
            val top = cy + radius + gap
            Rect(
                badgeLeft.toInt(), top.toInt(),
                (badgeLeft + badgeW).toInt(), (top + badgeH).toInt()
            )
        } else {
            null
        }

        // Speed-limit sign: circle below the speed badge, centered on the
        // same axis as the badge/rose.
        val speedLimit = if (showSpeedLimit) {
            val limitSize = LIMIT_DIAMETER_DP * density
            val top = (badge?.bottom ?: (cy + radius + gap).toInt()) + (LIMIT_GAP_DP * density).toInt()
            Rect(
                (cx - limitSize / 2f).toInt(), top,
                (cx + limitSize / 2f).toInt(), (top + limitSize).toInt()
            )
        } else {
            null
        }
        return IndicatorGeometry(cx, cy, radius, badge, speedLimit)
    }

    /**
     * Draw the indicators. [angleRadians] rotates the rose so north points
     * correctly; [currentKmH]/[maxKmH] drive the speed badge (red when over
     * the limit), omitted when NaN.
     */
    fun draw(
        canvas: Canvas,
        surfaceWidth: Int,
        surfaceHeight: Int,
        stableBounds: Rect,
        density: Float,
        angleRadians: Double,
        currentKmH: Double = Double.NaN,
        maxKmH: Double = Double.NaN,
        drawSpeedLimitSign: Boolean = false
    ) {
        // Values <= 0 mean "unknown" (native engine convention); NaN and
        // negatives must never render (e.g. a stale "-1 km/h").
        val hasSpeed = currentKmH > 0.0 || maxKmH > 0.0
        val g = geometry(
            stableBounds, surfaceWidth, density,
            showSpeed = hasSpeed,
            showSpeedLimit = drawSpeedLimitSign && maxKmH > 0.0
        )
        drawRose(canvas, g, angleRadians, density)
        val badge = g.badgeRect
        if (badge != null) {
            drawSpeedBadge(canvas, badge, density, currentKmH, maxKmH)
        }
        val limit = g.speedLimitRect
        if (limit != null) {
            drawSpeedLimitSign(canvas, limit, density, maxKmH)
        }
    }

    private fun drawRose(canvas: Canvas, g: IndicatorGeometry, angleRadians: Double, density: Float) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ROSE_BG
            style = Paint.Style.FILL
        }
        canvas.drawCircle(g.compassCenterX, g.compassCenterY, g.compassRadius, bg)

        canvas.save()
        // North pointer rendered at the screen direction of north, per the
        // shared core convention (ProjectionUtils.compassRotationDegrees) — the
        // same convention the phone compass uses (spec: auto-map-layout).
        canvas.rotate(
            ProjectionUtils.compassRotationDegrees(angleRadians).toFloat(),
            g.compassCenterX, g.compassCenterY
        )

        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ROSE_FG
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            strokeCap = Paint.Cap.ROUND
        }
        // Cardinal ticks
        for (i in 0 until 4) {
            val a = Math.toRadians(i * 90.0)
            val dx = kotlin.math.sin(a).toFloat()
            val dy = -kotlin.math.cos(a).toFloat()
            val inner = g.compassRadius * 0.72f
            canvas.drawLine(
                g.compassCenterX + dx * inner, g.compassCenterY + dy * inner,
                g.compassCenterX + dx * g.compassRadius, g.compassCenterY + dy * g.compassRadius,
                tickPaint
            )
        }

        // North pointer (red) above the north tick
        val north = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ROSE_NORTH
            style = Paint.Style.FILL
        }
        val tri = Path().apply {
            val tip = g.compassRadius * 0.95f
            val base = g.compassRadius * 0.55f
            moveTo(g.compassCenterX, g.compassCenterY - tip)
            lineTo(g.compassCenterX - 0.14f * g.compassRadius, g.compassCenterY - base)
            lineTo(g.compassCenterX + 0.14f * g.compassRadius, g.compassCenterY - base)
            close()
        }
        canvas.drawPath(tri, north)

        canvas.restore()
    }

    private fun drawSpeedBadge(canvas: Canvas, rect: Rect, density: Float, currentKmH: Double, maxKmH: Double) {
        // Compare the DISPLAYED (rounded) values: at exactly the limit the
        // badge stays the standard color — float noise must not trigger red.
        val overLimit = maxKmH > 0.0 && currentKmH > 0.0 &&
            currentKmH.roundToInt() > maxKmH.roundToInt()
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BADGE_BG
            style = Paint.Style.FILL
        }
        val accent = if (overLimit) BADGE_WARN else BADGE_FG
        val corner = 10f * density
        canvas.drawRoundRect(
            RectF(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat()),
            corner, corner, bg
        )

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 20f * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val label = when {
            // Badge shows the current speed (both free driving and navigation);
            // the limit is drawn as the round sign below (see drawSpeedLimitSign).
            currentKmH > 0.0 -> "${currentKmH.roundToInt()} km/h"
            // Fallback: only the limit known — show it in the badge.
            maxKmH > 0.0 -> "${maxKmH.roundToInt()} km/h"
            else -> ""
        }
        canvas.drawText(
            label,
            rect.exactCenterX(),
            rect.exactCenterY() - (text.ascent() + text.descent()) / 2f,
            text
        )
    }

    /**
     * Standard speed-limit sign: white circle, big red border, black text
     * (free driving — the badge above shows the current speed, the sign the
     * road's limit).
     */
    private fun drawSpeedLimitSign(canvas: Canvas, rect: Rect, density: Float, maxKmH: Double) {
        val cx = rect.exactCenterX()
        val cy = rect.exactCenterY()
        val radius = rect.width() / 2f

        // White fill.
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius, white)

        // Big red border.
        val red = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE53935.toInt()
            style = Paint.Style.STROKE
            strokeWidth = LIMIT_RING_DP * density
        }
        canvas.drawCircle(cx, cy, radius - (LIMIT_RING_DP * density) / 2f, red)

        // Black text.
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            textSize = LIMIT_TEXT_DP * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "${maxKmH.roundToInt()}",
            cx,
            cy - (text.ascent() + text.descent()) / 2f,
            text
        )
    }
}
