package com.naviveylin.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.ProjectionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Reusable interactive mini map widget (spec: mini-map).
 *
 * Renders its own independent, north-aligned map viewport centered on
 * [lat]/[lon] with small zoom buttons, single-finger panning, and an object
 * marker at the given location. Panning/zooming never affects the main map.
 *
 * Owns its rendering lifecycle: a dedicated [MapRenderer] + coroutine scope
 * created here and shut down when the widget leaves composition, so any screen
 * can embed it by supplying an object location and initial magnification.
 *
 * @param client shared OSMScoutClient (renders serialize on its dbThread)
 * @param lat object latitude (marker + initial center)
 * @param lon object longitude (marker + initial center)
 * @param initialMag initial magnification, clamped to the app range [MIN_MAG, MAX_MAG]
 * @param additionalMarkers extra object locations drawn as secondary pins
 *   (spec: mini-map Multiple object markers)
 * @param selectedMarker an additional marker to highlight distinctly (e.g.
 *   the user-selected POI result)
 * @param currentPosition optional current location drawn as a blue dot with a
 *   soft accuracy halo; nothing is drawn when null (spec: mini-map Optional
 *   current-position marker)
 * @param onViewportChanged optional callback fired after every viewport change
 *   (initial render, pan, zoom) with the widget's center/magnification
 */
