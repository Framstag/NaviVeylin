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
import com.naviveylin.auto.AutoMapRenderer.Companion.TAG
import com.naviveylin.auto.AutoMapRenderer.Companion.surfaceLock
import com.naviveylin.auto.AutoMapRenderer.Companion.OVERRUN_FACTOR


// The render pipeline: the frame decision, the full native render, the bitmap draw, the
// overrun-blit path and the surface-failure reports.

/**
 * Render one frame: serve a viewport change within the overrun region by a
 * sub-region blit, otherwise perform a full native render at overrun size.
 * Exposed for deterministic tests.
 */
internal fun AutoMapRenderer.renderFrame() {
    val surf = surface ?: return
    val w = surfaceWidth
    val h = surfaceHeight
    if (w <= 0 || h <= 0) return
    // Blit decision + draw under the shared lock (see extrapolationTick:
    // a concurrent full render recycles the overrun bitmap). The full
    // render itself runs OUTSIDE the lock so the extrapolation loop can
    // keep blitting the old frame while the native render is in flight.
    val blit = synchronized(surfaceLock) {
        val ob = overrunBitmap
        if (blitEligible && ob != null &&
            viewportZoomFraction == overrunMag && viewportAngle == overrunAngle
        ) {
            // Single resolved anchor (spec: auto-smooth-follow — Single resolved
            // anchor in the AA follow blit): the SAME fraction anchorCenterFor
            // commits the frame on. The raw preset differs from it for pane-band
            // presets (clampAnchorOutOfPane against the host's 40% leading band) and
            // a mismatch keeps the offset permanently outside the overrun margin —
            // every tick becomes a full native render instead of a sub-region blit.
            val resolvedAnchor = resolvedFollowAnchor()
            // The offset is measured on the DISPLAYED vehicle position, not on the
            // frame center (change `overlay-projects-against-displayed-frame`,
            // spec: auto-map-renderer — Map re-renders on viewport change;
            // auto-smooth-follow — Sub-region blit on viewport change):
            // displayOffsetPx measures a displayed point against the frame the
            // bitmap was rendered with, and the frame is rendered
            // anchor-centered, so this yields the vehicle's drift from its anchor.
            // A frame center is not a point of the rendered bitmap: passing it
            // charges the offset with the anchor displacement ((fy-0.5)*h, up to
            // 0.4x the surface height), which exceeds the overrun margin for every
            // preset away from the center — and then every follow viewport change
            // falls back to a full native render instead of a blit.
            val (blitLat, blitLon) = if (followMode) markerPosition() else viewportLat to viewportLon
            val offset = FollowPrediction.displayOffsetPx(
                blitLat, blitLon,
                overrunLat, overrunLon, overrunMag, overrunAngle,
                ob.width, ob.height, w, h, projectionDpi,
                if (followMode) resolvedAnchor.fx else 0.5,
                if (followMode) resolvedAnchor.fy else 0.5
            )
            if (!offset.clamped) {
                // Viewport change within the overrun region → blit, no
                // native render (spec: auto-map-renderer, design D2).
                blitEligible = false
                if (!followMode) {
                    displayLat = viewportLat
                    displayLon = viewportLon
                }
                blitToSurface(
                    surf, ob, offset.clampedX, offset.clampedY, w, h,
                    if (followMode) resolvedAnchor.fx else 0.5,
                    if (followMode) resolvedAnchor.fy else 0.5
                )
                true
            } else {
                blitEligible = false
                false
            }
        } else {
            blitEligible = false
            false
        }
    }
    if (!blit) {
        // Mark the render in flight for the extrapolation loop and measure it, so
        // the next request can wait for the duration the render actually takes
        // (spec: car-host-fault-isolation — Bounded periodic render work).
        renderInFlight = true
        val renderStartedMs = System.currentTimeMillis()
        try {
            fullRender(surf, w, h)
        } finally {
            lastRenderDurationMs = System.currentTimeMillis() - renderStartedMs
            renderInFlight = false
        }
    }
}

/**
 * Full native render at overrun size (design D1): render at ~1.2x the
 * surface size, keep the bitmap as the overrun buffer, and draw the
 * visible region to the surface.
 */
