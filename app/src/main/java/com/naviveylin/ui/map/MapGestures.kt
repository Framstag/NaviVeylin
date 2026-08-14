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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

private const val TAG = "MapGestures"

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

    /** Request a re-render (gesture ended or viewport changed). */
    fun onRenderRequested()
}

/**
 * Attaches the map canvas gesture handler: single-finger pan, two-finger
 * pan/rotate/zoom, and long press. A single pointerInput block handles all
 * gestures to avoid conflicts between competing detectors.
 *
 * The handler only reports raw gesture deltas; projection math (converting
 * deltas to geo changes) is the caller's responsibility via [MapGestureCallbacks].
 */
fun Modifier.mapGestureHandler(callbacks: MapGestureCallbacks): Modifier = composed {
    val currentCallbacks by rememberUpdatedState(callbacks)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downPos = down.position
            var dragActive = false
            var lastPointer = downPos
            val pointerId = down.id
            // Finger distance when the second finger went down — the reference
            // for the continuous zoom factor reported during the gesture.
            var gestureStartDist = -1f

            // Phase 1: Wait for up, drag, or 500ms timeout
            val upOrNull = withTimeoutOrNull(500L) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: event.changes.firstOrNull() ?: break

                    if (!change.pressed) {
                        return@withTimeoutOrNull change
                    }

                    val dist = (change.position - downPos).getDistance()
                    if (dist > 12f) {
                        // Drag started
                        dragActive = true
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
                // Timeout expired without drag — long press
                currentCallbacks.onLongPress(downPos)
            }

            // Phase 2: If drag started, continue tracking for pan/zoom/rotate
            if (dragActive) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: event.changes.firstOrNull() ?: break

                    if (!change.pressed) {
                        currentCallbacks.onRenderRequested()
                        break
                    }

                    val pressedChanges = event.changes.filter { it.pressed }
                    if (pressedChanges.size >= 2) {
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

                        if (abs(angleDelta) > 0.05f) {
                            currentCallbacks.onRotate(angleDelta.toDouble())
                        }

                        // Zoom: continuous distance ratio vs the gesture start.
                        // The caller applies it as a visual transform and commits
                        // the magnification change on gesture end. Ignored when the
                        // fingers started too close together (unreliable ratio).
                        val currDist = (c1.position - c2.position).getDistance()
                        if (gestureStartDist > 20f) {
                            val zoomFactor = currDist / gestureStartDist
                            if (abs(zoomFactor - 1f) > 0.01f) {
                                currentCallbacks.onZoom(centroid, zoomFactor)
                            }
                        }
                    } else {
                        // Single-finger pan: compute delta and update center
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
