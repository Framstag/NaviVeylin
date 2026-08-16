package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the full render pipeline carries a non-zero viewport angle through
 * to the front buffer (the path used when a rotation gesture ends).
 *
 * Note: the non-forced rotated render (tile-path preview with rotation) is not
 * exercised here — Robolectric's shadow Canvas hangs on rotated drawBitmap of
 * large tiles. The forced full render path (gesture end) is covered instead.
 */
@RunWith(RobolectricTestRunner::class)
class MapRendererRotatedRenderTest {

    private lateinit var renderer: MapRenderer
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        renderer = MapRenderer(
            client = FakeOSMScoutClient(),
            dpi = 320.0,
            scope = scope
        )
    }

    @After
    fun tearDown() {
        renderer.shutdown()
        scope.cancel()
    }

    /** Wait until the front buffer reports the given angle (or fail after 5s). */
    private fun awaitAngle(angle: Double) = runBlocking {
        withTimeout(5000) {
            while (renderer.renderedAngle != angle) {
                delay(10)
            }
        }
    }

    /** Wait until the front buffer reports the given magnification (or fail after 5s). */
    private fun awaitMag(mag: Int) = runBlocking {
        withTimeout(5000) {
            while (renderer.renderedMag != mag) {
                delay(10)
            }
        }
    }

    @Test
    fun northUpRenderKeepsZeroAngle() {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        renderer.requestRender(51.5, 7.5, 14, 0.0)

        // Tile path serves north-up; front buffer must still be emitted.
        awaitMag(14)
        assertEquals(0.0, renderer.renderedAngle, 1e-9)
    }

    @Test
    fun forcedFullRenderUpdatesFrontBufferAngle() {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        // Gesture-end render: force the full native path (correct labels).
        renderer.requestRender(51.5, 7.5, 14, Math.PI / 3, forceFullRender = true)

        awaitAngle(Math.PI / 3)
        assertEquals(Math.PI / 3, renderer.renderedAngle, 1e-6)
        assertEquals(14, renderer.renderedMag)
    }

    @Test
    fun forcedFullRenderAfterNorthUpUpdatesAngle() {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        renderer.requestRender(51.5, 7.5, 14, 0.0)
        awaitMag(14)

        renderer.requestRender(51.5, 7.5, 14, -Math.PI / 4, forceFullRender = true)
        awaitAngle(-Math.PI / 4)
        assertEquals(-Math.PI / 4, renderer.renderedAngle, 1e-6)
        assertEquals(14, renderer.renderedMag)
    }

    @Test
    fun largeAngleRenderNormalizesFrontBufferAngle() {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        // 450° = 90° mod 360 — the front buffer angle must be normalized.
        renderer.requestRender(51.5, 7.5, 14, 7.85, forceFullRender = true)

        awaitAngle(7.85 - 2 * Math.PI)
        assertEquals(7.85 - 2 * Math.PI, renderer.renderedAngle, 1e-6)
        assertEquals(14, renderer.renderedMag)
    }
}
