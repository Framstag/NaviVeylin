package com.naviveylin.ui.map

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

private const val TAG = "MapGestures"

/**
 * Minimum accumulated two-finger rotation (radians) before [MapGestureCallbacks.onRotate]
 * fires. Per-event angle deltas on high-refresh-rate devices are far smaller than this
 * (a 90° rotation over 1 s at 120 Hz is ~0.013 rad/event), so a fixed per-event threshold
 * would swallow the whole gesture. Accumulating the raw deltas filters finger jitter —
 * which oscillates around zero and rarely sums past the threshold — while still reporting
 * slow, deliberate rotations.
 */
private const val ROTATION_REPORT_THRESHOLD_RAD = 0.01f

/**
 * Travel (px) a finger must cover before the gesture counts as a drag rather than
 * a tap/long-press. The travel covered up to this threshold is reported with the
 * first pan delta, so the map content below the finger and the finger stay
 * together (spec map-pan-zoom — Touch-based pan).
 */
private const val DRAG_START_THRESHOLD_PX = 12f

/**
 * Callbacks for map canvas touch gestures. All positions are in screen pixels.
 */
interface MapGestureCallbacks {
    /** Single-finger pan delta in screen pixels. */
    fun onPan(dx: Float, dy: Float)

    /** Two-finger centroid pan delta in screen pixels. */
    fun onCentroidPan(dx: Float, dy: Float)

    /** Centroid of the two fingers on every multi-touch event (visual transform pivot). */
    fun onGestureCentroid(centroid: Offset) {}

    /** Two-finger rotation delta in radians (positive = clockwise on screen). */
    fun onRotate(angleDeltaRadians: Double)

    /** Two-finger zoom: centroid position and distance ratio (>1 = spread apart). */
    fun onZoom(centroid: Offset, zoomFactor: Float)

    /** Long press at the given position. */
    fun onLongPress(position: Offset)

    /** Two taps inside the platform's double-tap window and slop, at the given position. */
    fun onDoubleTap(position: Offset) {}

    /** Request a re-render (gesture ended or viewport changed). */
    fun onRenderRequested()
}

/**
 * Attaches the map canvas gesture handler: single-finger pan, two-finger
 * pan/rotate/zoom, long press, and double tap. A single pointerInput block handles all
 * gestures to avoid conflicts between competing detectors.
 *
 * The handler only reports raw gesture deltas; projection math (converting
 * deltas to geo changes) is the caller's responsibility via [MapGestureCallbacks].
 *
 * The long-press duration is configuration, never a literal: [longPressTimeoutMs] when the
 * caller configures it, the platform's configured long-press timeout otherwise, and the
 * gesture block is keyed on the resolved value so a change applies to the next gesture
 * (spec: map-long-press-timeout — The long-press timeout is a configuration, not a literal).
 */
