package com.naviveylin.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.TurnType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── Sealed interface ──

sealed interface NavSymbol {
    data class TurnArrow(val type: TurnType) : NavSymbol
    data class LaneArrow(val turn: LaneTurn) : NavSymbol
    data class Roundabout(
        val exitCount: Int,
        val exitAngles: List<Float>? = null,
        val selectedExit: Int = 0,
        val entryAngle: Float? = null
    ) : NavSymbol
}

// ── Main composable ──

@Composable
fun NavigationArrow(
    symbol: NavSymbol,
    size: Dp = 48.dp,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val resolvedColor = if (color == Color.Unspecified) {
        androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    } else color

    val density = LocalDensity.current
    val pxSize = with(density) { size.toPx() }

    Box(
        modifier = modifier.size(size).graphicsLayer(alpha = 0.99f, clip = true),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().drawBehind {
            when (symbol) {
                is NavSymbol.TurnArrow -> drawTurnArrow(symbol.type, this.size.width, resolvedColor)
                is NavSymbol.LaneArrow -> drawLaneArrow(symbol.turn, this.size.width, resolvedColor)
                is NavSymbol.Roundabout -> drawRoundabout(symbol, this.size.width, resolvedColor)
            }
        })
    }
}

// ── Coordinate helpers (normalized 0..1) ──

private fun DrawScope.nx(x: Float) = size.width * x
private fun DrawScope.ny(y: Float) = size.height * y
private fun DrawScope.nw(w: Float) = size.width * w
private fun DrawScope.nh(h: Float) = size.height * h

// ── Arrow geometry constants ──

private const val HW = 0.045f       // shaft half-width
private const val SL = 0.15f        // shaft left
private const val SR = 0.55f        // shaft right (notch)
private const val TIP = 0.82f       // arrow tip
private const val HH = 0.10f        // arrowhead half-width

// ── Turn arrow dispatch ──

private fun DrawScope.drawTurnArrow(type: TurnType, pxSize: Float, color: Color) {
    when (type) {
        TurnType.STRAIGHT_ON -> drawUpArrow(color)
        TurnType.SLIGHTLY_LEFT -> drawAngledArrow(color, 135f)
        TurnType.LEFT -> drawAngledArrow(color, 180f)
        TurnType.SHARP_LEFT -> drawSharpTurnArrow(color, left = true)
        TurnType.SLIGHTLY_RIGHT -> drawAngledArrow(color, 45f)
        TurnType.RIGHT -> drawRightArrow(color)
        TurnType.SHARP_RIGHT -> drawSharpTurnArrow(color, left = false)
        TurnType.START -> drawStartArrow(color)
        TurnType.TARGET_REACHED -> drawTargetMarker(color)
        TurnType.ROUNDABOUT_ENTER -> drawRoundaboutEnter(color)
        TurnType.ROUNDABOUT_LEAVE -> drawRoundaboutLeave(color)
        TurnType.MOTORWAY_ENTER -> drawMotorwayEnter(color)
        null -> drawRightArrow(color)
    }
}

// ── Right arrow ──

private fun DrawScope.drawRightArrow(color: Color) {
    val path = Path().apply {
        moveTo(nx(SL), ny(0.5f - HW))
        lineTo(nx(SR), ny(0.5f - HW))
        lineTo(nx(SR), ny(0.5f - HH))
        lineTo(nx(TIP), ny(0.5f))
        lineTo(nx(SR), ny(0.5f + HH))
        lineTo(nx(SR), ny(0.5f + HW))
        lineTo(nx(SL), ny(0.5f + HW))
        close()
    }
    drawPath(path, color, style = Fill)
}

// ── Up arrow ──

private fun DrawScope.drawUpArrow(color: Color) {
    val path = Path().apply {
        moveTo(nx(0.5f - HW), ny(1f - SL))
        lineTo(nx(0.5f + HW), ny(1f - SL))
        lineTo(nx(0.5f + HW), ny(1f - SR))
        lineTo(nx(0.5f + HH), ny(1f - SR))
        lineTo(nx(0.5f), ny(1f - TIP))
        lineTo(nx(0.5f - HH), ny(1f - SR))
        lineTo(nx(0.5f - HW), ny(1f - SR))
        close()
    }
    drawPath(path, color, style = Fill)
}

