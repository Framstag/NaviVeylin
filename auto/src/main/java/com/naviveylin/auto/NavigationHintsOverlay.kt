package com.naviveylin.auto

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.TurnType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Canvas rendering of the navigation turn/lane symbols, shared by the phone
 * surface arrows and the host-maneuver icons ([ManeuverGlyphs]).
 *
 * All arrow geometry is a direct port of the phone's
 * `NavigationArrowRenderer` (Compose DrawScope → android.graphics), using the
 * same normalized constants so the on-car symbols match the phone symbols.
 */
object NavigationHintsOverlay {

    // ── Arrow geometry constants (normalized 0..1, port of the phone renderer) ──
    private const val HW = 0.045f // shaft half-width
    private const val SL = 0.15f  // shaft left
    private const val SR = 0.55f  // shaft right (notch)
    private const val TIP = 0.82f // arrow tip
    private const val HH = 0.10f  // arrowhead half-width

    /** Format a distance in meters like the phone overlay (e.g. "1.2 km", "350 m"), rounded. */
    fun formatDistance(meters: Double): String {
        val rounded = NavigationTemplateMapper.roundDistanceMeters(meters)
        return if (rounded >= 1000) "%.1f km".format(rounded / 1000)
        else "%.0f m".format(rounded)
    }

    // ── Turn symbol dispatch (port of NavigationArrowRenderer.drawTurnArrow) ──

    fun drawTurnSymbol(
        canvas: Canvas,
        type: TurnType?,
        pxSize: Float,
        color: Int,
        left: Float,
        top: Float
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
            TurnType.ROUNDABOUT_LEAVE -> drawRoundaboutLeave(canvas, pxSize, color, left, top)
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
