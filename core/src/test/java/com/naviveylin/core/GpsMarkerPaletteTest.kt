package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Plain-JUnit tests for the overlay palette (spec: `dark-mode` — "Which app-drawn elements stay
 * visible in dark presentation"; `gps-location-marker` — "Accuracy circle follows the resolved
 * presentation"; `mini-map` — "Current-position marker palette and distinctness"; change
 * `fix-map-overlay-dark-palette`).
 *
 * The palette is the whole point of the change: the accuracy ring was one module-level literal in
 * both presentations (phone `#4A90D9`, car `#2196F3`), and the mini-map dot was literally the
 * theme's light `primary` (`#1A73E8`), so it collided with the primary object marker. These cases
 * pin the properties that were missing — a branch per value, a dark tone of the ring's own hue
 * family, a dot outside the object-marker roles, and a dot that stays the brightest part of its own
 * marker — instead of asserting bytes, so the exact tones stay tunable.
 *
 * No Robolectric and no Compose needed: the palette is a pure function of the presentation flag and
 * the values are ARGB in the low 32 bits.
 */
class GpsMarkerPaletteTest {

    /**
     * The fixed colour schemes' role values (`app/src/main/java/com/naviveylin/ui/theme/Color.kt`
     * and `Theme.kt`): the colours the mini map hands its primary, additional and selected object
     * markers. `Color.kt` is app-side, so the values are restated here — that is the point of the
     * case (the dot must not be one of them).
     */
    private val objectMarkerRoles = mapOf(
        "primary light (#1A73E8)" to 0xFF1A73E8L,
        "secondary light (#5F6368)" to 0xFF5F6368L,
        "tertiary light (#7D5260)" to 0xFF7D5260L,
        "primary dark (#8AB4F8)" to 0xFF8AB4F8L,
        "secondary dark (#C4C7C5)" to 0xFFC4C7C5L,
        "tertiary dark (#EFB8C8)" to 0xFFEFB8C8L
    )

    @Test
    fun everyOverlayColourBranchesOnThePresentation() {
        // Spec scenario "No overlay keeps a light literal while dark presentation is active".
        val light = GpsMarkerPalette.values(dark = false)
        val dark = GpsMarkerPalette.values(dark = true)

        assertEquals("six palette values are enumerated", 6, light.size)
        light.zip(dark).forEachIndexed { i, (l, d) ->
            assertNotEquals(
                "palette value #$i must follow the presentation (light=${argb(l)}, dark=${argb(d)})",
                argb(l),
                argb(d)
            )
        }
        // …and the two presentations are not merely permutations of each other.
        assertEquals("no light value is reused in dark presentation", 0, light.count { it in dark })
    }

    @Test
    fun theDarkRingIsADarkerToneOfTheSameHueFamily() {
        // Spec scenario "An overlay that carries no nav-essential information dims": the ring is not
        // navigation-essential, so its dark tone keeps the hue family and drops the tone.
        for ((what, pair) in listOf(
            "fill" to (GpsMarkerPalette.accuracyFill(false) to GpsMarkerPalette.accuracyFill(true)),
            "border" to (GpsMarkerPalette.accuracyBorder(false) to GpsMarkerPalette.accuracyBorder(true))
        )) {
            val (lightPx, darkPx) = pair
            val hueDelta = angularDistance(hueDegrees(lightPx), hueDegrees(darkPx))
            val lightLum = luminance(lightPx)
            val darkLum = luminance(darkPx)
            println(
                "accuracy $what light=${argb(lightPx)} hue=${hueDegrees(lightPx)}" +
                    " lum=${"%.4f".format(java.util.Locale.ROOT, lightLum)}" +
                    " dark=${argb(darkPx)} hue=${hueDegrees(darkPx)}" +
                    " lum=${"%.4f".format(java.util.Locale.ROOT, darkLum)}"
            )
            assertTrue(
                "the dark $what must stay in the light tone's hue family (drifted $hueDelta°)",
                hueDelta <= 20.0
            )
            assertTrue(
                "the dark $what must be a darker tone (light $lightLum, dark $darkLum)",
                darkLum < lightLum
            )
        }
    }

    @Test
    fun theLightDotIsNotAnObjectMarkerColour() {
        // Spec scenario "The dot's hue is not an object-marker colour": the dot used to be exactly
        // the theme's light primary (#1A73E8), i.e. the primary object marker's colour.
        for (dark in listOf(false, true)) {
            val dot = GpsMarkerPalette.positionDot(dark)
            for ((name, role) in objectMarkerRoles) {
                assertNotEquals(
                    "the dot (dark=$dark, ${argb(dot)}) must not be the object marker's $name",
                    role,
                    dot
                )
            }
        }
    }

    @Test
    fun theDotIsBrighterThanItsHaloInDarkPresentation() {
        // Spec scenario "The dot stays visible in dark presentation": the dot is the essential part
        // of the marker, so in dark presentation it is brighter than its own halo over dark land.
        val land = 0xFF121212L // the dark scheme's background
        val dot = luminance(GpsMarkerPalette.positionDot(dark = true))
        val halo = luminance(composite(GpsMarkerPalette.positionDotHalo(dark = true), land))
        println("dark presentation dot lum=$dot halo-over-land lum=$halo")
        assertTrue(
            "the dot ($dot) must be brighter than its halo over dark land ($halo)",
            dot > halo + 0.1
        )
        // …and the same holds in light presentation, where the halo must not swallow the dot.
        val lightLand = 0xFFF1F3F4L // the light scheme's surface variant
        val lightDot = luminance(GpsMarkerPalette.positionDot(dark = false))
        val lightHalo = luminance(composite(GpsMarkerPalette.positionDotHalo(dark = false), lightLand))
        println("light presentation dot lum=$lightDot halo-over-land lum=$lightHalo")
        assertTrue(
            "the light dot ($lightDot) must be distinguishable from its halo ($lightHalo)",
            abs(lightDot - lightHalo) > 0.05
        )
    }

    // ---- helpers ---------------------------------------------------------------------------

    private fun argb(value: Long): String = "0x" + value.toString(16).uppercase().padStart(8, '0')

    private fun channel(value: Long, shift: Int): Int = ((value shr shift) and 0xFF).toInt()

    /** WCAG 2.x relative luminance of an ARGB value, its alpha ignored (it composites elsewhere). */
    private fun luminance(value: Long): Double =
        0.2126 * linear(channel(value, 16)) + 0.7152 * linear(channel(value, 8)) + 0.0722 * linear(channel(value, 0))

    private fun linear(srgb: Int): Double {
        val c = srgb / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /** Alpha-composites a translucent ARGB value over an opaque one. */
    private fun composite(over: Long, under: Long): Long {
        val a = ((over shr 24) and 0xFF).toInt()
        fun mix(shift: Int): Long {
            val top = channel(over, shift)
            val bottom = channel(under, shift)
            return ((top * a + bottom * (255 - a) + 127) / 255).toLong()
        }
        return 0xFF000000L or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** HSV hue angle in degrees (0-360) of an ARGB value. */
    private fun hueDegrees(value: Long): Double {
        val r = channel(value, 16).toDouble()
        val g = channel(value, 8).toDouble()
        val b = channel(value, 0).toDouble()
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
}