internal fun AutoMapRenderer.fullRender(surf: Surface, w: Int, h: Int) {
    val renderW = (w * OVERRUN_FACTOR).toInt()
    val renderH = (h * OVERRUN_FACTOR).toInt()

    // Follow mode renders at the CURRENT displayed position, not at the frame
    // target the last fix wrote (spec: smooth-follow — Single follow center;
    // change `aa-follow-framing-and-zoom-parity`). The render runs 100 ms+ after
    // the fix that set the target, and the display keeps advancing in between
    // (plus ticks at ~11 Hz), so a render at the fix-time target is NOT continuous
    // with the blit that preceded it: the frame is anchor-centred on the fix while
    // the scene was placed so the DISPLAY sat at the anchor — the content and the
    // marker then shift by the display's advance since that fix (up to a full fix
    // interval = 25 m at 90 km/h, tens of px at car magnifications) and the next
    // blit moves them again: a sub-second up-and-back that stops when the vehicle
    // stands still. A render centred on the display's own anchor center is
    // continuous with the blit by construction (the blit had already put the
    // display at the anchor).
    if (followMode) {
        val (dLat, dLon) = markerPosition()
        if (!dLat.isNaN() && !dLon.isNaN()) {
            val (aLat, aLon) = anchorCenterFor(dLat, dLon)
            viewportLat = aLat
            viewportLon = aLon
            emitViewportState()
        }
    }

    // Snapshot the frame parameters ONCE (change
    // `overlay-projects-against-displayed-frame`, design D1). The native
    // render runs outside [surfaceLock] — by design, so the extrapolation
    // loop keeps blitting the old frame while it is in flight — so the
    // pending target can be written during the render: a fix re-anchor
    // (`setViewport` + `reengageFollow`), the loop's clamp branch, or an
    // auto-zoom commit. Re-reading the target afterwards to label the
    // committed frame would publish a center/mag/rotation the pixels were
    // NOT rendered at, and the overlays, the blit offset and the diagnostic
    // are all derived from that label: the marker would detach from the map
    // for the whole inter-commit window (~1 s), i.e. exactly the defect this
    // change removes, only intermittently.
    val frameLat = viewportLat
    val frameLon = viewportLon
    val frameAngle = viewportAngle
    val frameMag = viewportZoomFraction

    // The GPS marker is NOT passed to the native renderer (the JNI
    // setGpsMarker export was removed upstream in favor of Kotlin-side
    // overlays); it is drawn on the canvas in drawToSurface.
    //
    // The render target comes from the pool and stays the displayed frame until the
    // next one replaces it (spec: `render-performance` — Reusable render target for
    // map frames; design D5): a render that starts while a frame is displayed therefore
    // acquires a second target, which is why the pool retains a pair per size class.
    val target = RenderBitmapPool.acquire(renderW, renderH)
    val bitmap = try {
        MapRenderUtil.renderInto(
            client = client,
            target = target,
            lat = frameLat,
            lon = frameLon,
            angle = frameAngle,
            magnification = 2.0.pow(frameMag),
            dpi = projectionDpi,
            routeLats = routeLats,
            routeLons = routeLons,
            favoriteLats = favoriteLats,
            favoriteLons = favoriteLons
        )
    } catch (t: Throwable) {
        // Never strand a pooled target when the render throws.
        RenderBitmapPool.release(target)
        throw t
    }

    val now = System.currentTimeMillis()
    if (now - lastRenderLogMs > RENDER_LOG_INTERVAL_MS) {
        lastRenderLogMs = now
        // Identity only, never the centre (spec: auto-diagnostics — A car map-render
        // diagnostic identifies the frame without a position; TODO.md §98). The
        // position-free frame-vs-pending evidence lives in the `follow` entry above
        // (frameMag/pendingMag/dMag/dAng, the clamped pixel offset, the counters), so
        // the frame centre is not needed for an on-device follow or surface diagnosis.
        com.naviveylin.core.DiagnosticsLog.log(
            "MAP",
            renderLogMessage(
                frameMag = frameMag,
                bitmapWidth = bitmap?.width,
                bitmapHeight = bitmap?.height,
                surfaceDpi = projectionDpi
            )
        )
    }

    if (bitmap != null) {
        synchronized(surfaceLock) {
            // The replaced frame goes back to the pool instead of being recycled: the
            // pool owns render targets (spec: render-performance — Reusable render
            // target for map frames).
            overrunBitmap?.let { RenderBitmapPool.release(it) }
            overrunBitmap = bitmap
            // The SNAPSHOT, not the (possibly newer) pending target: this is
            // what the pixels show.
            overrunLat = frameLat
            overrunLon = frameLon
            overrunMag = frameMag
            overrunAngle = frameAngle
            // P3: the displayed-magnification state starts at the frame's own value; later
            // commits leave it easing (a committed step then reaches the eye across
            // frames instead of in one).
            if (displayMag <= 0.0) displayMag = frameMag
            if (!followMode) {
                // In follow mode the extrapolation loop owns the display
                // (the eased predicted position); a render triggered by a
                // transient follow-off (heading-up setViewport) must not
                // yank the display back to the render target — that
                // shows up as the vehicle "pumping" between the target
                // and the fix every fix.
                displayLat = frameLat
                displayLon = frameLon
            }
        }
        fullRenderCount++
        // The last committed viewport change was served by a full native
        // render (diagnostic, task 4.1).
        lastCommitWasBlit = false
        // A freshly rendered frame is drawn with the SAME placement rule as a blit:
        // the offset that puts the current DISPLAYED position on the resolved anchor
        // (spec: smooth-follow — Anchor-centered follow framing; change
        // `aa-follow-framing-and-zoom-parity`). Drawing it unshifted is only correct
        // when the display still sits exactly on the frame's own anchor position —
        // but the native render takes 25-300 ms, during which the display advances,
        // so an unshifted draw leaves the whole scene (map AND marker, which rides the
        // same offset) that advance away from where the previous frame had it: a ~5 px
        // jump up at every commit that the next blit tick moves back — measured on
        // device as an unexplained `dy -55 -> -60` step for an unchanged frame center.
        val (drawOx, drawOy) = if (followMode) {
            val (mLat, mLon) = markerPosition()
            if (mLat.isNaN() || mLon.isNaN()) {
                // No displayed position yet (before the first fix/tick): nothing to
                // place, so draw centered.
                0.0 to 0.0
            } else {
                val resolved = resolvedFollowAnchor()
                val off = FollowPrediction.displayOffsetPx(
                    mLat, mLon,
                    frameLat, frameLon, frameMag, frameAngle,
                    bitmap.width, bitmap.height, w, h, projectionDpi,
                    resolved.fx, resolved.fy
                )
                off.clampedX to off.clampedY
            }
        } else {
            0.0 to 0.0
        }
        val stillCurrent = synchronized(surfaceLock) { isCurrentSurface(surf) }
        if (stillCurrent) {
            drawToSurface(surf, bitmap, w, h, drawOx, drawOy)
        } else {
            // The native render ran outside the lock and outlived its surface
            // (destroyed or replaced meanwhile): the frame belongs to a surface
            // the session already released, so it is dropped instead of drawn
            // (spec: car-host-fault-isolation — "After release the system SHALL
            // NOT lock or draw the released surface").
            android.util.Log.d(TAG, "renderer#$rendererId dropping frame: surface changed during the render")
        }
    } else {
        // A failed render leaves the previous frame displayed; the unused target goes
        // straight back to the pool.
        RenderBitmapPool.release(target)
    }
}

