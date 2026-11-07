package com.naviveylin.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Canvas rendering of the navigation turn/lane symbols — the single source of
 * the maneuver artwork. Consumers: the Android Auto host-maneuver icons (the
 * `:auto` module's `ManeuverGlyphs`) and the ongoing notification's car large
 * icon (spec: auto-navigation-hints — "Car hint content"), so the rail widget
 * arrow and the host instruction panel arrow cannot drift apart.
 *
 * All arrow geometry is a direct port of the phone's
 * `NavigationArrowRenderer` (Compose DrawScope → android.graphics), using the
 * same normalized constants so the on-car symbols match the phone symbols.
 *
 * This is also the single implementation of the numbered roundabout-exit glyph
 * (spec: roundabout-renderer - Phone and car roundabout symbols stay one artwork):
 * the phone overlays, the phone notification and the car bitmap surfaces all draw
 * their exit number from [drawRoundaboutExit], so the number's geometry cannot
 * drift between them. The Android Auto instruction panel is the one deliberate
 * exception - the host draws that maneuver itself.
 *
 * Every cached bitmap lives on the main thread: the consumers are the car template
 * build (host callback thread) and the ongoing notification (`Dispatchers.Main`),
 * and no lock is taken here. Do not call this from another thread.
 */
object ManeuverSymbols {

    /** Edge length of a rendered maneuver bitmap, in pixels. */
    const val BITMAP_SIZE = 48

    /**
     * Cache bound. Only a handful of turn types exist and a roundabout exit ordinal is
     * small, so this is reached only if a caller feeds unbounded ordinals; clearing on
     * overflow keeps the cache bounded rather than growing without limit
     * (spec: car-host-fault-isolation - a template asset stays bounded).
     */
    private const val MAX_CACHED_MANEUVERS = 64

    /** A maneuver bitmap's identity: the turn type plus the roundabout exit ordinal. */
    private data class ManeuverKey(val type: TurnType?, val roundaboutExitNumber: Int)

    /** Cached maneuver bitmaps, keyed by [ManeuverKey] (`null` type = unknown fallback). */
    private val bitmapCache = HashMap<ManeuverKey, Bitmap>()

    /**
     * White maneuver arrow bitmap for [type], cached per turn type. The car
     * large icon of the ongoing navigation notification (spec:
     * auto-navigation-hints — "Car hint content"), drawn with the same
     * geometry as the host instruction panel arrows.
     *
     * A roundabout exit drawn through this entry point carries no number; use
     * [bitmapForStep] wherever the step's exit ordinal is available.
     */
    fun bitmapForTurnType(type: TurnType?): Bitmap = bitmapFor(type, 0)

    /**
     * Bitmap for [com.framstag.libosmscout.client.RouteStep], carrying the roundabout exit ordinal
     * when there is one (spec: roundabout-renderer - Roundabout exit symbol shows the exit number).
     * A step with no exit ordinal falls back to the plain leave arrow, never to an invented or zero
     * number.
     */
    fun bitmapForStep(step: com.framstag.libosmscout.client.RouteStep): Bitmap =
        bitmapFor(turnTypeFor(step), step.exitOrdinal)

    /** A self-drawn exit bitmap's identity: the ordinal and the pixel size it was drawn at. */
    private data class ExitBitmapKey(val exitNumber: Int, val sizePx: Int)

    private val exitBitmapCache = HashMap<ExitBitmapKey, Bitmap>()

    /**
     * The numbered roundabout-exit glyph drawn at [sizePx], for a surface that draws it itself at
     * a size other than [BITMAP_SIZE]: the phone overlay draws the icon at 64 dp, which is several
     * hundred pixels on a high-density screen, and scaling a 48 px bitmap up would blur the digit.
     * Same geometry as [bitmapForStep], so the phone, the notification and the car bitmap
     * surfaces cannot drift apart (spec: roundabout-renderer - Phone and car roundabout symbols
     * stay one artwork).
     *
     * Returns the plain leave glyph when [exitNumber] is not a known exit, so a caller cannot get a
     * numbered glyph for "no exit".
     */
    fun roundaboutExitBitmap(exitNumber: Int, sizePx: Int): Bitmap {
        if (exitNumber <= 0) return bitmapFor(TurnType.ROUNDABOUT_LEAVE, 0)
        val size = sizePx.coerceAtLeast(1)
        val key = ExitBitmapKey(exitNumber, size)
        exitBitmapCache[key]?.let { return it }
        if (exitBitmapCache.size >= MAX_CACHED_MANEUVERS) {
            exitBitmapCache.clear()
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawTurnSymbol(
            Canvas(bitmap), TurnType.ROUNDABOUT_LEAVE, size.toFloat(), 0xFFFFFFFF.toInt(), 0f, 0f, exitNumber
        )
        exitBitmapCache[key] = bitmap
        return bitmap
    }

    private fun bitmapFor(type: TurnType?, roundaboutExitNumber: Int): Bitmap {
        // An ordinal is only meaningful on a roundabout exit; anything else must not become
        // a distinct cache entry nor reach the glyph.
        val ordinal = if (type == TurnType.ROUNDABOUT_LEAVE && roundaboutExitNumber > 0) {
            roundaboutExitNumber
        } else {
            0
        }
        val key = ManeuverKey(type, ordinal)
        bitmapCache[key]?.let { return it }
        if (bitmapCache.size >= MAX_CACHED_MANEUVERS) {
            bitmapCache.clear()
        }
        val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        drawTurnSymbol(Canvas(bitmap), type, BITMAP_SIZE.toFloat(), 0xFFFFFFFF.toInt(), 0f, 0f, ordinal)
        bitmapCache[key] = bitmap
        return bitmap
    }

    // ── Arrow geometry constants (normalized 0..1, port of the phone renderer) ──
    private const val HW = 0.045f // shaft half-width
    private const val SL = 0.15f  // shaft left
    private const val SR = 0.55f  // shaft right (notch)
    private const val TIP = 0.82f // arrow tip
    private const val HH = 0.10f  // arrowhead half-width

    // ── Turn symbol dispatch (port of NavigationArrowRenderer.drawTurnArrow) ──

    fun drawTurnSymbol(
        canvas: Canvas,
        type: TurnType?,
        pxSize: Float,
        color: Int,
        left: Float,
        top: Float,
        roundaboutExitNumber: Int = 0
    ) {
        when (type) {
            TurnType.STRAIGHT_ON -> drawUpArrow(canvas, pxSize, color, left, top)
            TurnType.SLIGHTLY_LEFT -> drawAngledArrow(canvas, 135f, pxSize, color, left, top)
            TurnType.LEFT -> drawAngledArrow(canvas, 180f, pxSize, color, left, top)
            TurnType.SHARP_LEFT -> drawSharpTurnArrow(canvas, true, pxSize, color, left, top)
            TurnType.SLIGHTLY_RIGHT -> drawAngledArrow(canvas, 45f, pxSize, color, left, top)
            TurnType.RIGHT -> drawRightArrow(canvas, pxSize, color, left, top)
            TurnType.SHARP_RIGHT -> drawSharpTurnArrow(canvas, false, pxSize, color, left, top)
            TurnType.START -> drawStartArrow(canvas, pxSize, color, left, top)
            TurnType.TARGET_REACHED -> drawTargetMarker(canvas, pxSize, color, left, top)
            TurnType.ROUNDABOUT_ENTER -> drawRoundaboutEnter(canvas, pxSize, color, left, top)
            TurnType.ROUNDABOUT_LEAVE ->
                // A known ordinal gets the numbered ring; otherwise the plain leave arrow,
                // which is what this glyph was before the number existed.
                if (roundaboutExitNumber > 0) {
                    drawRoundaboutExit(canvas, pxSize, color, left, top, roundaboutExitNumber)
                } else {
                    drawRoundaboutLeave(canvas, pxSize, color, left, top)
                }
            TurnType.MOTORWAY_ENTER -> drawMotorwayEnter(canvas, pxSize, color, left, top)
            null -> drawRightArrow(canvas, pxSize, color, left, top)
        }
    }

    // ── Lane symbol dispatch (port of NavigationArrowRenderer.drawLaneArrow) ──

    fun drawLaneSymbol(
        canvas: Canvas,
        turn: LaneTurn,
        pxSize: Float,
        color: Int,
        left: Float,
        top: Float
    ) {
        when (turn) {
            LaneTurn.LEFT -> drawAngledArrow(canvas, 180f, pxSize, color, left, top)
            LaneTurn.SLIGHTLY_LEFT -> drawAngledArrow(canvas, 135f, pxSize, color, left, top)
            LaneTurn.SHARP_LEFT -> drawSharpLaneArrowAt(canvas, true, 0f, 0f, 1f, pxSize, color, left, top)
            LaneTurn.RIGHT -> drawRightArrow(canvas, pxSize, color, left, top)
            LaneTurn.SLIGHTLY_RIGHT -> drawAngledArrow(canvas, 45f, pxSize, color, left, top)
            LaneTurn.SHARP_RIGHT -> drawSharpLaneArrowAt(canvas, false, 0f, 0f, 1f, pxSize, color, left, top)
            LaneTurn.STRAIGHT_ON -> drawUpArrow(canvas, pxSize, color, left, top)
            LaneTurn.LEFT_AND_STRAIGHT -> {
                val off = 0.20f; val sc = 0.65f
                drawAngledArrowAt(canvas, 180f, 0f, off, sc, pxSize, color, left, top)
                drawUpArrowAt(canvas, 0f, -off, sc, pxSize, color, left, top)
            }
            LaneTurn.STRAIGHT_AND_RIGHT -> {
                val off = 0.20f; val sc = 0.65f
                drawUpArrowAt(canvas, 0f, off, sc, pxSize, color, left, top)
                drawRightArrowAt(canvas, 0f, -off, sc, pxSize, color, left, top)
            }
            LaneTurn.MERGE_TO_LEFT -> drawMergeArrow(canvas, true, pxSize, color, left, top)
            LaneTurn.MERGE_TO_RIGHT -> drawMergeArrow(canvas, false, pxSize, color, left, top)
            LaneTurn.STRAIGHT_AND_SLIGHTLY_LEFT -> {
                val off = 0.20f; val sc = 0.65f
                drawUpArrowAt(canvas, 0f, off, sc, pxSize, color, left, top)
                drawAngledArrowAt(canvas, 135f, 0f, -off, sc, pxSize, color, left, top)
            }
            LaneTurn.STRAIGHT_AND_SHARP_LEFT -> {
                val off = 0.24f; val sc = 0.6f
                drawUpArrowAt(canvas, 0f, off, sc, pxSize, color, left, top)
                drawSharpLaneArrowAt(canvas, true, 0f, -off, sc, pxSize, color, left, top)
            }
            LaneTurn.STRAIGHT_AND_SLIGHTLY_RIGHT -> {
                val off = 0.20f; val sc = 0.65f
                drawUpArrowAt(canvas, 0f, off, sc, pxSize, color, left, top)
                drawAngledArrowAt(canvas, 45f, 0f, -off, sc, pxSize, color, left, top)
            }
            LaneTurn.STRAIGHT_AND_SHARP_RIGHT -> {
                val off = 0.24f; val sc = 0.6f
                drawUpArrowAt(canvas, 0f, off, sc, pxSize, color, left, top)
                drawSharpLaneArrowAt(canvas, false, 0f, -off, sc, pxSize, color, left, top)
            }
            LaneTurn.LEFT_AND_RIGHT -> {
                val off = 0.20f; val sc = 0.65f
                drawAngledArrowAt(canvas, 180f, 0f, off, sc, pxSize, color, left, top)
                drawRightArrowAt(canvas, 0f, -off, sc, pxSize, color, left, top)
            }
            LaneTurn.NONE -> {
                val sw = px(0.06f, pxSize)
                val paint = stroke(color, sw)
                canvas.drawLine(px(0.2f, pxSize) + left, px(0.5f, pxSize) + top, px(0.8f, pxSize) + left, px(0.5f, pxSize) + top, paint)
            }
            LaneTurn.NULL -> {}
            LaneTurn.UNKNOWN -> {
                val sw = px(0.06f, pxSize)
                val r = px(0.12f, pxSize)
                val cx = px(0.5f, pxSize) + left
                val cy = px(0.5f, pxSize) + top
                canvas.drawCircle(cx, cy - r * 0.3f, r, stroke(color, sw))
                canvas.drawLine(cx, cy + r * 0.2f, cx, cy + r * 0.6f, stroke(color, sw))
                canvas.drawCircle(cx, cy + r * 0.9f, sw * 0.8f, fill(color))
            }
        }
    }

    // ── Arrow primitives (port of NavigationArrowRenderer) ──

    private fun drawRightArrow(canvas: Canvas, pxSize: Float, color: Int, left: Float, top: Float) {
        val path = Path().apply {
            moveTo(px(SL, pxSize) + left, px(0.5f - HW, pxSize) + top)
            lineTo(px(SR, pxSize) + left, px(0.5f - HW, pxSize) + top)
            lineTo(px(SR, pxSize) + left, px(0.5f - HH, pxSize) + top)
            lineTo(px(TIP, pxSize) + left, px(0.5f, pxSize) + top)
            lineTo(px(SR, pxSize) + left, px(0.5f + HH, pxSize) + top)
            lineTo(px(SR, pxSize) + left, px(0.5f + HW, pxSize) + top)
            lineTo(px(SL, pxSize) + left, px(0.5f + HW, pxSize) + top)
            close()
        }
        canvas.drawPath(path, fill(color))
    }

    private fun drawUpArrow(canvas: Canvas, pxSize: Float, color: Int, left: Float, top: Float) {
        val path = Path().apply {
            moveTo(px(0.5f - HW, pxSize) + left, px(1f - SL, pxSize) + top)
            lineTo(px(0.5f + HW, pxSize) + left, px(1f - SL, pxSize) + top)
            lineTo(px(0.5f + HW, pxSize) + left, px(1f - SR, pxSize) + top)
            lineTo(px(0.5f + HH, pxSize) + left, px(1f - SR, pxSize) + top)
            lineTo(px(0.5f, pxSize) + left, px(1f - TIP, pxSize) + top)
            lineTo(px(0.5f - HH, pxSize) + left, px(1f - SR, pxSize) + top)
            lineTo(px(0.5f - HW, pxSize) + left, px(1f - SR, pxSize) + top)
            close()
        }
        canvas.drawPath(path, fill(color))
    }

    private fun drawAngledArrow(canvas: Canvas, angleDeg: Float, pxSize: Float, color: Int, left: Float, top: Float) {
        val rad = angleDeg * PI.toFloat() / 180f
        val dx = cos(rad)
        val dy = -sin(rad) // screen Y inverted
        val pxv = -dy        // perpendicular
        val pyv = dx

        val cx = 0.5f
        val cy = 0.5f
        val shaftLen = SR - SL
        val totalLen = TIP - SL

        val bx = cx - dx * shaftLen * 0.5f
        val by = cy - dy * shaftLen * 0.5f
        val nx2 = cx + dx * (shaftLen * 0.5f)
        val ny2 = cy + dy * (shaftLen * 0.5f)
        val tx = cx + dx * totalLen * 0.5f
        val ty = cy + dy * totalLen * 0.5f

        val path = Path().apply {
            moveTo(px(bx + pxv * HW, pxSize) + left, px(by + pyv * HW, pxSize) + top)
            lineTo(px(bx - pxv * HW, pxSize) + left, px(by - pyv * HW, pxSize) + top)
            lineTo(px(nx2 - pxv * HW, pxSize) + left, px(ny2 - pyv * HW, pxSize) + top)
            lineTo(px(nx2 - pxv * HH, pxSize) + left, px(ny2 - pyv * HH, pxSize) + top)
            lineTo(px(tx, pxSize) + left, px(ty, pxSize) + top)
            lineTo(px(nx2 + pxv * HH, pxSize) + left, px(ny2 + pyv * HH, pxSize) + top)
            lineTo(px(nx2 + pxv * HW, pxSize) + left, px(ny2 + pyv * HW, pxSize) + top)
            close()
        }
        canvas.drawPath(path, fill(color))
    }

    private fun drawSharpTurnArrow(canvas: Canvas, leftTurn: Boolean, pxSize: Float, color: Int, left: Float, top: Float) {
        val dir = if (leftTurn) -1f else 1f
        val sw = px(0.07f, pxSize)
        val paint = stroke(color, sw)

        val path = Path().apply {
            moveTo(px(0.5f, pxSize) + left, px(0.85f, pxSize) + top)
            lineTo(px(0.5f, pxSize) + left, px(0.55f, pxSize) + top)
            lineTo(px(0.5f + dir * 0.30f, pxSize) + left, px(0.55f, pxSize) + top)
        }
        canvas.drawPath(path, paint)

        val tipX = px(0.5f + dir * 0.30f, pxSize) + left
        val tipY = px(0.55f, pxSize) + top
        val headLen = px(0.10f, pxSize)
        val headW = px(0.05f, pxSize)
        val headPaint = stroke(color, sw * 0.8f)
        canvas.drawLine(tipX, tipY, tipX - dir * headLen, tipY - headW, headPaint)
        canvas.drawLine(tipX, tipY, tipX - dir * headLen, tipY + headW, headPaint)
    }

    private fun drawStartArrow(canvas: Canvas, pxSize: Float, color: Int, left: Float, top: Float) {
        val sw = px(0.06f, pxSize)
        val paint = stroke(color, sw)
        val circleR = px(0.10f, pxSize)
        val cx = px(0.5f, pxSize) + left
        val circleY = px(0.72f, pxSize) + top
        canvas.drawCircle(cx, circleY, circleR, paint)

        val arrowStart = px(0.60f, pxSize) + top
        val arrowEnd = px(0.18f, pxSize) + top
        canvas.drawLine(cx, arrowStart, cx, arrowEnd, paint)
        val hl = px(0.08f, pxSize)
        canvas.drawLine(cx, arrowEnd, cx - hl * 0.5f, arrowEnd + hl, paint)
        canvas.drawLine(cx, arrowEnd, cx + hl * 0.5f, arrowEnd + hl, paint)
    }

    private fun drawTargetMarker(canvas: Canvas, pxSize: Float, color: Int, left: Float, top: Float) {
        val r = px(0.22f, pxSize)
        val sw = px(0.05f, pxSize)
        val cx = px(0.5f, pxSize) + left
        val cy = px(0.5f, pxSize) + top
        canvas.drawCircle(cx, cy, r, stroke(color, sw))
        canvas.drawLine(cx - r * 0.6f, cy, cx + r * 0.6f, cy, stroke(color, sw))
        canvas.drawLine(cx, cy - r * 0.6f, cx, cy + r * 0.6f, stroke(color, sw))
        canvas.drawCircle(cx, cy, sw * 0.8f, fill(color))
    }

    private fun drawRoundaboutEnter(canvas: Canvas, pxSize: Float, color: Int, left: Float, top: Float) {
        val sw = px(0.06f, pxSize)
        val r = px(0.20f, pxSize)
        val cx = px(0.5f, pxSize) + left
        val cy = px(0.5f, pxSize) + top
        canvas.drawCircle(cx, cy, r, stroke(color, sw))
        val es = px(0.85f, pxSize) + top
        val ee = cy + r * 0.7f
        canvas.drawLine(cx, es, cx, ee, stroke(color, sw))
        val hl = px(0.06f, pxSize)
        canvas.drawLine(cx, ee, cx - hl * 0.5f, ee + hl, stroke(color, sw))
        canvas.drawLine(cx, ee, cx + hl * 0.5f, ee + hl, stroke(color, sw))
    }

    private fun drawRoundaboutLeave(canvas: Canvas, pxSize: Float, color: Int, left: Float, top: Float) {
        val sw = px(0.06f, pxSize)
        val r = px(0.20f, pxSize)
        val cx = px(0.5f, pxSize) + left
        val cy = px(0.5f, pxSize) + top
        canvas.drawCircle(cx, cy, r, stroke(color, sw))
        val xs = cx + r * 0.7f
        val xe = px(0.85f, pxSize) + left
        canvas.drawLine(xs, cy, xe, cy, stroke(color, sw))
        val hl = px(0.06f, pxSize)
        canvas.drawLine(xe, cy, xe - hl, cy - hl * 0.5f, stroke(color, sw))
        canvas.drawLine(xe, cy, xe - hl, cy + hl * 0.5f, stroke(color, sw))
    }

    /**
     * The numbered roundabout-exit glyph: the ring with the exit ordinal in its centre.
     *
     * No marker per exit is drawn, and the marker count is never inferred from the ordinal:
     * an ordinal is "which exit you take", not "how many exits the ring has", and the ring's
     * total is not part of the route description. The digit's size follows the bitmap and is
     * shrunk to fit the ring's inner width, so a two-digit ordinal fits the same ring the
     * single-digit one uses and stays legible when the host rescales the icon.
     */
    private fun drawRoundaboutExit(
        canvas: Canvas,
        pxSize: Float,
        color: Int,
        left: Float,
        top: Float,
        exitNumber: Int
    ) {
        val sw = px(0.06f, pxSize)
        val r = px(0.20f, pxSize)
        val cx = px(0.5f, pxSize) + left
        val cy = px(0.5f, pxSize) + top
        canvas.drawCircle(cx, cy, r, stroke(color, sw))

        val text = exitLabel(exitNumber)
        val textPaint = fill(color).apply {
            textAlign = Paint.Align.CENTER
            textSize = px(0.26f, pxSize)
        }
        // Fit the whole number inside the ring, whatever its digit count.
        val innerWidth = r * 2f - sw * 1.5f
        textPaint.textSize = fittedTextSize(textPaint.textSize, textPaint.measureText(text), innerWidth)
        val metrics = textPaint.fontMetrics
        val baseline = cy - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, cx, baseline, textPaint)
    }

    /**
     * The number the numbered roundabout-exit glyph draws for [exitNumber]. Pure so the
     * glyph's digit count is testable without a rasterising canvas: Robolectric's canvas
     * discards the draws, so the number itself is asserted as arithmetic here and its look
     * is the on-device check.
     */
    internal fun exitLabel(exitNumber: Int): String = exitNumber.toString()

    /**
     * The text size to draw [measuredWidthPx] wide text with inside a ring of inner width
     * [innerWidthPx]: the requested size, shrunk only as far as needed when the text would
     * overrun the ring. So a two-digit ordinal fits the same ring a single-digit one uses.
     */
    internal fun fittedTextSize(requestedPx: Float, measuredWidthPx: Float, innerWidthPx: Float): Float =
        if (measuredWidthPx > innerWidthPx && measuredWidthPx > 0f) {
            requestedPx * (innerWidthPx / measuredWidthPx)
        } else {
            requestedPx
        }

    private fun drawMotorwayEnter(canvas: Canvas, pxSize: Float, color: Int, left: Float, top: Float) {
        val sw = px(0.05f, pxSize)
        val w = px(0.50f, pxSize)
        val h = px(0.36f, pxSize)
        val lx = px(0.5f, pxSize) + left - w / 2f
        val ty = px(0.5f, pxSize) + top - h / 2f
        val paint = stroke(color, sw)
        canvas.drawRect(lx, ty, lx + w, ty + h, paint)
        val ax = px(0.30f, pxSize) + left
        val ae = px(0.70f, pxSize) + left
        val cy = px(0.5f, pxSize) + top
        canvas.drawLine(ax, cy, ae, cy, paint)
        val hl = px(0.08f, pxSize)
        canvas.drawLine(ae, cy, ae - hl, cy - hl * 0.5f, paint)
        canvas.drawLine(ae, cy, ae - hl, cy + hl * 0.5f, paint)
    }

    // ── Scaled arrow helpers for compound lane arrows (port of ...At variants) ──

    private fun drawRightArrowAt(
        canvas: Canvas, cxOff: Float, cyOff: Float, scale: Float,
        pxSize: Float, color: Int, left: Float, top: Float
    ) {
        val s = scale
        val leftX = 0.5f + cxOff - (0.5f - SL) * s
        val notchX = 0.5f + cxOff + (SR - 0.5f) * s
        val tipX = 0.5f + cxOff + (TIP - 0.5f) * s
        val cy = 0.5f + cyOff
        val hw = HW * s
        val hh = HH * s
        val path = Path().apply {
            moveTo(px(leftX, pxSize) + left, px(cy - hw, pxSize) + top)
            lineTo(px(notchX, pxSize) + left, px(cy - hw, pxSize) + top)
            lineTo(px(notchX, pxSize) + left, px(cy - hh, pxSize) + top)
            lineTo(px(tipX, pxSize) + left, px(cy, pxSize) + top)
            lineTo(px(notchX, pxSize) + left, px(cy + hh, pxSize) + top)
            lineTo(px(notchX, pxSize) + left, px(cy + hw, pxSize) + top)
            lineTo(px(leftX, pxSize) + left, px(cy + hw, pxSize) + top)
            close()
        }
        canvas.drawPath(path, fill(color))
    }

    private fun drawUpArrowAt(
        canvas: Canvas, cxOff: Float, cyOff: Float, scale: Float,
        pxSize: Float, color: Int, left: Float, top: Float
    ) {
        val s = scale
        val cy = 0.5f + cyOff
        val bottomY = cy + (0.5f - SL) * s
        val notchY = cy - (SR - 0.5f) * s
        val tipY = cy - (TIP - 0.5f) * s
        val hw = HW * s
        val hh = HH * s
        val path = Path().apply {
            moveTo(px(0.5f + cxOff - hw, pxSize) + left, px(bottomY, pxSize) + top)
            lineTo(px(0.5f + cxOff + hw, pxSize) + left, px(bottomY, pxSize) + top)
            lineTo(px(0.5f + cxOff + hw, pxSize) + left, px(notchY, pxSize) + top)
            lineTo(px(0.5f + cxOff + hh, pxSize) + left, px(notchY, pxSize) + top)
            lineTo(px(0.5f + cxOff, pxSize) + left, px(tipY, pxSize) + top)
            lineTo(px(0.5f + cxOff - hh, pxSize) + left, px(notchY, pxSize) + top)
            lineTo(px(0.5f + cxOff - hw, pxSize) + left, px(notchY, pxSize) + top)
            close()
        }
        canvas.drawPath(path, fill(color))
    }

    private fun drawAngledArrowAt(
        canvas: Canvas, angleDeg: Float, cxOff: Float, cyOff: Float, scale: Float,
        pxSize: Float, color: Int, left: Float, top: Float
    ) {
        val rad = angleDeg * PI.toFloat() / 180f
        val dx = cos(rad)
        val dy = -sin(rad)
        val pxv = -dy
        val pyv = dx
        val s = scale

        val cx = 0.5f + cxOff
        val cy = 0.5f + cyOff
        val shaftLen = (SR - SL) * s
        val totalLen = (TIP - SL) * s

        val bx = cx - dx * shaftLen * 0.5f
        val by = cy - dy * shaftLen * 0.5f
        val nx2 = cx + dx * (shaftLen * 0.5f)
        val ny2 = cy + dy * (shaftLen * 0.5f)
        val tx = cx + dx * totalLen * 0.5f
        val ty = cy + dy * totalLen * 0.5f

        val path = Path().apply {
            moveTo(px(bx + pxv * HW * s, pxSize) + left, px(by + pyv * HW * s, pxSize) + top)
            lineTo(px(bx - pxv * HW * s, pxSize) + left, px(by - pyv * HW * s, pxSize) + top)
            lineTo(px(nx2 - pxv * HW * s, pxSize) + left, px(ny2 - pyv * HW * s, pxSize) + top)
            lineTo(px(nx2 - pxv * HH * s, pxSize) + left, px(ny2 - pyv * HH * s, pxSize) + top)
            lineTo(px(tx, pxSize) + left, px(ty, pxSize) + top)
            lineTo(px(nx2 + pxv * HH * s, pxSize) + left, px(ny2 + pyv * HH * s, pxSize) + top)
            lineTo(px(nx2 + pxv * HW * s, pxSize) + left, px(ny2 + pyv * HW * s, pxSize) + top)
            close()
        }
        canvas.drawPath(path, fill(color))
    }

    private fun drawSharpLaneArrowAt(
        canvas: Canvas, leftTurn: Boolean, cxOff: Float, cyOff: Float, scale: Float,
        pxSize: Float, color: Int, left: Float, top: Float
    ) {
        val dir = if (leftTurn) -1f else 1f
        val sw = px(0.09f * scale, pxSize)
        val s = scale
        val cy = 0.5f + cyOff
        val baseY = cy + 0.35f * s
        val tipY = cy - 0.05f * s
        val tipX = 0.5f + cxOff + dir * 0.30f * s

        val path = Path().apply {
            moveTo(px(0.5f + cxOff, pxSize) + left, px(baseY, pxSize) + top)
            lineTo(px(0.5f + cxOff, pxSize) + left, px(tipY, pxSize) + top)
            lineTo(px(tipX, pxSize) + left, px(tipY, pxSize) + top)
        }
        canvas.drawPath(path, stroke(color, sw))

        val headLen = 0.12f * s
        val headW = 0.06f * s
        val headPaint = stroke(color, sw * 0.9f)
        canvas.drawLine(
            px(tipX, pxSize) + left, px(tipY, pxSize) + top,
            px(tipX - dir * headLen, pxSize) + left, px(tipY - headW, pxSize) + top,
            headPaint
        )
        canvas.drawLine(
            px(tipX, pxSize) + left, px(tipY, pxSize) + top,
            px(tipX - dir * headLen, pxSize) + left, px(tipY + headW, pxSize) + top,
            headPaint
        )
    }

    private fun drawMergeArrow(canvas: Canvas, leftTurn: Boolean, pxSize: Float, color: Int, left: Float, top: Float) {
        val dir = if (leftTurn) -1f else 1f
        val sw = px(0.08f, pxSize)
        val paint = stroke(color, sw)

        val path = Path().apply {
            moveTo(px(0.5f, pxSize) + left, px(0.80f, pxSize) + top)
            lineTo(px(0.5f, pxSize) + left, px(0.50f, pxSize) + top)
            lineTo(px(0.5f + dir * 0.25f, pxSize) + left, px(0.30f, pxSize) + top)
        }
        canvas.drawPath(path, paint)

        val tipX = px(0.5f + dir * 0.25f, pxSize) + left
        val tipY = px(0.30f, pxSize) + top
        val headLen = px(0.10f, pxSize)
        val headW = px(0.05f, pxSize)
        val ddx = dir * 0.25f
        val ddy = -0.20f
        val len = sqrt(ddx * ddx + ddy * ddy)
        val ndx = ddx / len
        val ndy = ddy / len
        val pdx = -ndy
        val pdy = ndx
        val headPaint = stroke(color, sw * 0.9f)
        canvas.drawLine(
            tipX, tipY,
            tipX - ndx * headLen + pdx * headW,
            tipY - ndy * headLen + pdy * headW,
            headPaint
        )
        canvas.drawLine(
            tipX, tipY,
            tipX - ndx * headLen - pdx * headW,
            tipY - ndy * headLen - pdy * headW,
            headPaint
        )
    }

    // ── Paint helpers ──

    private fun px(v: Float, pxSize: Float): Float = pxSize * v

    private fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private fun stroke(
        color: Int,
        width: Float,
        cap: Paint.Cap = Paint.Cap.ROUND,
        join: Paint.Join = Paint.Join.ROUND
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.color = color
        strokeWidth = width
        strokeCap = cap
        strokeJoin = join
    }
}
