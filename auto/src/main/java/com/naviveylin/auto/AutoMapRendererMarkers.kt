package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.Surface
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.FollowDisplayState
import com.naviveylin.core.FollowPrediction
import com.naviveylin.core.MapRenderUtil
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.RenderBitmapPool
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.VehicleMarkerGeometry
import com.naviveylin.core.ResolvedAnchor
import com.naviveylin.core.anchorCenter
import com.naviveylin.core.resolveAnchorFraction
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.naviveylin.auto.AutoMapRenderer.Companion.MAX_DEST_LABEL_CHARS


// The two surface-drawn markers and the draw-object cache that makes a frame allocate
// nothing: the vehicle arrow and the destination pin.

/**
 * Draws the GPS position marker (accuracy circle + bearing arrow) in the
 * same projection the native renderer used for the map bitmap. In follow
 * mode the marker rides the displayed (eased predicted) position so it
 * glides with the blitted map (spec: auto-smooth-follow, design D7).
 */
internal fun AutoMapRenderer.drawGpsMarker(canvas: Canvas, w: Int, h: Int) {
    if (!gpsMarkerVisible || gpsMarkerLat.isNaN() || gpsMarkerLon.isNaN()) return

    val (x, y) = markerScreenPosition(w, h)

    if (x.isNaN() || y.isNaN()) return
    if (x < -200 || x > w + 200 || y < -200 || y > h + 200) return

    // Cached draw objects: the arrow paths, paints, shadow filter and core gradient are
    // reused across frames (spec: auto-map-renderer — Marker drawing allocates no
    // per-frame objects; design D9).
    val objects = markerDrawObjects(w, h)
    val density = objects.density
    val minRadius = 4f * density
    val accuracyThreshold = 20f * density

    // Meters per pixel at the rendered magnification (pixels-per-radian
    // times earth radius). Used for the accuracy circle.
    val scale = ProjectionUtils.computeScale(displayedMag(), w.toDouble(), projectionDpi).scale
    val metersPerPixel = ProjectionUtils.EARTH_RADIUS / scale
    val accuracyRadiusPx = if (gpsMarkerAccuracy > 0.0 && metersPerPixel > 0.0) {
        (gpsMarkerAccuracy / metersPerPixel).coerceAtLeast(minRadius.toDouble()).toFloat()
    } else {
        0f
    }

    val centerX = x.toFloat()
    val centerY = y.toFloat()

    if (accuracyRadiusPx >= accuracyThreshold) {
        canvas.drawCircle(centerX, centerY, accuracyRadiusPx, objects.accuracyFillPaint)
        canvas.drawCircle(centerX, centerY, accuracyRadiusPx, objects.accuracyBorderPaint)
    }

    // Screen bearing: raw GPS bearing + map rotation (same convention as
    // the phone overlay's ProjectionUtils.screenBearing).
    val rawBearing = if (gpsMarkerBearing >= 0.0) gpsMarkerBearing else 0.0
    val screenBearingDeg = ProjectionUtils.screenBearing(rawBearing, displayedAngle()).toFloat()

    // Unified marker (spec: gps-location-marker, cross-variant parity) —
    // the same shape and palette as the phone overlay, driven by
    // VehicleMarkerGeometry (38 dp density-aware, casing + rim + gradient
    // + soft shadow; legible on both daylight and dark map variants).
    // Soft blurred shadow, offset in screen space (drawn before the
    // arrow, rotated to the bearing) — floating-chip depth.
    val shadowOffsetPx = VehicleMarkerGeometry.SHADOW_OFFSET_DP * density
    canvas.save()
    canvas.translate(centerX + shadowOffsetPx, centerY + shadowOffsetPx)
    canvas.rotate(screenBearingDeg)
    canvas.drawPath(objects.corePath, objects.shadowPaint)
    canvas.restore()

    // Layered arrow (casing -> rim -> gradient core), rotated about position.
    // The casing's palette (white in day, deep blue-black in dark) and the gradient
    // stops live in the cached paints, keyed on the dark presentation
    // (spec: auto-map-renderer — Marker legible on dark map / No white halo in dark).
    canvas.save()
    canvas.translate(centerX, centerY)
    canvas.rotate(screenBearingDeg)
    canvas.drawPath(objects.casingPath, objects.casingPaint)
    canvas.drawPath(objects.corePath, objects.rimPaint)
    canvas.drawPath(objects.corePath, objects.corePaint)
    canvas.restore()
}

