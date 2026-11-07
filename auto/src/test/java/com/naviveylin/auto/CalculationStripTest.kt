package com.naviveylin.auto

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The app-drawn strip that reports a route calculation on the navigation surface (spec:
 * `route-calculation-feedback` — A route calculation during navigation is drawn on the map
 * surface; `auto-map-layout` — The route-calculation strip has one place on the navigation
 * surface; `auto/navigation-view` — Reroute keeps the navigation view on screen).
 *
 * The strip replaced a pushed `PaneTemplate`: a reroute kept taking the map away, and the renderer
 * paused and re-attached its surface on the way back. These cases pin the wait's appearance, its
 * bounded placement, its palette and the fact that it is drawing, not a control.
 */
@RunWith(RobolectricTestRunner::class)
class CalculationStripTest {

    private val density = 2.0f
    private val stable = Rect(320, 240, 1600, 900)
    private val surfaceWidth = 1920
    private val surfaceHeight = 900

    private val wait = "Calculating route…"
    private val waitPercent = "Calculating route… %1\$d%%"
    private val failed = "Recalculation failed"

    private fun label(snapshot: CalculationStrip.Snapshot): String =
        CalculationStrip.label(snapshot, wait, waitPercent, failed)

    private fun strip(snapshot: CalculationStrip.Snapshot): Rect? =
        CalculationStrip.geometry(label(snapshot), stable, surfaceWidth, surfaceHeight, density)

    private fun navigating(percent: Int? = null) =
        CalculationStrip.Snapshot(phase = CalculationStrip.Phase.WAITING, percent = percent)

    @Before
    fun setUp() {
        CalculationStrip.resetForTest()
    }

    // --- what the strip says ---

    @Test
    fun theStripAppearsWithTheCalculation() {
        val snapshot = navigating(percent = 42)

        val rect = strip(snapshot)

        assertNotNull("the strip has a place on the surface", rect)
        assertTrue("and states the wait", label(snapshot).isNotEmpty())
    }

    @Test
    fun theStripStatesTheWaitWithoutAPercent() {
        val text = label(navigating(percent = null))

        assertEquals(wait, text)
        assertFalse("no percentage before one is reported", text.contains("%"))
    }

    @Test
    fun theStripShowsTheReportedPercent() {
        assertEquals(
            "42 % is displayed in the 5 % step, like the cold-start notice",
            "Calculating route… 40%",
            label(navigating(percent = 42))
        )
        assertEquals("Calculating route… 100%", label(navigating(percent = 100)))
    }

    @Test
    fun aPercentChangeBelowTheStepKeepsTheStripUnchanged() {
        assertEquals(
            "a percentage inside the same step cannot change what the driver sees",
            label(navigating(percent = 41)),
            label(navigating(percent = 40))
        )
        assertNotEquals(
            "the next step is a new display",
            label(navigating(percent = 44)),
            label(navigating(percent = 45))
        )
    }

    @Test
    fun theWaitEndsWithTheCalculation() {
        val idle = CalculationStrip.Snapshot()
        val canvas = mockk<Canvas>(relaxed = true)

        assertEquals("nothing to say", "", label(idle))
        assertNull("and nothing to draw", strip(idle))
        CalculationStrip.draw(canvas, surfaceWidth, surfaceHeight, density, stable, false, idle, "")
        verify(exactly = 0) { canvas.drawText(any(), any(), any(), any()) }
        verify(exactly = 0) { canvas.drawRoundRect(any<RectF>(), any(), any(), any()) }
    }

    @Test
    fun aNewCalculationReplacesTheFailureReport() {
        assertEquals(failed, label(CalculationStrip.Snapshot(phase = CalculationStrip.Phase.FAILED)))
        assertEquals(
            "a retried calculation is the wait again, not the failure",
            wait,
            label(navigating(percent = null))
        )
        assertNotEquals(
            label(CalculationStrip.Snapshot(phase = CalculationStrip.Phase.FAILED)),
            label(navigating(percent = null))
        )
    }

