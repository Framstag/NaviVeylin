package com.naviveylin.ui.navigation

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.naviveylin.core.ManeuverSymbols
import kotlin.math.ceil
import kotlin.math.hypot
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 7.4: measure the numbered roundabout-exit glyph's digit on the device.
 *
 * The spec clause is "the number SHALL be legible at the smallest size any surface draws the symbol"
 * (`roundabout-renderer`), and the glyph's own comment defers its look to this check in as many words:
 * "Robolectric's canvas discards the draws, so the number itself is asserted as arithmetic here and its
 * look is the on-device check". Every JVM case in `ManeuverSymbolsTest` therefore asserts arithmetic
 * (the label, the fitted size, the marker count), and none of them can see a rasterised digit.
 *
 * What this measures, with the geometry from `ManeuverSymbols`: the ink is white on a transparent
 * bitmap, the ring is stroked at radius `0.20 * size` with width `0.06 * size`, so the ring occupies
 * radius 0.17..0.23 and anything with alpha inside radius 0.15 is digit, not ring. The digit's
 * bounding box is measured there and reported against the ring's inner diameter - the number that
 * decides whether the ordinal is readable, and the number that would have to change if it is not.
 *
 * Sizes measured are the two the surfaces actually use: 48 px ([ManeuverSymbols.BITMAP_SIZE], the
 * ongoing notification's large icon and the car bitmap) and the phone overlay's 64 dp converted with
 * this device's own density. The 48 px case is the smaller of the two and the one the requirement's
 * "smallest size any surface draws" names.
 */
@RunWith(AndroidJUnit4::class)
class RoundaboutExitGlyphDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** The phone overlay draws the icon at this Compose `dp` size (`NextTurnOverlay`). */
    private val phoneOverlayDp = 64f

    private fun pxForDp(dp: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()

    private data class Measurement(
        val size: Int,
        val exitNumber: Int,
        val digitHeight: Int,
        val digitWidth: Int,
        val ringInnerDiameter: Int,
        val inkPixels: Int
    ) {
        fun line(): String =
            "glyph size=${size}px exit=$exitNumber digit=${digitWidth}x$digitHeight px " +
                "ringInner=$ringInnerDiameter px ink=$inkPixels px"
    }

    /**
     * The digit's bounding box, found only inside the ring's interior so the ring's own stroke cannot
     * be mistaken for it. `interior` is 0.15 * size, against a ring whose inner edge is at 0.17 * size.
     */
    private fun measure(size: Int, exitNumber: Int): Measurement {
        // The bitmap cache is main-thread-only by contract (ManeuverSymbols' header), and the
        // instrumentation thread is not the main thread.
        var drawn: Bitmap? = null
        InstrumentationRegistry.getInstrumentation()
            .runOnMainSync { drawn = ManeuverSymbols.roundaboutExitBitmap(exitNumber, size) }
        val bitmap = requireNotNull(drawn) { "the glyph was not drawn" }
        val centre = size / 2.0
        val interior = 0.15 * size
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var ink = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (hypot(x - centre, y - centre) > interior) continue
                if (Color.alpha(bitmap.getPixel(x, y)) == 0) continue
                ink++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        // sw = 0.06 * size, r = 0.20 * size  ->  inner edge of the ring is at 0.17 * size.
        val ringInner = (2.0 * (0.17 * size)).toInt()
        return Measurement(
            size = size,
            exitNumber = exitNumber,
            digitHeight = if (ink > 0) maxY - minY + 1 else 0,
            digitWidth = if (ink > 0) maxX - minX + 1 else 0,
            ringInnerDiameter = ringInner,
            inkPixels = ink
        )
    }

    /**
     * The floor the requirement's "legible" is held to, as a fraction of the symbol's edge. The digit is
     * requested at `0.26 * size`, whose capital height is ~0.19 * size, so 0.16 is just under what the
     * geometry promises: a case that fails here means the digit was shrunk or clipped, not that the
     * floor is arbitrary. A single digit at 48 px is ~13 px tall; the floor at 48 px is 8 px.
     *
     * It is asserted over the ordinals a real roundabout exit carries (one and two digits). A three-digit
     * ordinal is measured too, but only against the ring: the measurement below shows its proportional
     * height is *not* constant across sizes, so it is not a bound this floor can hold.
     */
    private val legibleHeightFraction = 0.16

    @Test
    fun theExitNumberIsLegibleAtTheSmallestSurfaceSize() {
        val smallest = ManeuverSymbols.BITMAP_SIZE
        val sizes = linkedMapOf(
            "smallest (notification and car bitmap, ${smallest}px)" to smallest,
            "phone overlay (${phoneOverlayDp}dp = ${pxForDp(phoneOverlayDp)}px on this device)" to pxForDp(phoneOverlayDp)
        )
        // One and two digits: the range a roundabout exit carries, and the range the floor holds for.
        val legibleOrdinals = listOf(1, 3, 12)
        // Past any real roundabout. `fittedTextSize` shrinks the number to fit the ring rather than
        // clipping it, so this measures where that shrink starts costing legibility - the boundary
        // design.md's Open Question 2 wants a clamp from. It is deliberately not held to the floor: it
        // measures 0.25 of the edge at 48 px but 0.143 at 168 px, so the ratio is size-dependent and a
        // digit-count clamp cannot be justified by proportion alone.
        val beyondLegible = 112
        val measurements = mutableListOf<Measurement>()
        for ((label, size) in sizes) {
            for (exit in legibleOrdinals) {
                val m = measure(size, exit)
                measurements += m
                Log.i("RoundaboutGlyphTest", "$label ${m.line()}")
                assertTrue(
                    "$label exit=$exit: the digit must be drawn inside the ring (ink=0)",
                    m.inkPixels > 0
                )
                assertTrue(
                    "$label exit=$exit: digit height ${m.digitHeight}px is below the legible floor " +
                        "of ${ceil(legibleHeightFraction * size).toInt()}px (0.16 of ${size}px) - " +
                        "raise the digit's size from the ring radius and re-measure",
                    m.digitHeight >= ceil(legibleHeightFraction * size).toInt()
                )
                assertTrue(
                    "$label exit=$exit: digit width ${m.digitWidth}px overruns the ring's inner " +
                        "diameter ${m.ringInnerDiameter}px",
                    m.digitWidth <= m.ringInnerDiameter
                )
            }

            val wide = measure(size, beyondLegible)
            measurements += wide
            Log.i(
                "RoundaboutGlyphTest",
                "$label ${wide.line()} heightFraction=%.3f (floor %.3f)"
                    .format(wide.digitHeight.toDouble() / size, legibleHeightFraction)
            )
            // What does hold at every digit count: the number is shrunk to fit the ring, never clipped.
            assertTrue(
                "$label exit=$beyondLegible: the number must fit the ring rather than be clipped - " +
                    "digit width ${wide.digitWidth}px against the inner diameter ${wide.ringInnerDiameter}px",
                wide.digitWidth <= wide.ringInnerDiameter
            )
            assertTrue(
                "$label exit=$beyondLegible: the number must still be drawn (ink=0)",
                wide.inkPixels > 0
            )
        }
        Log.i(
            "RoundaboutGlyphTest",
            "verdict: " + measurements.joinToString("; ") {
                "${it.size}px/exit${it.exitNumber}=${it.digitWidth}x${it.digitHeight}px"
            }
        )
    }

}
