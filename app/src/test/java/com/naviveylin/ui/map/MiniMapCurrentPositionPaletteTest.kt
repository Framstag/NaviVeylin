package com.naviveylin.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.naviveylin.core.GpsMarkerPalette
import com.naviveylin.test.ComposeSandboxRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The mini map's current-position marker SHALL follow the resolved presentation and keep its dot clear
 * of the object-marker colours (spec: mini-map — "Current-position marker palette and distinctness";
 * change `fix-map-overlay-dark-palette`).
 *
 * The marker is drawn on a Compose canvas with no semantics node, so this case measures the pixels it
 * produces: with native graphics the Robolectric view hierarchy rasterizes, and the dot's own annulus
 * is the set of pixels painted with the palette's dot colour exactly. One tree carries both
 * presentations (`LocalDensity provides Density(1f)`, so `dp` is `px`).
 *
 * It draws [drawCurrentPositionMarker] — the widget's own draw call, extracted for exactly this reason —
 * rather than composing `MiniMap`, because the widget needs an `OSMScoutClient` and therefore loads the
 * host JNI stub, and a stub-loading class must run in the default Robolectric sandbox: it may not pin a
 * graphics mode (`guidelines/Build.md` §6, `JniStubSandboxRuleTest`). The widget's own mapping from the
 * resolved flag to these four colours is asserted by
 * [theWidgetsMarkerColoursComeFromThePalette], which needs no renderer.
 *
 * Before the change the dot was `0xFF1A73E8` — literally `ui/theme/Color.kt`'s `PrimaryLight`, i.e. the
 * value `MiniMap` hands the primary object marker as `MaterialTheme.colorScheme.primary` — so both drawn
 * cases below fail on HEAD.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiniMapCurrentPositionPaletteTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private companion object {
        const val FRAME_PX = 150

        /** The widget's marker radius at density 1: `with(LocalDensity.current) { 9.dp.toPx() }`. */
        const val MARKER_RADIUS_PX = 9f

        const val LIGHT_TAG = "mini-map-marker-light"
        const val DARK_TAG = "mini-map-marker-dark"

        /** The pre-change dot, i.e. the app's fixed light scheme's `primary` (`Color.kt:6`). */
        const val PRIMARY_LIGHT = 0xFF1A73E8.toInt()

        /** Mid grey land, the same base for both frames. */
        const val LAND = 0xFF3A3A3A.toInt()
    }

    private data class Frames(val bitmap: Bitmap, val lightBounds: Rect, val darkBounds: Rect)

    private fun renderBothPresentations(): Frames {
        composeRule.setContent {
            Row {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    MarkerFrame(LIGHT_TAG, dark = false)
                    MarkerFrame(DARK_TAG, dark = true)
                }
            }
        }
        composeRule.waitForIdle()

        val bounds = listOf(LIGHT_TAG, DARK_TAG).associateWith { tag ->
            composeRule.onAllNodesWithContentDescription(tag).fetchSemanticsNodes().single().boundsInRoot
        }
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            maxOf(view.width, 1), maxOf(view.height, 1), Bitmap.Config.ARGB_8888
        )
        view.draw(Canvas(bitmap))

        fun rect(tag: String): Rect = bounds.getValue(tag).let { b ->
            Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
        }
        val light = rect(LIGHT_TAG)
        val dark = rect(DARK_TAG)
        assertEquals("both frames rasterize at $FRAME_PX px (density 1 tree)", FRAME_PX, light.width())
        assertEquals(FRAME_PX, dark.width())
        return Frames(bitmap, light, dark)
    }

    @Composable
    private fun MarkerFrame(tag: String, dark: Boolean) {
        Box(
            Modifier
                .size(FRAME_PX.dp)
                .background(Color(LAND))
                .semantics { contentDescription = tag }
        ) {
            ComposeCanvas(Modifier.fillMaxSize()) {
                drawCurrentPositionMarker(
                    center = Offset(size.width / 2f, size.height / 2f),
                    markerRadiusPx = MARKER_RADIUS_PX,
                    colors = miniMapMarkerColors(isDarkPresentation = dark)
                )
            }
        }
    }

    /** How many pixels of [rect] are painted with exactly [argb]. */
    private fun Frames.count(argb: Int, rect: Rect): Int {
        val px = IntArray(rect.width() * rect.height())
        bitmap.getPixels(px, 0, rect.width(), rect.left, rect.top, rect.width(), rect.height())
        return px.count { it == argb }
    }

    @Test
    fun theDrawnDotChangesWithThePresentation() {
        val frames = renderBothPresentations()
        val lightDot = miniMapMarkerColors(isDarkPresentation = false).dot.toArgb()
        val darkDot = miniMapMarkerColors(isDarkPresentation = true).dot.toArgb()
        val lightCount = frames.count(lightDot, frames.lightBounds)
        val darkCount = frames.count(darkDot, frames.darkBounds)
        val lightIsDarkTone = frames.count(darkDot, frames.lightBounds)
        val darkIsLightTone = frames.count(lightDot, frames.darkBounds)
        println(
            "MiniMap dot: light frame has $lightCount px of the light dot and $lightIsDarkTone px of the dark dot; " +
                "dark frame has $darkCount px of the dark dot and $darkIsLightTone px of the light dot"
        )

        assertTrue("the light frame must draw the dot in the light tone ($lightCount px)", lightCount > 0)
        assertTrue("the dark frame must draw the dot in the dark tone ($darkCount px)", darkCount > 0)
        assertEquals("the light frame must not draw the dot in the dark tone", 0, lightIsDarkTone)
        assertEquals("the dark frame must not draw the dot in the light tone", 0, darkIsLightTone)
    }

    @Test
    fun theDrawnDotIsNotTheObjectMarkerPrimary() {
        val frames = renderBothPresentations()
        val lightDot = miniMapMarkerColors(isDarkPresentation = false).dot.toArgb()
        val lightCount = frames.count(lightDot, frames.lightBounds)
        val primaryCount = frames.count(PRIMARY_LIGHT, frames.lightBounds)
        println(
            "MiniMap dot: the light frame has $lightCount px of the palette dot (0x${Integer.toHexString(lightDot)}) " +
                "and $primaryCount px of the object marker's primary (0x${Integer.toHexString(PRIMARY_LIGHT)})"
        )

        assertTrue("the light frame must draw the palette's dot ($lightCount px)", lightCount > 0)
        assertEquals(
            "the dot must not be drawn in the object-marker primary the palette replaced",
            0,
            primaryCount
        )
        assertNotEquals(
            "the palette's light dot must not be the object marker's primary",
            PRIMARY_LIGHT,
            lightDot
        )
    }

    @Test
    fun theWidgetsMarkerColoursComeFromThePalette() {
        // The mapping the widget applies to its resolved flag — asserted without a renderer, because
        // composing MiniMap needs the JNI stub and so may not pin a graphics mode.
        for (dark in listOf(false, true)) {
            val colors = miniMapMarkerColors(isDarkPresentation = dark)
            assertEquals(
                "the dot comes from the shared palette",
                Color(GpsMarkerPalette.positionDot(dark)),
                colors.dot
            )
            assertEquals(
                "the inner highlight comes from the shared palette",
                Color(GpsMarkerPalette.positionDotCore(dark)),
                colors.core
            )
            assertEquals(
                "the halo comes from the shared palette",
                Color(GpsMarkerPalette.positionDotHalo(dark)),
                colors.halo
            )
            assertEquals(
                "the casing comes from the shared palette",
                Color(GpsMarkerPalette.positionDotCasing(dark)),
                colors.casing
            )
        }
        assertNotEquals(
            "the whole marker colour set must branch on the presentation",
            miniMapMarkerColors(isDarkPresentation = false),
            miniMapMarkerColors(isDarkPresentation = true)
        )
        println(
            "MiniMap colours: light=${miniMapMarkerColors(false)} dark=${miniMapMarkerColors(true)}"
        )
    }
}
