package com.naviveylin.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The compass needle's stroke SHALL be a density-independent size — the same visual
 * width on every screen (spec: compass-button — Compass needle stroke is
 * density-independent; TODO.md §72). `drawCompassNeedle` stroked both needle halves
 * with `3f`, a raw device-pixel count, while every other length in the file goes
 * through `.dp.toPx()` (`needleLength = 10.dp.toPx()`, the rim's `1.dp.toPx()`), so on
 * a 3.5x screen the needle was 0.86 dp wide — visibly thinner than on a 1x screen.
 *
 * The stroke is a draw-time value with no semantics node, so this case measures the
 * pixels it produces instead: with native graphics the Robolectric view hierarchy
 * rasterizes, and at the needle's centre column the run of needle-colored pixels is
 * the drawn stroke width. (Compose's own `captureToImage()` is not usable here — it
 * times out on Robolectric.)
 *
 * One tree carries the same needle at two densities (`LocalDensity provides
 * Density(1f)` / `Density(4f)`) so a single case compares 1x against 4x. The device
 * density stays the Robolectric default (mdpi), so the semantics bounds — read in
 * pixels from `SemanticsNode.boundsInRoot` — are bitmap pixels. The measurement is
 * printed, so the numbers this change's evidence quotes stay in the JUnit XML's
 * `system-out`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompassNeedleStrokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** One rasterized frame of the two-density tree, with each needle's centre in pixels. */
    private data class NeedleFrame(val bitmap: Bitmap, val centersPx: List<Pair<Int, Int>>, val densities: List<Float>) {
        override fun toString(): String =
            "NeedleFrame raster=${bitmap.width}x${bitmap.height} " +
                centersPx.mapIndexed { i, c -> "d=${densities[i]}x center=${c.first},${c.second}" }
                    .joinToString(" | ")
    }

    private fun renderSameNeedleAt1xAnd4x(): NeedleFrame {
        val densities = listOf(1f, 4f)
        composeRule.setContent {
            Row {
                for (density in densities) {
                    CompositionLocalProvider(LocalDensity provides Density(density)) {
                        CompassButton(
                            mapAngleRadians = 0.0,
                            gpsFixQuality = GpsFixQuality.GOOD,
                            isDarkPresentation = false,
                            onCenterClick = {},
                            onToggleOrientation = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val matches = composeRule.onAllNodesWithContentDescription("Compass")
        val bounds = matches.fetchSemanticsNodes().map { it.boundsInRoot }
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            maxOf(view.width, 1), maxOf(view.height, 1), Bitmap.Config.ARGB_8888
        )
        view.draw(Canvas(bitmap))
        val frame = NeedleFrame(
            bitmap = bitmap,
            centersPx = bounds.map { b ->
                ((b.left + b.right) / 2f).roundToInt() to ((b.top + b.bottom) / 2f).roundToInt()
            },
            densities = densities
        )
        println(frame)
        return frame
    }

    /**
     * A pixel belongs to the needle when it is nearer the needle color (`#1F1F1F`) than
     * the fill (light green `#C8E6C9`), so the antialiased edge counts once — half of it
     * at the low density and a quarter of it at 4x, which is the tolerance the case
     * allows below.
     */
    private fun isNeedle(px: Int): Boolean {
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        return r < 150 && g < 150 && b < 150
    }

    /** The drawn stroke width in pixels: the needle-colored run across the needle, 1 px above its hub. */
    private fun strokeWidthPx(frame: NeedleFrame, index: Int): Int {
        val (centerX, centerY) = frame.centersPx[index]
        val row = centerY - 1 // hub, one pixel up: only the north half is drawn there, no "N" glyph
        var left = centerX
        var right = centerX
        while (left > 0 && isNeedle(frame.bitmap.getPixel(left - 1, row))) left--
        while (right < frame.bitmap.width - 1 && isNeedle(frame.bitmap.getPixel(right + 1, row))) right++
        return right - left + 1
    }

    @Test
    fun `needle stroke keeps its width in dp at 1x and 4x`() {
        val frame = renderSameNeedleAt1xAnd4x()
        val px1x = strokeWidthPx(frame, 0)
        val px4x = strokeWidthPx(frame, 1)
        val dp1x = px1x / 1f
        val dp4x = px4x / 4f
        println("CompassNeedleStroke 1x=${px1x}px (${dp1x}dp) 4x=${px4x}px (${dp4x}dp)")

        // Density-independent: the drawn width in dp is the same at both densities. The
        // antialiased edge is ~1 px, i.e. 1.0 dp at 1x and 0.25 dp at 4x, so 1.5 dp is the
        // tolerance that edge needs and the same figure a raw-pixel stroke exceeds
        // (measured on HEAD: 4 px at both densities = 4.0 dp vs 1.0 dp).
        assertTrue(
            "the needle stroke must be the same width in dp at 1x and 4x " +
                "(1x=${px1x}px=${dp1x}dp, 4x=${px4x}px=${dp4x}dp)",
            abs(dp4x - dp1x) <= 1.5f
        )

        // …and it must be a drawn, density-scaled stroke: a raw-pixel stroke keeps the
        // same pixel count at 4x instead of growing with the screen.
        assertTrue(
            "the needle stroke must scale with density (1x=${px1x}px, 4x=${px4x}px)",
            px4x >= 2 * px1x
        )
    }
}
