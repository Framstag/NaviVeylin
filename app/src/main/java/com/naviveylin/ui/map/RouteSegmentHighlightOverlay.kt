package com.naviveylin.ui.map

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.naviveylin.core.ProjectionUtils

/** Test tag of the analysed-segment layer; absent when no step is analysed. */
internal const val ROUTE_SEGMENT_HIGHLIGHT_TAG = "RouteSegmentHighlightOverlay"

/*
 * Analysed-segment palette (spec: `route-analysis` — "Analysed segment is highlighted
 * on the map"). One function per role, day and dark values in one place, mirroring the
 * `compassFillColor` convention (`guidelines/Design.md` §12 — one source of truth per
 * signal). Cyan is used in neither presentation's route paint (daylight violet with a
 * magenta-leaning casing, dark red with a white casing — `stylesheets/include/route.oss`),
 * so the highlight never reads as the route itself.
 */
private val SegmentFillDay = Color(0xE600E5FF)  // bright cyan over the violet daylight route
private val SegmentFillDark = Color(0xE600E5FF) // same fill reads on the darkened map
private val SegmentCasingDay = Color(0xFF00454F)  // opaque dark teal: contrast on white roads and light land
private val SegmentCasingDark = Color(0xFFE0F7FA) // light casing against the dark map

/** Fill colour of the analysed segment, per presentation. */
internal fun segmentHighlightFillColor(isDarkPresentation: Boolean): Color =
    if (isDarkPresentation) SegmentFillDark else SegmentFillDay

/** Casing (outline) colour of the analysed segment, per presentation. */
internal fun segmentHighlightCasingColor(isDarkPresentation: Boolean): Color =
    if (isDarkPresentation) SegmentCasingDark else SegmentCasingDay

/** Fill stroke width in dp — the route's own 1.5 mm paint at the app's default density. */
internal const val SEGMENT_FILL_WIDTH_DP = 7f

/** Casing stroke width in dp — wider than the fill, so the highlight stays bordered. */
internal const val SEGMENT_CASING_WIDTH_DP = 9f

/**
 * Compose overlay that highlights the polyline range owned by the analysed route
 * step (spec: `route-analysis` — "Analysed segment is highlighted on the map").
 *
 * Drawn on a layer above the rendered map bitmap and below the markers/pins, so the
 * route's own paint (which the native renderer bakes into the frame) stays visible
 * around the highlight and no pin is hidden. Projection uses the viewport of the
 * displayed bitmap, exactly like [LocationMarkerOverlay], so the segment rides the
 * same frame the map shows — on the tile path and on the full-render path alike.
 *
 * Renders nothing when there is no analysed step, no displayed frame, or when the
 * range is too short to have a length (a single vertex).
 */
@Composable
fun RouteSegmentHighlightOverlay(
    polylineLats: DoubleArray?,
    polylineLons: DoubleArray?,
    segment: IntRange?,
    viewport: MapRenderer.RenderViewport?,
    dpi: Double,
    dark: Boolean = false,
    modifier: Modifier = Modifier,
    zoomScale: Float = 1f,
    zoomAnchor: Offset = Offset.Zero
) {
    if (segment == null || viewport == null || dpi <= 0.0) return
    if (polylineLats == null || polylineLons == null) return
    val pointCount = minOf(polylineLats.size, polylineLons.size)
    if (segment.first < 0 || segment.last >= pointCount) return
    if (segment.last - segment.first < 1) return

    val fill = segmentHighlightFillColor(dark)
    val casing = segmentHighlightCasingColor(dark)
    val density = LocalDensity.current
    val fillWidth = with(density) { SEGMENT_FILL_WIDTH_DP.dp.toPx() }
    val casingWidth = with(density) { SEGMENT_CASING_WIDTH_DP.dp.toPx() }
    // Diagnostics (spec: auto-diagnostics — pixels, never a position): what this overlay actually
    // draws, so the fit's own numbers (`MapCanvasVM: segment focus: …`) and a screenshot measured
    // with tools/measure-highlight.py can be compared three ways. A disagreement between the two
    // logs means the fit and the overlay project differently; a disagreement with the measurement
    // means the drawn frame does not match either. Logged only when the drawn box changes.
    val lastLogged = remember { arrayOfNulls<String>(1) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag(ROUTE_SEGMENT_HIGHLIGHT_TAG)
    ) {
        val points = segmentScreenPoints(
            polylineLats = polylineLats,
            polylineLons = polylineLons,
            segment = segment,
            viewport = viewport,
            screenWidthPx = size.width.toDouble(),
            screenHeightPx = size.height.toDouble(),
            dpi = dpi
        ).map { applyZoomAnchorScale(it, zoomScale, zoomAnchor) }
        if (points.size < 2) return@Canvas
        val box = "range=$segment canvas=${size.width.toInt()}x${size.height.toInt()} " +
            "bboxPx=[${points.minOf { it.x }.toInt()},${points.maxOf { it.x }.toInt()}," +
            "${points.minOf { it.y }.toInt()},${points.maxOf { it.y }.toInt()}]"
        if (lastLogged[0] != box) {
            lastLogged[0] = box
            Log.d("RouteHighlight", box)
        }

        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (index in 1 until points.size) {
                lineTo(points[index].x, points[index].y)
            }
        }
        drawPath(
            path = path,
            color = casing,
            style = Stroke(width = casingWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = path,
            color = fill,
            style = Stroke(width = fillWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Project the polyline vertices of [segment] onto the screen using the viewport of
 * the displayed bitmap, in range order. Extracted pure for unit testing, and the
 * same projection [LocationMarkerOverlay] uses, so the highlight and the markers
 * cannot disagree about where a coordinate is.
 */
internal fun segmentScreenPoints(
    polylineLats: DoubleArray,
    polylineLons: DoubleArray,
    segment: IntRange,
    viewport: MapRenderer.RenderViewport,
    screenWidthPx: Double,
    screenHeightPx: Double,
    dpi: Double
): List<Offset> {
    val pointCount = minOf(polylineLats.size, polylineLons.size)
    if (pointCount == 0) return emptyList()
    if (segment.first < 0 || segment.last >= pointCount || segment.first > segment.last) return emptyList()

    val projected = ProjectionUtils.viewport(
        viewport.lat, viewport.lon, viewport.mag,
        screenWidthPx.toInt(), screenHeightPx.toInt(), dpi, viewport.angle
    )
    return (segment.first..segment.last).map { index ->
        val (x, y) = projected.geoToScreenRotated(polylineLats[index], polylineLons[index])
        Offset(x.toFloat(), y.toFloat())
    }
}

/**
 * Preview of the analysed-segment layer over a plain surface: the shape a selected
 * step's highlight takes on the map, in the daylight presentation.
 */
@Preview(showBackground = true)
@Composable
private fun RouteSegmentHighlightOverlayPreview() {
    Surface {
        RouteSegmentHighlightOverlay(
            polylineLats = doubleArrayOf(52.5200, 52.5230, 52.5260, 52.5300),
            polylineLons = doubleArrayOf(13.4050, 13.4080, 13.4090, 13.4100),
            segment = 1..2,
            viewport = MapRenderer.RenderViewport(52.5250, 13.4075, 14.0, 0.0),
            dpi = 160.0,
            dark = false,
            modifier = Modifier.fillMaxSize()
        )
    }
}
