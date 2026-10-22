package com.naviveylin.auto

import com.naviveylin.core.CarMapViewportProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The car surface's published search distance reference (spec: `search-result-ranking` — Distance
 * reference for ordering and display).
 *
 * A car map screen publishes the center of its last drawn frame as it stops drawing, so a search
 * opened from a surface that shows no map still measures from the map the driver last saw. These
 * cases pin the publish half: which value is taken from the renderer, and what a screen that drew
 * nothing does.
 */
class CarMapViewportTest {

    private val provider = CarMapViewportProvider()

    @Test
    fun theLastDrawnFrameCenterIsPublished() {
        val renderer = mockk<AutoMapRenderer>()
        every { renderer.markerViewport() } returns (51.5136 to 7.4653)

        publishCarViewportCenter(renderer, provider)

        assertEquals(51.5136 to 7.4653, provider.center.value)
    }

    @Test
    fun aScreenThatDrewNothingKeepsTheCenterAlreadyPublished() {
        provider.publish(51.5136, 7.4653)

        publishCarViewportCenter(null, provider)

        assertEquals(
            "a map that never drew must not erase the center of the map that did",
            51.5136 to 7.4653,
            provider.center.value
        )
    }

    @Test
    fun aCenterThatIsNotALocationIsDropped() {
        provider.publish(51.5136, 7.4653)

        provider.publish(Double.NaN, 7.5)
        assertEquals(51.5136 to 7.4653, provider.center.value)

        provider.publish(51.6, Double.POSITIVE_INFINITY)
        assertEquals(51.5136 to 7.4653, provider.center.value)
    }

    @Test
    fun noCarMapHasDrawnYetMeansNoCenter() {
        assertNull(provider.center.value)
    }
}