// ── Angled arrow (0=right, 90=up, 180=left) ──

private fun DrawScope.drawAngledArrow(color: Color, angleDeg: Float) {
    val rad = angleDeg * PI.toFloat() / 180f
    val dx = cos(rad)
    val dy = -sin(rad)  // screen Y inverted
    val px = -dy        // perpendicular
    val py = dx

    val cx = 0.5f
    val cy = 0.5f
    val shaftLen = SR - SL
    val totalLen = TIP - SL

    // Shaft start (base) and end (notch)
    val bx = cx - dx * shaftLen * 0.5f
    val by = cy - dy * shaftLen * 0.5f
    val nx2 = cx + dx * (shaftLen * 0.5f)
    val ny2 = cy + dy * (shaftLen * 0.5f)
    val tx = cx + dx * totalLen * 0.5f
    val ty = cy + dy * totalLen * 0.5f

    val path = Path().apply {
        // Shaft: rectangle from base to notch
        moveTo(nx(bx + px * HW), ny(by + py * HW))
        lineTo(nx(bx - px * HW), ny(by - py * HW))
        lineTo(nx(nx2 - px * HW), ny(ny2 - py * HW))
        // Arrowhead top
        lineTo(nx(nx2 - px * HH), ny(ny2 - py * HH))
        lineTo(nx(tx), ny(ty))
        // Arrowhead bottom
        lineTo(nx(nx2 + px * HH), ny(ny2 + py * HH))
        // Back to shaft bottom
        lineTo(nx(nx2 + px * HW), ny(ny2 + py * HW))
        close()
    }
    drawPath(path, color, style = Fill)
}

// ── Sharp turn arrows ──
// Design: go up, then bend sharply to the side.
// SHARP_LEFT:  up → bend left (arrowhead points left-down)
// SHARP_RIGHT: up → bend right (arrowhead points right-down)

private fun DrawScope.drawSharpTurnArrow(color: Color, left: Boolean) {
    val dir = if (left) -1f else 1f
    val sw = nw(0.07f)

    val path = Path().apply {
        // Vertical shaft: bottom to center
        moveTo(nx(0.5f), ny(0.85f))
        lineTo(nx(0.5f), ny(0.55f))
        // Horizontal bend: center to side (this is the tip)
        lineTo(nx(0.5f + dir * 0.30f), ny(0.55f))
    }
    drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Arrowhead at tip, pointing in direction of horizontal segment
    val tipX = 0.5f + dir * 0.30f
    val tipY = 0.55f
    val headLen = 0.10f
    val headW = 0.05f
    drawLine(color, Offset(nx(tipX), ny(tipY)),
        Offset(nx(tipX - dir * headLen), ny(tipY - headW)),
        sw * 0.8f, cap = StrokeCap.Round)
    drawLine(color, Offset(nx(tipX), ny(tipY)),
        Offset(nx(tipX - dir * headLen), ny(tipY + headW)),
        sw * 0.8f, cap = StrokeCap.Round)
}

// ── START arrow (circle + arrow pointing UP) ──

private fun DrawScope.drawStartArrow(color: Color) {
    val sw = nw(0.06f)
    // Circle (start point) at bottom
    val circleR = nw(0.10f)
    drawCircle(color, circleR, Offset(nx(0.5f), ny(0.72f)), style = Stroke(sw))
    // Arrow from circle pointing up
    val arrowStart = ny(0.60f)
    val arrowEnd = ny(0.18f)
    drawLine(color, Offset(nx(0.5f), arrowStart), Offset(nx(0.5f), arrowEnd), sw, cap = StrokeCap.Round)
    // Arrowhead
    val hl = nw(0.08f)
    drawLine(color, Offset(nx(0.5f), arrowEnd), Offset(nx(0.5f) - hl * 0.5f, arrowEnd + hl), sw, cap = StrokeCap.Round)
    drawLine(color, Offset(nx(0.5f), arrowEnd), Offset(nx(0.5f) + hl * 0.5f, arrowEnd + hl), sw, cap = StrokeCap.Round)
}

