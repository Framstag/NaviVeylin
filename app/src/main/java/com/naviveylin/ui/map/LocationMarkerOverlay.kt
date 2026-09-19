package com.naviveylin.ui.map

import android.graphics.BlurMaskFilter
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.VehicleMarkerGeometry
import android.graphics.Paint as AndroidPaint

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
    modifier: Modifier = Modifier,
    zoomScale: Float = 1f,
    zoomAnchor: Offset = Offset.Zero,
    dark: Boolean = false
) {
    if (lat.isNaN() || lon.isNaN() || viewport == null) return
    if (dpi <= 0.0) return

    val density = LocalDensity.current
    val minRadiusPx = with(density) { MIN_RADIUS_DP.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = size.width.toDouble()
        val screenHeightPx = size.height.toDouble()
        val projected = projectMarker(lat, lon, viewport, screenWidthPx, screenHeightPx, dpi)
        if (projected == null) return@Canvas
        val center = applyZoomAnchorScale(projected, zoomScale, zoomAnchor)

        // Meters per pixel at the rendered magnification for the accuracy circle.
        val scale = ProjectionUtils.computeScale(viewport.mag, screenWidthPx, dpi)
        val metersPerPixel = ProjectionUtils.EARTH_RADIUS / scale.scaleGradtorad

        val accuracyRadiusPx = if (accuracy > 0f && metersPerPixel > 0.0) {
            ((accuracy / metersPerPixel) * zoomScale).coerceAtLeast(minRadiusPx.toDouble()).toFloat()
        } else {
            minRadiusPx * zoomScale.coerceAtLeast(1f)
        }

        // Bearing < 0 (unavailable) draws the arrow pointing north on the map.
        // Screen bearing = raw bearing + map rotation (same sign convention the
        // native renderer used — do not flip). Orientation mode never changes the
        // arrow: north-up is a map-rotation choice, not a bearing-availability state.
        val rawBearing = if (bearing >= 0.0) bearing else 0.0
        val bearingDegrees = ProjectionUtils.screenBearing(rawBearing, viewport.angle).toFloat()

        if (accuracyRadiusPx >= POOR_ACCURACY_THRESHOLD_PX) {
            drawAccuracyCircle(center, accuracyRadiusPx)
        }

        drawCompassArrowWithShadow(center, bearingDegrees, dark)
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

/*
 * Apply the smooth-zoom display scale around the zoom anchor to an already
 * projected screen position — keeps the marker on the map feature it is
 * anchored to while the front-buffer zoom animation plays (spec: smooth-zoom).
 * Extracted pure for unit testing.
 */
internal fun applyZoomAnchorScale(pos: Offset, zoomScale: Float, zoomAnchor: Offset): Offset =
    if (zoomScale != 1f) zoomAnchor + (pos - zoomAnchor) * zoomScale else pos

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

private fun DrawScope.drawCompassArrowWithShadow(center: Offset, bearingDegrees: Float, dark: Boolean) {
    // Unified marker geometry (spec: gps-location-marker, cross-variant parity):
    // the shape/palette come from VehicleMarkerGeometry and match Android Auto.
    val hPx = (VehicleMarkerGeometry.SIZE_DP.dp).toPx() / 2f
    val shadowOffsetPx = VehicleMarkerGeometry.SHADOW_OFFSET_DP.dp.toPx()
    val blurPx = VehicleMarkerGeometry.SHADOW_BLUR_DP.dp.toPx()
    val rimPx = VehicleMarkerGeometry.RIM_WIDTH_H * hPx

    // Path builder — rounded cap via quad to the tip point (same on both
    // renderers; vertices from VehicleMarkerGeometry.outlineVertices).
    fun buildCore(): Path {
        val path = Path()
        val vertices = VehicleMarkerGeometry.outlineVertices()
        val (x0, y0) = vertices[0]
        val (x1, y1) = vertices[1]
        path.moveTo(x0 * hPx, y0 * hPx)
        path.quadraticBezierTo(0f, -hPx, x1 * hPx, y1 * hPx)
        for (i in 2 until vertices.size) {
            val (x, y) = vertices[i]
            path.lineTo(x * hPx, y * hPx)
        }
        path.close()
        return path
    }

    // Soft blurred shadow, offset down-right in screen space (drawn before
    // the arrow, rotated to the bearing) — floating-chip depth instead of the
    // former hard-offset triangle.
    val shadowPath = buildCore()
    translate(left = center.x + shadowOffsetPx, top = center.y + shadowOffsetPx) {
        rotate(degrees = bearingDegrees, pivot = Offset.Zero) {
            drawBlurredPath(shadowPath, blurPx, Color(VehicleMarkerGeometry.COLOR_SHADOW))
        }
    }

    // Layered arrow (casing -> rim -> gradient core), rotated about position.
    val corePath = buildCore()
    translate(left = center.x, top = center.y) {
        rotate(degrees = bearingDegrees, pivot = Offset.Zero) {
        // White casing ring: core scaled about the center. In dark
        // presentation the casing turns deep blue-black (COLOR_CASING_DARK)
        // so no stencil-white halo shows against dark land (user feedback:
        // unified-vehicle-marker). Dark flag rides the app's resolved dark
        // presentation (state.isDarkPresentation at the call site).
        val casing = buildCore()
        casing.transform(
            Matrix().apply {
                scale(VehicleMarkerGeometry.CASING_SCALE, VehicleMarkerGeometry.CASING_SCALE)
            }
        )
        drawPath(
            casing,
            Color(if (dark) VehicleMarkerGeometry.COLOR_CASING_DARK else VehicleMarkerGeometry.COLOR_CASING)
        )

            // Dark accent rim: stroke around the core (tri-layer arrow).
            drawPath(
                corePath,
                color = Color(VehicleMarkerGeometry.COLOR_RIM),
                style = Stroke(width = rimPx)
            )

            // Core: vertical gradient, light from above. Dark presentation
            // swaps in the lighter dark-presentation stops so the marker reads
            // on dark land (spec: gps-location-marker).
            val (gradientTop, gradientBottom) = VehicleMarkerGeometry.gradientColors(dark)
            val gradient = Brush.linearGradient(
                start = Offset(0f, -hPx),
                end = Offset(0f, VehicleMarkerGeometry.TAIL_Y * hPx),
                colors = listOf(
                    Color(gradientTop),
                    Color(gradientBottom)
                )
            )
            drawPath(corePath, brush = gradient)
        }
    }
}

/** Soft blurred fill of a path via the native canvas (BlurMaskFilter). */
private fun DrawScope.drawBlurredPath(path: Path, blurPx: Float, color: Color) {
    drawIntoCanvas { canvas ->
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
}

private val ACCURACY_FILL_COLOR = Color(0x1A4A90D9)   // ~10% blue
private val ACCURACY_BORDER_COLOR = Color(0x664A90D9) // ~40% blue
private val MIN_RADIUS_DP: Dp = 4.dp
internal const val MARGIN_PX = 100
private const val POOR_ACCURACY_THRESHOLD_PX = 20f
