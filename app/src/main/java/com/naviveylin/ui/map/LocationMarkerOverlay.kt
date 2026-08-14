package com.naviveylin.ui.map

import android.location.Location
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.naviveylin.core.ProjectionUtils
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compose overlay that renders the GPS location marker on top of the map.
 *
 * Draws:
 * - Accuracy circle (alpha fill + border) — only when accuracy is poor
 * - Compass-style direction arrow with drop shadow (two triangles, centered on GPS position)
 */
@Composable
fun LocationMarkerOverlay(
    location: Location?,
    viewport: MapRenderer.RenderViewport?,
    screenWidthPx: Int,
    screenHeightPx: Int,
    dpi: Double,
    modifier: Modifier = Modifier
) {
    if (location == null || viewport == null) return
    if (screenWidthPx <= 0 || screenHeightPx <= 0) return

    val density = LocalDensity.current
    val minRadiusPx = with(density) { MIN_RADIUS_DP.toPx() }

    val projected = ProjectionUtils.viewport(
        viewport.lat, viewport.lon, viewport.mag,
        screenWidthPx, screenHeightPx, dpi, viewport.angle
    )
    val (sx, sy) = projected.geoToScreenRotated(location.latitude, location.longitude)

    if (sx.toFloat() < -MARGIN_PX || sx.toFloat() > screenWidthPx + MARGIN_PX ||
        sy.toFloat() < -MARGIN_PX || sy.toFloat() > screenHeightPx + MARGIN_PX
    ) return

    // Meters per pixel at the rendered magnification for the accuracy circle.
    val scale = ProjectionUtils.computeScale(viewport.mag, screenWidthPx.toDouble(), dpi)
    val metersPerPixel = ProjectionUtils.EARTH_RADIUS / scale.scaleGradtorad

    val accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f
    val accuracyRadiusPx = if (accuracyMeters > 0f && metersPerPixel > 0.0) {
        (accuracyMeters / metersPerPixel).coerceAtLeast(minRadiusPx.toDouble()).toFloat()
    } else {
        minRadiusPx
    }

    val hasBearing = location.hasBearing() && location.bearing >= 0f
    val rawBearing = if (hasBearing) location.bearing.toDouble() else 0.0
    // In follow-direction mode the map angle is set to -bearing, so screenBearing is 0
    // (arrow points up). In north-up mode the map angle is 0, so screenBearing = bearing.
    val bearingDegrees = ProjectionUtils.screenBearing(rawBearing, viewport.angle).toFloat()
    val trueBearing = if (hasBearing) location.bearing.toInt() else -1

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(sx.toFloat(), sy.toFloat())

        if (accuracyRadiusPx >= POOR_ACCURACY_THRESHOLD_PX) {
            drawAccuracyCircle(center, accuracyRadiusPx)
        }

        if (hasBearing) {
            drawCompassArrowWithShadow(center, bearingDegrees)
        }
        if (markerLogCount++ % 10 == 0) {
            Log.d("Marker", "draw sx=${sx.toInt()}, sy=${sy.toInt()} " +
                    "trueBearing=${trueBearing} screenBearing=${bearingDegrees.toInt()} " +
                    "vp=${"%.5f".format(viewport.lat)},${"%.5f".format(viewport.lon)} " +
                    "mag=${viewport.mag} angle=${Math.toDegrees(viewport.angle).toInt()}")
        }
    }
}

private var markerLogCount: Int = 0

private fun DrawScope.drawAccuracyCircle(center: Offset, radiusPx: Float) {
    drawCircle(
        color = ACCURACY_FILL_COLOR,
        radius = radiusPx,
        center = center
    )
    drawCircle(
        color = ACCURACY_BORDER_COLOR,
        radius = radiusPx,
        center = center,
        style = Stroke(width = 2.dp.toPx())
    )
}

private fun DrawScope.drawCompassArrowWithShadow(center: Offset, bearingDegrees: Float) {
    val arrowSize = ARROW_SIZE_DP.toPx()
    val halfSize = arrowSize / 2f
    val shadowOffset = SHADOW_OFFSET_DP.toPx()

    // Compass-style arrow: two triangles forming a clear direction indicator
    // Forward triangle (long, wide) + backward triangle (short, narrow tail)
    val arrowPath = Path().apply {
        // Forward triangle — tip at front, wide base across center
        moveTo(0f, -halfSize)                    // tip
        lineTo(-halfSize * 0.45f, 0f)             // left base (wider)
        lineTo(halfSize * 0.45f, 0f)              // right base (wider)
        close()

        // Backward triangle — short tail pointing opposite direction
        moveTo(0f, halfSize * 0.25f)              // tail tip (shorter)
        lineTo(-halfSize * 0.25f, 0f)              // left base
        lineTo(halfSize * 0.25f, 0f)               // right base
        close()
    }

    // Shadow
    translate(left = center.x + shadowOffset, top = center.y + shadowOffset) {
        rotate(degrees = bearingDegrees, pivot = Offset.Zero) {
            drawPath(path = arrowPath, color = SHADOW_COLOR, style = Fill)
        }
    }

    // Main arrow
    translate(left = center.x, top = center.y) {
        rotate(degrees = bearingDegrees, pivot = Offset.Zero) {
            drawPath(path = arrowPath, color = MARKER_COLOR, style = Fill)
        }
    }
}

private val ACCURACY_FILL_COLOR = Color(0x1A4A90D9)   // ~10% blue
private val ACCURACY_BORDER_COLOR = Color(0x664A90D9) // ~40% blue
private val MARKER_COLOR = Color(0xFF4A90D9)           // solid blue
private val SHADOW_COLOR = Color(0x40000000)            // 25% black

private val ARROW_SIZE_DP: Dp = 56.dp
private val MIN_RADIUS_DP: Dp = 4.dp
private val SHADOW_OFFSET_DP: Dp = 2.dp
private const val MARGIN_PX = 100
private const val POOR_ACCURACY_THRESHOLD_PX = 20f