@Composable
fun MiniMap(
    client: OSMScoutClient,
    lat: Double,
    lon: Double,
    initialMag: Int,
    modifier: Modifier = Modifier,
    additionalMarkers: List<Pair<Double, Double>> = emptyList(),
    selectedMarker: Pair<Double, Double>? = null,
    currentPosition: Pair<Double, Double>? = null,
    onViewportChanged: ((lat: Double, lon: Double, mag: Int) -> Unit)? = null
) {
    val densityDpi = LocalContext.current.resources.displayMetrics.densityDpi.toDouble()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val markerColor = MaterialTheme.colorScheme.primary
    val additionalMarkerColor = MaterialTheme.colorScheme.secondary
    val selectedMarkerColor = MaterialTheme.colorScheme.tertiary
    val gpsMarkerColor = Color(0xFF1A73E8)

    // Own renderer + scope; never touches the main map's renderer/viewport.
    val renderer = remember(client) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        MapRenderer(client, densityDpi, scope)
    }

    DisposableEffect(renderer) {
        onDispose {
            renderer.shutdown()
        }
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Size change or new object location → (re)render. North-locked: angle 0.
    LaunchedEffect(canvasSize, lat, lon, initialMag) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            renderer.screenWidth = canvasSize.width
            renderer.screenHeight = canvasSize.height
            renderer.requestRender(
                lat.coerceIn(-90.0, 90.0), lon,
                initialMag.coerceIn(MapCanvasViewModel.MIN_MAG, MapCanvasViewModel.MAX_MAG),
                0.0
            )
            onViewportChanged?.invoke(renderer.currentLat, renderer.currentLon, renderer.currentMag)
        }
    }

    val frame by renderer.frameFlow.collectAsState()
    val viewport by renderer.currentViewportFlow.collectAsState()

    val markerRadiusPx = with(LocalDensity.current) { 9.dp.toPx() }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("MiniMapCanvas")
                .onSizeChanged { canvasSize = it }
                .pointerInput(renderer, canvasSize) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val w = canvasSize.width.toDouble()
                        val h = canvasSize.height.toDouble()
                        if (w <= 0.0 || h <= 0.0) return@detectDragGestures
                        val mag = renderer.currentMag
                        val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenter(
                            dragAmount.x.toDouble(), dragAmount.y.toDouble(),
                            mag,
                            w, h,
                            renderer.currentLat, renderer.currentLon,
                            densityDpi
                        )
                        renderer.requestRender(newLat, newLon, mag, 0.0)
                        onViewportChanged?.invoke(renderer.currentLat, renderer.currentLon, renderer.currentMag)
                    }
                }
        ) {
            drawRect(color = surfaceColor)

            frame.bitmap?.let { bitmap ->
                // Scale to fill (same as the main map) so the bitmap covers
                // the canvas exactly.
                val scale = (size.width / bitmap.width.toFloat())
                    .coerceAtLeast(size.height / bitmap.height.toFloat())
                val w = (bitmap.width * scale).toInt()
                val h = (bitmap.height * scale).toInt()
                val dx = ((size.width - w) / 2f).toInt()
                val dy = ((size.height - h) / 2f).toInt()
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = IntOffset(dx, dy),
                    dstSize = IntSize(w, h)
                )
            }

            // Shared pin drawing: colored head with a pointing tip and a white
            // inner dot, anchored to the marker's geographic position.
            val drawPin: DrawScope.(Double, Double, Float, Color) -> Unit = { px, py, radius, color ->
                drawCircle(
                    color = color,
                    radius = radius,
                    center = Offset(px.toFloat(), (py - radius).toFloat())
                )
                val tip = Path().apply {
                    moveTo((px - radius).toFloat(), (py - radius).toFloat())
                    lineTo((px + radius).toFloat(), (py - radius).toFloat())
                    lineTo(px.toFloat(), (py + radius).toFloat())
                    close()
                }
                drawPath(path = tip, color = color)
                drawCircle(
                    color = Color.White,
                    radius = radius * 0.45f,
                    center = Offset(px.toFloat(), (py - radius).toFloat())
                )
                drawCircle(
                    color = color,
                    radius = radius * 0.15f,
                    center = Offset(px.toFloat(), (py - radius).toFloat())
                )
            }

            val inView: (Double, Double) -> Boolean = { px, py ->
                px >= 0.0 && px <= size.width && py >= 0.0 && py <= size.height
            }

            // Primary object marker: anchored to the object's geographic
            // position, hidden when panned/zoomed out of the visible area.
            val (sx, sy) = ProjectionUtils.geoToScreen(
                lat, lon,
                size.width.toInt(), size.height.toInt(),
                viewport.mag, viewport.lat, viewport.lon,
                densityDpi
            )
            if (inView(sx, sy)) {
                drawPin(sx, sy, markerRadiusPx, markerColor)
            }

            // Additional object markers (e.g. all POI search results), drawn
            // slightly smaller; the selected one uses a distinct color.
            additionalMarkers.forEach { (markerLat, markerLon) ->
                val (mx, my) = ProjectionUtils.geoToScreen(
                    markerLat, markerLon,
                    size.width.toInt(), size.height.toInt(),
                    viewport.mag, viewport.lat, viewport.lon,
                    densityDpi
                )
                if (inView(mx, my)) {
                    val selected = selectedMarker != null &&
                        selectedMarker.first == markerLat && selectedMarker.second == markerLon
                    if (selected) {
                        drawPin(mx, my, markerRadiusPx, selectedMarkerColor)
                    } else {
                        drawPin(mx, my, markerRadiusPx * 0.8f, additionalMarkerColor)
                    }
                }
            }

            // Current-position marker: blue dot with a soft accuracy halo.
            currentPosition?.let { (clat, clon) ->
                val (gx, gy) = ProjectionUtils.geoToScreen(
                    clat, clon,
                    size.width.toInt(), size.height.toInt(),
                    viewport.mag, viewport.lat, viewport.lon,
                    densityDpi
                )
                if (inView(gx, gy)) {
                    val center = Offset(gx.toFloat(), gy.toFloat())
                    drawCircle(
                        color = gpsMarkerColor.copy(alpha = 0.2f),
                        radius = markerRadiusPx * 2f,
                        center = center
                    )
                    drawCircle(color = Color.White, radius = markerRadiusPx * 0.9f, center = center)
                    drawCircle(color = gpsMarkerColor, radius = markerRadiusPx * 0.7f, center = center)
                    drawCircle(color = Color.White, radius = markerRadiusPx * 0.3f, center = center)
                }
            }
        }

        // Small zoom controls, bottom-right of the widget.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            FilledTonalIconButton(
                onClick = {
                    val mag = renderer.currentMag
                    val newMag = (mag + 1).coerceAtMost(MapCanvasViewModel.MAX_MAG)
                    if (newMag != mag) {
                        renderer.requestRender(renderer.currentLat, renderer.currentLon, newMag, 0.0)
                        onViewportChanged?.invoke(renderer.currentLat, renderer.currentLon, renderer.currentMag)
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom in",
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            FilledTonalIconButton(
                onClick = {
                    val mag = renderer.currentMag
                    val newMag = (mag - 1).coerceAtLeast(MapCanvasViewModel.MIN_MAG)
                    if (newMag != mag) {
                        renderer.requestRender(renderer.currentLat, renderer.currentLon, newMag, 0.0)
                        onViewportChanged?.invoke(renderer.currentLat, renderer.currentLon, renderer.currentMag)
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom out",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