fun Modifier.mapGestureHandler(
    callbacks: MapGestureCallbacks,
    longPressTimeoutMs: Long? = null,
): Modifier = composed {
    val currentCallbacks by rememberUpdatedState(callbacks)
    val resolvedLongPressTimeoutMs = longPressTimeoutMs
        ?: LocalViewConfiguration.current.longPressTimeoutMillis
    pointerInput(resolvedLongPressTimeoutMs) {
        // The double-tap pair (spec: smooth-zoom — Double tap zooms one level at the tap
        // point): the up event time of the last completed single tap, and the down point it
        // started at — the two references the platform's own window and slop are measured
        // from. It lives in the pointerInput scope, not in the gesture, because the second tap
        // is the next gesture this block sees. 0 = no tap waiting for a partner.
        var pendingTapUpMs = 0L
        var pendingTapPos = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downPos = down.position
            var dragActive = false
            var lastPointer = downPos
            val pointerId = down.id
            // Finger distance when the second finger went down — the reference
            // for the continuous zoom factor reported during the gesture.
            var gestureStartDist = -1f
            // Accumulated rotation since the last onRotate report (see
            // ROTATION_REPORT_THRESHOLD_RAD). Per-gesture state.
            var rotationAccumulator = 0f

            // Phase 1: Wait for up, drag, or the configured long-press timeout
            val upOrNull = withTimeoutOrNull(resolvedLongPressTimeoutMs) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: event.changes.firstOrNull() ?: break

                    if (!change.pressed) {
                        return@withTimeoutOrNull change
                    }

                    val dist = (change.position - downPos).getDistance()
                    if (dist > DRAG_START_THRESHOLD_PX) {
                        // Drag started: report the travel covered so far (down → this
                        // event) right away and continue from here. A drag delivered as
                        // a single move event would otherwise be swallowed until the
                        // next event (a pan that never reaches the map), and holding
                        // it back would make the content lag the finger by the
                        // threshold (spec map-pan-zoom — Touch-based pan: the reported
                        // deltas sum to the finger travel).
                        dragActive = true
                        currentCallbacks.onPan(
                            change.position.x - downPos.x,
                            change.position.y - downPos.y
                        )
                        lastPointer = change.position
                        return@withTimeoutOrNull null
                    }

                    // Check for pinch zoom during initial wait
                    if (event.changes.size >= 2) {
                        dragActive = true
                        // Capture the finger distance at the moment the second
                        // finger goes down — the reference for the zoom factor.
                        val c1 = event.changes[0]
                        val c2 = event.changes[1]
                        gestureStartDist = (c1.position - c2.position).getDistance()
                        return@withTimeoutOrNull null
                    }

                    change.consume()
                }
                null
            }

            if (upOrNull == null && !dragActive) {
                // Timeout expired without drag — long press. A long press consumes the
                // tap it started on, so it never pairs with an earlier tap.
                pendingTapUpMs = 0L
                currentCallbacks.onLongPress(downPos)
            } else if (dragActive) {
                // A drag or a pinch is not a tap: it cannot complete a pending pair.
                pendingTapUpMs = 0L
            } else if (upOrNull != null) {
                // A completed single tap. Two taps inside the platform's double-tap window
                // and touch slop are one double tap, reported at the point the second one
                // lifted (spec: smooth-zoom — Double tap is the map's second discrete zoom-in input).
                // Both numbers are the platform's, never literals (same rule as the long-press
                // timeout above), and both intervals are the platform's own: the timeout is
                // measured from the first tap's **up** to the second tap's **down**
                // (`ViewConfiguration.getDoubleTapTimeout`), so the second press's own duration
                // cannot eat the window; the slop is measured between the two taps' **down**
                // points, because the first tap may drift up to the slop and still be a tap
                // (review round 1, finding 2).
                val withinWindow = pendingTapUpMs != 0L &&
                    down.uptimeMillis - pendingTapUpMs <= viewConfiguration.doubleTapTimeoutMillis
                val withinSlop = (downPos - pendingTapPos).getDistance() <= viewConfiguration.touchSlop
                if (withinWindow && withinSlop) {
                    pendingTapUpMs = 0L
                    currentCallbacks.onDoubleTap(upOrNull.position)
                } else {
                    pendingTapUpMs = upOrNull.uptimeMillis
                    pendingTapPos = downPos
                }
            }

            // Phase 2: If drag started, continue tracking for pan/zoom/rotate
            if (dragActive) {
                // A multi-touch phase re-bases the single-finger reference (see the
                // single-finger branch): the tracked pointer's delta must be measured
                // from the event the multi-touch phase ended on, never from the down
                // position — otherwise lifting one finger of a pinch teleports the map.
                var multiTouchPhase = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: event.changes.firstOrNull() ?: break

                    if (!change.pressed) {
                        // Flush any residual rotation below the report threshold so
                        // the committed angle matches the fingers' total rotation.
                        if (rotationAccumulator != 0f) {
                            currentCallbacks.onRotate(rotationAccumulator.toDouble())
                        }
                        currentCallbacks.onRenderRequested()
                        break
                    }

                    val pressedChanges = event.changes.filter { it.pressed }
                    if (pressedChanges.size >= 2) {
                        multiTouchPhase = true
                        // Multi-touch: pan via centroid, rotate via finger angle,
                        // zoom via finger distance. Compose sorts changes by
                        // pointer id, so c1/c2 are stable across events.
                        val c1 = pressedChanges[0]
                        val c2 = pressedChanges[1]
                        val centroid = Offset(
                            (c1.position.x + c2.position.x) / 2f,
                            (c1.position.y + c2.position.y) / 2f
                        )
                        val prevCentroid = Offset(
                            (c1.previousPosition.x + c2.previousPosition.x) / 2f,
                            (c1.previousPosition.y + c2.previousPosition.y) / 2f
                        )
                        currentCallbacks.onGestureCentroid(centroid)

                        // Pan via centroid movement (rotation-aware). A pure
                        // rotation around the midpoint keeps the centroid fixed.
                        val cdx = centroid.x - prevCentroid.x
                        val cdy = centroid.y - prevCentroid.y
                        if (abs(cdx) > 0.5f || abs(cdy) > 0.5f) {
                            Log.d(TAG, "gesture centroidPan c1=" + c1.position + " c2=" + c2.position +
                                " prev=" + prevCentroid + " delta=" + cdx + "," + cdy)
                            // Reject absurd per-event deltas (a corrupted pointer
                            // position would otherwise explode the map center).
                            val maxPan = maxOf(size.width, size.height).toFloat()
                            val clampedDx = cdx.coerceIn(-maxPan, maxPan)
                            val clampedDy = cdy.coerceIn(-maxPan, maxPan)
                            if (clampedDx != cdx || clampedDy != cdy) {
                                Log.w(TAG, "gesture centroidPan delta clamped " + cdx + "," + cdy +
                                    " -> " + clampedDx + "," + clampedDy)
                            }
                            currentCallbacks.onCentroidPan(clampedDx, clampedDy)
                        }

                        // Rotation: angle delta between fingers
                        val prevAngle = atan2(
                            c2.previousPosition.y - c1.previousPosition.y,
                            c2.previousPosition.x - c1.previousPosition.x
                        )
                        val currAngle = atan2(
                            c2.position.y - c1.position.y,
                            c2.position.x - c1.position.x
                        )
                        var angleDelta = currAngle - prevAngle
                        if (angleDelta > PI.toFloat()) angleDelta -= 2f * PI.toFloat()
                        if (angleDelta < -PI.toFloat()) angleDelta += 2f * PI.toFloat()

                        // Accumulate raw deltas and report only when the accumulated
                        // rotation exceeds a small threshold. A per-event threshold
                        // would swallow slow rotations on high-refresh-rate devices.
                        rotationAccumulator += angleDelta
                        if (abs(rotationAccumulator) > ROTATION_REPORT_THRESHOLD_RAD) {
                            currentCallbacks.onRotate(rotationAccumulator.toDouble())
                            rotationAccumulator = 0f
                        }

                        // Zoom: continuous distance ratio vs the gesture start.
                        // The caller applies it as a visual transform and commits
                        // the magnification change on gesture end. Ignored when the
                        // fingers started too close together (unreliable ratio — e.g. emulated
                        // pinch pointers appearing at nearly the same point make the ratio
                        // explode and the map flicker between zoom levels).
                        val currDist = (c1.position - c2.position).getDistance()
                        if (gestureStartDist > 60f) {
                            val zoomFactor = currDist / gestureStartDist
                            if (abs(zoomFactor - 1f) > 0.01f) {
                                currentCallbacks.onZoom(centroid, zoomFactor)
                            }
                        }
                    } else {
                        // Single-finger pan: compute delta and update center
                        if (multiTouchPhase) {
                            // Coming back from a two-finger phase (one finger lifted):
                            // re-base on this event so the first single-finger delta is
                            // a real finger move, not the whole gesture travel.
                            multiTouchPhase = false
                            lastPointer = change.position
                        }
                        val dx = change.position.x - lastPointer.x
                        val dy = change.position.y - lastPointer.y
                        lastPointer = change.position

                        if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                            currentCallbacks.onPan(dx, dy)
                        }
                    }
                }
            }
        }
    }
}
