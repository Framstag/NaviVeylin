package com.naviveylin.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Unit tests for the live multi-touch visual transform translation
 * ([gestureTransformTranslation]): the map rotates around the screen center
 * (so it always covers the canvas — the rendered bitmap exactly fills it, so
 * an off-center pivot would swing the map off-screen at large angles) while
 * the zoom keeps its pivot at the gesture centroid (matching the committed
 * zoomAtCursor).
 */
class MapCanvasGestureTransformTest {

    private val canvas = Size(1080f, 2400f)
    private val center = Offset(540f, 1200f)

    @Test
    fun purePinchKeepsCentroidFixed() {
        val centroid = Offset(200f, 400f)
        val t = gestureTransformTranslation(0f, 2f, centroid, canvas, Offset.Zero)
        // x' = T + O + s·(x - O); at x = centroid, x' must equal centroid.
        val xp = t.x + center.x + 2f * (centroid.x - center.x)
        val yp = t.y + center.y + 2f * (centroid.y - center.y)
        assertEquals(centroid.x, xp, 0.01f)
        assertEquals(centroid.y, yp, 0.01f)
    }

    @Test
    fun pureRotationHasNoTranslation() {
        // Zoom 1, no pan: the map rotates around the screen center and stays
        // covering the canvas at any angle (no off-center pivot swing).
        val t = gestureTransformTranslation(PI.toFloat(), 1f, Offset(200f, 400f), canvas, Offset.Zero)
        assertEquals(0f, t.x, 0.01f)
        assertEquals(0f, t.y, 0.01f)
    }

    @Test
    fun panIsPreserved() {
        val pan = Offset(30f, -20f)
        val t = gestureTransformTranslation(0f, 1f, Offset(200f, 400f), canvas, pan)
        assertEquals(pan.x, t.x, 0.01f)
        assertEquals(pan.y, t.y, 0.01f)
    }

    @Test
    fun rotationAroundCenterKeepsMapCoveringCanvas() {
        // At 180° the map must still cover the canvas: the transform is a pure
        // rotation around the center (no translation), so every canvas point
        // maps to a point within the canvas-sized bitmap.
        val t = gestureTransformTranslation(PI.toFloat(), 1f, Offset(200f, 400f), canvas, Offset.Zero)
        assertEquals(0f, t.x, 0.01f)
        assertEquals(0f, t.y, 0.01f)
        // A canvas corner maps to the opposite corner — still inside the bitmap.
        val corner = Offset(0f, 0f)
        val mapped = Offset(
            t.x + center.x + (corner.x - center.x) * cos(PI.toFloat()) - (corner.y - center.y) * sin(PI.toFloat()),
            t.y + center.y + (corner.x - center.x) * sin(PI.toFloat()) + (corner.y - center.y) * cos(PI.toFloat())
        )
        assertEquals(center.x * 2f, mapped.x, 0.01f)
        assertEquals(center.y * 2f, mapped.y, 0.01f)
    }

    @Test
    fun visualZoomClampsToHeadroomAtMaxMagnification() {
        // At the max mag the commit cannot zoom in, so the preview must not
        // show a zoom that would snap back on gesture end.
        assertEquals(1f, clampGestureVisualZoom(4f, 20), 0.001f)
        assertEquals(1f, clampGestureVisualZoom(1.3f, 20), 0.001f)
        // One level of headroom: preview clamps to 2× (the committed zoom).
        assertEquals(2f, clampGestureVisualZoom(4f, 19), 0.001f)
    }

    @Test
    fun visualZoomClampsToHeadroomAtMinMagnification() {
        // At the min mag the commit cannot zoom out, so the preview must not
        // show a zoom-out that would snap back on gesture end.
        assertEquals(1f, clampGestureVisualZoom(0.25f, 4), 0.001f)
        // One level of headroom: preview clamps to 0.5× (the committed zoom).
        assertEquals(0.5f, clampGestureVisualZoom(0.25f, 5), 0.001f)
    }

    @Test
    fun visualZoomKeepsFullRangeAtMidMagnification() {
        // Mid-range mags have enough headroom for the full ±2-level preview.
        assertEquals(4f, clampGestureVisualZoom(4f, 10), 0.001f)
        assertEquals(0.25f, clampGestureVisualZoom(0.25f, 10), 0.001f)
        assertEquals(1.5f, clampGestureVisualZoom(1.5f, 10), 0.001f)
    }
}
