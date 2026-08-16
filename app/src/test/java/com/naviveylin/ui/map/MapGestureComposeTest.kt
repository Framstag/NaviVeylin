package com.naviveylin.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compose UI tests for the map canvas gesture handler ([mapGestureHandler]):
 * two-finger rotation reports clockwise/counter-clockwise angle deltas without
 * spurious zoom or pan, pinch reports zoom, and single-finger drag reports pan.
 */
@RunWith(RobolectricTestRunner::class)
class MapGestureComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val panDeltas = mutableListOf<Pair<Float, Float>>()
    private val centroidPans = mutableListOf<Pair<Float, Float>>()
    private val rotations = mutableListOf<Double>()
    private val zooms = mutableListOf<Pair<Offset, Float>>()
    private val centroids = mutableListOf<Offset>()
    private var longPresses = 0
    private var renderRequests = 0

    private fun launchMap() {
        composeRule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .mapGestureHandler(
                        object : MapGestureCallbacks {
                            override fun onPan(dx: Float, dy: Float) { panDeltas.add(dx to dy) }
                            override fun onCentroidPan(dx: Float, dy: Float) { centroidPans.add(dx to dy) }
                            override fun onGestureCentroid(centroid: Offset) { centroids.add(centroid) }
                            override fun onRotate(angleDeltaRadians: Double) { rotations.add(angleDeltaRadians) }
                            override fun onZoom(centroid: Offset, zoomFactor: Float) { zooms.add(centroid to zoomFactor) }
                            override fun onLongPress(position: Offset) { longPresses++ }
                            override fun onRenderRequested() { renderRequests++ }
                        }
                    )
            )
        }
        composeRule.waitForIdle()
    }

    /**
     * Rotate two fingers around [center] by [degrees] (positive = clockwise on
     * screen) in [steps] equal-angle steps. Both fingers move in the same event
     * (via updatePointerTo + move) so the centroid stays fixed and the finger
     * distance stays constant — a pure rotation.
     */
    private fun TouchInjectionScope.rotateFingers(
        center: Offset,
        degrees: Double,
        steps: Int = 10,
        radius: Float = 50f
    ) {
        down(0, center + Offset(0f, -radius))
        down(1, center + Offset(0f, radius))
        for (i in 1..steps) {
            val a = Math.toRadians(degrees * i / steps)
            updatePointerTo(0, center + Offset((radius * sin(a)).toFloat(), (-radius * cos(a)).toFloat()))
            updatePointerTo(1, center + Offset((-radius * sin(a)).toFloat(), (radius * cos(a)).toFloat()))
            move()
        }
        up(0)
        up(1)
    }

    @Test
    fun twoFingerClockwiseRotationReportsPositiveAngleDelta() {
        launchMap()
        composeRule.onRoot().performTouchInput {
            rotateFingers(center, 90.0)
        }
        composeRule.waitForIdle()

        assertTrue("rotation deltas must be reported", rotations.isNotEmpty())
        val total = rotations.sum()
        assertTrue("clockwise rotation must be positive, got $total", total > 0.5)
        assertEquals("total rotation ≈ 90°", PI / 2, total, 0.3)
    }

    @Test
    fun slowRotationWithSmallPerEventDeltasIsReported() {
        launchMap()
        composeRule.onRoot().performTouchInput {
            // Real-device scenario: high touch-sampling rates deliver many small
            // per-event deltas (0.9° here — below the old 0.05 rad per-event
            // threshold). The handler must accumulate and report them.
            rotateFingers(center, 90.0, steps = 100)
        }
        composeRule.waitForIdle()

        assertTrue("slow rotation must be reported", rotations.isNotEmpty())
        val total = rotations.sum()
        assertEquals("total rotation ≈ 90°", PI / 2, total, 0.3)
    }

    @Test
    fun twoFingerCounterClockwiseRotationReportsNegativeAngleDelta() {
        launchMap()
        composeRule.onRoot().performTouchInput {
            rotateFingers(center, -90.0)
        }
        composeRule.waitForIdle()

        assertTrue("rotation deltas must be reported", rotations.isNotEmpty())
        val total = rotations.sum()
        assertTrue("counter-clockwise rotation must be negative, got $total", total < -0.5)
        assertEquals("total rotation ≈ -90°", -PI / 2, total, 0.3)
    }

    @Test
    fun rotationWithConstantDistanceDoesNotZoomOrPan() {
        launchMap()
        composeRule.onRoot().performTouchInput {
            rotateFingers(center, 90.0)
        }
        composeRule.waitForIdle()

        assertTrue("constant-distance rotation must not zoom", zooms.isEmpty())
        assertTrue("rotation around midpoint must not pan", centroidPans.isEmpty())
    }

    @Test
    fun rotationWithSmallDistanceJitterDoesNotZoom() {
        launchMap()
        composeRule.onRoot().performTouchInput {
            val c = center
            val r = 50f
            down(0, c + Offset(0f, -r))
            down(1, c + Offset(0f, r))
            // Rotate 90° while the finger distance jitters ±5% — the reported
            // zoom factors must stay near 1.0 (no net zoom).
            for (i in 1..10) {
                val a = Math.toRadians(i * 9.0)
                val jitter = 1f + 0.05f * (i % 2)
                updatePointerTo(0, c + Offset((r * jitter * sin(a)).toFloat(), (-r * jitter * cos(a)).toFloat()))
                updatePointerTo(1, c + Offset((-r * jitter * sin(a)).toFloat(), (r * jitter * cos(a)).toFloat()))
                move()
            }
            up(0)
            up(1)
        }
        composeRule.waitForIdle()

        assertTrue(
            "rotation jitter zoom factors must stay near 1.0, got $zooms",
            zooms.all { abs(it.second - 1f) < 0.1f }
        )
        assertTrue("rotation must still be reported", rotations.isNotEmpty())
    }

    @Test
    fun sustainedPinchReportsGrowingZoom() {
        launchMap()
        composeRule.onRoot().performTouchInput {
            val c = center
            down(0, c + Offset(-50f, 0f))
            down(1, c + Offset(50f, 0f))
            // Small steps: the continuous zoom factor grows with the distance.
            for (i in 1..4) {
                updatePointerTo(0, c + Offset(-50f - 10f * i, 0f))
                updatePointerTo(1, c + Offset(50f + 10f * i, 0f))
                move()
            }
            up(0)
            up(1)
        }
        composeRule.waitForIdle()

        assertTrue("sustained pinch must report zoom", zooms.isNotEmpty())
        assertTrue("spread must have zoomFactor > 1", zooms.all { it.second > 1f })
        assertTrue("zoom factor must grow with the pinch", zooms.last().second > zooms.first().second)
    }

    @Test
    fun multiTouchReportsGestureCentroid() {
        launchMap()
        var expected: Offset? = null
        composeRule.onRoot().performTouchInput {
            val c = center
            expected = c
            down(0, c + Offset(-50f, 0f))
            down(1, c + Offset(50f, 0f))
            updatePointerTo(0, c + Offset(-60f, 0f))
            updatePointerTo(1, c + Offset(60f, 0f))
            move()
            up(0)
            up(1)
        }
        composeRule.waitForIdle()

        assertTrue("multi-touch must report the finger centroid", centroids.isNotEmpty())
        // Centroid is the midpoint of the two fingers (symmetric around the box center).
        val last = centroids.last()
        val c = expected ?: return
        assertTrue("centroid x must be near the box center", abs(last.x - c.x) < 10f)
        assertTrue("centroid y must be near the box center", abs(last.y - c.y) < 10f)
    }

    @Test
    fun rotationRequestsRenderOnlyOnGestureEnd() {
        launchMap()
        composeRule.onRoot().performTouchInput {
            rotateFingers(center, 90.0)
        }
        composeRule.waitForIdle()

        assertTrue("rotation deltas must be reported", rotations.isNotEmpty())
        // The handler must not request renders during the rotation; the single
        // render request fires when the fingers lift, so the map is redrawn
        // once with the final angle (correct label direction).
        assertEquals("render must be requested exactly once, on gesture end", 1, renderRequests)
    }

    @Test
    fun pinchSpreadReportsZoom() {
        launchMap()
        composeRule.onRoot().performTouchInput {
            val c = center
            down(0, c + Offset(-50f, 0f))
            down(1, c + Offset(50f, 0f))
            updatePointerTo(0, c + Offset(-100f, 0f))
            updatePointerTo(1, c + Offset(100f, 0f))
            move()
            up(0)
            up(1)
        }
        composeRule.waitForIdle()

        assertTrue("pinch must report zoom", zooms.isNotEmpty())
        assertTrue("spread must have zoomFactor > 1", zooms.all { it.second > 1f })
        assertTrue("pure pinch must not rotate", rotations.isEmpty())
    }

    @Test
    fun singleFingerDragReportsPanDeltas() {
        launchMap()
        composeRule.onRoot().performTouchInput {
            down(0, center)
            // First move crosses the 12px drag threshold (Phase 1), the second
            // is tracked as a pan delta (Phase 2).
            moveTo(0, center + Offset(20f, 0f))
            moveTo(0, center + Offset(100f, 0f))
            up(0)
        }
        composeRule.waitForIdle()

        assertTrue("drag must report pan deltas", panDeltas.isNotEmpty())
        val totalDx = panDeltas.sumOf { it.first.toDouble() }
        assertTrue("pan right must be positive, got $totalDx", totalDx > 50.0)
        assertTrue("single-finger drag must not rotate", rotations.isEmpty())
    }
}
