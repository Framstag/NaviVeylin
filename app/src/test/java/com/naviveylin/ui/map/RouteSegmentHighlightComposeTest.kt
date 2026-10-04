package com.naviveylin.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose tests for the analysed-segment layer (spec: `route-analysis`): the layer
 * is composed exactly when a step with a highlightable range is analysed and a frame
 * is displayed, and composed not at all otherwise — so a stale highlight cannot
 * survive the end of a selection.
 */
@RunWith(RobolectricTestRunner::class)
class RouteSegmentHighlightComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val lats = doubleArrayOf(52.5200, 52.5230, 52.5260, 52.5300)
    private val lons = doubleArrayOf(13.4050, 13.4080, 13.4090, 13.4100)
    private val viewport = MapRenderer.RenderViewport(lat = 52.5250, lon = 13.4075, mag = 14.0, angle = 0.0)

    private fun launch(
        segment: IntRange? = 1..2,
        viewport: MapRenderer.RenderViewport? = this.viewport,
        dpi: Double = 480.0,
        lats: DoubleArray? = this.lats,
        lons: DoubleArray? = this.lons
    ) {
        composeRule.setContent {
            Box(modifier = Modifier.size(200.dp)) {
                RouteSegmentHighlightOverlay(
                    polylineLats = lats,
                    polylineLons = lons,
                    segment = segment,
                    viewport = viewport,
                    dpi = dpi,
                    dark = false
                )
            }
        }
    }

    @Test
    fun `an analysed step with a range draws the layer`() {
        launch()

        composeRule.onNodeWithTag(ROUTE_SEGMENT_HIGHLIGHT_TAG).assertIsDisplayed()
    }

    @Test
    fun `no analysed step draws nothing`() {
        launch(segment = null)

        composeRule.onNodeWithTag(ROUTE_SEGMENT_HIGHLIGHT_TAG).assertDoesNotExist()
    }

    @Test
    fun `a single-vertex range draws nothing`() {
        launch(segment = 2..2)

        composeRule.onNodeWithTag(ROUTE_SEGMENT_HIGHLIGHT_TAG).assertDoesNotExist()
    }

    @Test
    fun `no displayed frame draws nothing`() {
        launch(viewport = null)

        composeRule.onNodeWithTag(ROUTE_SEGMENT_HIGHLIGHT_TAG).assertDoesNotExist()
    }

    @Test
    fun `no polyline draws nothing`() {
        launch(lats = null, lons = null)

        composeRule.onNodeWithTag(ROUTE_SEGMENT_HIGHLIGHT_TAG).assertDoesNotExist()
    }

    @Test
    fun `a range beyond the polyline draws nothing`() {
        launch(segment = 2..9)

        composeRule.onNodeWithTag(ROUTE_SEGMENT_HIGHLIGHT_TAG).assertDoesNotExist()
    }
}
