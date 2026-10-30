package com.naviveylin.ui.map

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.naviveylin.core.FollowDisplayState
import com.naviveylin.core.FollowPrediction
import com.naviveylin.core.FollowRefreshPolicy
import com.naviveylin.core.ZoomAnimation
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.pow


/**
 * The phone map screen's follow display loop: one `withFrameNanos` iteration per frame that advances
 * the smooth-zoom animation and the render-completion crossfades, feeds the follow prediction, eases
 * the displayed centre, computes the follow blit offset through [FollowBlitRules] and the pan display
 * window through [PanWindowRules], and requests the next frame before the overrun margin runs out.
 *
 * Moved out of `MapCanvasScreen.kt` verbatim by `2026-10-25-fix-map-canvas-screen-file-size` (the
 * `source-size` row's bound). The screen's remembered display values are threaded in as [MutableState]
 * rather than by value, because the loop's `LaunchedEffect` body is started once and can only see
 * current values through the state objects; the local functions it calls are threaded in as lambdas
 * for the same reason. Behaviour — the frame loop, its ordering and its log lines — is unchanged, and
 * `FollowBlitCallSiteTest` reads this file for the seam call it pins.
 */

/** Logcat tag of the phone map screen, kept identical to the file the loop was split out of. */
private const val TAG = "MapCanvasScreen"

/** Duration of the render-completion crossfade in ms (smooth-zoom / zoom-transition-scaling delta). */
private const val CROSSFADE_MS = 150.0f

/**
 * Movement threshold (km/h) for the follow-mode extrapolation loop: below it
 * the display is frozen (spec: smooth-follow — Extrapolation loop gating,
 * delta fix-follow-vehicle-jumps).
 */
private const val FOLLOW_MIN_SPEED_KMH = 1.8

