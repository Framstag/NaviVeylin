package com.naviveylin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Guard for the daylight map palette contract (spec `daylight-map-palette` and
 * the daylight route requirement of spec `route-appearance`, change
 * `daylight-palette-route-and-roads`).
 *
 * Why this test exists: way labels are drawn *after* way fills
 * (`MapPainter` render step `DrawLabels` (22) after `DrawWays` (12)) and street
 * `WAY.TEXT` carries no halo, so a street label lying on a route or on a road
 * is painted on top of that fill and its readability is exactly the contrast
 * between the label colour and the fill. The palette shipped the opposite once:
 * an opaque `#7b1fa2` route gave a black label 2.56:1 and the `#4440ec`
 * motorway gave 3.19:1. The thresholds below are the spec's, so a future colour
 * edit that re-breaks legibility fails here instead of on a device.
 *
 * The colors are read from the **packaged** stylesheets (what the APK ships,
 * i.e. the libosmscout submodule state) rather than duplicated as literals, and
 * the stylesheet's own `lighten` / `darken` expressions are evaluated the way
 * `osmscout::Color` evaluates them (`libosmscout/include/osmscout/util/Color.h`:
 * a plain lerp toward white / black that preserves alpha).
 *
 * Style coverage: the styles that use the blue/red/orange road-class scheme —
 * `standard` and `winter-sports`. `cycle` is deliberately excluded: its
 * daylight motorway is `#bbbbbb`, a desaturated cycling palette that does not
 * use this scheme (see the change design, Non-Goals).
 *
 * JNI-free by design: no `FakeOSMScoutClient` / `OSMScoutClient` touch, no
 * `@Config` — the default Robolectric sandbox only, so it cannot fall foul of
 * the "stub .so already loaded in another classloader" rule.
 */
