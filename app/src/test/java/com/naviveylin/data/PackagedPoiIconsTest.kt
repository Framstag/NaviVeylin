package com.naviveylin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guard for the packaged POI icon set: the stylesheets request their icons **by name**, and the
 * renderer resolves that name to `<name>.png` in the configured icon directory
 * (`MapPainterCairo::HasIcon`). An icon style whose image loads draws the image; one whose image
 * does not load falls back to the vector `symbol` the style declares inline; a style that declares
 * only a name therefore draws **nothing** (`MapPainter::LayoutPointLabels`).
 *
 * The gate is on the request that can only be satisfied by an image — a `name:` without a
 * `symbol:` — against the icons actually packaged in this APK. `TODO.md` §85 is the defect it
 * prevents: ten raster requests, no icon directory configured, and no icon set packaged at all.
 *
 * The symbol-backed requests without a PNG stay legal on purpose (spec `map-render` — A
 * symbol-backed icon request needs no raster icon): upstream ships `charging_station` and
 * `mini_roundabout` as SVG only, and the Cairo renderer loads PNG only (`MapPainterCairo`: "TODO:
 * add support for reading svg images"). They draw their inline symbol, and they are asserted here
 * as *not required*, so a future reader does not mistake their failed-load line for this defect.
 *
 * The test reads the merged assets — what the APK ships, i.e. the libosmscout submodule state —
 * the same way `StylesheetHexColorCaseTest` does for the stylesheet colours.
 *
 * JNI-free by design: no `FakeOSMScoutClient` / `OSMScoutClient` touch, no `@Config` — the default
 * Robolectric sandbox only.
 */
@RunWith(RobolectricTestRunner::class)
class PackagedPoiIconsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun everyRasterOnlyIconRequestHasAPackagedPng() {
        val requests = rasterRequests()

        // A broken parser must not make this gate vacuous: the shipped stylesheets carry ~185 live
        // ICON styles (measured 2026-09-29), so anything near zero means the parse failed.
        assertTrue(
            "implausibly few ICON styles parsed (${requests.size}) — the gate would be vacuous",
            requests.size >= 150
        )

        val shapeless = requests.filter { it.name == null && !it.hasSymbol }
        assertTrue(
            "an ICON style with neither a name nor a symbol draws nothing: $shapeless",
            shapeless.isEmpty()
        )

        val required = requests.filterNot { it.hasSymbol }.mapNotNull { it.name }.distinct().sorted()
        assertTrue("no raster-only icon request found — the gate would be vacuous", required.isNotEmpty())

        val packaged = packagedIconNames()
        assertTrue("no icons packaged under ${IconAssets.RASTER_LEAF}", packaged.isNotEmpty())

        val missing = required.filterNot { it in packaged }
        assertTrue(
            "an ICON style that declares only a name draws nothing when its PNG is missing " +
                "(spec map-render — A raster-only icon request has a packaged PNG). Missing: $missing",
            missing.isEmpty()
        )
    }

    @Test
    fun symbolBackedRequestsWithoutAPngAreNotRequired() {
        val requests = rasterRequests()
        val packaged = packagedIconNames()

        // Documented exceptions (design D5): raster-requested, symbol-backed, upstream SVG only.
        for (name in SYMBOL_BACKED_WITHOUT_PNG) {
            val request = requests.firstOrNull { it.name == name }
            assertTrue("$name is no longer an ICON request in the packaged stylesheets", request != null)
            assertTrue(
                "$name must be symbol-backed — with neither a symbol nor a PNG it would draw nothing",
                request?.hasSymbol == true
            )
            assertTrue(
                "$name is packaged now — remove it from the documented exception list",
                name !in packaged
            )
        }
    }

    @Test
    fun theRasterLeafIsPackagedAndResolvable() {
        val names = packagedIconNames()

        assertTrue("the raster leaf must be packaged: ${IconAssets.RASTER_LEAF}", names.isNotEmpty())
        assertTrue("expected the whole packaged raster set, not a fragment: $names", names.size >= 20)

        // Each name must resolve through the loader's own path shape — this is also what proves the
        // leaf is flat: a nested file would be listed under its basename and fail here.
        for (name in names) {
            context.assets.open(IconAssets.assetPathOf(name)).close()
        }
    }

    /** Every `ICON` style in the packaged stylesheets: its icon name and whether it has a symbol. */
    private fun rasterRequests(): List<IconRequest> {
        val requests = mutableListOf<IconRequest>()
        for (path in packagedStylesheets()) {
            val text = context.assets.open(path).use { it.readBytes().decodeToString() }
            requests += parseIconStyles(stripComments(text))
        }
        return requests
    }

    /**
     * The packaged raster icon names. The listing may come back flattened into the parent level
     * ("14x14/standard/bus_stop.png"), so the basename is taken — the same tolerance `AssetCopier`
     * documents for the stylesheet tree.
     */
    private fun packagedIconNames(): Set<String> =
        context.assets.list("${IconAssets.ASSET_ROOT}/${IconAssets.RASTER_LEAF}")
            .orEmpty()
            .map { it.substringAfterLast('/') }
            .filter { it.endsWith(".png") }
            .map { it.removeSuffix(".png") }
            .toSet()

    /**
     * Every packaged `.oss` / `.ost` asset path, including the `include/` tree. The asset manager
     * lists a directory's direct children only, so the tree is walked (tolerating the flattened
     * listing some implementations return — the same two forms `AssetCopier` handles).
     */
    private fun packagedStylesheets(): List<String> {
        val paths = mutableListOf<String>()

        fun walk(path: String) {
            for (name in context.assets.list(path).orEmpty()) {
                if (name.contains('/')) {
                    if (name.endsWith(".oss") || name.endsWith(".ost")) paths += "$path/$name"
                    continue
                }
                val sub = "$path/$name"
                if (context.assets.list(sub)?.isNotEmpty() == true) {
                    walk(sub)
                } else if (name.endsWith(".oss") || name.endsWith(".ost")) {
                    paths += sub
                }
            }
        }

        walk("stylesheets")
        return paths.sorted()
    }

    /**
     * Parses every `ICON { … }` style block. All forms count — `NODE.ICON { … }`, `AREA.ICON { … }`
     * and a bare `ICON { … }` inside a `SYMBOL` block each resolve an image by name. The block body
     * is read beyond the opening line, because one shipped block spreads over four lines
     * (`include/religious.oss`).
     */
    private fun parseIconStyles(text: String): List<IconRequest> {
        val requests = mutableListOf<IconRequest>()
        var searchFrom = 0
        while (true) {
            val open = ICON_BLOCK.find(text, searchFrom) ?: break
            val bodyStart = open.range.last + 1
            val bodyEnd = text.indexOf('}', bodyStart).let { if (it < 0) text.length else it }
            searchFrom = bodyStart
            val body = text.substring(bodyStart, bodyEnd)
            requests += IconRequest(
                name = ICON_NAME.find(body)?.groupValues?.get(1),
                hasSymbol = body.contains("symbol:")
            )
        }
        return requests
    }

    /** Blanks both comment forms, so a commented-out style is not mistaken for a live request. */
    private fun stripComments(text: String): String =
        text.replace(BLOCK_COMMENT, " ").replace(LINE_COMMENT, " ")

    private data class IconRequest(val name: String?, val hasSymbol: Boolean)

    private companion object {
        /** Requests whose PNG upstream ships as SVG only — legal, documented (design D5). */
        val SYMBOL_BACKED_WITHOUT_PNG = listOf("charging_station", "mini_roundabout")

        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
        val ICON_BLOCK = Regex("""\bICON[ \t]*\{""")
        val ICON_NAME = Regex("""\bname:[ \t]*([A-Za-z0-9_]+)""")
    }
}
