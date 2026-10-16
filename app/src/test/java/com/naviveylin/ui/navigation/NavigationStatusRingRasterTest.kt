package com.naviveylin.ui.navigation

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The routing status row's progress rings are a draw-time shape with no semantics node, so these
 * cases measure the pixels they produce: with native graphics the Robolectric view hierarchy
 * rasterizes, and a pixel on the ring's centreline reports which half of the ring was drawn
 * (spec: `navigation-status-details` — Route progress rings on the routing status row; Distance
 * ring reflects traveled distance; Time ring reflects elapsed time; A recalculation does not hold
 * a stale ring). Compose's own `captureToImage()` is not usable here — it times out on Robolectric
 * — and the two theme colours the ring draws with are read from swatches rendered into the same
 * frame, so no colour is hardcoded.
 *
 * One tree carries the card at two densities (`LocalDensity provides Density(1f)` / `Density(2f)`)
 * and re-renders it under a changed percent, so a single case compares the two and a stale value
 * cannot hide behind a fresh draw. 2x rather than 4x: at 4x a 48 dp stop control fills 192 px of
 * the 320 px test window, the three slots overlap and a scan across the ring reads the
 * neighbouring arc — the density pair stays one the layout can lay out cleanly. The measurements
 * are printed, so the numbers the change's evidence quotes stay in the JUnit XML's `system-out`
 * (the `CompassNeedleStrokeTest` pattern).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationStatusRingRasterTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** The card's density: 1x and 2x are the two the stroke case compares. */
    private var density by mutableFloatStateOf(1f)

    private var distancePercent by mutableIntStateOf(42)
    private var timePercent by mutableIntStateOf(50)

    /**
     * The card under test at [density], with the two theme colours it draws with as swatches above
     * it. The card is the only child of its density scope, so its ring tags are unique and the
     * middle column's slot — the one the 4x layout cannot push past the window edge — is the one
     * that stays measurable at both densities.
     */
    private fun showCard() {
        composeRule.setContent {
            Column {
                Box(
                    Modifier
                        .size(4.dp)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("refValue")
                )
                Box(
                    Modifier
                        .size(4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("refTrack")
                )
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    NavigationStateOverlay(
                        remainingDistance = 5000.0,
                        etaMillis = System.currentTimeMillis() + 600_000,
                        distanceProgressPercent = distancePercent,
                        timeProgressPercent = timePercent,
                        onStopNavigation = {},
                        onClick = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** One rasterized frame of the window, with the two theme colours read from its swatches. */
    private data class Frame(val bitmap: Bitmap, val value: Int, val track: Int) {
        override fun toString(): String = "Frame raster=${bitmap.width}x${bitmap.height}"
    }

    private fun rasterize(): Frame {
        composeRule.waitForIdle()
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            maxOf(view.width, 1), maxOf(view.height, 1), Bitmap.Config.ARGB_8888
        )
        view.draw(Canvas(bitmap))
        return Frame(bitmap, colourOf(bitmap, "refValue"), colourOf(bitmap, "refTrack"))
    }

    private fun colourOf(bitmap: Bitmap, tag: String): Int {
        val bounds = boundsOf(tag)
        return bitmap.getPixel(
            ((bounds.left + bounds.right) / 2f).roundToInt().coerceIn(0, bitmap.width - 1),
            ((bounds.top + bounds.bottom) / 2f).roundToInt().coerceIn(0, bitmap.height - 1)
        )
    }

    private fun boundsOf(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** Channel-wise distance, so an antialiased edge lands on the colour it is nearer. */
    private fun colourDistance(a: Int, b: Int): Int {
        fun channel(px: Int, shift: Int) = (px shr shift) and 0xFF
        return abs(channel(a, 16) - channel(b, 16)) +
            abs(channel(a, 8) - channel(b, 8)) +
            abs(channel(a, 0) - channel(b, 0))
    }

    private fun isValue(px: Int, frame: Frame): Boolean =
        colourDistance(px, frame.value) < colourDistance(px, frame.track)

    /**
     * The drawn stroke width in pixels: the run of value-coloured pixels around the ring's own
     * centre row, measured in the ring's outer fifth — outside the glyph, which sits in the middle
     * of the slot and is itself nearer the value colour than the track, so a scan from the slot's
     * edge must stay outboard of it to see the stroke. On a closed ring (100 %) the left crossing
     * is the value arc; at a partial percent the same run is found where the arc's other side
     * crosses the row.
     */
    private fun strokeWidthPx(frame: Frame, ring: Rect): Int {
        val row = ((ring.top + ring.bottom) / 2f).roundToInt()
        val left = ring.left.roundToInt().coerceIn(0, frame.bitmap.width - 1)
        val bandEnd = (ring.left + ring.width * 0.18f).roundToInt().coerceIn(0, frame.bitmap.width - 1)
        var x = left
        while (x <= bandEnd && !isValue(frame.bitmap.getPixel(x, row), frame)) x++
        var width = 0
        while (x < frame.bitmap.width && isValue(frame.bitmap.getPixel(x, row), frame)) {
            width++
            x++
        }
        return width
    }

    /**
     * A pixel inside the ring's stroke at [degrees] (0 = three o'clock, positive clockwise). The
     * sampling radius is a fraction of the slot — the stroke's own centreline is at
     * (slot - stroke) / 2, i.e. 0.446 of the slot for a 3 dp stroke in a 28 dp slot — so the sample
     * does not depend on measuring the stroke first.
     */
    private fun sampleOnRing(frame: Frame, ring: Rect, degrees: Float): Int {
        val cx = (ring.left + ring.right) / 2f
        val cy = (ring.top + ring.bottom) / 2f
        val radius = ring.width * 0.446f
        val radians = Math.toRadians(degrees.toDouble())
        val x = (cx + radius * cos(radians)).roundToInt().coerceIn(0, frame.bitmap.width - 1)
        val y = (cy + radius * sin(radians)).roundToInt().coerceIn(0, frame.bitmap.height - 1)
        return frame.bitmap.getPixel(x, y)
    }

    @Test
    fun arcStrokeKeepsItsWidthInDp() {
        distancePercent = 100
        timePercent = 100
        showCard()

        density = 1f
        composeRule.waitForIdle()
        var frame = rasterize()
        val px1x = strokeWidthPx(frame, boundsOf("timeProgressRing"))

        density = 2f
        composeRule.waitForIdle()
        frame = rasterize()
        val px2x = strokeWidthPx(frame, boundsOf("timeProgressRing"))

        val dp1x = px1x / 1f
        val dp2x = px2x / 2f
        println("NavStatusRingStroke 1x=${px1x}px (${dp1x}dp) 2x=${px2x}px (${dp2x}dp)")

        // Density-independent: the drawn width in dp is the same at both densities. The pixel run
        // is counted only where it is nearer the value colour than the track, so the antialiased
        // edge contributes at most one pixel — 1.0 dp at 1x, 0.5 dp at 2x — and a raw-pixel stroke
        // misses the tolerance outright: it keeps its pixel count while the design's stroke grows
        // with the screen (measured: 3 dp both densities against 3.0 dp / 1.5 dp).
        assertTrue(
            "the ring stroke must be the same width in dp at 1x and 2x " +
                "(1x=${px1x}px=${dp1x}dp, 2x=${px2x}px=${dp2x}dp)",
            abs(dp2x - dp1x) <= 1.0f
        )
        assertTrue(
            "the ring stroke must scale with density (1x=${px1x}px, 2x=${px2x}px)",
            px2x >= 1.5f * px1x
        )
    }

    @Test
    fun arcSitsOnTheCompletedSide() {
        distancePercent = 50
        timePercent = 50
        showCard()

        val half = rasterize()
        println(
            "NavStatusRingHalf ring=${boundsOf("timeProgressRing")} " +
                "threeOClock=${Integer.toHexString(sampleOnRing(half, boundsOf("timeProgressRing"), 0f))} " +
                "nineOClock=${Integer.toHexString(sampleOnRing(half, boundsOf("timeProgressRing"), 180f))}"
        )
        for (tag in listOf("timeProgressRing", "distanceProgressRing")) {
            val ring = boundsOf(tag)
            assertTrue(
                "$tag: at 50 % the completed side (three o'clock) must be the value colour",
                isValue(sampleOnRing(half, ring, 0f), half)
            )
            assertTrue(
                "$tag: at 50 % the unfinished side (nine o'clock) must still be the track",
                !isValue(sampleOnRing(half, ring, 180f), half)
            )
        }

        distancePercent = 100
        timePercent = 100
        val closed = rasterize()
        for (tag in listOf("timeProgressRing", "distanceProgressRing")) {
            val ring = boundsOf(tag)
            assertTrue(
                "$tag: a closed ring must be the value colour all the way round",
                isValue(sampleOnRing(closed, ring, 0f), closed) &&
                    isValue(sampleOnRing(closed, ring, 180f), closed)
            )
        }
    }

    @Test
    fun arcFollowsALoweredPercent() {
        distancePercent = 60
        timePercent = 60
        showCard()

        val ring = boundsOf("distanceProgressRing")
        val raised = rasterize()
        // Six o'clock: inside a 60 % sweep (216 deg from twelve o'clock), outside a 40 % one.
        println(
            "NavStatusRingPercent 60%->sixOClock=${Integer.toHexString(sampleOnRing(raised, ring, 90f))} " +
                "value=${Integer.toHexString(raised.value)} track=${Integer.toHexString(raised.track)}"
        )
        assertTrue(
            "at 60 % the point at six o'clock must be inside the completed arc",
            isValue(sampleOnRing(raised, ring, 90f), raised)
        )

        // The route is recalculated and the share already travelled drops: the arc must follow the
        // new percent instead of holding the value it was drawn with (spec: A recalculation does
        // not hold a stale ring).
        distancePercent = 40
        timePercent = 40
        val lowered = rasterize()
        println(
            "NavStatusRingPercent 40%->sixOClock=${Integer.toHexString(sampleOnRing(lowered, ring, 90f))}"
        )
        assertTrue(
            "after the percent dropped to 40 %, six o'clock must be outside the arc",
            !isValue(sampleOnRing(lowered, ring, 90f), lowered)
        )
    }
}
