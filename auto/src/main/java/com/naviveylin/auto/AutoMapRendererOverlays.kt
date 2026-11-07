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


// The screen-space readers a drawn overlay uses — the marker position in surface
// pixels, the blit offset the committed frame carries, the overrun size — and the
// viewport observation the screen reads.

/**
 * Surface position of the GPS marker: the displayed (eased predicted) position
 * projected against the DISPLAYED frame — the bitmap currently on the surface —
 * and shifted by the offset that frame was blitted with, i.e. the position of
 * the map content the marker rides (spec: gps-location-marker — Marker projects
 * against displayed bitmap viewport; changes `anchor-per-surface-visible-area`,
 * `overlay-projects-against-displayed-frame`).
 *
 * Two defects met here: projecting against the frame target alone left the marker
 * ahead of the content by the blit offset between commits, and projecting against
 * a PENDING frame target detached the marker from the content for one render
 * latency after every viewport write (a fix re-anchor, a heading rotation, an
 * auto-zoom band change) and snapped it back on the commit. Both are covered now:
 * displayed*() reads the committed frame and the blit offset is subtracted.
 * Exposed for tests.
 */
internal fun AutoMapRenderer.markerScreenPosition(w: Int, h: Int): Pair<Double, Double> {
    val (markerLat, markerLon) = markerPosition()
    val vp = ProjectionUtils.viewport(
        displayedLat(), displayedLon(), displayedMag(), w, h, projectionDpi, displayedAngle()
    )
    val (x, y) = vp.geoToScreenRotated(markerLat, markerLon)
    return (x - blitOffsetX) to (y - blitOffsetY)
}

/** Current blit offset of the displayed frame — exposed for tests. */
internal fun AutoMapRenderer.blitOffset(): Pair<Double, Double> = blitOffsetX to blitOffsetY

/** Test hook: pretend the displayed frame was blitted by (x, y). */
@androidx.annotation.VisibleForTesting
internal fun AutoMapRenderer.setBlitOffsetForTest(x: Double, y: Double) {
    blitOffsetX = x
    blitOffsetY = y
}

internal fun AutoMapRenderer.emitViewportState() {
    _viewportState.value = AutoMapRenderer.ViewportState(viewportLat, viewportLon, viewportZoom, viewportAngle, viewportZoomFraction)
}

/**
 * Position the GPS marker is drawn at: the displayed (eased predicted)
 * position in follow mode, else the raw fix. Exposed for tests.
 */
internal fun AutoMapRenderer.markerPosition(): Pair<Double, Double> =
    if (followMode && !displayLat.isNaN() && !displayLon.isNaN()) {
        displayLat to displayLon
    } else {
        gpsMarkerLat to gpsMarkerLon
    }

/** Size of the current overrun buffer, or null when none. Exposed for tests. */
internal fun AutoMapRenderer.overrunSize(): Pair<Int, Int>? =
    overrunBitmap?.let { it.width to it.height }