// ── Target marker (diamond + dot) ──

private fun DrawScope.drawTargetMarker(color: Color) {
    // Target: circle outline + crosshair + center dot
    val r = nw(0.22f)
    val sw = nw(0.05f)
    drawCircle(color, r, Offset(nx(0.5f), ny(0.5f)), style = Stroke(sw))
    // Crosshair lines
    drawLine(color, Offset(nx(0.5f - r * 0.6f), ny(0.5f)), Offset(nx(0.5f + r * 0.6f), ny(0.5f)), sw, cap = StrokeCap.Round)
    drawLine(color, Offset(nx(0.5f), ny(0.5f - r * 0.6f)), Offset(nx(0.5f), ny(0.5f + r * 0.6f)), sw, cap = StrokeCap.Round)
    // Center dot
    drawCircle(color, sw * 0.8f, Offset(nx(0.5f), ny(0.5f)))
}

// ── Roundabout enter/leave ──

private fun DrawScope.drawRoundaboutEnter(color: Color) {
    val sw = nw(0.06f)
    val r = nw(0.20f)
    drawCircle(color, r, Offset(nx(0.5f), ny(0.5f)), style = Stroke(sw))
    val es = ny(0.85f)
    val ee = ny(0.5f + r / nw(1f) * 0.7f)
    drawLine(color, Offset(nx(0.5f), es), Offset(nx(0.5f), ee), sw, cap = StrokeCap.Round)
    val hl = nw(0.06f)
    drawLine(color, Offset(nx(0.5f), ee), Offset(nx(0.5f) - hl * 0.5f, ee + hl), sw, cap = StrokeCap.Round)
    drawLine(color, Offset(nx(0.5f), ee), Offset(nx(0.5f) + hl * 0.5f, ee + hl), sw, cap = StrokeCap.Round)
}

private fun DrawScope.drawRoundaboutLeave(color: Color) {
    val sw = nw(0.06f)
    val r = nw(0.20f)
    drawCircle(color, r, Offset(nx(0.5f), ny(0.5f)), style = Stroke(sw))
    val xs = nx(0.5f + r / nw(1f) * 0.7f)
    val xe = nx(0.85f)
    drawLine(color, Offset(xs, ny(0.5f)), Offset(xe, ny(0.5f)), sw, cap = StrokeCap.Round)
    val hl = nw(0.06f)
    drawLine(color, Offset(xe, ny(0.5f)), Offset(xe - hl, ny(0.5f) - hl * 0.5f), sw, cap = StrokeCap.Round)
    drawLine(color, Offset(xe, ny(0.5f)), Offset(xe - hl, ny(0.5f) + hl * 0.5f), sw, cap = StrokeCap.Round)
}

// ── Motorway enter ──

private fun DrawScope.drawMotorwayEnter(color: Color) {
    val sw = nw(0.05f)
    val w = nw(0.50f)
    val h = nh(0.36f)
    val lx = nx(0.5f) - w / 2f
    val ty2 = ny(0.5f) - h / 2f
    // Simple stroked rectangle
    drawRect(color, topLeft = Offset(lx, ty2), size = Size(w, h), style = Stroke(sw))
    // Arrow inside
    val ax = nx(0.30f)
    val ae = nx(0.70f)
    drawLine(color, Offset(ax, ny(0.5f)), Offset(ae, ny(0.5f)), sw, cap = StrokeCap.Round)
    val hl = nw(0.08f)
    drawLine(color, Offset(ae, ny(0.5f)), Offset(ae - hl, ny(0.5f) - hl * 0.5f), sw, cap = StrokeCap.Round)
    drawLine(color, Offset(ae, ny(0.5f)), Offset(ae - hl, ny(0.5f) + hl * 0.5f), sw, cap = StrokeCap.Round)
}

// ── Lane arrow dispatch ──

