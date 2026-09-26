package com.naviveylin.ui.map

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Plain-JUnit tests for the compass palette (spec: compass-button — GPS fix
 * status fill color, Compass colors follow the resolved day/night presentation;
 * change compass-day-night-palette).
 *
 * The palette is the whole point of the change: a light fill with a light
 * dark-scheme symbol color measured 1.04:1, i.e. an unreadable needle. These
 * tests pin the two properties that were missing — needle/fill contrast per
 * presentation, and a hue family per fix quality that survives the presentation
 * switch — instead of asserting literals, so the exact tones stay tunable.
 *
 * No Robolectric needed: [compassFillColor] and [compassOnFillColor] are pure
 * functions of the presentation flag, and `Color` construction is
 * platform-independent.
 */
class CompassPaletteTest {

    private val qualities = listOf(GpsFixQuality.NONE, GpsFixQuality.POOR, GpsFixQuality.GOOD)
    private val presentations = listOf(false, true)

    @Test
    fun needleHasAtLeast4Point5ToOneContrastOnEveryFill() {
        // Spec scenario "Dark presentation uses light needle on dark fill" /
        // "Light presentation uses dark needle on light fill".
        for (dark in presentations) {
            val onFill = compassOnFillColor(isDarkPresentation = dark)
            for (quality in qualities) {
                val ratio = contrastRatio(onFill, compassFillColor(quality, isDarkPresentation = dark))
                assertTrue(
                    "needle on $quality fill (dark=$dark) contrast $ratio < 4.5:1",
                    ratio >= 4.5
                )
            }
        }
    }

    @Test
    fun everyQualityUsesADifferentTonePerPresentation() {
        // Spec scenario "Quality change stays inside the active presentation" —
        // six distinct fills: three qualities, two tones each.
        val fills = presentations.flatMap { dark -> qualities.map { compassFillColor(it, dark) } }
        assertEquals("six distinct fills expected", 6, fills.toSet().size)
    }

    @Test
    fun hueFamilyIsPreservedAcrossPresentations() {
        // The quality's meaning must not change with the presentation: the same
        // hue family in a different tone (spec: "GPS fix status fill color").
        for (quality in qualities) {
            val lightHue = hueDegrees(compassFillColor(quality, isDarkPresentation = false))
            val darkHue = hueDegrees(compassFillColor(quality, isDarkPresentation = true))
            val delta = angularDistance(lightHue, darkHue)
            assertTrue(
                "hue of $quality drifted $delta° between presentations " +
                    "(light $lightHue°, dark $darkHue°)",
                delta <= 20.0
            )
        }
    }

    @Test
    fun hueFamiliesMatchTheFixQuality() {
        // Red = no fix, yellow = poor, green = good, in BOTH presentations.
        for (dark in presentations) {
            val none = signedHue(compassFillColor(GpsFixQuality.NONE, isDarkPresentation = dark))
            val poor = signedHue(compassFillColor(GpsFixQuality.POOR, isDarkPresentation = dark))
            val good = signedHue(compassFillColor(GpsFixQuality.GOOD, isDarkPresentation = dark))
            assertTrue("no-fix fill must be red (dark=$dark, hue $none°)", abs(none) <= 30.0)
            assertTrue("poor-fix fill must be yellow (dark=$dark, hue $poor°)", poor in 35.0..70.0)
            assertTrue("good-fix fill must be green (dark=$dark, hue $good°)", good in 90.0..160.0)
        }
    }

    @Test
    fun needleColorDependsOnPresentationOnly() {
        // Spec scenario "GPS quality does not change the needle color": the
        // symbol color is one value per presentation, so a quality change cannot
        // move it; the presentations differ, so the palette actually branches.
        for (dark in presentations) {
            val colors = qualities.map { compassOnFillColor(isDarkPresentation = dark) }
            assertEquals("one symbol color per presentation (dark=$dark)", 1, colors.toSet().size)
        }
        assertTrue(
            "symbol color must differ between presentations",
            compassOnFillColor(isDarkPresentation = false) != compassOnFillColor(isDarkPresentation = true)
        )
    }

    @Test
    fun lightSymbolColorOnLightFillsIsTheDefectThisChangeFixes() {
        // Guards the regression: the light symbol color must never be paired with
        // a light fill again (it measured 1.04:1 before the change).
        val lightSymbol = compassOnFillColor(isDarkPresentation = true)
        for (quality in qualities) {
            val ratio = contrastRatio(lightSymbol, compassFillColor(quality, isDarkPresentation = false))
            assertTrue(
                "light symbol color on the light $quality fill is $ratio — " +
                    "this is the pre-change defect, not a valid pairing",
                ratio < 4.5
            )
        }
    }

    /** WCAG 2.x relative luminance of an sRGB color. */
    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = max(la, lb)
        val darker = min(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** HSV hue angle in degrees (0-360) of an sRGB color. */
    private fun hueDegrees(color: Color): Double {
        val r = color.red.toDouble()
        val g = color.green.toDouble()
        val b = color.blue.toDouble()
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

    /** Hue mapped into (-180, 180] so red sits at ~0 and distances stay small. */
    private fun signedHue(color: Color): Double {
        val hue = hueDegrees(color)
        return if (hue > 180.0) hue - 360.0 else hue
    }

    private fun angularDistance(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360.0
        return min(diff, 360.0 - diff)
    }
}