/**
 * Draw a full-render bitmap to the surface at the given blit offset: the offset
 * places the current displayed position on the resolved anchor, so a fresh frame
 * lands exactly where the blitted frames had the scene (offset 0 for browse mode,
 * where the frame center IS the displayed position).
 */
internal fun AutoMapRenderer.drawToSurface(surf: Surface, bitmap: Bitmap, w: Int, h: Int, ox: Double, oy: Double) {
    // The host reuses ONE display surface across screens, so multiple
    // renderers (MapScreen + FreeDrivingScreen) can lock the same Surface
    // concurrently → IllegalArgumentException from lockCanvas ("surface
    // already locked"). Serialize lock/draw/unlock across all renderers.
    // The map-content overlays MUST be shifted by the same offset as the frame
    // they are drawn on. The offset MUST be published under
    // [surfaceLock] together with the frame it describes: assigning it outside
    // the lock lets a concurrent blit overwrite it between the assignment and
    // the lock, so the frame would be drawn with one offset and its overlays
    // with another (a one-frame marker pop the width of the offset).
    synchronized(surfaceLock) {
        blitOffsetX = ox
        blitOffsetY = oy
        var canvas: Canvas? = null
        try {
            // Surface.isValid() false = the host destroyed the underlying
            // buffer queue; lockCanvas would throw IAE. Skip the frame
            // (throttled diagnostics) instead of failing every render.
            if (!surf.isValid) {
                reportFailureIfCurrent(surf, null, "surface invalid (isValid=false)")
                return
            }
            canvas = surf.lockCanvas(null)
            if (canvas == null) {
                // Same condition as a failed lock (already-locked or
                // invalid surface); treat it as a dead surface.
                reportFailureIfCurrent(surf, null, "lockCanvas returned null")
                return
            }
            android.util.Log.d(TAG, "renderer#$rendererId lock OK surface=${System.identityHashCode(surf)}")
            // roundToInt, not toInt: truncation toward zero leaves the CONTENT up to
            // 1 px off the exact placement while the overlays use the exact fractional
            // offset (measured bias +0.9 px in the drawn-frame continuity check).
            val dx = ((w - bitmap.width) / 2f).roundToInt() - roundOffset(ox)
            val dy = ((h - bitmap.height) / 2f).roundToInt() - roundOffset(oy)
            // P3 (see blitToSurface): the scale that eases the displayed magnification
            // toward the committed target, about the follow anchor.
            val scale = displayedScale()
            val anchorX = if (followMode) resolvedFollowAnchor().fx else 0.5
            val anchorY = if (followMode) resolvedFollowAnchor().fy else 0.5
            canvas.save()
            if (scale != 1f) {
                val ax = (anchorX * w).toFloat()
                val ay = (anchorY * h).toFloat()
                canvas.translate(ax, ay)
                canvas.scale(scale, scale)
                canvas.translate(-ax, -ay)
            }
            canvas.drawBitmap(bitmap, dx.toFloat(), dy.toFloat(), null)
            canvas.restore()
            drawGpsMarker(canvas, w, h)
            drawDestinationMarker(canvas, w, h)
            overlayDrawer?.invoke(canvas, w, h)
        } catch (e: Exception) {
            // Surface may be invalid (e.g., during lifecycle transitions)
            // or locked by the host. Log the full stack trace — the
            // message alone is often null.
            reportFailureIfCurrent(surf, e, "lockCanvas failed")
        } finally {
            // ALWAYS unlock: skipping unlock on an exception leaves the
            // surface permanently locked, so every later lockCanvas returns
            // null and the map freezes ("Failed to draw to surface: null").
            if (canvas != null) {
                try {
                    surf.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "renderer#$rendererId failed to unlock surface", e)
                }
            }
        }
    }
}