@RunWith(RobolectricTestRunner::class)
class DaylightPaletteContrastTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // ---------------------------------------------------------------- route

    @Test
    fun daylightRouteCasingIsOpaqueAndTheFillMayBeTranslucent() {
        val route = paletteOf("include/route.oss")
        val fill = route.color("routeColor")
        val casing = route.color("routeCasingColor")

        assertEquals(
            "the daylight casing must be opaque: it is what keeps the composited route " +
                "centre independent of the road underneath it",
            1.0,
            casing.alpha,
            0.0
        )
        assertTrue(
            "the daylight route fill is expected to be translucent (alpha < 1), was ${fill.alpha}",
            fill.alpha < 1.0
        )
    }

    @Test
    fun daylightRouteKeepsBlackStreetLabelsLegible() {
        val route = paletteOf("include/route.oss")
        val label = paletteOf("standard.oss", LAND_SEA).color("wayLabelColor")
        val core = routeCore(route)
        val contrast = contrast(label, core)

        assertTrue(
            "black street label on the composited route core $core is ${fmt(contrast)}:1, " +
                "needs >= ${fmt(TEXT_MIN)}:1 — the label is drawn on top of the route",
            contrast >= TEXT_MIN
        )
    }

    @Test
    fun daylightRouteCentreIsIndependentOfTheRoadUnderneath() {
        val route = paletteOf("include/route.oss")
        val fill = route.color("routeColor")
        val casing = route.color("routeCasingColor")
        val expected = fill.over(casing)

        // The extremes plus every road class the scheme colours, plus water.
        val backgrounds = mutableListOf(WHITE, BLACK, paletteOf(LAND_SEA).color("waterColor"))
        for ((_, palette) in schemeStyles()) {
            for (name in ROAD_CLASSES) backgrounds += palette.color("${name}Color")
        }

        for (road in backgrounds) {
            val core = fill.over(casing.over(road))
            assertEquals(
                "the route centre must not depend on the road underneath: over $road it is " +
                    "$core, over the casing alone it is $expected — the casing must stay opaque",
                expected,
                core
            )
        }
    }

    @Test
    fun daylightRouteCentreReadsAgainstItsCasing() {
        val route = paletteOf("include/route.oss")
        val core = routeCore(route)
        val casing = route.color("routeCasingColor")
        val edge = contrast(core, casing)

        assertTrue(
            "the route centre $core must be lighter than its casing $casing so the casing " +
                "still reads as a border",
            luminance(core) > luminance(casing)
        )
        assertTrue(
            "the route centre against its casing is ${fmt(edge)}:1, needs >= ${fmt(EDGE_MIN)}:1",
            edge >= EDGE_MIN
        )
    }

    @Test
    fun daylightRouteFillHueStaysClearOfEveryRoadClass() {
        val route = paletteOf("include/route.oss")
        val core = routeCore(route)

        for ((style, palette) in schemeStyles()) {
            for (name in ROAD_CLASSES) {
                val road = palette.color("${name}Color")
                val gap = hueDistance(core, road)
                assertTrue(
                    "$style: the route hue (${hueOf(core)} deg) is only $gap deg from " +
                        "${name}Color (${hueOf(road)} deg), needs >= $HUE_MIN deg",
                    gap >= HUE_MIN
                )
            }
        }
    }

    // ----------------------------------------------------------- road fills

    @Test
    fun daylightRoadFillsKeepBlackStreetLabelsLegible() {
        for ((style, palette) in schemeStyles()) {
            val label = palette.color("wayLabelColor")
            for (name in ROAD_CLASSES) {
                val fill = palette.color("${name}Color")
                val contrast = contrast(label, fill)
                assertTrue(
                    "$style: black way label on ${name}Color $fill is ${fmt(contrast)}:1, " +
                        "needs >= ${fmt(TEXT_MIN)}:1",
                    contrast >= TEXT_MIN
                )
            }
        }
    }

    // -------------------------------------------------------------- shields

    @Test
    fun daylightShieldTextIsLegibleOnItsOwnDarkerConstant() {
        for ((style, palette) in schemeStyles()) {
            for (name in SHIELD_CLASSES) {
                val fill = palette.color("${name}Color")
                val shield = palette.color("${name}ShieldColor")
                val text = contrast(WHITE, shield)
                assertTrue(
                    "$style: white shield text on ${name}ShieldColor $shield is ${fmt(text)}:1, " +
                        "needs >= ${fmt(TEXT_MIN)}:1",
                    text >= TEXT_MIN
                )
                assertTrue(
                    "$style: ${name}ShieldColor must differ from ${name}Color $fill so the " +
                        "shield still reads as an object on the road",
                    shield != fill
                )
                val separation = contrast(shield, fill)
                assertTrue(
                    "$style: ${name}ShieldColor $shield is only ${fmt(separation)}:1 against " +
                        "${name}Color $fill, needs >= ${fmt(SHIELD_SEPARATION_MIN)}:1",
                    separation >= SHIELD_SEPARATION_MIN
                )
            }
        }
    }

    // --------------------------------------------------- low-zoom hairlines

    @Test
    fun daylightLowZoomColoursStayVisibleOnLand() {
        for ((style, palette) in schemeStyles()) {
            val land = palette.color("landColor")
            for (name in THIN_CLASSES) {
                val thin = palette.color("thin${capitalized(name)}Color")
                val fill = palette.color("${name}Color")
                val onLand = contrast(thin, land)
                assertTrue(
                    "$style: thin${capitalized(name)}Color $thin on land $land is ${fmt(onLand)}:1, " +
                        "needs >= ${fmt(HAIRLINE_MIN)}:1",
                    onLand >= HAIRLINE_MIN
                )
                val hueGap = hueDistance(thin, land)
                assertTrue(
                    "$style: thin${capitalized(name)}Color $thin is only $hueGap deg from land $land " +
                        "in hue, needs >= $HUE_MIN deg — a hairline at ${fmt(onLand)}:1 luminance " +
                        "contrast relies on its hue to stay visible",
                    hueGap >= HUE_MIN
                )
                assertTrue(
                    "$style: thin${capitalized(name)}Color $thin must stay lighter than ${name}Color $fill, " +
                        "so both renderings still read as the same road class",
                    luminance(thin) > luminance(fill)
                )
            }
        }
    }

    // ------------------------------------------------------- junction label

    @Test
    fun daylightJunctionLabelIsLegibleOnLandAndCarriesTheHalo() {
        for ((style, palette) in schemeStyles()) {
            val land = palette.color("landColor")
            val label = palette.color("motorwayJunctionLabelColor")
            val onLand = contrast(label, land)
            assertTrue(
                "$style: motorwayJunctionLabelColor $label on land $land is ${fmt(onLand)}:1, " +
                    "needs >= ${fmt(JUNCTION_MIN)}:1",
                onLand >= JUNCTION_MIN
            )
        }

        // The spec requires the halo, not just the colour: the label is drawn over
        // varying land cover, where its own contrast alone is not enough.
        val junctionLine = raw("stylesheets/$ROADS").lineSequence()            .firstOrNull { "@motorwayJunctionLabelColor" in it }
        assertTrue(
            "the motorway junction label style must be found in $ROADS",
            junctionLine != null
        )
        assertTrue(
            "the motorway junction label must carry the emphasis halo: $junctionLine",
            junctionLine!!.contains("style: emphasize")
        )
    }

    // ------------------------------------------------------------- plumbing

    /** The composited colour of the route's centre: the fill over its opaque casing. */
    private fun routeCore(route: Palette): Rgba = route.color("routeColor").over(route.color("routeCasingColor"))

    private fun schemeStyles(): List<Pair<String, Palette>> = listOf(
        "standard.oss" to paletteOf("standard.oss", LAND_SEA),
        "winter-sports.oss" to paletteOf("winter-sports.oss")
    )

    private fun raw(assetPath: String): String =
        context.assets.open(assetPath).use { it.readBytes().decodeToString() }

    /**
     * A stylesheet's resolved daylight colours. Declarations are collected in the
     * given asset order and the **first** declaration of a name wins, which is the
     * `IF daylight` branch: every colour that branches on the presentation is
     * declared inside `IF daylight { ... }` before the `ELSE { ... }` block.
     */
    private fun paletteOf(vararg assets: String): Palette {
        val declarations = linkedMapOf<String, String>()
        for (asset in assets) {
            for ((name, expression) in colorLines("stylesheets/$asset")) {
                declarations.putIfAbsent(name, expression)
            }
        }
        return Palette(assets.joinToString(), declarations)
    }

    private fun colorLines(assetPath: String): List<Pair<String, String>> {
        val text = raw(assetPath)
        return COLOR_DECLARATION.findAll(text).map { it.groupValues[1] to it.groupValues[2] }.toList()
    }

    private class Palette(
        private val description: String,
        private val declarations: Map<String, String>
    ) {
        private val cache = mutableMapOf<String, Rgba>()

        fun color(name: String): Rgba = cache.getOrPut(name) {
            val expression = declarations[name]
                ?: throw AssertionError("$description declares no COLOR $name")
            resolve(expression)
        }

        private fun resolve(expression: String): Rgba {
            val e = expression.trim()
            if (e.startsWith("#")) return parseHex(e)
            if (e.startsWith("@")) return color(e.drop(1))

            val lerp = LERP.matchEntire(e)
                ?: throw AssertionError("$description: unsupported colour expression '$e'")
            val base = resolve(lerp.groupValues[2])
            val factor = lerp.groupValues[3].toDouble()
            return when (lerp.groupValues[1]) {
                "lighten" -> base.lighten(factor)
                "darken" -> base.darken(factor)
                else -> throw AssertionError("$description: unknown colour function '${lerp.groupValues[1]}'")
            }
        }
    }

    /** A colour as the stylesheets express it: `#rrggbb` (opaque) or `#rrggbbaa`. */
    private data class Rgba(val r: Int, val g: Int, val b: Int, val alpha: Double = 1.0) {
        /** `osmscout::Color::Lighten` — lerp toward white, alpha preserved. */
        fun lighten(factor: Double) = Rgba(
            (r + (255 - r) * factor).toInt(),
            (g + (255 - g) * factor).toInt(),
            (b + (255 - b) * factor).toInt(),
            alpha
        )

        /** `osmscout::Color::Darken` — lerp toward black, alpha preserved. */
        fun darken(factor: Double) = Rgba(
            (r - r * factor).toInt(),
            (g - g * factor).toInt(),
            (b - b * factor).toInt(),
            alpha
        )

        /** Source-over compositing: this colour drawn on top of [background]. */
        fun over(background: Rgba) = Rgba(
            (r * alpha + background.r * (1 - alpha)).toInt(),
            (g * alpha + background.g * (1 - alpha)).toInt(),
            (b * alpha + background.b * (1 - alpha)).toInt(),
            1.0
        )

        override fun toString() = "#%02x%02x%02x".format(r, g, b) +
            if (alpha < 1.0) "@%.2f".format(alpha) else ""
    }

    private companion object {
        const val LAND_SEA = "include/land_sea_color.oss"
        const val ROADS = "include/roads.oss"

        val WHITE = Rgba(255, 255, 255)
        val BLACK = Rgba(0, 0, 0)

        val ROAD_CLASSES = listOf("motorway", "trunk", "primary", "secondary", "tertiary")
        val SHIELD_CLASSES = listOf("motorway", "trunk", "primary")
        val THIN_CLASSES = listOf("motorway", "trunk", "primary")

        /** `motorway` -> `Motorway`, for the constants that capitalise the class name. */
        fun capitalized(roadClass: String) = roadClass.replaceFirstChar { it.uppercase() }

        /** Text: black way labels on a fill, white shield text on a shield. */
        const val TEXT_MIN = 4.5

        /**
         * A hairline is not text, so it gets a lower bar than [TEXT_MIN]. 1.6 is above the
         * palette's own convention rather than a relaxation of it: `thinSecondaryColor`
         * ships at 1.22:1 against land and `thinTertiaryColor` at 1.08:1. The new trunk fill
         * is itself only 2.02:1 against land, so no variant *lighter than the fill* can reach
         * 2.0 — which is why this floor is paired with the [HUE_MIN] hue check.
         */
        const val HAIRLINE_MIN = 1.6

        /** The shield must still look like an object sitting on the road. */
        const val SHIELD_SEPARATION_MIN = 2.0

        /** The junction label carries a white glyph halo on top of this. */
        const val JUNCTION_MIN = 1.9

        /** The route's rim must read as a border, not be swallowed by the fill. */
        const val EDGE_MIN = 1.25

        /** The route's hue must not be a road class hue. */
        const val HUE_MIN = 25.0

        val COLOR_DECLARATION =
            Regex("""^\s*COLOR\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([^;]+);""", RegexOption.MULTILINE)
        val LERP = Regex("""^(lighten|darken)\((.+),\s*([0-9.]+)\)$""")
        val HEX = Regex("""^#([0-9a-fA-F]{6})([0-9a-fA-F]{2})?$""")

        fun parseHex(literal: String): Rgba {
            val match = HEX.matchEntire(literal)
                ?: throw AssertionError("unsupported colour literal '$literal'")
            val rgb = match.groupValues[1]
            val alpha = match.groupValues[2]
            return Rgba(
                rgb.substring(0, 2).toInt(16),
                rgb.substring(2, 4).toInt(16),
                rgb.substring(4, 6).toInt(16),
                if (alpha.isEmpty()) 1.0 else alpha.toInt(16) / 255.0
            )
        }

        private fun channelLuminance(channel: Int): Double {
            val s = channel / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }

        fun luminance(colour: Rgba): Double =
            0.2126 * channelLuminance(colour.r) +
                0.7152 * channelLuminance(colour.g) +
                0.0722 * channelLuminance(colour.b)

        /** WCAG contrast ratio, 1.0 (identical) to 21.0 (black on white). */
        fun contrast(a: Rgba, b: Rgba): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        }

        fun hueOf(colour: Rgba): Double {
            val r = colour.r / 255.0
            val g = colour.g / 255.0
            val b = colour.b / 255.0
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min
            if (delta == 0.0) return 0.0
            val hue = when (max) {
                r -> 60 * (((g - b) / delta) % 6)
                g -> 60 * (((b - r) / delta) + 2)
                else -> 60 * (((r - g) / delta) + 4)
            }
            return if (hue < 0) hue + 360 else hue
        }

        /** Shortest angular distance between two hues, in degrees. */
        fun hueDistance(a: Rgba, b: Rgba): Double {
            val raw = abs(hueOf(a) - hueOf(b)) % 360
            return min(raw, 360 - raw)
        }

        fun fmt(value: Double): String = "%.2f".format(value)
    }
}