private fun DrawScope.drawLaneArrow(turn: LaneTurn, pxSize: Float, color: Color) {
    when (turn) {
        LaneTurn.LEFT -> drawAngledArrow(color, 180f)
        LaneTurn.SLIGHTLY_LEFT -> drawAngledArrow(color, 135f)
        LaneTurn.SHARP_LEFT -> drawSharpLaneArrow(color, left = true)
        LaneTurn.RIGHT -> drawRightArrow(color)
        LaneTurn.SLIGHTLY_RIGHT -> drawAngledArrow(color, 45f)
        LaneTurn.SHARP_RIGHT -> drawSharpLaneArrow(color, left = false)
        LaneTurn.STRAIGHT_ON -> drawUpArrow(color)
        LaneTurn.LEFT_AND_STRAIGHT -> {
            val off = 0.20f; val sc = 0.65f
            drawAngledArrowAt(color, 180f, 0f, off, sc)
            drawUpArrowAt(color, 0f, -off, sc)
        }
        LaneTurn.STRAIGHT_AND_RIGHT -> {
            val off = 0.20f; val sc = 0.65f
            drawUpArrowAt(color, 0f, off, sc)
            drawRightArrowAt(color, 0f, -off, sc)
        }
        LaneTurn.MERGE_TO_LEFT -> drawMergeArrow(color, left = true)
        LaneTurn.MERGE_TO_RIGHT -> drawMergeArrow(color, left = false)
        LaneTurn.STRAIGHT_AND_SLIGHTLY_LEFT -> {
            val off = 0.20f; val sc = 0.65f
            drawUpArrowAt(color, 0f, off, sc)
            drawAngledArrowAt(color, 135f, 0f, -off, sc)
        }
        LaneTurn.STRAIGHT_AND_SHARP_LEFT -> {
            val off = 0.24f; val sc = 0.6f
            drawUpArrowAt(color, 0f, off, sc)
            drawSharpLaneArrowAt(color, true, 0f, -off, sc)
        }
        LaneTurn.STRAIGHT_AND_SLIGHTLY_RIGHT -> {
            val off = 0.20f; val sc = 0.65f
            drawUpArrowAt(color, 0f, off, sc)
            drawAngledArrowAt(color, 45f, 0f, -off, sc)
        }
        LaneTurn.STRAIGHT_AND_SHARP_RIGHT -> {
            val off = 0.24f; val sc = 0.6f
            drawUpArrowAt(color, 0f, off, sc)
            drawSharpLaneArrowAt(color, false, 0f, -off, sc)
        }
        LaneTurn.LEFT_AND_RIGHT -> {
            val off = 0.20f; val sc = 0.65f
            drawAngledArrowAt(color, 180f, 0f, off, sc)
            drawRightArrowAt(color, 0f, -off, sc)
        }
        LaneTurn.NONE -> {
            val sw = nw(0.06f)
            drawLine(color, Offset(nx(0.2f), ny(0.5f)), Offset(nx(0.8f), ny(0.5f)), sw, cap = StrokeCap.Round)
        }
        LaneTurn.NULL -> {}
        LaneTurn.UNKNOWN -> {
            val sw = nw(0.06f)
            val r = nw(0.12f)
            drawCircle(color, r, Offset(nx(0.5f), ny(0.5f - r / nw(1f) * 0.3f)), style = Stroke(sw))
            drawLine(color, Offset(nx(0.5f), ny(0.5f + r / nw(1f) * 0.2f)),
                Offset(nx(0.5f), ny(0.5f + r / nw(1f) * 0.6f)), sw, cap = StrokeCap.Round)
            drawCircle(color, sw * 0.8f, Offset(nx(0.5f), ny(0.5f + r / nw(1f) * 0.9f)))
        }
    }
}

// ── Scaled arrow helpers for compound lane arrows ──

private fun DrawScope.drawRightArrowAt(color: Color, cxOff: Float, cyOff: Float = 0f, scale: Float = 1f) {
    val s = scale
    val leftX = 0.5f + cxOff - (0.5f - SL) * s
    val notchX = 0.5f + cxOff + (SR - 0.5f) * s
    val tipX = 0.5f + cxOff + (TIP - 0.5f) * s
    val cy = 0.5f + cyOff
    val hw = HW * s
    val hh = HH * s
    val path = Path().apply {
        moveTo(nx(leftX), ny(cy - hw))
        lineTo(nx(notchX), ny(cy - hw))
        lineTo(nx(notchX), ny(cy - hh))
        lineTo(nx(tipX), ny(cy))
        lineTo(nx(notchX), ny(cy + hh))
        lineTo(nx(notchX), ny(cy + hw))
        lineTo(nx(leftX), ny(cy + hw))
        close()
    }
    drawPath(path, color, style = Fill)
}

