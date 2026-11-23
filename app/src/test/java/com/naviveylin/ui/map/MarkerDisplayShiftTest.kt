package com.naviveylin.ui.map

import com.naviveylin.core.ProjectionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

/**
 * One displayed viewport per frame: the frame's own viewport moved by the display
 * offset the frame is blitted with (spec: smooth-follow — A pan offset lands the
 * overlay on the frame's own shift; The derivation does not branch on the mode).
 *
 * The map content is drawn at `center − displayOffset`, so an overlay projected
 * against the derived viewport lands on the content it rides — in pan mode and in
 * follow mode alike. The mode selects the derivation's INPUT (the frame's own
 * anchor-centred viewport in follow, the committed viewport in pan); it does not
 * select between two projections, and no overlay carries a `graphicsLayer` shift of
 * its own.
 *
 * Plain JUnit — pure functions, no Android classes, no JNI (AGENTS.md classloader
 * rules). The derivation is reached by reflection on purpose: the case has to be RED
 * on a tree that still answers the shift with a mode test, i.e. where the function
 * does not exist and `markerDisplayShiftPx` still does.
 */
class MarkerDisplayShiftTest {

    private val screenW = 1080
    private val screenH = 1920
    private val dpi = 420.0

    @Test
    fun theOverlayShiftIsTheFramesShift() {
        val frameLat = 48.3
        val frameLon = 11.5
        val mag = 15.0
        val offsetX = 42f
        val offsetY = -17f

        val (derivedLat, derivedLon) = displayedCenter(frameLat, frameLon, mag, 0.0, offsetX, offsetY)
        val displayed = ProjectionUtils.viewport(derivedLat, derivedLon, mag, screenW, screenH, dpi, 0.0)

        // The frame's own center is drawn at `center − offset`; the overlay's
        // viewport has to reproduce exactly that one screen position.
        val (px, py) = displayed.geoToScreen(frameLat, frameLon)
        assertEquals("the frame's shift, x", screenW / 2.0 - offsetX.toDouble(), px, 0.5)
        assertEquals("the frame's shift, y", screenH / 2.0 - offsetY.toDouble(), py, 0.5)
    }

    @Test
    fun theDerivationDoesNotBranchOnTheMode() {
        val offsetX = 42f
        val offsetY = -17f
        val mag = 15.0

        // Follow feeds the derivation the frame's own anchor-centred viewport, pan the
        // committed one: two inputs, one derivation, the same offset applied once.
        val followLat = 48.30
        val followLon = 11.50
        val panLat = 48.34
        val panLon = 11.56

        val follow = derivedPosition(followLat, followLon, mag, offsetX, offsetY)
        val pan = derivedPosition(panLat, panLon, mag, offsetX, offsetY)

        assertEquals("the offset is applied once, x", follow.first, pan.first, 0.5)
        assertEquals("the offset is applied once, y", follow.second, pan.second, 0.5)

        val derivation = derivation()
        assertTrue(
            "the derivation takes no mode flag: " + derivation.parameterTypes.joinToString(),
            derivation.parameterTypes.none {
                it == java.lang.Boolean.TYPE || it == java.lang.Boolean::class.java
            }
        )
    }

    @Test
    fun theModeTestHelperIsRetired() {
        val stillThere = Class.forName(SCREEN_PARTS)
            .methods
            .filter { it.name == "markerDisplayShiftPx" }
        assertTrue(
            "markerDisplayShiftPx is gone: the shift is a viewport, not a mode test",
            stillThere.isEmpty()
        )
    }

    /** The screen position the derived viewport gives the input viewport's own centre. */
    private fun derivedPosition(lat: Double, lon: Double, mag: Double, offsetX: Float, offsetY: Float): Pair<Double, Double> {
        val (derivedLat, derivedLon) = displayedCenter(lat, lon, mag, 0.0, offsetX, offsetY)
        return ProjectionUtils.viewport(derivedLat, derivedLon, mag, screenW, screenH, dpi, 0.0)
            .geoToScreen(lat, lon)
    }

    private fun displayedCenter(
        lat: Double,
        lon: Double,
        mag: Double,
        angle: Double,
        offsetX: Float,
        offsetY: Float
    ): Pair<Double, Double> {
        val result = derivation().invoke(null, lat, lon, mag, angle, offsetX, offsetY, screenW, screenH, dpi)
        @Suppress("UNCHECKED_CAST")
        return result as Pair<Double, Double>
    }

    private fun derivation(): Method =
        Class.forName(SCREEN_PARTS)
            .methods
            .firstOrNull { it.name == "displayedViewportCenter" }
            ?: throw AssertionError(
                "no displayedViewportCenter: the overlay shift is still selected by a mode test"
            )

    private companion object {
        const val SCREEN_PARTS = "com.naviveylin.ui.map.MapCanvasScreenPartsKt"
    }
}