/**
 * Sub-region blit (design D2): draw the overrun buffer shifted by the
 * display offset directly to the surface — one draw call, no intermediate
 * bitmap, no native render. Serialized on the shared [surfaceLock]; a dead
 * surface goes through the same failure path as a full render.
 */
internal fun AutoMapRenderer.blitToSurface(
    surf: Surface,
    bitmap: Bitmap,
    ox: Double,
    oy: Double,
    w: Int,
    h: Int,
    anchorX: Double,
    anchorY: Double
) {
    // Remember the offset the displayed frame is shifted by: every overlay that
    // represents map content (vehicle marker, destination pin) has to be shifted
    // by the same amount, otherwise it leads the content between commits.
    // Published under [surfaceLock] with the draw (see [drawToSurface]): an
    // assignment outside the lock can be overwritten by a concurrent render,
    // leaving the frame drawn with one offset and its overlays with another
    // (a one-frame marker pop).
    synchronized(surfaceLock) {
        blitOffsetX = ox
        blitOffsetY = oy
        var canvas: Canvas? = null
        try {
            if (!surf.isValid) {
                reportFailureIfCurrent(surf, null, "surface invalid (isValid=false)")
                return
            }
            canvas = surf.lockCanvas(null)
            if (canvas == null) {
                reportFailureIfCurrent(surf, null, "lockCanvas returned null")
                return
            }
            val dx = ((w - bitmap.width) / 2f).roundToInt() - roundOffset(ox)
            val dy = ((h - bitmap.height) / 2f).roundToInt() - roundOffset(oy)
            // P3: apply the displayed-magnification scale about the follow anchor, so a
            // committed magnification change reaches the eye across frames instead of as
            // one scale step (the anchors themselves stay unscaled). A scale of 1 is a
            // no-op — the frame is already at the committed magnification.
            val scale = displayedScale()
            canvas.save()
            if (scale != 1f) {
                val ax = (anchorX * w).toFloat()
                val ay = (anchorY * h).toFloat()
                canvas.translate(ax, ay)
                canvas.scale(scale, scale)
                canvas.translate(-ax, -ay)
            }
            canvas.drawBitmap(bitmap, dx.toFloat(), dy.toFloat(), null)
            canvas.restore()
            drawGpsMarker(canvas, w, h)
            drawDestinationMarker(canvas, w, h)
            overlayDrawer?.invoke(canvas, w, h)
        } catch (e: Exception) {
            reportFailureIfCurrent(surf, e, "blit lockCanvas failed")
        } finally {
            if (canvas != null) {
                try {
                    surf.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "renderer#$rendererId failed to unlock surface", e)
                }
            }
        }
    }
    blitCount++
    // The last committed viewport change was served by a sub-region blit
    // (diagnostic, task 4.1).
    lastCommitWasBlit = true
}

