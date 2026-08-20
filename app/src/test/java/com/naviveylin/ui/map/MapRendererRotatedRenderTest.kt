package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import android.graphics.Bitmap
import android.graphics.Canvas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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

    /** Wait until a new FrameState is emitted (or fail after 5s); returns its bitmap. */
    private fun awaitNextFrame(): Bitmap? = runBlocking {
        withTimeout(5000) {
            val initial = renderer.frameFlow.value
            while (renderer.frameFlow.value === initial) {
                delay(10)
            }
            renderer.frameFlow.value.bitmap
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

    @Test
    fun repeatedEmissionReusesBitmap() {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        renderer.requestRender(51.5, 7.5, 14, 0.0)
        val first = awaitNextFrame()
        assertNotNull(first)

        // Same front buffer, marker cleared → the emitted bitmap must be reused,
        // not copied again (frame emission only on change). clearGpsMarkerState
        // emits synchronously, so read the frame directly.
        renderer.clearGpsMarkerState()
        val second = renderer.frameFlow.value.bitmap
        assertSame(first, second)
    }

    @Test
    fun newRenderProducesNewBitmap() {
        renderer.screenWidth = 200
        renderer.screenHeight = 300
        renderer.requestRender(51.5, 7.5, 14, 0.0)
        val first = awaitNextFrame()
        assertNotNull(first)

        // A new render replaces the front buffer → the emitted bitmap must be a
        // fresh copy, not the previous frame.
        renderer.requestRender(51.6, 7.6, 14, 0.0)
        val second = awaitNextFrame()
        assertNotSame(first, second)
    }

    @Test
    fun drawBitmapBlitCopiesPixels() {
        val src = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        src.eraseColor(0xFF336699.toInt())
        val dst = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        dst.eraseColor(0)
        Canvas(dst).drawBitmap(src, 0f, 0f, null)
        assertEquals(0xFF336699.toInt(), dst.getPixel(2, 2))
        // drawBitmap copies pixels into dst's own storage — src can be recycled
        // without corrupting dst (no backing-storage sharing).
        src.recycle()
        assertEquals(0xFF336699.toInt(), dst.getPixel(2, 2))
    }
}
