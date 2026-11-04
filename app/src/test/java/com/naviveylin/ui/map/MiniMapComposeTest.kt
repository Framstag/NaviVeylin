package com.naviveylin.ui.map

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.naviveylin.test.ComposeSandboxRule
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
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

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * The applier's record is keyed on the client, so a fresh client starts clean; clearing it anyway
     * keeps a case from inheriting a value another case recorded on a reused instance.
     */
    @Before
    fun resetDaylightRecord() {
        MapDaylightApplier.reset()
    }

    /** The index of the first native render in [FakeOSMScoutClient.variantCalls], or -1. */
    private fun firstRenderIndex(client: FakeOSMScoutClient): Int =
        client.variantCalls.indexOfFirst { it == "render" }

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
        isDarkPresentation: Boolean = false,
        onViewportChanged: ((lat: Double, lon: Double, mag: Double) -> Unit)? = null,
        onStyleLoadFailure: ((String) -> Unit)? = null
    ) {
        composeRule.setContent {
            MiniMap(
                client = client,
                lat = lat,
                lon = lon,
                initialMag = mag,
                modifier = Modifier.size(200.dp),
                isDarkPresentation = isDarkPresentation,
                onViewportChanged = onViewportChanged,
                onStyleLoadFailure = onStyleLoadFailure
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

    @Test
    fun darkPresentationPushesTheDarkDaylightFlag() {
        val client = FakeOSMScoutClient()
        launch(client, isDarkPresentation = true)

        awaitMag(client, 12.0)

        assertEquals(
            "the first frame is rendered under the pushed dark variant",
            listOf<Boolean?>(false),
            client.daylightAtRender.distinct()
        )
        assertEquals(
            "the mini map pushes the dark stylesheet variant",
            listOf(MapCanvasViewModel.STYLE_FLAG_DAYLIGHT to false),
            client.styleFlags.toList()
        )
        assertEquals(
            "the push is the widget's first native call",
            "flag:${MapCanvasViewModel.STYLE_FLAG_DAYLIGHT}=false",
            client.variantCalls.firstOrNull()
        )
    }

    @Test
    fun lightPresentationPushesTheDaylightFlag() {
        val client = FakeOSMScoutClient()
        launch(client, isDarkPresentation = false)

        awaitMag(client, 12.0)

        // The ordering half, and the reason these two cases exist: the first frame the widget
        // renders has to be rendered under the pushed variant. The renderer requests frames
        // asynchronously, so native call order alone cannot show it — the fake records the flag
        // value in effect at each render, and a push that lands after the first frame leaves a null
        // (or previous) entry there.
        assertEquals(
            "every frame is rendered under the pushed light variant",
            listOf<Boolean?>(true),
            client.daylightAtRender.distinct()
        )
        assertEquals(
            "the mini map pushes the light stylesheet variant, once",
            listOf(MapCanvasViewModel.STYLE_FLAG_DAYLIGHT to true),
            client.styleFlags.toList()
        )
        assertEquals(
            "the push is the widget's first native call",
            "flag:${MapCanvasViewModel.STYLE_FLAG_DAYLIGHT}=true",
            client.variantCalls.firstOrNull()
        )
    }

    @Test
    fun aClientThatAlreadyCarriesTheVariantIsNotReloaded() {
        // The main map (or an earlier mount of this widget) already applied the dark variant: the
        // applier's record says so, so mounting the widget must not reload the stylesheet set —
        // that reload is the cost guidelines/MapStyling.md §15 budgets (one per changed value).
        val client = FakeOSMScoutClient()
        MapDaylightApplier.record(client, true)

        launch(client, isDarkPresentation = true)
        awaitMag(client, 12.0)

        assertEquals(
            "no variant push for a value the client already carries",
            emptyList<Pair<String, Boolean>>(),
            client.styleFlags.toList()
        )
        assertTrue("the widget still requested a frame", firstRenderIndex(client) >= 0)
    }

    @Test
    fun aRejectedVariantReloadIsReported() {
        val client = FakeOSMScoutClient()
        client.styleLoadSuccessful = false
        var reported: String? = null

        launch(client, isDarkPresentation = true, onStyleLoadFailure = { reported = it })
        awaitMag(client, 12.0)

        assertNotNull(
            "a rejected variant reload is reported to the embedding surface (spec: map-styles — " +
                "\"Style flag change fails\")",
            reported
        )
    }

    @Test
    fun presentationChangeReRendersTheMiniMap() {
        val client = FakeOSMScoutClient()
        var dark by mutableStateOf(false)
        composeRule.setContent {
            MiniMap(
                client = client,
                lat = 51.5,
                lon = 7.4,
                initialMag = 12.0,
                isDarkPresentation = dark,
                modifier = Modifier.size(200.dp)
            )
        }
        awaitMag(client, 12.0)
        // Let the first frame's render calls drain before sampling: only a render caused by
        // the presentation change may raise the count past this value.
        composeRule.waitForIdle()
        runBlocking { delay(300) }
        val rendersBefore = client.renderCount.get() + client.renderWithRouteAndPoisCount.get()

        composeRule.runOnIdle { dark = true }
        composeRule.waitForIdle()
        assertEquals(
            "the presentation change pushes the dark variant",
            MapCanvasViewModel.STYLE_FLAG_DAYLIGHT to false,
            client.styleFlags.last()
        )

        runBlocking {
            withTimeout(10_000) {
                while (client.renderCount.get() + client.renderWithRouteAndPoisCount.get() <= rendersBefore) {
                    delay(10)
                }
            }
        }
        assertEquals(
            "the presentation change re-renders the mini map with the dark variant",
            MapCanvasViewModel.STYLE_FLAG_DAYLIGHT to false,
            client.styleFlags.last()
        )
    }
}
