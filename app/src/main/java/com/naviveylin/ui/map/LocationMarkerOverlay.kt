package com.naviveylin.ui.map

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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naviveylin.core.ProjectionUtils

/**
 * Compose overlay that renders the GPS location marker on top of the map.
 *
 * The marker is drawn per-frame on a layer above the rendered map bitmap and is
 * NEVER baked into cached tiles, the back buffer, or the front buffer — those
 * contain only static map content (specs: gps-location-marker, tile-cache,
 * double-buffering). Projection uses the viewport of the displayed bitmap
 * ([MapRenderer.RenderViewport] carried by the emitted frame), so the marker
 * stays anchored to the map features actually on screen.
 *
 * Draws:
 * - Accuracy circle (alpha fill + border) — only when accuracy is poor
 * - Compass-style direction arrow with drop shadow (two triangles, centered on GPS position)
 */
@Composable
fun LocationMarkerOverlay(
    lat: Double,
    lon: Double,
    bearing: Double,
    accuracy: Double,
    viewport: MapRenderer.RenderViewport?,
    dpi: Double,
    modifier: Modifier = Modifier
) {
    if (lat.isNaN() || lon.isNaN() || viewport == null) return
    if (dpi <= 0.0) return

    val density = LocalDensity.current
    val minRadiusPx = with(density) { MIN_RADIUS_DP.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = size.width.toDouble()
        val screenHeightPx = size.height.toDouble()
        val center = projectMarker(lat, lon, viewport, screenWidthPx, screenHeightPx, dpi)
            ?: return@Canvas

        // Meters per pixel at the rendered magnification for the accuracy circle.
        val scale = ProjectionUtils.computeScale(viewport.mag, screenWidthPx, dpi)
        val metersPerPixel = ProjectionUtils.EARTH_RADIUS / scale.scaleGradtorad

        val accuracyRadiusPx = if (accuracy > 0f && metersPerPixel > 0.0) {
            (accuracy / metersPerPixel).coerceAtLeast(minRadiusPx.toDouble()).toFloat()
        } else {
            minRadiusPx
        }

        // Bearing < 0 (unavailable or north-up orientation) draws the arrow pointing
        // north on the map. Screen bearing = raw bearing + map rotation (same sign
        // convention the native renderer used — do not flip).
        val rawBearing = if (bearing >= 0.0) bearing else 0.0
        val bearingDegrees = ProjectionUtils.screenBearing(rawBearing, viewport.angle).toFloat()

        if (accuracyRadiusPx >= POOR_ACCURACY_THRESHOLD_PX) {
            drawAccuracyCircle(center, accuracyRadiusPx)
        }

        drawCompassArrowWithShadow(center, bearingDegrees)
        if (markerLogCount++ % 10 == 0) {
            Log.d("Marker", "draw sx=${center.x.toInt()}, sy=${center.y.toInt()} " +
                    "bearing=${bearing.toInt()} screenBearing=${bearingDegrees.toInt()} " +
                    "lat=${"%.6f".format(lat)} lon=${"%.6f".format(lon)} " +
                    "vp=${"%.5f".format(viewport.lat)},${"%.5f".format(viewport.lon)} " +
                    "mag=${viewport.mag} angle=${Math.toDegrees(viewport.angle).toInt()}")
        }
    }
}

/**
 * Project the GPS coordinate onto the screen using the viewport of the displayed
 * bitmap. Returns null when the marker is outside the visible area (with margin).
 * Extracted for unit testing.
 */
internal fun projectMarker(
    lat: Double,
    lon: Double,
    viewport: MapRenderer.RenderViewport,
    screenWidthPx: Double,
    screenHeightPx: Double,
    dpi: Double
): Offset? {
    val projected = ProjectionUtils.viewport(
        viewport.lat, viewport.lon, viewport.mag,
        screenWidthPx.toInt(), screenHeightPx.toInt(), dpi, viewport.angle
    )
    val (sx, sy) = projected.geoToScreenRotated(lat, lon)
    if (sx.isNaN() || sy.isNaN()) return null
    if (sx < -MARGIN_PX || sx > screenWidthPx + MARGIN_PX ||
        sy < -MARGIN_PX || sy > screenHeightPx + MARGIN_PX
    ) return null
    return Offset(sx.toFloat(), sy.toFloat())
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
internal const val MARGIN_PX = 100
private const val POOR_ACCURACY_THRESHOLD_PX = 20f
