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
 * ([gestureTransformTranslation]): the map rotates AND zooms around the finger
 * midpoint (the graphicsLayer transformOrigin — design D1, spec
 * map-rotation-gesture: Rotation anchored at the finger midpoint), so the
 * translation reduces to the pan compensation only. The RenderNode applies the
 * translation in local (pre-scale) space: p' = P + s·R(θ)·(p − P + T) with the
 * pivot P = the midpoint.
 */
class MapCanvasGestureTransformTest {

    private val canvas = Size(1080f, 2400f)
    private val center = Offset(540f, 1200f)

    @Test
    fun purePinchKeepsCentroidFixed() {
        val centroid = Offset(200f, 400f)
        val t = gestureTransformTranslation(0f, 2f, centroid, canvas, Offset.Zero)
        // The RenderNode applies the translation in local (pre-scale) space with
        // the pivot at the centroid: p' = C + s·(p − C + T); at p = centroid,
        // p' must equal centroid.
        val xp = centroid.x + 2f * (centroid.x - centroid.x + t.x)
        val yp = centroid.y + 2f * (centroid.y - centroid.y + t.y)
        assertEquals(centroid.x, xp, 0.01f)
        assertEquals(centroid.y, yp, 0.01f)
    }

    @Test
    fun pureRotationHasNoTranslation() {
        // No pan: the map rotates around the midpoint pivot with no translation.
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
    fun panIsNotAmplifiedByZoom() {
        // A screen-space pan D at zoom s must move the content by exactly D.
        // The RenderNode translation is in local space, so the compensation
        // divides the pan by s.
        val centroid = Offset(200f, 400f)
        val pan = Offset(30f, -20f)
        val t = gestureTransformTranslation(0f, 2f, centroid, canvas, pan)
        // p' = C + s·(p − C + T); at p = centroid the content must land at C + D.
        val xp = centroid.x + 2f * (centroid.x - centroid.x + t.x)
        val yp = centroid.y + 2f * (centroid.y - centroid.y + t.y)
        assertEquals(centroid.x + pan.x, xp, 0.01f)
        assertEquals(centroid.y + pan.y, yp, 0.01f)
    }

    @Test
    fun rotationKeepsMidpointAnchorFixed() {
        // Rotation around the finger midpoint: the content under the midpoint
        // stays under the midpoint at any angle (spec: Rotation anchored at the
        // finger midpoint).
        val midpoint = Offset(200f, 400f)
        val t = gestureTransformTranslation(PI.toFloat(), 1f, midpoint, canvas, Offset.Zero)
        // p' = M + R(θ)·(p − M + T); at p = midpoint, p' must equal midpoint.
        val xp = midpoint.x + (midpoint.x - midpoint.x + t.x) * cos(PI.toFloat()) -
            (midpoint.y - midpoint.y + t.y) * sin(PI.toFloat())
        val yp = midpoint.y + (midpoint.x - midpoint.x + t.x) * sin(PI.toFloat()) +
            (midpoint.y - midpoint.y + t.y) * cos(PI.toFloat())
        assertEquals(midpoint.x, xp, 0.01f)
        assertEquals(midpoint.y, yp, 0.01f)
    }

    @Test
    fun rotateZoomKeepsMidpointAnchorFixed() {
        // Combined rotate+zoom around the midpoint: the content under the
        // midpoint stays fixed under both transforms (spec: Rotation anchored at
        // the finger midpoint).
        val midpoint = Offset(200f, 400f)
        val t = gestureTransformTranslation(PI.toFloat(), 2f, midpoint, canvas, Offset.Zero)
        // p' = M + s·R(θ)·(p − M + T); at p = midpoint, p' must equal midpoint.
        val xp = midpoint.x + 2f * ((midpoint.x - midpoint.x + t.x) * cos(PI.toFloat()) -
            (midpoint.y - midpoint.y + t.y) * sin(PI.toFloat()))
        val yp = midpoint.y + 2f * ((midpoint.x - midpoint.x + t.x) * sin(PI.toFloat()) +
            (midpoint.y - midpoint.y + t.y) * cos(PI.toFloat()))
        assertEquals(midpoint.x, xp, 0.01f)
        assertEquals(midpoint.y, yp, 0.01f)
    }

    @Test
    fun panFollowsPivotInScreenSpaceAtAnyRotation() {
        // Frozen-pivot model (design D1): the pan is the screen-space centroid
        // drift, so the content under the pivot follows it by exactly D at any
        // accumulated rotation/zoom. (A LIVE pivot would re-anchor the
        // accumulated rotation around the new origin each frame and jump the map
        // by (I − s·R(θ))·ΔC — the archived 2026-08-15-bugfix-rotation defect;
        // the caller freezes the pivot at the gesture-start midpoint.)
        val pivot = Offset(200f, 400f)
        val pan = Offset(30f, -20f)
        for (theta in listOf(0f, 0.5f, PI.toFloat(), -0.8f)) {
            val t = gestureTransformTranslation(theta, 1.5f, pivot, canvas, pan)
            // p' = P + s·R(θ)·(p − P + T); at p = P the content must land at P + D.
            val xp = pivot.x + 1.5f * ((pivot.x - pivot.x + t.x) * cos(theta) -
                (pivot.y - pivot.y + t.y) * sin(theta))
            val yp = pivot.y + 1.5f * ((pivot.x - pivot.x + t.x) * sin(theta) +
                (pivot.y - pivot.y + t.y) * cos(theta))
            assertEquals(pivot.x + pan.x, xp, 0.01f)
            assertEquals(pivot.y + pan.y, yp, 0.01f)
        }
    }

    @Test
    fun visualZoomClampsToHeadroomAtMaxMagnification() {
        // At the max mag the commit cannot zoom in, so the preview must not
        // show a zoom that would snap back on gesture end.
        assertEquals(1f, clampGestureVisualZoom(4f, 20.0), 0.001f)
        assertEquals(1f, clampGestureVisualZoom(1.3f, 20.0), 0.001f)
        // One level of headroom: preview clamps to 2× (the committed zoom).
        assertEquals(2f, clampGestureVisualZoom(4f, 19.0), 0.001f)
    }

    @Test
    fun visualZoomClampsToHeadroomAtMinMagnification() {
        // At the min mag the commit cannot zoom out, so the preview must not
        // show a zoom-out that would snap back on gesture end.
        assertEquals(1f, clampGestureVisualZoom(0.25f, 4.0), 0.001f)
        // One level of headroom: preview clamps to 0.5× (the committed zoom).
        assertEquals(0.5f, clampGestureVisualZoom(0.25f, 5.0), 0.001f)
    }

    @Test
    fun visualZoomKeepsFullRangeAtMidMagnification() {
        // Mid-range mags have enough headroom for the full ±2-level preview.
        assertEquals(4f, clampGestureVisualZoom(4f, 10.0), 0.001f)
        assertEquals(0.25f, clampGestureVisualZoom(0.25f, 10.0), 0.001f)
        assertEquals(1.5f, clampGestureVisualZoom(1.5f, 10.0), 0.001f)
    }

    @Test
    fun gestureEndCommitIsUnrounded() {
        // continuous-pinch-zoom (spec: map-pan-zoom): the committed magnification
        // keeps the fractional part of log2(factor) — no rounding to a level.
        assertEquals(15.20163, gestureEndMagnification(14.0, 2.3f), 1e-4)
        assertEquals(15.5, gestureEndMagnification(14.0, 2f * 1.4142135623730951f), 1e-3) // √2·2 → +1.5 levels
        assertEquals(16.0, gestureEndMagnification(14.0, 4f), 1e-9)
        assertEquals(14.0, gestureEndMagnification(14.0, 1f), 1e-9)
        assertEquals(14.67807, gestureEndMagnification(16.0, 0.4f), 1e-4)
    }

    @Test
    fun gestureEndCommitClampMatchesPreviewHeadroom() {
        // At the limits the commit is the same clamp the visual preview uses:
        // preview (clampGestureVisualZoom headroom) and commit stay equal —
        // no snap-back at gesture end.
        assertEquals(20.0, gestureEndMagnification(19.0, 8f), 1e-9) // headroom clamp → 20
        assertEquals(20.0, gestureEndMagnification(20.0, 8f), 1e-9) // max → stays 20
        assertEquals(4.0, gestureEndMagnification(5.0, 0.125f), 1e-9)
        assertEquals(20.0, gestureEndMagnification(21.0, 16f), 1e-9)
    }
}