    @Test
    fun theStripIsTheSurfaceTextAndCarriesNoStreetName() {
        // The surface carries the calculation strip and no street name: the ETA card owns the
        // street (spec: `auto/navigation-view` — No street-name label on the map surface).
        val text = label(navigating(percent = 40))
        val canvas = mockk<Canvas>(relaxed = true)

        CalculationStrip.draw(
            canvas,
            surfaceWidth,
            surfaceHeight,
            density,
            stable,
            darkPresentation = false,
            snapshot = navigating(percent = 40),
            label = text
        )

        assertFalse("the strip is not the street label", text.contains("Hauptstrasse"))
        verify(exactly = 1) { canvas.drawText(text, any(), any(), any()) }
        verify(exactly = 0) { canvas.drawText("Hauptstrasse", any(), any(), any()) }
    }

    // --- where it sits ---

    @Test
    fun theStripSitsInsideTheStableArea() {
        val rect = strip(navigating(percent = 40))!!

        assertTrue("left inside: ${rect.left}", rect.left >= stable.left)
        assertTrue("top inside: ${rect.top}", rect.top >= stable.top)
        assertTrue("right inside: ${rect.right}", rect.right <= stable.right)
        assertTrue("bottom inside: ${rect.bottom}", rect.bottom <= stable.bottom)
    }

    @Test
    fun theStripClearsTheIndicatorColumnAndTheAttribution() {
        val rect = strip(navigating(percent = 40))!!
        val indicators = SurfaceIndicators.geometry(
            usableBounds = stable,
            surfaceWidth = surfaceWidth,
            density = density,
            showSpeed = true,
            showSpeedLimit = true
        )
        val attribution = SurfaceAttribution.geometry(
            usableBounds = stable,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            density = density,
            text = "© OpenStreetMap contributors"
        )!!

        assertFalse(
            "clear of the compass rose",
            Rect.intersects(
                rect,
                Rect(
                    (indicators.compassCenterX - indicators.compassRadius).toInt(),
                    (indicators.compassCenterY - indicators.compassRadius).toInt(),
                    (indicators.compassCenterX + indicators.compassRadius).toInt(),
                    (indicators.compassCenterY + indicators.compassRadius).toInt()
                )
            )
        )
        assertFalse("clear of the speed badge", Rect.intersects(rect, indicators.badgeRect!!))
        assertFalse("clear of the speed-limit sign", Rect.intersects(rect, indicators.speedLimitRect!!))
        assertFalse("clear of the attribution", Rect.intersects(rect, attribution))
    }

    @Test
    fun theStripPaletteFollowsTheAppliedPresentation() {
        val day = CalculationStrip.palette(darkPresentation = false, failed = false)
        val night = CalculationStrip.palette(darkPresentation = true, failed = false)

        assertNotEquals("day and night do not share a chip", day.chip, night.chip)
        assertNotEquals("nor a text colour", day.text, night.text)

        val canvas = mockk<Canvas>(relaxed = true)
        val chip = slot<Paint>()
        CalculationStrip.draw(
            canvas,
            surfaceWidth,
            surfaceHeight,
            density,
            stable,
            darkPresentation = true,
            snapshot = navigating(percent = 40),
            label = label(navigating(percent = 40))
        )
        verify { canvas.drawRoundRect(any<RectF>(), any(), any(), capture(chip)) }

        assertEquals("the dark variant's chip", night.chip, chip.captured.color)
    }

    @Test
    fun theStripInstallsNoGestureHandling() {
        // A surface element takes no taps: the AAOS template host does not forward surface gestures
        // (spec: `auto-map-layout` — The strip is not a control).
        val gestureMethods = CalculationStrip::class.java.declaredMethods.filter { method ->
            method.name.contains("tap", ignoreCase = true) ||
                method.name.contains("touch", ignoreCase = true) ||
                method.name.contains("click", ignoreCase = true) ||
                method.name.contains("gesture", ignoreCase = true) ||
                method.parameterTypes.any { it.name == "android.view.MotionEvent" }
        }

        assertTrue("the strip installs no gesture handling: $gestureMethods", gestureMethods.isEmpty())
    }
}
