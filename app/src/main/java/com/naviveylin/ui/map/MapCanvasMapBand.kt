package com.naviveylin.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import android.content.Context
import com.naviveylin.core.FollowPrediction
import com.naviveylin.ui.route.RoutePanelUiState

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naviveylin.R
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.anchorCenter
import kotlin.math.abs


/**
 * The phone map screen's [MapLayer.MAP] band content: the rendered frame with its gesture handling, the
 * analysed-route highlight, the displayed frame transform and the loading/error states. Moved out of
 * `MapCanvasScreen.kt` verbatim by `2026-10-25-fix-map-canvas-screen-file-size`; the band's own
 * `Modifier.zIndex`/`testTag` wrapper stayed in the screen, so the overlay layer stack and its
 * back-handler registration order are still decided there.
 */

/** Logcat tag of the phone map screen, kept identical to the file the band was split out of. */
private const val TAG = "MapCanvasScreen"

@Composable
internal fun BoxScope.MapCanvasMapBandContent(
    state: MapCanvasUiState,
    viewModel: MapCanvasViewModel,
    routeState: RoutePanelUiState,
    surfaceColor: Color,
    context: Context,
    animateDiscreteZoom: (Offset) -> Unit,
    animateDiscreteZoomToCenter: () -> Unit,
    freezeZoomAnimationForGesture: () -> Unit,
    panWindowOffset: () -> FollowPrediction.Companion.DisplayOffset?,
    commitPanCenter: () -> Unit,
    requestPanRecenter: (Boolean) -> Unit,
    attributionInteractionTickState: MutableState<Int>,
    canvasSizeState: MutableState<IntSize>,
    crossfadeAlphaState: MutableState<Float>,
    crossfadeAnchorState: MutableState<Offset>,
    crossfadeAngleState: MutableState<Float>,
    crossfadeBitmapState: MutableState<ImageBitmap?>,
    crossfadePivotState: MutableState<Offset>,
    crossfadeScaleState: MutableState<Float>,
    followActiveState: MutableState<Boolean>,
    followDisplayLatState: MutableState<Double>,
    followDisplayLonState: MutableState<Double>,
    followOffsetXState: MutableState<Float>,
    followOffsetYState: MutableState<Float>,
    gestureActiveState: MutableState<Boolean>,
    gestureBaseScaleState: MutableState<Float>,
    gestureCentroidState: MutableState<Offset>,
    gesturePanState: MutableState<Offset>,
    gesturePivotState: MutableState<Offset>,
    gestureRotationState: MutableState<Float>,
    gestureSmoothFactorState: MutableState<Float>,
    gestureZoomState: MutableState<Float>,
    lastPanRenderRequestMsState: MutableState<Long>,
    panDisplayLatState: MutableState<Double>,
    panDisplayLonState: MutableState<Double>,
    panGestureActiveState: MutableState<Boolean>,
    panOffsetXState: MutableState<Float>,
    panOffsetYState: MutableState<Float>,
    rotationHoldActiveState: MutableState<Boolean>,
    zoomAnchorState: MutableState<Offset>,
    zoomAnimScaleState: MutableState<Float>
) {
    when {
        state.isLoading && state.renderedBitmap == null -> {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        state.error != null && state.renderedBitmap == null -> {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.error ?: stringResource(R.string.unknown_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.retryRender() }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }

        else -> {
            // Map canvas with gesture handling
            // Single pointerInput block handles all gestures to avoid conflicts
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .focusTarget()
                    .onSizeChanged { size ->
                        viewModel.setScreenSize(size.width, size.height)
                        canvasSizeState.value = size
                    }
                    // Live gesture transform on the current bitmap: rotation,
                    // zoom, and pan are applied visually with no render calls
                    // until the gesture ends (onRenderRequested commits).
                    // The rotation AND zoom pivot at the finger midpoint
                    // (gesturePivotState.value, FROZEN at the gesture-start midpoint): the
                    // geographic point under the midpoint at gesture start stays
                    // under the midpoint for the whole gesture, and the
                    // gesture-end commit adjusts the viewport center so the
                    // anchor holds in the rendered frame (design D1/D2, spec:
                    // Rotation anchored at the finger midpoint). Rotating around
                    // an off-center pivot can expose empty corners at large
                    // angles (accepted — the overrun buffer margin covers
                    // moderate angles; standard map-app behavior).
                    .graphicsLayer {
                        // Rotation display-layer hold (design D1): between gesture
                        // end and render land the display keeps the final angle by
                        // rotating the current front buffer by the angle gap
                        // (committed − front-buffer) — derived from the SAME
                        // collected state the draw reads, so the rotation zeroes
                        // atomically with the committed bitmap swap (no one-frame
                        // double-rotation twist). rotationDisplayTheta is pure.
                        val theta = rotationDisplayTheta(
                            gestureRotationState.value, rotationHoldActiveState.value,
                            state.renderViewport?.angle, state.viewport.angle
                        )
                        val s = gestureZoomState.value
                        // Pivot: the frozen gesture-start midpoint during the
                        // gesture; the gesture-end midpoint (crossfadePivotState.value) while
                        // the rotation hold is armed, so the held rotation and the
                        // render-land crossfade pivot around the commit's focal
                        // point (D2) — the same point the committed render rotates
                        // around.
                        val pivot = if (rotationHoldActiveState.value && crossfadePivotState.value != Offset.Zero) {
                            crossfadePivotState.value
                        } else {
                            gesturePivotState.value
                        }
                        val t = gestureTransformTranslation(
                            theta, s, pivot, size, gesturePanState.value
                        )
                        translationX = t.x
                        translationY = t.y
                        rotationZ = normalizeDegrees(Math.toDegrees(theta.toDouble())).toFloat()
                        scaleX = s
                        scaleY = s
                        // Pivot at the finger midpoint (fraction of the layer
                        // size); fall back to the screen center when unset.
                        val pw = size.width
                        val ph = size.height
                        transformOrigin = if (pw > 0f && ph > 0f &&
                            pivot.x.isFinite() && pivot.y.isFinite() &&
                            (pivot != Offset.Zero || theta != 0f)
                        ) {
                            TransformOrigin(
                                (pivot.x / pw).coerceIn(0f, 1f),
                                (pivot.y / ph).coerceIn(0f, 1f)
                            )
                        } else {
                            TransformOrigin(0.5f, 0.5f)
                        }
                    }
                    .mapGestureHandler(
                        // The long-press duration is the configured setting, not a literal
                        // (spec: map-long-press-timeout — The long-press timeout is a
                        // configuration, not a literal).
                        longPressTimeoutMs = state.longPressTimeoutMs.toLong(),
                        callbacks = object : MapGestureCallbacks {
                            override fun onPan(dx: Float, dy: Float) {
                                // Gesture-start side effects run once per gesture
                                // (spec: render-performance — Pan hot path stays
                                // off the frame budget): follow-mode disengagement
                                // and the attribution interaction tick must not
                                // repeat on every touch event.
                                if (!panGestureActiveState.value) {
                                    panGestureActiveState.value = true
                                    attributionInteractionTickState.value++
                                    // Leave follow mode on the framing the user sees
                                    // (spec: smooth-follow — anchor restored), then
                                    // pan from the displayed frame center.
                                    viewModel.disengageFollowMode()
                                    val start = viewModel.uiState.value
                                    panDisplayLatState.value = start.viewport.centerLat
                                    panDisplayLonState.value = start.viewport.centerLon
                                }
                                val s = viewModel.uiState.value
                                val dpi = context.resources.displayMetrics.densityDpi.toDouble()
                                // Chain the delta from the DISPLAYED center (not from
                                // the committed viewport): the committed center only
                                // follows at gesture end, so chaining off it would
                                // make every event recompute from a stale base and
                                // the content would lag the finger.
                                val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenterRotated(
                                    dx.toDouble(), dy.toDouble(),
                                    s.viewport.angle,
                                    s.viewport.magnification,
                                    canvasSizeState.value.width.toDouble(), canvasSizeState.value.height.toDouble(),
                                    panDisplayLatState.value, panDisplayLonState.value, dpi
                                )
                                if (newLat.isNaN() || newLon.isNaN()) return
                                // Same clamp the ViewModel applies on commit, so the
                                // displayed and the committed center can never differ.
                                panDisplayLatState.value = newLat.coerceIn(-85.0, 85.0)
                                panDisplayLonState.value = newLon.coerceIn(-180.0, 180.0)
                                // No render while the frame in hand still covers the
                                // pan window (spec: canvas-overrun — Small pan uses
                                // sub-region blit).
                                requestPanRecenter(false)
                            }

                            override fun onCentroidPan(dx: Float, dy: Float) {
                                attributionInteractionTickState.value++
                                // Update the center state (no render); the visual
                                // translation is applied to the current bitmap and
                                // committed on gesture end.
                                viewModel.disengageFollowMode()
                                val s = viewModel.uiState.value
                                val dpi = context.resources.displayMetrics.densityDpi.toDouble()
                                val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenterRotated(
                                    dx.toDouble(), dy.toDouble(),
                                    s.viewport.angle,
                                    s.viewport.magnification,
                                    canvasSizeState.value.width.toDouble(), canvasSizeState.value.height.toDouble(),
                                    s.viewport.centerLat, s.viewport.centerLon, dpi
                                )
                                Log.d(TAG, "gesture centroidPan dx=" + dx + " dy=" + dy +
                                    " canvas=" + canvasSizeState.value.width + "x" + canvasSizeState.value.height +
                                    " angle=" + s.viewport.angle + " mag=" + s.viewport.magnification)
                                viewModel.updateCenter(newLat, newLon)
                                gesturePanState.value += Offset(dx, dy)
                            }

                            override fun onRotate(angleDeltaRadians: Double) {
                                attributionInteractionTickState.value++
                                // A new gesture supersedes any held rotation from
                                // the previous one (design D1 fallback).
                                rotationHoldActiveState.value = false
                                // Disengage follow mode + clear north-up immediately;
                                // the angle is applied visually to the current bitmap
                                // and committed on gesture end.
                                viewModel.onManualRotationStart()
                                gestureRotationState.value += angleDeltaRadians.toFloat()
                            }

                            override fun onGestureCentroid(centroid: Offset) {
                                // A single-finger pan that grew a second finger: the
                                // multi-touch commit math builds on the viewport
                                // center, so commit the displayed (panned) center
                                // here and let the frame catch up. The pan window is
                                // KEPT until the re-centered frame lands (clearing it
                                // would snap the content back by the offset); the
                                // frame loop releases it on that frame.
                                if (panGestureActiveState.value) {
                                    panGestureActiveState.value = false
                                    commitPanCenter()
                                    requestPanRecenter(true)
                                }
                                // A new gesture supersedes any held rotation from the
                                // previous one (design D1 fallback).
                                rotationHoldActiveState.value = false
                                // smooth-zoom: a pinch gesture takes over the
                                // zoom display (design D3) — park any running
                                // discrete-zoom animation at its current scale.
                                freezeZoomAnimationForGesture()
                                // Clamp the pivot to the canvas: corrupted pointer
                                // positions from multi-touch emulation would otherwise
                                // produce a garbage zoom/rotation pivot (map swings away).
                                val cw = canvasSizeState.value.width.toFloat()
                                val ch = canvasSizeState.value.height.toFloat()
                                gestureCentroidState.value = if (cw > 0f && ch > 0f &&
                                    centroid.x.isFinite() && centroid.y.isFinite()) {
                                    Offset(centroid.x.coerceIn(0f, cw), centroid.y.coerceIn(0f, ch))
                                } else {
                                    Offset(cw / 2f, ch / 2f)
                                }
                                // Rotation/zoom pivot = the finger midpoint
                                // (design D1), FROZEN at the FIRST midpoint of
                                // the gesture: a live pivot re-anchors the
                                // accumulated rotation/zoom each frame and jumps
                                // the map by (I − s·R(θ))·ΔC (archived
                                // 2026-08-15-bugfix-rotation defect). The
                                // centroid drift is carried by the pan
                                // compensation (gestureTransformTranslation).
                                if (!gestureActiveState.value) {
                                    gesturePivotState.value = gestureCentroidState.value
                                    gestureActiveState.value = true
                                }
                            }

                            override fun onZoom(centroid: Offset, zoomFactor: Float) {
                                attributionInteractionTickState.value++
                                // smooth-zoom: gesture takes over the zoom display.
                                freezeZoomAnimationForGesture()
                                // Continuous zoom factor vs gesture start; applied
                                // visually and committed on gesture end. Clamped to
                                // the range the commit can actually deliver: the
                                // committed magnification is clamped to
                                // [GESTURE_MIN_MAG, MAX_MAG], so at the limits the
                                // visual preview must not exceed the headroom —
                                // otherwise the map zooms in visually and then
                                // snaps back on gesture end.
                                // Damped visual factor: track the raw cumulative
                                // factor with a short exponential average so noisy
                                // input cannot teleport the zoom level mid-gesture.
                                // The commit keeps visual == commit parity.
                                val rawFactor = clampGestureVisualZoom(
                                    zoomFactor, viewModel.uiState.value.viewport.magnification
                                )
                                gestureSmoothFactorState.value = if (gestureSmoothFactorState.value == 1f) {
                                    rawFactor
                                } else {
                                    gestureSmoothFactorState.value +
                                        (rawFactor - gestureSmoothFactorState.value) * 0.4f
                                }
                                gestureZoomState.value = gestureBaseScaleState.value * gestureSmoothFactorState.value
                            }

                            override fun onLongPress(position: Offset) {
                                fireLongPress(viewModel, context, position, canvasSizeState.value)
                            }

                            override fun onDoubleTap(position: Offset) {
                                attributionInteractionTickState.value++
                                // spec: smooth-zoom — A double tap steps one level at its own
                                // point. The step is committed at once (the viewport records
                                // the target level) and the display eases toward it around the
                                // tap point, through the same animation the zoom buttons and
                                // the scroll wheel use.
                                fireDoubleTap(
                                    viewModel, context, position, canvasSizeState.value
                                ) { anchor -> animateDiscreteZoom(anchor) }
                            }

                            override fun onRenderRequested() {
                                attributionInteractionTickState.value++
                                // Gesture end: commit the accumulated multi-touch
                                // changes to the viewport and render once with the
                                // final angle/mag/center (correct label direction).
                                val hasMultiTouchChanges = gestureRotationState.value != 0f ||
                                    gestureZoomState.value != 1f || gesturePanState.value != Offset.Zero
                                if (hasMultiTouchChanges) {
                                    val s = viewModel.uiState.value
                                    Log.d(TAG, "gesture end rot=" + gestureRotationState.value + " zoom=" + gestureZoomState.value +
                                        " pan=" + gesturePanState.value + " centroid=" + gestureCentroidState.value +
                                        " mag=" + s.viewport.magnification + " angle=" + s.viewport.angle)
                                    val newAngle = normalizeRadians(s.viewport.angle + gestureRotationState.value.toDouble())
                                    viewModel.updateAngle(newAngle)
                                    // continuous-pinch-zoom: commit the unrounded
                                    // fractional magnification — the visual preview
                                    // factor already equals the committed factor within
                                    // the headroom clamp, so no snap at gesture end.
                                    // Commit uses the detector's cumulative factor only —
                                    // the folded base scale is already committed in
                                    // viewport.magnification (visual continuity: display
                                    // = front buffer × gestureBaseScaleState.value × factor).
                                    val detectorFactor = gestureZoomState.value / gestureBaseScaleState.value
                                    val newMag = gestureEndMagnification(s.viewport.magnification, detectorFactor)
                                    val rotationChanged = gestureRotationState.value != 0f
                                    val zoomChanged = abs(newMag - s.viewport.magnification) > 1e-6
                                    if (rotationChanged || zoomChanged) {
                                            val dpi = context.resources.displayMetrics.densityDpi.toDouble()
                                            // Generalized focal-point commit (design D2, spec:
                                            // Rotation anchored at the finger midpoint): the
                                            // viewport center is adjusted so the geo point under
                                            // the finger midpoint stays under it after the
                                            // combined rotate+zoom. Reduces to zoomAtCursor for
                                            // a pure zoom (Δ=0); a pure rotation now also moves
                                            // the center.
                                            val (clat, clon) = ProjectionUtils.rotateZoomAtFocalPoint(
                                                gestureCentroidState.value.x.toDouble(), gestureCentroidState.value.y.toDouble(),
                                                s.viewport.magnification, newMag,
                                                gestureRotationState.value.toDouble(),
                                                canvasSizeState.value.width.toDouble(), canvasSizeState.value.height.toDouble(),
                                                s.viewport.centerLat, s.viewport.centerLon,
                                                s.viewport.angle, dpi
                                            )
                                            Log.d(TAG, "gesture commit angle=" + newAngle +
                                                " zoomFactor=" + detectorFactor +
                                                " mag=" + s.viewport.magnification + "->" + newMag +
                                                " focal=" + gestureCentroidState.value +
                                                " canvas=" + canvasSizeState.value.width + "x" + canvasSizeState.value.height +
                                                " -> " + clat + "," + clon)
                                            viewModel.updateCenter(clat, clon)
                                            if (zoomChanged) viewModel.updateMagnification(newMag)
                                    }
                                    // Full native render only when the angle or mag
                                    // changed (correct label direction); a pure pan
                                    // uses the fast tile path.
                                    // Fold the composed gesture scale into the
                                    // display layer: the screen keeps showing
                                    // frontBuffer × gestureZoomState.value (the exact gesture
                                    // preview) while the debounced render at the
                                    // fractional commit runs — the render-land handoff
                                    // resets the display scale when the buffer matches
                                    // (smooth-zoom D3/D4, no zoom-level snap between
                                    // gesture end and render completion).
                                    zoomAnchorState.value = gestureCentroidState.value
                                    zoomAnimScaleState.value = gestureZoomState.value
                                    val needsFullRender = gestureRotationState.value != 0f || gestureZoomState.value != 1f
                                    // Rotation display-layer hold (design D1): arm the
                                    // derived rotation gap (committed − front-buffer
                                    // angle) until the re-render at the committed angle
                                    // lands — the frame loop disarms it when the front
                                    // buffer matches (mirror of the zoomAnimScaleState.value
                                    // handoff).
                                    rotationHoldActiveState.value = gestureRotationState.value != 0f
                                    // Gesture-end midpoint: the hold and the
                                    // render-land crossfade pivot around this
                                    // point (the commit's focal point, D2).
                                    crossfadePivotState.value = gestureCentroidState.value
                                    gestureRotationState.value = 0f
                                    gestureZoomState.value = 1f
                                    gestureBaseScaleState.value = 1f
                                    gestureSmoothFactorState.value = 1f
                                    gesturePanState.value = Offset.Zero
                                    gestureCentroidState.value = Offset.Zero
                                    gestureActiveState.value = false
                                    viewModel.renderMap(forceFullRender = needsFullRender)
                                }
                                if (panGestureActiveState.value) {
                                    // Single-finger pan end (spec: map-pan-zoom —
                                    // Touch-based pan): commit the displayed center
                                    // and persist it. A pan that stayed inside the
                                    // overrun margin needs NO render — the frame in
                                    // hand keeps serving the shifted window; only a
                                    // saturated (or missing) frame is re-centered.
                                    panGestureActiveState.value = false
                                    val window = panWindowOffset()
                                    commitPanCenter()
                                    viewModel.saveViewport()
                                    if (PAN_DIAGNOSTICS_ENABLED) {
                                        // One line per gesture: whether the window was
                                        // served, the applied offset and the
                                        // magnification — the two stuck states of a
                                        // dead pan (renderer dropped the request /
                                        // display applied a zero offset while the
                                        // displayed center moved) are distinguishable
                                        // from logcat alone, with no coordinates and no
                                        // per-event output (spec: map-pan-zoom —
                                        // diagnosable without per-event output).
                                        val state = window?.let {
                                            if (it.clamped) "clamped" else "served"
                                        } ?: "unavailable"
                                        Log.d(
                                            TAG,
                                            "pan end: window=" + state +
                                                " offsetPx=" + panOffsetXState.value + "," + panOffsetYState.value +
                                                " mag=" + viewModel.uiState.value.viewport.magnification
                                        )
                                    }
                                    if (window == null || window.clamped) {
                                        lastPanRenderRequestMsState.value = System.currentTimeMillis()
                                        viewModel.renderMap()
                                    }
                                }
                            }
                        }
                    )
                    // Scroll-wheel zoom (emulator/testing)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: return@awaitEachGesture
                            if (change.type == PointerType.Mouse &&
                                event.type == PointerEventType.Scroll) {
                                val scrollDelta = change.scrollDelta
                                val deltaY = scrollDelta.y
                                if (deltaY != 0f) {
                                    attributionInteractionTickState.value++
                                    val s = viewModel.uiState.value
                                    val mag = s.viewport.magnification
                                    val dir = if (deltaY < 0) 1 else -1
                                    val newMag = (mag + dir).coerceIn(
                                        MapCanvasViewModel.MIN_MAG, MapCanvasViewModel.MAX_MAG
                                    )
                                    if (newMag != mag) {
                                        viewModel.disengageFollowMode()
                                        val dpi = context.resources.displayMetrics.densityDpi.toDouble()
                                        val (clat, clon) = ProjectionUtils.zoomAtCursor(
                                            change.position.x.toDouble(), change.position.y.toDouble(),
                                            mag, newMag,
                                            size.width.toDouble(), size.height.toDouble(),
                                            s.viewport.centerLat, s.viewport.centerLon, dpi
                                        )
                                        viewModel.updateCenter(clat, clon)
                                        viewModel.updateMagnification(newMag)
                                        // smooth-zoom: animate the discrete zoom
                                        // anchored at the cursor position.
                                        animateDiscreteZoom(change.position)
                                    }
                                }
                            }
                        }
                    }
                    .onKeyEvent { event ->
                        dispatchMapCanvasKey(
                            event = event,
                            onOpenSearch = { viewModel.openSearch() },
                            onZoomIn = {
                                attributionInteractionTickState.value++
                                viewModel.disengageFollowMode()
                                viewModel.zoomIn()
                                viewModel.renderMap()
                                animateDiscreteZoomToCenter()
                            },
                            onZoomOut = {
                                attributionInteractionTickState.value++
                                viewModel.disengageFollowMode()
                                viewModel.zoomOut()
                                viewModel.renderMap()
                                animateDiscreteZoomToCenter()
                            }
                        )
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                drawRect(color = surfaceColor)

                // Display offset of the frame currently on screen: the follow-mode
                // prediction drift in follow mode, the pan overrun window otherwise
                // (spec: canvas-overrun — Overrun window shift for pan). Exactly one
                // of the two is non-zero: a pan disengages follow mode, and the
                // follow display loop resets its offsets whenever follow is off.
                //
                // A route-planning session that holds the camera (spec:
                // `route-planning-session` — Session holds the camera while active) draws
                // with **no** offset: the session's fits (route overview, analysed segment)
                // place the content in the free area by the viewport alone, so a follow
                // drift offset of up to the overrun margin (240 px here) would shift the
                // drawn frame and the overlays away from what the fit computed — the
                // analysed leg looked centred in the model and sat off-centre/clipped at
                // the bottom or a side on the device (owner finding, 2026-10-03).
                val sessionOwnsViewport = viewModel.sessionHoldsCamera()
                val displayOffsetX = followDisplayOffset(
                    followActiveState.value, sessionOwnsViewport, followOffsetXState.value, panOffsetXState.value
                )
                val displayOffsetY = followDisplayOffset(
                    followActiveState.value, sessionOwnsViewport, followOffsetYState.value, panOffsetYState.value
                )

                state.renderedBitmap?.let { bitmap ->
                    // Overrun frame: drawn at natural size, positioned by the
                    // display offset within the overrun margin.
                    drawFrontFrame(
                        bitmap, canvasWidth.toFloat(), canvasHeight.toFloat(),
                        displayOffsetX, displayOffsetY,
                        zoomAnimScaleState.value, zoomAnchorState.value, 1f
                    )
                }

                // smooth-zoom render-completion crossfade (zoom-transition-
                // scaling delta): the scaled old frame fades out over the
                // swapped-in rendered frame — no single-frame content jump.
                crossfadeBitmapState.value?.let { old ->
                    if (crossfadeAlphaState.value > 0f) {
                        drawFrontFrame(
                            old, canvasWidth.toFloat(), canvasHeight.toFloat(),
                            displayOffsetX, displayOffsetY,
                            crossfadeScaleState.value, crossfadeAnchorState.value, crossfadeAlphaState.value,
                            crossfadeAngleState.value, crossfadePivotState.value
                        )
                    }
                }
            }

            // Analysed route step (spec: route-analysis): the polyline range the
            // selected step owns, drawn on a layer ABOVE the rendered frame and
            // BELOW the markers/pins, so the route's own paint stays visible around
            // the highlight and no pin is hidden. Same viewport source as the
            // markers (the displayed bitmap), and the same pan/zoom display
            // transform, so the highlight rides the map content it belongs to.
            if (state.renderedBitmap != null) {
                RouteSegmentHighlightOverlay(
                    polylineLats = routeState.routeEntry?.latitudes,
                    polylineLons = routeState.routeEntry?.longitudes,
                    segment = routeState.analysedSegment,
                    viewport = state.renderViewport,
                    dpi = context.resources.displayMetrics.densityDpi.toDouble(),
                    dark = state.isDarkPresentation,
                    modifier = Modifier.graphicsLayer {
                        val shift = markerDisplayShiftPx(followActiveState.value, panOffsetXState.value, panOffsetYState.value)
                        translationX = shift.first
                        translationY = shift.second
                    },
                    zoomScale = zoomAnimScaleState.value,
                    zoomAnchor = zoomAnchorState.value
                )
            }

            // GPS marker is rendered as a Compose overlay on top of the rendered map,
            // never baked into cached tiles or reusable buffers (spec: gps-location-marker).
            // Projection uses the front-buffer viewport so the marker stays anchored
            // to the bitmap actually on screen. No bitmap yet → no marker: there is no
            // displayed frame to project against.
            if (state.renderedBitmap != null) {
                // In follow mode the marker rides the displayed (eased predicted)
                // position so it glides with the blitted map; the viewport is
                // centered on the same position so the marker lands on the road.
                val markerLat = if (followActiveState.value) followDisplayLatState.value else state.gpsMarkerLat
                val markerLon = if (followActiveState.value) followDisplayLonState.value else state.gpsMarkerLon
                // The marker projects against the displayed bitmap's viewport. In
                // follow mode that viewport is the anchor center of the DISPLAYED
                // (eased predicted) position — the frame itself is anchor-centered
                // on its own position and then blitted by the drift (spec:
                // smooth-follow — Anchor-centered follow framing), so this is the one
                // projection that lands the marker on the map content it rides at the
                // anchor instead of ahead of it by the blit offset (spec:
                // gps-location-marker — Marker projects against displayed bitmap
                // viewport).
                val markerViewport = if (followActiveState.value) {
                    val shown = state.renderViewport
                    // Anchor fraction = the value the ViewModel resolved against
                    // the VISIBLE map area (spec: smooth-follow — visible-area
                    // scenarios); a second derivation here would break the
                    // marker/content alignment on surfaces with overlays.
                    val (aLat, aLon) = anchorCenter(
                        markerLat, markerLon,
                        state.resolvedAnchor.fx, state.resolvedAnchor.fy,
                        shown?.mag ?: 0.0, canvasSizeState.value.width, canvasSizeState.value.height,
                        context.resources.displayMetrics.densityDpi.toDouble(),
                        shown?.angle ?: 0.0
                    )
                    MapRenderer.RenderViewport(
                        aLat, aLon,
                        shown?.mag ?: 0.0, shown?.angle ?: 0.0
                    )
                } else {
                    state.renderViewport
                }
                LocationMarkerOverlay(
                    lat = markerLat,
                    lon = markerLon,
                    bearing = state.gpsMarkerBearing,
                    accuracy = state.gpsMarkerAccuracy,
                    viewport = markerViewport,
                    dpi = context.resources.displayMetrics.densityDpi.toDouble(),
                    modifier = Modifier.graphicsLayer {
                        // The map content is drawn shifted by the pan display
                        // offset, so the overlays must be shifted by the same
                        // value — the marker can never drift off the content it
                        // rides (spec: gps-location-marker — Marker projects
                        // against displayed bitmap viewport). In follow mode the
                        // anchor-center projection already carries the drift, so
                        // translating there would apply it twice.
                        val shift = markerDisplayShiftPx(followActiveState.value, panOffsetXState.value, panOffsetYState.value)
                        translationX = shift.first
                        translationY = shift.second
                    },
                    zoomScale = zoomAnimScaleState.value,
                    zoomAnchor = zoomAnchorState.value,
                    dark = state.isDarkPresentation
                )
            }
        }
    }
}
