package com.naviveylin.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.naviveylin.test.ComposeSandboxRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The marker's off-screen branch SHALL draw one cue clamped to the viewport edge in the vehicle's
 * direction instead of nothing (spec: gps-location-marker — "Marker hidden when off-screen"; issue
 * #725, decision A).
 *
 * The cue is a draw-time value with no semantics node, so this case measures the pixels it produces:
 * with native graphics the Robolectric view hierarchy rasterizes, and the frame the overlay draws into
 * is read back as a bitmap. `LocalDensity provides Density(1f)`, so `dp` is `px` and the geometry stays
 * deterministic; the frame is drawn over one flat land colour, so any non-land pixel inside the edge
 * band is the cue.
 *
 * On HEAD the overlay draws nothing for an off-screen position — every pixel of the frame is the land
 * colour — so the case fails with an empty band.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocationMarkerEdgeCueTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private companion object {
        const val LAT = 48.8566
        const val LON = 2.3522
        const val MAG = 14.0
        const val DPI = 320.0

        /** Frame side in px (density 1 in this tree). */
        const val FRAME_PX = 300

        const val TAG = "edge-cue"

        /** Mid grey, so the cue's casing and core both separate from the land. */
        const val LAND = 0xFF3A3A3A.toInt()

        /** The cue's drawn radius in px (density 1 tree) — `CUE_RADIUS_DP` in the overlay. */
        const val CUE_RADIUS_PX = 12

        /** The right-side control column the phone/foldable map band reserves, in px. */
        const val RIGHT_COLUMN_PX = 200

        /** Channel distance that separates a drawn cue pixel from the flat land behind it. */
        const val CUE_DISTANCE = 30

        /** The vehicle is placed ~10° east of the viewport centre: far outside the frame. */
        const val OFF_SCREEN_LON = LON + 10.0
    }

    private data class Frame(val bitmap: Bitmap, val left: Int, val top: Int, val side: Int) {
        override fun toString(): String =
            "Frame raster=${bitmap.width}x${bitmap.height} origin=($left,$top) side=$side"
    }

    /** Renders one overlay frame with the vehicle far off the east edge and reads it back. */
    private fun renderOffScreenFrame(): Frame {
        val viewport = MapRenderer.RenderViewport(lat = LAT, lon = LON, mag = MAG, angle = 0.0)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                CueFrame(viewport)
            }
        }
        composeRule.waitForIdle()

        val bounds = composeRule.onAllNodesWithContentDescription(TAG)
            .fetchSemanticsNodes().single().boundsInRoot
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            maxOf(view.width, 1), maxOf(view.height, 1), Bitmap.Config.ARGB_8888
        )
        view.draw(Canvas(bitmap))
        val frame = Frame(
            bitmap,
            bounds.left.roundToInt(),
            bounds.top.roundToInt(),
            bounds.width.roundToInt()
        )
        println(frame)
        return frame
    }

    @Composable
    private fun CueFrame(viewport: MapRenderer.RenderViewport) {
        Box(
            Modifier
                .size(FRAME_PX.dp)
                .background(Color(LAND))
                .semantics { contentDescription = TAG }
        ) {
            LocationMarkerOverlay(
                lat = LAT,
                lon = OFF_SCREEN_LON,
                bearing = 90.0,
                accuracy = 0.0,
                viewport = viewport,
                dpi = DPI,
                dark = false
            )
        }
    }

    private fun channelDistance(a: Int, b: Int): Int =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
            abs((a and 0xFF) - (b and 0xFF))

    /** The frame's own pixels, in the raster's absolute coordinates, inside the vertical middle band. */
    private fun bandPixels(frame: Frame, xFrom: Int, xTo: Int, yFrom: Int, yTo: Int): List<Int> {
        val out = ArrayList<Int>()
        for (x in xFrom..xTo) {
            for (y in yFrom..yTo) {
                out.add(frame.bitmap.getPixel(frame.left + x, frame.top + y))
            }
        }
        return out
    }

    @Test
    fun theOffScreenVehicleDrawsACueAtTheEdge() {
        val frame = renderOffScreenFrame()
        assertTrue(
            "the frame must rasterize at $FRAME_PX px (density 1 tree)",
            frame.side == FRAME_PX
        )

        // The cue's centre sits one radius inside the east edge, on the frame's vertical middle:
        // (FRAME_PX - CUE_RADIUS_PX, FRAME_PX / 2). The window covers the whole glyph around it.
        val x0 = FRAME_PX - 3 * CUE_RADIUS_PX
        val x1 = FRAME_PX - 1
        val y0 = FRAME_PX / 2 - 2 * CUE_RADIUS_PX
        val y1 = FRAME_PX / 2 + 2 * CUE_RADIUS_PX
        val drawn = bandPixels(frame, x0, x1, y0, y1).count { channelDistance(it, LAND) >= CUE_DISTANCE }

        assertTrue(
            "an off-screen vehicle must draw an edge cue: no cue pixel in " +
                "x=$x0..$x1 y=$y0..$y1 of the $FRAME_PX px frame (land=#${LAND.toUInt().toString(16)})",
            drawn > 0
        )
    }

    /** The viewport the placement cases project against: 1080 x 2400 px, density 1, no inset. */
    private val placementViewport =
        MapRenderer.RenderViewport(lat = LAT, lon = LON, mag = MAG, angle = 0.0)

    @Test
    fun theCueSitsOnTheEdgeTowardTheVehicle() {
        val w = 1080.0
        val h = 2400.0
        val fix = projectMarkerRaw(LAT, OFF_SCREEN_LON, placementViewport, w, h, DPI)
        assertNotNull("the off-screen fix must still project", fix)
        assertNull(
            "the marker itself must stay culled for an off-screen fix",
            projectMarker(LAT, OFF_SCREEN_LON, placementViewport, w, h, DPI)
        )

        val cue = edgeCuePlacement(fix!!, w, h, radiusPx = CUE_RADIUS_PX.toDouble())
        assertNotNull("an off-screen position must place a cue", cue)
        assertEquals(
            "the cue sits one radius inside the east edge",
            w - CUE_RADIUS_PX,
            cue!!.center.x.toDouble(),
            1.0
        )
        assertEquals(
            "the cue sits on the frame's vertical middle",
            h / 2.0,
            cue.center.y.toDouble(),
            1.0
        )
        assertEquals(
            "the cue points east — 90° in the arrow convention (0° is up)",
            90.0,
            cue.pointingDegrees.toDouble(),
            1.0
        )
    }

    @Test
    fun theCueClearsTheColumnInsetBounds() {
        // Foldable-width frame with the phone's reserved right-side control column.
        val w = 900.0
        val h = 1200.0
        val fix = projectMarkerRaw(LAT, OFF_SCREEN_LON, placementViewport, w, h, DPI)!!
        val cue = edgeCuePlacement(
            fix, w, h,
            insetRightPx = RIGHT_COLUMN_PX,
            radiusPx = CUE_RADIUS_PX.toDouble()
        )
        assertNotNull("the cue must be placed inside the usable area", cue)
        val lastUsableX = (w - RIGHT_COLUMN_PX - CUE_RADIUS_PX).toFloat()
        assertTrue(
            "the cue must sit left of the reserved ${RIGHT_COLUMN_PX}px column: " +
                "x=${cue!!.center.x} must be <= $lastUsableX",
            cue.center.x <= lastUsableX + 0.01f
        )
        assertTrue(
            "the cue's whole extent must stay inside the frame: x=${cue.center.x}",
            cue.center.x - CUE_RADIUS_PX >= 0f && cue.center.x + CUE_RADIUS_PX <= lastUsableX + CUE_RADIUS_PX
        )
    }

    @Test
    fun theOnScreenMarkerPlacesNoCue() {
        val w = 1080.0
        val h = 2400.0
        val inside = projectMarkerRaw(LAT, LON, placementViewport, w, h, DPI)!!
        assertNull(
            "an on-screen position places no cue — the marker itself is drawn",
            edgeCuePlacement(inside, w, h, radiusPx = CUE_RADIUS_PX.toDouble())
        )
    }
}