/** Return the overrun frame's render target to the pool (surface change / shutdown). */
internal fun AutoMapRenderer.clearOverrunBuffer() {
    overrunBitmap?.let { RenderBitmapPool.release(it) }
    overrunBitmap = null
    overrunLat = Double.NaN
    overrunLon = Double.NaN
    overrunMag = 0.0
    overrunAngle = 0.0
    displayLat = Double.NaN
    displayLon = Double.NaN
    followDisplayState.reset()
}

/**
 * Whether [surf] is still the surface this renderer draws on. A frame whose
 * surface was destroyed or replaced while the native render was in flight must be
 * dropped (spec: car-host-fault-isolation — "After release the system SHALL NOT
 * lock or draw the released surface"). Exposed for tests.
 */
internal fun AutoMapRenderer.isCurrentSurface(surf: Surface): Boolean = surface === surf

/**
 * Whether this renderer currently considers its surface dead (a failed lock or an
 * invalid surface). Exposed for tests: the stop path clears it (spec:
 * auto-map-renderer — A stopped renderer holds no surface or frame buffer).
 */
internal fun AutoMapRenderer.isSurfaceFailed(): Boolean = surfaceFailed

/**
 * Report a draw failure only while [surf] is still the renderer's surface: a
 * surface that was destroyed or replaced while a render was in flight is stale,
 * not a failure of the current one — reporting it would set `surfaceFailed` and
 * stop the render loop for the live surface (observed on the automotive AVD:
 * a background round trip replaced the surface and the map stayed frozen).
 */
internal fun AutoMapRenderer.reportFailureIfCurrent(surf: Surface, e: Throwable?, why: String) {
    if (isCurrentSurface(surf)) {
        reportSurfaceFailure(surf, e, why)
    } else {
        android.util.Log.d(
            TAG,
            "renderer#$rendererId $why on a replaced surface ${System.identityHashCode(surf)} — ignored"
        )
    }
}

/**
 * Records that the current surface cannot be drawn to, stops the render
 * loop (via [surfaceFailed]) and notifies the owner. Throttled: a dead
 * surface fails every frame.
 */
internal fun AutoMapRenderer.reportSurfaceFailure(surf: Surface, e: Throwable?, why: String) {
    val now = System.currentTimeMillis()
    if (now - lastSurfaceFailureLogMs >= SURFACE_FAILURE_LOG_INTERVAL_MS) {
        lastSurfaceFailureLogMs = now
        val msg = "renderer#$rendererId $why valid=${surf.isValid} " +
            "surface=${System.identityHashCode(surf)} " +
            "size=${surfaceWidth}x$surfaceHeight"
        if (e != null) {
            android.util.Log.w(TAG, msg, e)
        } else {
            android.util.Log.w(TAG, msg)
        }
    }
    surfaceFailed = true
    if (now - lastSurfaceFailureCallbackMs >= SURFACE_FAILURE_CALLBACK_INTERVAL_MS) {
        lastSurfaceFailureCallbackMs = now
        onSurfaceFailed?.invoke()
    }
}