/**
 * Draws the destination marker (pin + name label) in the same projection
 * the native renderer used for the map bitmap (spec:
 * auto-destination-details). In follow mode it projects against the
 * displayed center so it stays anchored to the blitted map.
 */
internal fun AutoMapRenderer.drawDestinationMarker(canvas: Canvas, w: Int, h: Int) {
    if (!destMarkerVisible || destMarkerLat.isNaN() || destMarkerLon.isNaN()) return

    val (vpLat, vpLon) = markerViewport()
    val vp = ProjectionUtils.viewport(
        vpLat, vpLon, displayedMag(), w, h, projectionDpi, displayedAngle()
    )
    val (destX0, destY0) = vp.geoToScreenRotated(destMarkerLat, destMarkerLon)
    val x = destX0 - blitOffsetX
    val y = destY0 - blitOffsetY
    if (x.isNaN() || y.isNaN()) return
    if (x < -200 || x > w + 200 || y < -200 || y > h + 200) return

    // Cached draw objects (spec: auto-map-renderer — Marker drawing allocates no
    // per-frame objects; design D9): the pin is built in its own local frame and placed
    // with a canvas translation, so one path serves every position.
    val objects = markerDrawObjects(w, h)
    val density = objects.density
    val pinRadius = objects.pinRadius
    val centerX = x.toFloat()
    val centerY = y.toFloat()

    // Pin: dark outline + white center dot, tip pointing at the location.
    canvas.save()
    canvas.translate(centerX, centerY)
    canvas.drawPath(objects.pinPath, objects.pinOutlinePaint)
    canvas.drawCircle(0f, -pinRadius * 1.8f, pinRadius * 0.45f, objects.pinInnerPaint)
    canvas.restore()

    // Name label below the pin when known.
    val name = destMarkerName
    if (!name.isNullOrBlank()) {
        val textPaint = objects.labelTextPaint
        val label = name.take(MAX_DEST_LABEL_CHARS)
        val textWidth = textPaint.measureText(label)
        val labelY = centerY + pinRadius * 1.6f + textPaint.textSize
        canvas.drawRoundRect(
            centerX - textWidth / 2f - 6f * density,
            labelY - textPaint.textSize - 4f * density,
            centerX + textWidth / 2f + 6f * density,
            labelY + 4f * density,
            4f * density, 4f * density, objects.labelBackgroundPaint
        )
        canvas.drawText(label, centerX - textWidth / 2f, labelY, textPaint)
    }
}

// ------------------------------------------------------------------
// Marker draw objects (spec: auto-map-renderer — Marker drawing allocates
// no per-frame objects; design D9)
// ------------------------------------------------------------------

/**
 * Draw objects for the car markers (GPS arrow, destination pin): paths, paints, the
 * shadow blur filter and the core gradient. Built on first use and reused on every
 * frame, so a drawn frame allocates nothing. Rebuilt only when an input that defines
 * the geometry or the palette changes — the surface bounds, the projection density, or
 * the dark presentation. All shape and palette constants still come from
 * [VehicleMarkerGeometry], so the marker's contract is unchanged.
 */