private fun DrawScope.drawUpArrowAt(color: Color, cxOff: Float, cyOff: Float = 0f, scale: Float = 1f) {
    val s = scale
    val cy = 0.5f + cyOff
    val bottomY = cy + (0.5f - SL) * s
    val notchY = cy - (SR - 0.5f) * s
    val tipY = cy - (TIP - 0.5f) * s
    val hw = HW * s
    val hh = HH * s
    val path = Path().apply {
        moveTo(nx(0.5f + cxOff - hw), ny(bottomY))
        lineTo(nx(0.5f + cxOff + hw), ny(bottomY))
        lineTo(nx(0.5f + cxOff + hw), ny(notchY))
        lineTo(nx(0.5f + cxOff + hh), ny(notchY))
        lineTo(nx(0.5f + cxOff), ny(tipY))
        lineTo(nx(0.5f + cxOff - hh), ny(notchY))
        lineTo(nx(0.5f + cxOff - hw), ny(notchY))
        close()
    }
    drawPath(path, color, style = Fill)
}

private fun DrawScope.drawAngledArrowAt(color: Color, angleDeg: Float, cxOff: Float, cyOff: Float = 0f, scale: Float = 1f) {
    val rad = angleDeg * PI.toFloat() / 180f
    val dx = cos(rad)
    val dy = -sin(rad)
    val px = -dy
    val py = dx
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
        moveTo(nx(bx + px * HW * s), ny(by + py * HW * s))
        lineTo(nx(bx - px * HW * s), ny(by - py * HW * s))
        lineTo(nx(nx2 - px * HW * s), ny(ny2 - py * HW * s))
        lineTo(nx(nx2 - px * HH * s), ny(ny2 - py * HH * s))
        lineTo(nx(tx), ny(ty))
        lineTo(nx(nx2 + px * HH * s), ny(ny2 + py * HH * s))
        lineTo(nx(nx2 + px * HW * s), ny(ny2 + py * HW * s))
        close()
    }
    drawPath(path, color, style = Fill)
}

private fun DrawScope.drawSharpLaneArrowAt(color: Color, left: Boolean, cxOff: Float, cyOff: Float = 0f, scale: Float = 1f) {
    val dir = if (left) -1f else 1f
    val sw = nw(0.09f * scale)
    val s = scale
    val cy = 0.5f + cyOff
    val baseY = cy + 0.35f * s
    val tipY = cy - 0.05f * s
    val tipX = 0.5f + cxOff + dir * 0.30f * s

    val path = Path().apply {
        moveTo(nx(0.5f + cxOff), ny(baseY))
        lineTo(nx(0.5f + cxOff), ny(tipY))
        lineTo(nx(tipX), ny(tipY))
    }
    drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

    val headLen = 0.12f * s
    val headW = 0.06f * s
    drawLine(color, Offset(nx(tipX), ny(tipY)),
        Offset(nx(tipX - dir * headLen), ny(tipY - headW)),
        sw * 0.9f, cap = StrokeCap.Round)
    drawLine(color, Offset(nx(tipX), ny(tipY)),
        Offset(nx(tipX - dir * headLen), ny(tipY + headW)),
        sw * 0.9f, cap = StrokeCap.Round)
}

private fun DrawScope.drawSharpLaneArrow(color: Color, left: Boolean) {
    drawSharpLaneArrowAt(color, left, 0f, 0f, 1f)
}

// ── Merge arrow (single bent shaft: straight then diagonal) ──

