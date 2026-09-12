package com.naviveylin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the [AmbientLightPipeline] — hysteresis + debounce over a sequence of
 * lux readings with a deterministic clock (spec: dark-mode "Ambient light
 * sensor option" — no flapping near the threshold).
 */
class AmbientLightPipelineTest {

    private class FakeClock {
        var now = 0L
        fun tick(ms: Long) {
            now += ms
        }
    }

    private fun pipeline(clock: FakeClock): AmbientLightPipeline =
        AmbientLightPipeline(nowMs = { clock.now })

    private fun pipeline(clock: FakeClock, sensitivity: AmbientLightSensitivity): AmbientLightPipeline =
        AmbientLightPipeline(sensitivity = sensitivity, nowMs = { clock.now })

    @Test
    fun darkEntryAfterDebounce() {
        val clock = FakeClock()
        val p = pipeline(clock)

        // Bright start.
        assertEquals(false, p.onLux(200f))
        // Sudden dark: classification flips immediately but must hold the window.
        assertNull(p.onLux(5f))
        clock.tick(DEBOUNCE_MS)
        assertEquals(true, p.onLux(5f))
    }

    @Test
    fun transientDarkIsDiscarded() {
        val clock = FakeClock()
        val p = pipeline(clock)

        assertEquals(false, p.onLux(200f))
        // Brief shadow (hand over sensor) shorter than the debounce window.
        assertNull(p.onLux(5f))
        clock.tick(5_000)
        assertNull(p.onLux(200f)) // back to light before the window elapsed
        clock.tick(DEBOUNCE_MS)
        assertNull(p.onLux(200f)) // no flip ever reported
    }

    @Test
    fun hysteresisHoldsInBand() {
        val clock = FakeClock()
        val p = pipeline(clock)

        assertEquals(false, p.onLux(200f))
        // 30 lux is in the hysteresis band: below LIGHT_LUX, above DARK_LUX.
        assertNull(p.onLux(30f))
        clock.tick(DEBOUNCE_MS)
        assertNull(p.onLux(30f)) // still light — band holds previous state
    }

    @Test
    fun flipBackIsDebouncedAgain() {
        val clock = FakeClock()
        val p = pipeline(clock)

        assertEquals(false, p.onLux(200f))
        assertNull(p.onLux(5f))
        clock.tick(DEBOUNCE_MS)
        assertEquals(true, p.onLux(5f))

        // Back to light: must hold the window again.
        assertNull(p.onLux(200f))
        clock.tick(DEBOUNCE_MS)
        assertEquals(false, p.onLux(200f))
    }

    @Test
    fun unchangedClassificationReportsNull() {
        val clock = FakeClock()
        val p = pipeline(clock)

        assertEquals(false, p.onLux(200f))
        assertNull(p.onLux(180f)) // still light, no flip
        assertNull(p.onLux(300f)) // still light, no flip
    }

    @Test
    fun mediumLevelDoesNotEnterDarkAtHighBoundaryLux() {
        val clock = FakeClock()
        // 6 lux is dark under HIGH (below 10) but light under MEDIUM (above 5).
        val high = pipeline(clock, AmbientLightSensitivity.HIGH)

        assertEquals(false, high.onLux(6f)) // bright-start classification
        assertNull(high.onLux(6f))          // pending debounce
        clock.tick(DEBOUNCE_MS)
        assertEquals(true, high.onLux(6f))  // HIGH flips dark after the window

        // Same readings under MEDIUM stay light.
        val clock2 = FakeClock()
        val medium = pipeline(clock2, AmbientLightSensitivity.MEDIUM)
        assertEquals(false, medium.onLux(6f))
        clock2.tick(DEBOUNCE_MS)
        assertNull(medium.onLux(6f)) // still light at 6 lux under MEDIUM
    }
}
