package com.naviveylin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guard for the packaged stylesheets: every `#RRGGBB` / `#RRGGBBAA` color
 * literal must use LOWERCASE hex digits.
 *
 * Why this test exists: `osmscout::Color::FromHexString` documents
 * "@param hexString (lowercase)" and `GetHexValue()` only accepts `0-9` and
 * `a-f` — anything else trips `assert(false)`. An uppercase literal therefore
 * fails the whole module load, libosmscout reports "Cannot load module", the
 * stylesheet is rejected, and the app then crashes in native code
 * (`StyleConfig::HasNodeTextStyles`, SIGSEGV) because the renderer keeps going
 * with a rejected style config. That happened in change
 * `map-marker-route-contrast`, where the route colors were first written as
 * `#7B1FA2` / `#311B92`.
 *
 * The test reads the merged assets (what the APK ships, i.e. the libosmscout
 * submodule state), so it covers every stylesheet and include, not just the one
 * that was edited.
 *
 * JNI-free by design: no `FakeOSMScoutClient` / `OSMScoutClient` touch, no
 * `@Config` — the default Robolectric sandbox only.
 */
@RunWith(RobolectricTestRunner::class)
class StylesheetHexColorCaseTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun everyPackagedHexColorLiteralIsLowercase() {
        val files = packagedStylesheets()
        assertTrue("no packaged stylesheets found", files.isNotEmpty())

        val offenders = mutableListOf<String>()
        var literals = 0
        for (path in files) {
            val text = context.assets.open(path).use { it.readBytes().decodeToString() }
            text.lineSequence().forEachIndexed { index, line ->
                for (match in HEX_COLOR.findAll(line)) {
                    val literal = match.value
                    literals++
                    if (literal != literal.lowercase()) {
                        offenders += "$path:${index + 1}: $literal"
                    }
                }
            }
        }

        assertTrue("no hex color literals found in the packaged stylesheets", literals > 0)
        assertTrue(
            "uppercase hex color literal(s) break the stylesheet load " +
                "(osmscout::Color::FromHexString accepts 0-9a-f only): $offenders",
            offenders.isEmpty()
        )
    }

    /**
     * Pins the two route colors introduced by `map-marker-route-contrast` in the
     * shared route include, in their lowercase spelling, so the day/night branch
     * and its casing constant stay wired to the stylesheet the app ships.
     */
    @Test
    fun sharedRouteIncludeCarriesThePresentationColors() {
        val route = context.assets.open("stylesheets/include/route.oss")
            .use { it.readBytes().decodeToString() }

        assertTrue("route include must branch on the daylight flag", route.contains("IF daylight"))
        assertTrue("daylight route fill missing", route.contains("#7b1fa2"))
        assertTrue("daylight route casing missing", route.contains("#311b92"))
        assertTrue("route casing must be a constant, not an inline white", route.contains("@routeCasingColor"))
        assertTrue("dark presentation keeps the red fill", route.contains("#ff000088"))
    }

    /**
     * Every packaged `.oss` / `.ost` asset path, including the `include/` tree.
     * The asset manager lists a directory's direct children only, so the two
     * levels used by the stylesheet tree are walked explicitly (the same pattern
     * `AssetCopier` handles).
     */
    private fun packagedStylesheets(): List<String> {
        val root = "stylesheets"
        val paths = mutableListOf<String>()
        for (name in context.assets.list(root).orEmpty()) {
            if (name.endsWith(".oss") || name.endsWith(".ost")) {
                paths += "$root/$name"
            }
        }
        for (name in context.assets.list("$root/include").orEmpty()) {
            if (name.endsWith(".oss") || name.endsWith(".ost")) {
                paths += "$root/include/$name"
            }
        }
        return paths.sorted()
    }

    private companion object {
        val HEX_COLOR = Regex("#[0-9A-Fa-f]{6,8}")
    }
}
