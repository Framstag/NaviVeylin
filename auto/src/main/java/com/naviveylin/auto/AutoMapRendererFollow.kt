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
import com.naviveylin.core.MapRenderUtil
import com.naviveylin.core.OverrunWindow
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


// Follow mode: the diagnostic line, the vehicle anchor and its resolution against the
// host chrome and insets, and the read-only view of the displayed frame the overlays and
// the marker projection see.

/**
 * The follow diagnostic entry: fix speed, the prediction's effective speed, the display
 * offset, the displayed frame's and the pending target's magnification and angle, their
 * deltas, the last commit path and the render/blit counters — the line the on-device
 * recipe (`guidelines/Build.md` §10) greps for the frame-vs-pending evidence.
 *
 * Every number is formatted with [Locale.ROOT] (spec: auto-diagnostics — Diagnostic
 * numbers are locale-independent): on a decimal-comma device locale the default-locale
 * formatter printed `off=3,4,-1,5` and `frameMag=15,00`, a decimal comma that is
 * indistinguishable from the field separator the recipe splits on. No coordinates
 * (spec: auto-diagnostics — Diagnostics carry no coordinates): the identity is
 * magnification, angle deltas, clamped pixels and counters.
 */
internal fun AutoMapRenderer.followDiagnosticLine(nowMs: Long, offset: OverrunWindow.DisplayOffset): String {
    val dbg = followPrediction.debugState(nowMs)
    fun f(format: String, value: Double): String = String.format(Locale.ROOT, format, value)
    return "follow" +
        " spd=" + (if (lastFixSpeedMs.isNaN()) "-" else f("%.1f", lastFixSpeedMs * 3.6)) +
        " eff=" + f("%.1f", dbg.effectiveSpeedMs * 3.6) +
        " dec=" + dbg.decelerating +
        " stp=" + dbg.stopped +
        " off=" + f("%.1f", offset.clampedX) + "," + f("%.1f", offset.clampedY) +
        " clamped=" + offset.clamped +
        " frameMag=" + f("%.2f", overrunMag) +
        " frameAng=" + f("%.3f", overrunAngle) +
        " pendingMag=" + f("%.2f", viewportZoomFraction) +
        " pendingAng=" + f("%.3f", viewportAngle) +
        // Committed-vs-displayed deltas + last-commit path: `dAng` is the pending
        // rotation minus the displayed frame's rotation (normalized), `dMag` the pending
        // minus the displayed magnification, `last` whether the previous commit was
        // blitted or rendered — the on-device ranking of P2/P3 from one line.
        " dAng=" + f("%.3f", angleDeltaRadians(viewportAngle, overrunAngle)) +
        " dMag=" + f("%.3f", viewportZoomFraction - overrunMag) +
        " last=" + (if (lastCommitWasBlit) "blit" else "render") +
        " renders=" + fullRenderCount + " blits=" + blitCount
}

/** Current follow anchor — exposed for tests. */
internal fun AutoMapRenderer.followAnchor(): VehicleAnchorPosition = followAnchor

/**
 * Follow-framing center for a vehicle position: the [anchorCenter] of the
 * position under the current surface size, magnification and rotation, with the
 * preset clamped out of the host's panel region (spec: auto/navigation-view —
 * "Panel clearance via side anchor"; change `anchor-per-surface-visible-area`).
 * The host panel covers [PANE_FRACTION] of the surface width on the leading edge
 * (left in LTR, right in RTL — same convention as [paneOffsetCenter]), so a preset
 * inside that band moves to the nearest free position; every other preset keeps
 * its exact fraction, so the default framing is unchanged. Falls back to the raw
 * position when the surface is not ready.
 */
internal fun AutoMapRenderer.anchorCenterFor(lat: Double, lon: Double): Pair<Double, Double> {
    if (surfaceWidth <= 0 || surfaceHeight <= 0) return lat to lon
    val resolved = resolvedFollowAnchor()
    return anchorCenter(
        lat, lon, resolved.fx, resolved.fy,
        viewportZoomFraction, surfaceWidth, surfaceHeight, projectionDpi, viewportAngle
    )
}

/** Current host top-inset flag — exposed for tests. */
internal fun AutoMapRenderer.hostTopInset(): Int = hostTopInsetPx

/** Current host-pane side flag — exposed for tests. */
internal fun AutoMapRenderer.hostPaneRtl(): Boolean = hostPaneRtl

/**
 * Resolved follow anchor fraction (host panel + bottom band clamped) —
 * exposed for tests.
 */
internal fun AutoMapRenderer.resolvedFollowAnchor(): ResolvedAnchor {
    val paneInset = (surfaceWidth * PANE_FRACTION).toInt().coerceAtLeast(0)
    return resolveAnchorFraction(
        followAnchor,
        leftPx = if (hostPaneRtl) 0 else paneInset,
        rightPx = if (hostPaneRtl) paneInset else 0,
        bottomPx = hostBottomInsetPx,
        screenW = surfaceWidth,
        screenH = surfaceHeight
    )
}

/**
 * Displayed-frame accessors (spec: gps-location-marker — Marker projects against
 * displayed bitmap viewport; change `overlay-projects-against-displayed-frame`):
 * center, magnification and rotation of the frame currently on the surface, with
 * the pending render target as the fallback before the first frame exists.
 *
 * The frame bookkeeping is published under [surfaceLock] by [fullRender] (commit)
 * and [blitToSurface] (offset), so a reader inside that lock — every canvas draw
 * happens there — sees one whole frame; the fields are @Volatile for readers
 * outside it. Overlays MUST project against these and not against the pending
 * target: the two differ for one render latency after each viewport write.
 */
internal fun AutoMapRenderer.displayedLat(): Double = if (overrunBitmap != null) overrunLat else viewportLat

internal fun AutoMapRenderer.displayedLon(): Double = if (overrunBitmap != null) overrunLon else viewportLon

internal fun AutoMapRenderer.displayedMag(): Double = if (overrunBitmap != null) overrunMag else viewportZoomFraction

internal fun AutoMapRenderer.displayedAngle(): Double = if (overrunBitmap != null) overrunAngle else viewportAngle