private fun DrawScope.drawMergeArrow(color: Color, left: Boolean) {
    val dir = if (left) -1f else 1f
    val sw = nw(0.08f)

    val path = Path().apply {
        // Straight segment: bottom to center
        moveTo(nx(0.5f), ny(0.80f))
        lineTo(nx(0.5f), ny(0.50f))
        // Diagonal bend: center to side
        lineTo(nx(0.5f + dir * 0.25f), ny(0.30f))
    }
    drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Arrowhead at end of diagonal
    val tipX = 0.5f + dir * 0.25f
    val tipY = 0.30f
    val headLen = 0.10f
    val headW = 0.05f
    // Direction of diagonal segment
    val ddx = dir * 0.25f  // dx of diagonal
    val ddy = -0.20f       // dy of diagonal (upward)
    val len = kotlin.math.sqrt(ddx * ddx + ddy * ddy)
    val ndx = ddx / len
    val ndy = ddy / len
    // Perpendicular
    val pdx = -ndy
    val pdy = ndx

    drawLine(color, Offset(nx(tipX), ny(tipY)),
        Offset(nx(tipX - ndx * headLen + pdx * headW),
               ny(tipY - ndy * headLen + pdy * headW)),
        sw * 0.9f, cap = StrokeCap.Round)
    drawLine(color, Offset(nx(tipX), ny(tipY)),
        Offset(nx(tipX - ndx * headLen - pdx * headW),
               ny(tipY - ndy * headLen - pdy * headW)),
        sw * 0.9f, cap = StrokeCap.Round)
}

// ── Roundabout ──

private fun DrawScope.drawRoundabout(symbol: NavSymbol.Roundabout, pxSize: Float, color: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = nw(0.28f)
    val sw = nw(0.05f)
    val exitCount = symbol.exitCount.coerceIn(2, 12)
    val selectedExit = symbol.selectedExit.coerceIn(0, exitCount - 1)

    drawCircle(color.copy(alpha = 0.4f), r, Offset(cx, cy), style = Stroke(sw))

    val angles: List<Float> = if (symbol.exitAngles != null && symbol.exitAngles.size >= exitCount) {
        symbol.exitAngles
    } else {
        (0 until exitCount).map { i ->
            (270f + 360f / exitCount * i) % 360f
        }
    }

    val entryAngle = symbol.entryAngle ?: 270f
    val entryRad = entryAngle * PI.toFloat() / 180f
    val ex = cx + cos(entryRad) * r
    val ey = cy + sin(entryRad) * r
    val eix = cx + cos(entryRad) * r * 0.6f
    val eiy = cy + sin(entryRad) * r * 0.6f
    drawLine(color, Offset(ex, ey), Offset(eix, eiy), sw * 1.2f, cap = StrokeCap.Round)

    for (i in 0 until exitCount) {
        val a = angles[i] * PI.toFloat() / 180f
        val sel = i == selectedExit
        val ec = if (sel) color else color.copy(alpha = 0.35f)
        val ew = if (sel) sw * 1.5f else sw
        val ix = cx + cos(a) * r
        val iy = cy + sin(a) * r
        val ol = if (sel) nw(0.12f) else nw(0.08f)
        val ox = cx + cos(a) * (r + ol)
        val oy = cy + sin(a) * (r + ol)
        drawLine(ec, Offset(ix, iy), Offset(ox, oy), ew, cap = StrokeCap.Round)

        if (sel) {
            val hl = nw(0.05f)
            val px2 = -sin(a)
            val py2 = cos(a)
            drawLine(ec, Offset(ox, oy),
                Offset(ox - hl * cos(a) + px2 * hl * 0.5f,
                       oy - hl * sin(a) + py2 * hl * 0.5f),
                ew * 0.8f, cap = StrokeCap.Round)
            drawLine(ec, Offset(ox, oy),
                Offset(ox - hl * cos(a) - px2 * hl * 0.5f,
                       oy - hl * sin(a) - py2 * hl * 0.5f),
                ew * 0.8f, cap = StrokeCap.Round)
        }
    }

    drawCircle(color.copy(alpha = 0.5f), nw(0.03f), Offset(cx, cy))
}

// ── Shared utility ──

fun formatDistance(meters: Double): String {
    return if (meters >= 1000) "%.1f km".format(meters / 1000)
    else "%.0f m".format(meters)
}