@Composable
internal fun MapCanvasFollowDisplayLoop(
    viewModel: MapCanvasViewModel,
    context: Context,
    zoomAnim: ZoomAnimation,
    followPrediction: FollowPrediction,
    followDisplayState: FollowDisplayState,
    followRefresh: FollowRefreshPolicy,
    endCrossfade: () -> Unit,
    animateDiscreteZoom: (Offset, Long) -> Unit,
    zoomAnimationAnchor: () -> Offset,
    zoomAnchorState: MutableState<Offset>,
    requestPanRecenter: (Boolean) -> Unit,
    canvasSizeState: MutableState<IntSize>,
    crossfadeAlphaState: MutableState<Float>,
    crossfadeAnchorState: MutableState<Offset>,
    crossfadeAngleState: MutableState<Float>,
    crossfadeBitmapState: MutableState<ImageBitmap?>,
    crossfadeScaleState: MutableState<Float>,
    crossfadeStartMsState: MutableState<Long>,
    followActiveState: MutableState<Boolean>,
    followDisplayLatState: MutableState<Double>,
    followDisplayLonState: MutableState<Double>,
    followLogCountState: MutableState<Int>,
    followOffsetXState: MutableState<Float>,
    followOffsetYState: MutableState<Float>,
    gestureActiveState: MutableState<Boolean>,
    gestureBaseScaleState: MutableState<Float>,
    gestureZoomState: MutableState<Float>,
    lastFrameViewportState: MutableState<MapRenderer.RenderViewport?>,
    lastFrontAngleState: MutableState<Double>,
    lastFrontMagState: MutableState<Double>,
    lastZoomAnimTargetState: MutableState<Double>,
    panDisplayLatState: MutableState<Double>,
    panDisplayLonState: MutableState<Double>,
    panGestureActiveState: MutableState<Boolean>,
    panOffsetXState: MutableState<Float>,
    panOffsetYState: MutableState<Float>,
    prevRenderedBitmapState: MutableState<ImageBitmap?>,
    rotationHoldActiveState: MutableState<Boolean>,
    zoomAnimScaleState: MutableState<Float>
) {
    LaunchedEffect(Unit) {
        var lastFixTime = -1L
        // The fix the prediction was last fed, and the position the display's correction references
        // (design D1: the prediction supplies the speed, the fix supplies the position).
        var followBaseLat = Double.NaN
        var followBaseLon = Double.NaN
        var lastFrameMs = 0L
        var lastRenderRequestMs = 0L
        while (isActive) {
            withFrameNanos { _ ->
                val ui = viewModel.uiState.value
                val fix = ui.gpsLocation
                val nowMs = System.currentTimeMillis()

                // smooth-zoom (spec: smooth-zoom): drive the eased animation
                // and advance the render-completion crossfade each frame.
                if (zoomAnim.active) {
                    zoomAnimScaleState.value = zoomAnim.tick(nowMs)
                }
                if (crossfadeBitmapState.value != null) {
                    val t = (nowMs - crossfadeStartMsState.value).toFloat() / CROSSFADE_MS
                    if (t >= 1f) {
                        endCrossfade()
                    } else {
                        crossfadeAlphaState.value = 1f - t
                    }
                }

                // Continuous display scale: when a gesture is active and the front
                // buffer swaps mid-gesture (previous render completion), the frozen
                // base scale must be transformed to the new buffer level so the
                // composed visual (buffer × base × factor) stays continuous —
                // otherwise the committed frame replaces the buffer at a different
                // level and shows a zoom jump until render-land.
                // The magnification the display animation is heading for: a walked step
                // while a walk is pending, the committed viewport magnification otherwise
                // (spec: smooth-zoom - the displayed frame never shows a magnification
                // farther than the frame in hand can serve).
                val committedMag = ui.zoomAnimationTargetMag
                val frontMag = ui.renderViewport?.mag ?: -1.0
                if (frontMag > 0 && lastFrontMagState.value > 0 && frontMag != lastFrontMagState.value &&
                    (gestureBaseScaleState.value != 1f || gestureZoomState.value != 1f)) {
                    // Invert: the base represented 2^(commit − bufferOld); after the
                    // swap the new buffer carries 2^(bufferNew − bufferOld) of the
                    // visual itself, so base must be DIVIDED by the level delta
                    // (base × 2^(old − new)). When the swap is the committed render,
                    // base collapses to 1.0 (the factor alone stays in gestureZoomState.value).
                    gestureBaseScaleState.value = (gestureBaseScaleState.value * Math.pow(
                        2.0, (lastFrontMagState.value - frontMag)
                    )).toFloat()
                    Log.d(TAG, "smooth-zoom: gesture base rescaled to ${gestureBaseScaleState.value} (buffer ${lastFrontMagState.value} -> $frontMag)")
                }

                // Render-completion handoff (design D4): when the front buffer
                // swaps to the committed magnification, the rendered frame
                // supplies the target zoom exactly. Animation-case (uniform
                // hold at the aligned scale) swaps immediately; any residual
                // mismatch (fractional pinch end followed within the freeze)
                // crossfades from the scaled old frame so no single-frame
                // content jump is visible (spec: zoom-transition-scaling).
                if (lastFrontMagState.value > 0 && frontMag == committedMag &&
                    lastFrontMagState.value != frontMag && zoomAnimScaleState.value != 1f) {
                    val hold = 2.0.pow((committedMag - lastFrontMagState.value).toDouble()).toFloat()
                    zoomAnim.finish(nowMs)
                    if (abs(zoomAnimScaleState.value - hold) < 0.02f) {
                        // Animation already holds the frame-aligned scale —
                        // the swapped-in frame matches exactly.
                        zoomAnimScaleState.value = 1f
                    } else {
                        prevRenderedBitmapState.value?.let { oldBitmap ->
                            crossfadeBitmapState.value = copyImageBitmap(oldBitmap)
                            crossfadeScaleState.value = zoomAnimScaleState.value
                            crossfadeAnchorState.value = zoomAnchorState.value
                            crossfadeStartMsState.value = nowMs
                            crossfadeAlphaState.value = 1f
                        }
                        zoomAnimScaleState.value = 1f
                    }
                    Log.d(TAG, "smooth-zoom: render landed mag=$frontMag hold=$hold displayed=${zoomAnimScaleState.value} crossfade=${crossfadeBitmapState.value != null}")
                }

                // Walked magnification step (spec: smooth-zoom - Stepped magnification
                // change retracks per step): a landed step retargets the running display
                // animation instead of restarting it, keeping the cadence the animation
                // was started with. Gated off while a gesture handoff owns the displayed
                // scale (one displayed-scale model, design D3).
                if (committedMag != lastZoomAnimTargetState.value) {
                    lastZoomAnimTargetState.value = committedMag
                    if (!gestureActiveState.value && gestureBaseScaleState.value == 1f) {
                        animateDiscreteZoom(zoomAnimationAnchor(), zoomAnim.activeDurationMs)
                    }
                }
                // Rotation render-land (design D1): when the front buffer swaps to
                // the committed angle while the rotation hold is armed, disarm the
                // hold and crossfade the old rotated frame into the new native
                // render — masks any tile-vs-native rasterization difference at the
                // swap (mirror of the zoom crossfade above). The old frame is drawn
                // rotated by (committed − old front-buffer angle) so it aligns with
                // the new frame while fading out.
                val frontAngle = ui.renderViewport?.angle
                if (rotationHoldActiveState.value && frontAngle != null &&
                    frontAngle == ui.viewport.angle && lastFrontAngleState.value != frontAngle) {
                    prevRenderedBitmapState.value?.let { oldBitmap ->
                        // A superseding transition REPLACES the copy an in-flight one holds — it
                        // never stacks a second frame copy on top of it.
                        if (crossfadeBitmapState.value == null) {
                            crossfadeBitmapState.value = copyImageBitmap(oldBitmap)
                            crossfadeScaleState.value = 1f
                            crossfadeAnchorState.value = Offset(
                                canvasSizeState.value.width / 2f, canvasSizeState.value.height / 2f
                            )
                            crossfadeStartMsState.value = nowMs
                            crossfadeAlphaState.value = 1f
                        }
                        crossfadeAngleState.value = (ui.viewport.angle - lastFrontAngleState.value).toFloat()
                    }
                    rotationHoldActiveState.value = false
                    Log.d(TAG, "rotation: render landed angle=${Math.toDegrees(frontAngle)} crossfade=${crossfadeBitmapState.value != null}")
                }
                lastFrontMagState.value = frontMag
                lastFrontAngleState.value = frontAngle ?: lastFrontAngleState.value
                prevRenderedBitmapState.value = ui.renderedBitmap
                if (fix != null && fix.time != lastFixTime) {
                    // Use the receipt time (not fix.time) as the prediction base:
                    // GPS fix timestamps can be ahead of the system clock (GPS
                    // time vs UTC), which would make the extrapolation window
                    // negative and hold the position instead of scrolling.
                    //
                    // Single position source (delta fix-follow-vehicle-jumps, spec:
                    // smooth-follow — Display center extrapolation "Prediction base
                    // matches the rendered position during guidance"): the
                    // prediction extrapolates from the SAME position the ViewModel
                    // renders with — the engine position while navigating, the raw
                    // GPS fix otherwise — so the frame center and the prediction
                    // cannot disagree at a junction snap.
                    val srcLat = if (!ui.gpsMarkerLat.isNaN()) ui.gpsMarkerLat else fix.lat
                    val srcLon = if (!ui.gpsMarkerLon.isNaN()) ui.gpsMarkerLon else fix.lon
                    followBaseLat = srcLat
                    followBaseLon = srcLon
                    followPrediction.update(
                        srcLat, srcLon,
                        if (fix.speedKmH.isNaN()) Double.NaN else fix.speedKmH / 3.6,
                        if (fix.smoothedBearing.isNaN()) Double.NaN else fix.smoothedBearing,
                        nowMs
                    )
                    lastFixTime = fix.time
                }
                val dtSec = if (lastFrameMs > 0) (nowMs - lastFrameMs) / 1000.0 else 0.016
                lastFrameMs = nowMs
                val moving = ui.followMode && fix != null &&
                        !fix.speedKmH.isNaN() && fix.speedKmH > FOLLOW_MIN_SPEED_KMH
                if (moving) {
                    val heading = if (fix!!.smoothedBearing.isNaN()) Double.NaN else fix.smoothedBearing
                    // Bounded-rate display (spec: smooth-follow — Correction easing): the display
                    // advances at the committed effective speed plus a rate-clamped correction
                    // toward the fix, so a fix that lands behind the display becomes a rate dip
                    // instead of a halt. The reference is the fix itself, not the extrapolated
                    // position: the prediction supplies the speed, the fix supplies the position,
                    // so the correction cannot double-count the vehicle's own motion.
                    val vEff = followPrediction.effectiveSpeedMs(nowMs)
                    // The reference is the extrapolated target the prediction publishes (design D1): a
                    // CONTINUOUS reference, not the raw fix — the raw fix is a step function between
                    // arrivals and pulling the display onto it would run the map a quarter slow.
                    val predicted = followPrediction.predictedPosition(nowMs)
                    val (dLat, dLon) = followDisplayState.advance(
                        predicted.first, predicted.second, dtSec, heading, vEff,
                        followPrediction.feedAgeSec(nowMs)
                    )
                    followDisplayLatState.value = dLat
                    followDisplayLonState.value = dLon
                    followActiveState.value = true
                    // Single follow center (spec: smooth-follow — Prediction state
                    // update): the displayed position IS the follow center the
                    // ViewModel anchors every follow render on — a fix never
                    // re-commits the center on its own.
                    viewModel.followDisplayLat = dLat
                    viewModel.followDisplayLon = dLon
                    // Compute the display offset against the current frame and clamp
                    // to the overrun margin; request a render when clamped so the map
                    // keeps scrolling instead of sticking at the edge.
                    val bitmap = ui.renderedBitmap
                    val vp = ui.renderViewport
                    if (bitmap != null && vp != null && canvasSizeState.value.width > 0) {
                        val dpi = context.resources.displayMetrics.densityDpi.toDouble()
                        // Single resolved anchor (spec: smooth-follow — Single resolved
                        // anchor across render, blit and marker): FollowBlitRules takes the whole
                        // state and reads `resolvedAnchor` itself, so the raw preset
                        // (activeFollowAnchor) cannot reach this call site — it differs from the
                        // resolved fraction whenever navigation overlays move the anchor
                        // (bottom-center above the routing card), and a blit against it places the
                        // displayed content at a different fraction than the marker and keeps the
                        // offset outside the overrun margin (500 ms re-render churn).
                        val offset = FollowBlitRules.offsetFor(
                            ui,
                            PanWindowRules.Frame(
                                vp.lat, vp.lon, vp.mag, vp.angle,
                                bitmap.width, bitmap.height
                            ),
                            followDisplayLatState.value, followDisplayLonState.value,
                            canvasSizeState.value.width, canvasSizeState.value.height, dpi
                        )
                        // The frame is rendered anchor-centered (spec: smooth-follow —
                        // Anchor-centered follow framing), so the blit offset carries the
                        // prediction drift only. Applying the anchor here as well would push
                        // the frame outside the overrun margin and leave an uncovered strip
                        // of surface color; the clamp keeps the offset inside the margin.
                        followOffsetXState.value = offset.clampedX.toFloat()
                        followOffsetYState.value = offset.clampedY.toFloat()
                        // The requested frame landed: arm the refresh threshold again. The viewport is
                        // the frame description the display holds, so a change means a new frame.
                        if (vp != lastFrameViewportState.value) {
                            lastFrameViewportState.value = vp
                            followRefresh.onFrameLanded()
                        }
                        // Request the next frame while margin remains (spec: smooth-follow — Follow
                        // frame refresh before the overrun margin is exhausted, design D2). The clamp
                        // below is the LAST resort: requesting only when `offset.clamped` was already
                        // true pinned the drawn content at the margin for a whole render — measured
                        // 1.5-2.7 s of byte-identical frames in 29 % of a steady 50 km/h drive.
                        val (driftPx, marginPx) = followRefreshWindow(
                            offset.rawX, offset.rawY,
                            bitmap.width, bitmap.height, canvasSizeState.value.width, canvasSizeState.value.height
                        )
                        if (nowMs - lastRenderRequestMs > 500 &&
                            followRefresh.shouldRequest(driftPx, marginPx, nowMs)
                        ) {
                            lastRenderRequestMs = nowMs
                            // Re-anchor on the displayed position (not the viewport
                            // center): the frame stays anchor-centered and the display
                            // lands back inside the margin.
                            viewModel.renderFollowFrameAt(followDisplayLatState.value, followDisplayLonState.value)
                        }
                        // Diagnostic: log the prediction state on fix arrival so a
                        // device logcat shows exactly where the overshoot comes from
                        // (fix vs predicted vs displayed vs offset).
                        if (fix.time == lastFixTime && followLogCountState.value++ % 30 == 0) {
                            val dbg = followPrediction.debugState(nowMs)
                            // Numbers are evidence, not display text — the builder fixes their locale
                            // so a German install prints the same line (spec: i18n-l10n — Log and
                            // diagnostic numbers are locale-independent, issue #44).
                            Log.d(
                                TAG,
                                followDiagnosticLine(
                                    fix.time, fix.speedKmH, fix.smoothedBearing, dbg, offset
                                )
                            )
                        }
                    }
                } else if (ui.followMode && fix != null) {
                    // Frozen stop/go (spec: smooth-follow — Extrapolation loop
                    // gating; gps-location-marker — "Marker stays put at a brief
                    // stop"): below the movement threshold the displayed position
                    // and the blit offset stay FROZEN, never reset — the resume
                    // continues from here without a snap.
                    if (!followDisplayState.hasPosition) {
                        // Seed the display at the marker source on the first fix at
                        // standstill so the marker has a consistent, road-true point.
                        val seedLat = if (!ui.gpsMarkerLat.isNaN()) ui.gpsMarkerLat else fix!!.lat
                        val seedLon = if (!ui.gpsMarkerLon.isNaN()) ui.gpsMarkerLon else fix!!.lon
                        followDisplayState.advance(seedLat, seedLon, 0.0, Double.NaN, 0.0)
                        followDisplayLatState.value = seedLat
                        followDisplayLonState.value = seedLon
                        viewModel.followDisplayLat = seedLat
                        viewModel.followDisplayLon = seedLon
                    }
                    followActiveState.value = true
                } else {
                    // Follow mode disengaged or no fix: only now is the display
                    // reset (offsets zeroed) — next engagement re-initializes.
                    followActiveState.value = false
                    followDisplayLatState.value = Double.NaN
                    followDisplayLonState.value = Double.NaN
                    followOffsetXState.value = 0f
                    followOffsetYState.value = 0f
                    followDisplayState.reset()
                    followRefresh.reset()
                    viewModel.followDisplayLat = Double.NaN
                    viewModel.followDisplayLon = Double.NaN
                }

                // Pan display window (spec: canvas-overrun — Overrun window shift for
                // pan): while a single-finger pan is live or its center is held,
                // position the frame in hand by the delta between the DISPLAYED center
                // and the frame's own viewport, rotated with the viewport and clamped
                // to the overrun margin. Derived here, once per frame, from the frame
                // actually on screen — no per-event work and no native render while
                // the window stays inside the margin.
                val panFrameVp = ui.renderViewport
                val panFrameBitmap = ui.renderedBitmap
                if (!followActiveState.value && !panDisplayLatState.value.isNaN()) {
                    val frame = if (panFrameVp != null && panFrameBitmap != null) {
                        PanWindowRules.Frame(
                            panFrameVp.lat, panFrameVp.lon, panFrameVp.mag, panFrameVp.angle,
                            panFrameBitmap.width, panFrameBitmap.height
                        )
                    } else {
                        null
                    }
                    if (PanWindowRules.holdReleased(
                            panDisplayLatState.value, panDisplayLonState.value,
                            frame,
                            gestureActive = panGestureActiveState.value
                        )
                    ) {
                        // A frame carrying the panned center is on screen: the display is
                        // where the pan put it. Release the hold (offset 0 by definition,
                        // no jump: the displayed center and the frame's center are the
                        // same position).
                        panDisplayLatState.value = Double.NaN
                        panDisplayLonState.value = Double.NaN
                        panOffsetXState.value = 0f
                        panOffsetYState.value = 0f
                    } else {
                        val panWindow = PanWindowRules.offsetFor(
                            panDisplayLatState.value, panDisplayLonState.value, frame,
                            canvasSizeState.value.width, canvasSizeState.value.height,
                            context.resources.displayMetrics.densityDpi.toDouble()
                        )
                        panOffsetXState.value = panWindow?.clampedX?.toFloat() ?: 0f
                        panOffsetYState.value = panWindow?.clampedY?.toFloat() ?: 0f
                        // A window the frame cannot serve must not leave the pan looking
                        // dead: the finger keeps moving the displayed center and the
                        // screen asks for a re-centered frame at the throttled cadence
                        // (spec: map-pan-zoom — A pan SHALL never be invisible).
                        if (panWindow == null) requestPanRecenter(false)
                    }
                } else if (!followActiveState.value) {
                    panOffsetXState.value = 0f
                    panOffsetYState.value = 0f
                }
            }
        }
    }
}
