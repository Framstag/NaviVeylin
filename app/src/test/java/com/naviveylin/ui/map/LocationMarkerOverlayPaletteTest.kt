package com.naviveylin.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.test.ComposeSandboxRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The accuracy circle SHALL follow the resolved presentation (spec: gps-location-marker —
 * "Accuracy circle follows the resolved presentation"; change `fix-map-overlay-dark-palette`).
 *
 * The circle's colours are draw-time values with no semantics node, so this case measures the pixels
 * they produce: with native graphics the Robolectric view hierarchy rasterizes, and at the ring's own
 * radius the drawn pixels are the ring. One tree carries both presentations
 * (`LocalDensity provides Density(1f)`, so `dp` is `px` and the geometry stays deterministic), and the
 * cases compare the two frames — so the comparison does not depend on what the composable is drawn
 * over, only on the palette branch that is under test.
 *
 * The accuracy circle is drawn only when the projected accuracy is at least
 * `POOR_ACCURACY_THRESHOLD_PX` (20 px), so the accuracy is derived here from the same projection the
 * overlay uses, giving a ring radius of exactly [RADIUS_PX].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocationMarkerOverlayPaletteTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private companion object {
        const val LAT = 48.8566
        const val LON = 2.3522
        const val MAG = 14.0
        const val DPI = 320.0

        /** The ring radius this case draws and samples, in px (density 1 in this tree). */
        const val RADIUS_PX = 60.0

        /** Frame side in px (density 1), so both frames fit the Robolectric window side by side. */
        const val FRAME_PX = 150

        const val LIGHT_TAG = "accuracy-ring-light"
        const val DARK_TAG = "accuracy-ring-dark"

        /** Mid grey, so both presentations composite the translucent ring over the same base. */
        const val LAND = 0xFF3A3A3A.toInt()

        /** The shipped light ring: `#4A90D9`, fill at ~10 %, border at ~40 % (LocationMarkerOverlay). */
        const val SHIPPED_RING_RGB = 0x4A90D9
        const val SHIPPED_FILL_ALPHA = 0x1A
        const val SHIPPED_BORDER_ALPHA = 0x66

        /** How far the border profile walks around [RADIUS_PX], in px. */
        const val PROFILE_HALF = 6

        /** Channel distance that separates the drawn border from the translucent fill (fill ~26). */
        const val BORDER_DISTANCE = 30
    }

    /** The accuracy that projects to [RADIUS_PX] at this magnification, dpi and canvas width. */
    private fun accuracyForRadius(): Double {
        val scale = ProjectionUtils.computeScale(MAG, FRAME_PX.toDouble(), DPI)
        val metersPerPixel = ProjectionUtils.EARTH_RADIUS / scale.scaleGradtorad
        return RADIUS_PX * metersPerPixel
    }

    private data class Frame(
        val bitmap: Bitmap,
        val lightCentre: Pair<Int, Int>,
        val darkCentre: Pair<Int, Int>
    ) {
        override fun toString(): String =
            "Frame raster=${bitmap.width}x${bitmap.height} lightCentre=$lightCentre darkCentre=$darkCentre"
    }

    /** Renders the overlay twice — light presentation beside dark — and reports both centres in px. */
    private fun renderBothPresentations(): Frame {
        val viewport = MapRenderer.RenderViewport(lat = LAT, lon = LON, mag = MAG, angle = 0.0)
        val accuracy = accuracyForRadius()
        composeRule.setContent {
            Row {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    RingFrame(LIGHT_TAG, dark = false, viewport = viewport, accuracy = accuracy)
                    RingFrame(DARK_TAG, dark = true, viewport = viewport, accuracy = accuracy)
                }
            }
        }
        composeRule.waitForIdle()

        val bounds = mapOf(
            LIGHT_TAG to composeRule.onAllNodesWithContentDescription(LIGHT_TAG)
                .fetchSemanticsNodes().single().boundsInRoot,
            DARK_TAG to composeRule.onAllNodesWithContentDescription(DARK_TAG)
                .fetchSemanticsNodes().single().boundsInRoot
        )
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            maxOf(view.width, 1), maxOf(view.height, 1), Bitmap.Config.ARGB_8888
        )
        view.draw(Canvas(bitmap))

        fun centre(tag: String) = bounds.getValue(tag).let { b ->
            ((b.left + b.right) / 2f).roundToInt() to ((b.top + b.bottom) / 2f).roundToInt()
        }
        // FRAME_PX per side in this tree (density 1), so the projected geometry matches the raster.
        assertEquals(
            "the frame must rasterize at $FRAME_PX px (density 1 tree)",
            FRAME_PX.toFloat(), bounds.getValue(LIGHT_TAG).width, 0.5f
        )
        val frame = Frame(bitmap, centre(LIGHT_TAG), centre(DARK_TAG))
        println(frame)
        return frame
    }

    @Composable
    private fun RingFrame(
        tag: String,
        dark: Boolean,
        viewport: MapRenderer.RenderViewport,
        accuracy: Double
    ) {
        Box(
            Modifier
                .size(FRAME_PX.dp)
                .background(Color(LAND))
                .semantics { contentDescription = tag }
        ) {
            LocationMarkerOverlay(
                lat = LAT,
                lon = LON,
                bearing = 0.0,
                accuracy = accuracy,
                viewport = viewport,
                dpi = DPI,
                dark = dark
            )
        }
    }

    /** The drawn pixels on the ring's own radius, in the frame's absolute bitmap coordinates. */
    private fun ringPixels(frame: Frame, centre: Pair<Int, Int>, radiusPx: Double, angles: Int = 24): List<Pair<Int, Int>> =
        (0 until angles).map { i ->
            val a = 2.0 * Math.PI * i / angles
            val x = centre.first + (radiusPx * cos(a)).roundToInt()
            val y = centre.second + (radiusPx * sin(a)).roundToInt()
            x to y
        }

    private fun rgb(px: Int): Triple<Int, Int, Int> =
        Triple((px shr 16) and 0xFF, (px shr 8) and 0xFF, px and 0xFF)

    /** The land behind the ring: the pixel well outside it, along the upward ray. */
    private fun land(frame: Frame, centre: Pair<Int, Int>): Int =
        frame.bitmap.getPixel(centre.first, centre.second - (RADIUS_PX + 8).roundToInt())

    private fun channelDistance(a: Int, b: Int): Int =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
            abs((a and 0xFF) - (b and 0xFF))

    /**
     * The border's radial profile along the upward ray: the channel distance from the land colour at
     * radii [RADIUS_PX]-[PROFILE_HALF] … [RADIUS_PX]+[PROFILE_HALF]. The translucent fill is ~26 away
     * and the border ~70-100, so [BORDER_DISTANCE] separates them and the profile shows where the
     * stroke is drawn and how thick it is.
     */
    private fun borderProfile(frame: Frame, centre: Pair<Int, Int>): List<Int> {
        val landPx = land(frame, centre)
        return (-PROFILE_HALF..PROFILE_HALF).map { d ->
            val px = frame.bitmap.getPixel(centre.first, centre.second - (RADIUS_PX + d).roundToInt())
            channelDistance(px, landPx)
        }
    }

    private fun borderRadiusPx(profile: List<Int>): Double =
        RADIUS_PX + (profile.indices.maxByOrNull { profile[it] }!! - PROFILE_HALF)

    private fun borderThicknessPx(profile: List<Int>): Int {
        val peak = profile.indices.maxByOrNull { profile[it] }!!
        var lo = peak
        var hi = peak
        while (lo - 1 >= 0 && profile[lo - 1] > BORDER_DISTANCE) lo--
        while (hi + 1 < profile.size && profile[hi + 1] > BORDER_DISTANCE) hi++
        return hi - lo + 1
    }

    /** Mean RGB of the sampled pixels, as one ARGB value. */
    private fun meanColor(samples: List<Int>): Int {
        val r = samples.sumOf { (it shr 16) and 0xFF } / samples.size
        val g = samples.sumOf { (it shr 8) and 0xFF } / samples.size
        val b = samples.sumOf { it and 0xFF } / samples.size
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** WCAG 2.x relative luminance of an opaque pixel. */
    private fun luminance(px: Int): Double {
        fun linear(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear((px shr 16) and 0xFF) +
            0.7152 * linear((px shr 8) and 0xFF) +
            0.0722 * linear(px and 0xFF)
    }

    /** HSV hue angle in degrees (0-360) of an opaque pixel. */
    private fun hueDegrees(px: Int): Double {
        val r = ((px shr 16) and 0xFF).toDouble()
        val g = ((px shr 8) and 0xFF).toDouble()
        val b = (px and 0xFF).toDouble()
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC
        if (delta == 0.0) return 0.0
        val hue = when (maxC) {
            r -> 60.0 * (((g - b) / delta) % 6.0)
            g -> 60.0 * (((b - r) / delta) + 2.0)
            else -> 60.0 * (((r - g) / delta) + 4.0)
        }
        return if (hue < 0) hue + 360.0 else hue
    }

    private fun angularDistance(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360.0
        return min(diff, 360.0 - diff)
    }

    /** Alpha-composites a translucent ARGB value over an opaque pixel. */
    private fun composite(over: Int, under: Int): Int {
        val a = (over ushr 24) and 0xFF
        fun mix(shift: Int): Int {
            val top = (over shr shift) and 0xFF
            val bottom = (under shr shift) and 0xFF
            return (top * a + bottom * (255 - a) + 127) / 255
        }
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** Every channel of [actual] inside the [low]..[high] band, padded by [tolerance]. */
    private fun assertChannelsBetween(low: Int, high: Int, actual: Int, tolerance: Int, message: String) {
        listOf(16, 8, 0).forEach { shift ->
            val lo = ((low shr shift) and 0xFF) - tolerance
            val hi = ((high shr shift) and 0xFF) + tolerance
            val a = (actual shr shift) and 0xFF
            assertTrue(
                "$message (allowed $lo..$hi, drawn ${rgb(actual)}, channel ${shift / 8} is $a)",
                a in lo..hi
            )
        }
    }

    /** Every channel of [actual] within [tolerance] of [expected]. */
    private fun assertChannelsWithin(expected: Int, actual: Int, tolerance: Int, message: String) {
        listOf(16, 8, 0).forEach { shift ->
            val e = (expected shr shift) and 0xFF
            val a = (actual shr shift) and 0xFF
            assertTrue(
                "$message (expected ${rgb(expected)}, drawn ${rgb(actual)}, channel ${shift / 8} off by ${abs(e - a)})",
                abs(e - a) <= tolerance
            )
        }
    }

    @Test
    fun theDrawnAccuracyRingChangesWithThePresentation() {
        // Red before the shared palette exists: the ring was the same module-level literal in both
        // presentations, so the two frames agreed pixel for pixel on the ring's own radius.
        val frame = renderBothPresentations()
        val light = ringPixels(frame, frame.lightCentre, RADIUS_PX)
            .map { (x, y) -> rgb(frame.bitmap.getPixel(x, y)) }
        val dark = ringPixels(frame, frame.darkCentre, RADIUS_PX)
            .map { (x, y) -> rgb(frame.bitmap.getPixel(x, y)) }

        val differing = light.zip(dark).count { (l, d) -> l != d }
        println("AccuracyRing presentation samples: ${differing}/${light.size} differ, light=${light.first()} dark=${dark.first()}")

        assertTrue(
            "the accuracy ring must follow the resolved presentation, but only $differing of " +
                "${light.size} sampled ring pixels differ (light=${light.first()}, dark=${dark.first()})",
            differing >= light.size / 2
        )
    }

    @Test
    fun theDrawnAccuracyRingUsesADarkerToneOfTheSameHueFamily() {
        // Spec scenario "An overlay that carries no nav-essential information dims": drawn proof, on
        // the pixels the ring actually paints over the land.
        val frame = renderBothPresentations()
        val light = meanColor(ringPixels(frame, frame.lightCentre, RADIUS_PX).map { (x, y) -> frame.bitmap.getPixel(x, y) })
        val dark = meanColor(ringPixels(frame, frame.darkCentre, RADIUS_PX).map { (x, y) -> frame.bitmap.getPixel(x, y) })
        val lightLum = luminance(light)
        val darkLum = luminance(dark)
        val hueDelta = angularDistance(hueDegrees(light), hueDegrees(dark))
        println(
            "AccuracyRing drawn tone: light=${rgb(light)} lum=${lightLum} hue=${hueDegrees(light)} " +
                "dark=${rgb(dark)} lum=${darkLum} hue=${hueDegrees(dark)} hueDelta=${hueDelta}"
        )

        assertTrue(
            "the drawn dark ring must be dimmer than the light one (light=$lightLum, dark=$darkLum)",
            darkLum < lightLum
        )
        assertTrue(
            "the drawn dark ring must stay in the light ring's hue family (drifted ${hueDelta}°)",
            hueDelta <= 20.0
        )
    }

    @Test
    fun theRingRadiusAndStrokeWidthAreIdenticalInBothPresentations() {
        // Spec scenario "The accuracy circle's geometry does not branch": only the palette may branch.
        val frame = renderBothPresentations()
        val lightProfile = borderProfile(frame, frame.lightCentre)
        val darkProfile = borderProfile(frame, frame.darkCentre)
        val lightRadius = borderRadiusPx(lightProfile)
        val darkRadius = borderRadiusPx(darkProfile)
        val lightThickness = borderThicknessPx(lightProfile)
        val darkThickness = borderThicknessPx(darkProfile)
        println(
            "AccuracyRing geometry: light radius=${lightRadius}px thickness=${lightThickness}px " +
                "dark radius=${darkRadius}px thickness=${darkThickness}px " +
                "profiles light=$lightProfile dark=$darkProfile"
        )

        assertEquals("the drawn radius must not branch", lightRadius, darkRadius, 0.5)
        assertEquals("the drawn stroke width must not branch", lightThickness, darkThickness)
        assertEquals(
            "the ring must be drawn where the accuracy projects it",
            RADIUS_PX,
            lightRadius,
            1.0
        )
    }

    @Test
    fun theLightRingKeepsItsShippedTone() {
        // Regression guard for light presentation: the shipped ring is `#4A90D9` at ~40 %, composited
        // over the land, and this change must not move it.
        val frame = renderBothPresentations()
        val landPx = land(frame, frame.lightCentre)
        val shippedBorder = (SHIPPED_BORDER_ALPHA shl 24) or SHIPPED_RING_RGB
        val shippedFill = (SHIPPED_FILL_ALPHA shl 24) or SHIPPED_RING_RGB
        // The border is stroked over its own translucent fill, so a drawn sample lies between the
        // shipped tone over the land and the same tone over its fill — both computed from the shipped
        // bytes, so the case fails the moment the light palette moves.
        val fillOnly = composite(shippedFill, landPx)
        val overLand = composite(shippedBorder, landPx)
        val overFill = composite(shippedBorder, fillOnly)
        val samples = ringPixels(frame, frame.lightCentre, RADIUS_PX)
            .map { (x, y) -> frame.bitmap.getPixel(x, y) }
        val mean = meanColor(samples)
        println(
            "AccuracyRing light tone: land=${rgb(landPx)} fillOnly=${rgb(fillOnly)} " +
                "overLand=${rgb(overLand)} overFill=${rgb(overFill)} mean=${rgb(mean)} samples=${samples.size}"
        )

        // A sample lies between the fill alone and the shipped border over that fill: the stroke's
        // edge pixels blend toward the fill. Any other light tone leaves this band.
        samples.forEach { drawn ->
            assertChannelsBetween(
                fillOnly,
                overFill,
                drawn,
                4,
                "the light ring's drawn tone must stay the shipped #4A90D9 at ~40 %"
            )
        }
        // With 24 samples around the circle the partial coverages average out to the full-coverage
        // tone drawn over the land.
        assertChannelsWithin(
            overLand,
            mean,
            6,
            "the light presentation's mean ring tone must be the shipped one"
        )
        assertTrue("the light ring must be sampled", samples.isNotEmpty())
    }
}