internal class MarkerDrawObjects(
    val width: Int,
    val height: Int,
    val density: Float,
    val dark: Boolean
) {
    /** Half the marker size in px (density-aware, same on phone and car). */
    val hPx: Float = VehicleMarkerGeometry.SIZE_DP * density / 2f

    /** Arrow outline, local frame (origin = marker center, arrow up). */
    val corePath: Path = Path().apply {
        val vertices = VehicleMarkerGeometry.outlineVertices()
        val (x0, y0) = vertices[0]
        val (x1, y1) = vertices[1]
        moveTo(x0 * hPx, y0 * hPx)
        quadTo(0f, -hPx, x1 * hPx, y1 * hPx)
        for (i in 2 until vertices.size) {
            val (x, y) = vertices[i]
            lineTo(x * hPx, y * hPx)
        }
        close()
    }

    /** Core scaled about the center — the casing ring. */
    val casingPath: Path = Path().apply {
        val scale = Matrix().apply {
            setScale(VehicleMarkerGeometry.CASING_SCALE, VehicleMarkerGeometry.CASING_SCALE)
        }
        set(corePath)
        transform(scale)
    }

    val accuracyFillPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x1A2196F3.toInt()
    }

    val accuracyBorderPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x662196F3.toInt()
        strokeWidth = 1.5f * density
    }

    val shadowPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = VehicleMarkerGeometry.COLOR_SHADOW.toInt()
        maskFilter = BlurMaskFilter(
            VehicleMarkerGeometry.SHADOW_BLUR_DP * density,
            BlurMaskFilter.Blur.NORMAL
        )
    }

    val casingPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (dark) {
            VehicleMarkerGeometry.COLOR_CASING_DARK.toInt()
        } else {
            VehicleMarkerGeometry.COLOR_CASING.toInt()
        }
    }

    val rimPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = VehicleMarkerGeometry.COLOR_RIM.toInt()
        strokeWidth = VehicleMarkerGeometry.RIM_WIDTH_H * hPx
    }

    /** Core gradient stops for the built presentation (test-observable palette). */
    private val gradientStops: Pair<Long, Long> = VehicleMarkerGeometry.gradientColors(dark)
    val gradientTop: Int = gradientStops.first.toInt()
    val gradientBottom: Int = gradientStops.second.toInt()

    val corePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(
            0f, -hPx, 0f, VehicleMarkerGeometry.TAIL_Y * hPx,
            intArrayOf(gradientTop, gradientBottom),
            null,
            Shader.TileMode.CLAMP
        )
    }

    /** Pin radius (px). */
    val pinRadius: Float = 9f * density

    /** Destination pin, local frame (tip at the origin, body above it). */
    val pinPath: Path = Path().apply {
        moveTo(0f, 0f)
        lineTo(-pinRadius, -pinRadius * 2.2f)
        arcTo(
            -pinRadius, -pinRadius * 3.4f,
            pinRadius, -pinRadius * 0.2f,
            180f, 180f, false
        )
        lineTo(pinRadius, -pinRadius * 2.2f)
        close()
    }

    val pinOutlinePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFB71C1C.toInt()
    }

    val pinInnerPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }

    val labelTextPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF212121.toInt()
        textSize = 13f * density
        isFakeBoldText = true
    }

    val labelBackgroundPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xCCFFFFFF.toInt()
    }
}

/**
 * The marker draw objects for this frame, rebuilt only when the surface bounds, the
 * density or the dark presentation changed. The hit path allocates nothing.
 */
internal fun AutoMapRenderer.markerDrawObjects(w: Int, h: Int): MarkerDrawObjects {
    val density = (projectionDpi / 160.0).toFloat()
    val cached = markerDrawObjects
    if (cached != null &&
        cached.width == w && cached.height == h &&
        cached.density == density && cached.dark == darkPresentation
    ) {
        return cached
    }
    val rebuilt = MarkerDrawObjects(w, h, density, darkPresentation)
    markerDrawObjects = rebuilt
    markerDrawRebuilds++
    return rebuilt
}

/** Test-only: the cached draw objects (identity changes only on a rebuild). */
internal fun AutoMapRenderer.markerDrawObjectsForTest(): MarkerDrawObjects? = markerDrawObjects

/** Test-only: how often the marker draw objects were rebuilt. */
internal fun AutoMapRenderer.markerDrawRebuildsForTest(): Int = markerDrawRebuilds
