package com.naviveylin.ui.navigation

import android.util.Log
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.ManoeuvreLabels
import com.naviveylin.core.guidanceStep
import com.naviveylin.core.manoeuvreFromStep
import com.naviveylin.core.stringResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The next-next row as the driver sees it, on the device (spec: `next-turn-overlay` — "A roundabout exit
 * is announced before the ring is entered", "Next-next turn text SHALL use smaller typography").
 *
 * `NextNextHintDeviceTest` proves that real map data yields an announced entry whose hint is the leave;
 * this case renders *that* record through the real overlay and measures the row: the hint arrow is the
 * 36 dp node, its ink is present (the glyph is drawn as the `ManeuverSymbols` bitmap, which Robolectric's
 * canvas discards and no JVM case can rasterise), and the row's wording names the exit the route takes.
 */
@RunWith(AndroidJUnit4::class)
class NextTurnHintRowDeviceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun step(
        kind: RouteStep.Kind,
        distanceFromStart: Double,
        name: String = "",
        exit: Int = 0
    ) = RouteStep().apply {
        this.kind = kind
        this.distanceFromStart = distanceFromStart
        if (name.isNotBlank()) this.name = RouteStep.Name(name, "")
        if (exit > 0) {
            roundaboutLeave = RouteStep.RoundaboutLeave().apply { exitCountTaken = exit.toLong() }
        }
    }

    @Test
    fun theHintRowIsTheNumberedExitAtTheHintSize() {
        // The shape real data produced in `NextNextHintDeviceTest`: entry, a facts-only ring node, the leave.
        val route = listOf(
            step(RouteStep.Kind.ROUNDABOUT_ENTER, 1000.0, "Kreisverkehr"),
            step(RouteStep.Kind.INFORMATIVE, 1010.0, "Ringstraße"),
            step(RouteStep.Kind.ROUNDABOUT_LEAVE, 1035.0, "Bahnhofstraße", exit = 2),
            step(RouteStep.Kind.INFORMATIVE, 1050.0)
        )
        val published = step(RouteStep.Kind.INFORMATIVE, 1000.0, "Kreisverkehr").apply {
            remainingDistance = 0.0
        }

        val announced = guidanceStep(published, route)
        val hint = announced.nextNext
        assertTrue("the approach announces the exit as its hint", hint != null)

        composeRule.setContent { NextTurnOverlay(step = announced) }

        val arrow = composeRule.onNodeWithTag("nextTurnNextNextArrow").assertExists()
            .fetchSemanticsNode()
        val expectedPx = with(composeRule.density) { 36.dp.roundToPx() }
        assertEquals("the hint arrow is the 36 dp node", expectedPx, arrow.size.width)
        assertEquals("the hint arrow is the 36 dp node", expectedPx, arrow.size.height)

        // The row words the manoeuvre the route takes, with its ordinal.
        val label = ManoeuvreLabels.short(context.stringResolver(), manoeuvreFromStep(hint!!))
        composeRule.onNodeWithText(label).assertExists()
        assertTrue("the hint's wording names the exit ($label)", label.contains("2"))

        // The glyph is a rasterised bitmap: measure that the row drew ink, and record the box.
        val image = composeRule.onNodeWithTag("nextTurnNextNextArrow").captureToImage()
        val pixels = image.toPixelMap()
        var ink = 0
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                if (pixels[x, y].alpha > 0.1f) {
                    ink++
                    minX = minOf(minX, x); maxX = maxOf(maxX, x)
                    minY = minOf(minY, y); maxY = maxOf(maxY, y)
                }
            }
        }
        assertTrue("the hint glyph drew ink", ink > 0)
        Log.i(
            TAG,
            "hint row: arrow=${arrow.size.width}x${arrow.size.height}px (36 dp, density=" +
                "${composeRule.density.density}) ink=$ink box=${maxX - minX + 1}x${maxY - minY + 1}px" +
                " exit=${hint.roundaboutLeave?.exitCountTaken} label=$label"
        )
    }

    private companion object {
        const val TAG = "NextTurnHintRowDevice"
    }
}
