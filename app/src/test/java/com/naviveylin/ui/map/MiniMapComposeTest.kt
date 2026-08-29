package com.naviveylin.ui.map

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.FakeOSMScoutClient
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose tests for the reusable [MiniMap] widget (spec: mini-map):
 * zoom buttons visible, zoom-in steps magnification, clamping at the app
 * limits, panning moves the center, and a frame renders for the marker.
 * No @Config — default Robolectric sandbox so the FakeOSMScoutClient JNI
 * stub loads correctly.
 */
@RunWith(RobolectricTestRunner::class)
class MiniMapComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Wait until the fake client saw a render at zoom level [expected] — the JNI
     *  boundary takes the scale factor 2^z; the fake stores it raw (continuous-pinch-zoom). */
    private fun awaitMag(client: FakeOSMScoutClient, expectedLevel: Double) = runBlocking {
        val expected = 2.0.pow(expectedLevel)
        withTimeout(10_000) {
            while (client.lastRenderMag != expected) {
                delay(10)
            }
        }
    }

    private fun launch(
        client: FakeOSMScoutClient,
        lat: Double = 51.5,
        lon: Double = 7.4,
        mag: Double = 12.0,
        onViewportChanged: ((lat: Double, lon: Double, mag: Double) -> Unit)? = null
    ) {
        composeRule.setContent {
            MiniMap(
                client = client,
                lat = lat,
                lon = lon,
                initialMag = mag,
                modifier = Modifier.size(200.dp),
                onViewportChanged = onViewportChanged
            )
        }
    }

    @Test
    fun zoomButtonsAreVisible() {
        launch(FakeOSMScoutClient())
        composeRule.onNodeWithContentDescription("Zoom in").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Zoom out").assertIsDisplayed()
    }

    @Test
    fun zoomInIncreasesMagnificationByOneStep() {
        val client = FakeOSMScoutClient()
        launch(client, mag = 12.0)
        awaitMag(client, 12.0)

        composeRule.onNodeWithContentDescription("Zoom in").performClick()

        awaitMag(client, 13.0)
    }

    @Test
    fun zoomOutDecreasesMagnificationByOneStep() {
        val client = FakeOSMScoutClient()
        launch(client, mag = 12.0)
        awaitMag(client, 12.0)

        composeRule.onNodeWithContentDescription("Zoom out").performClick()

        awaitMag(client, 11.0)
    }

    @Test
    fun zoomInClampedAtMaximumMagnification() {
        val client = FakeOSMScoutClient()
        launch(client, mag = MapCanvasViewModel.MAX_MAG)
        awaitMag(client, MapCanvasViewModel.MAX_MAG)

        composeRule.onNodeWithContentDescription("Zoom in").performClick()
        composeRule.waitForIdle()
        runBlocking { delay(500) } // let any (unexpected) render land

        assertTrue(client.lastRenderMag == 2.0.pow(MapCanvasViewModel.MAX_MAG))
    }

    @Test
    fun zoomOutClampedAtMinimumMagnification() {
        val client = FakeOSMScoutClient()
        launch(client, mag = MapCanvasViewModel.MIN_MAG)
        awaitMag(client, MapCanvasViewModel.MIN_MAG)

        composeRule.onNodeWithContentDescription("Zoom out").performClick()
        composeRule.waitForIdle()
        runBlocking { delay(500) }

        assertTrue(client.lastRenderMag == 2.0.pow(MapCanvasViewModel.MIN_MAG))
    }

    @Test
    fun panMovesTheMapCenter() {
        val client = FakeOSMScoutClient()
        val latestViewport = AtomicReference<Triple<Double, Double, Double>?>(null)
        launch(client, lat = 51.5, lon = 7.4, onViewportChanged = { l, o, m ->
            latestViewport.set(Triple(l, o, m))
        })
        awaitMag(client, 12.0)
        val initial = latestViewport.get()!!

        composeRule.onNodeWithTag("MiniMapCanvas").performTouchInput { swipeLeft() }

        runBlocking {
            withTimeout(10_000) {
                while (latestViewport.get() == initial) {
                    delay(10)
                }
            }
        }
        val moved = latestViewport.get()!!
        assertTrue("pan must move the mini map center", moved.second != initial.second)
    }

    @Test
    fun rendersFrameForMarkerDrawing() {
        val client = FakeOSMScoutClient()
        launch(client, lat = 51.5, lon = 7.4)

        awaitMag(client, 12.0)

        // A frame reached the widget (bitmap drawn, marker projected on top).
        assertTrue(client.renderWithRouteAndPoisCount.get() > 0 || client.renderCount.get() > 0)
    }
}
