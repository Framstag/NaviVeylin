package com.naviveylin.auto

import android.graphics.Canvas
import android.view.Surface
import com.framstag.libosmscout.client.FakeAutoRenderClient
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule

/**
 * The harness every [AutoMapRenderer] case class shares: one fake render client, one tracked
 * renderer, the teardown rule, the fix position and the surface mock.
 *
 * The cases were a single 2 099-line `AutoMapRendererTest` until the file crossed the `source-size`
 * row's bound (1 500 lines per tracked file, `tools/sensors/sensors.json`; item #300). They are now
 * grouped by what they hold down — each group a class beside this one — and the split is the whole
 * delta: the same case names, the same assertions, the same harness.
 */
abstract class AutoMapRendererTestSupport {

    /** Shuts [renderer] down after every test: the renderer's loops start in `init`
     *  and would otherwise outlive the test (change `fix-auto-unit-test-heap-overflow`,
     *  `TODO.md` §33). */
    @get:Rule
    val renderers = RendererTestRule()

    protected lateinit var client: FakeAutoRenderClient
    protected lateinit var renderer: AutoMapRenderer

    @Before
    fun setUp() {
        client = FakeAutoRenderClient()
        renderer = renderers.newRenderer(client)
    }

    /** The fix most cases drive from; a case that moves the vehicle picks its own. */
    protected val fixLat = 51.5142273
    protected val fixLon = 7.4652789

    /** A surface whose `lockCanvas` answers a relaxed canvas, as a renderer needs one. */
    protected fun mockSurface(): Pair<Surface, Canvas> {
        val surface = mockk<Surface>(relaxed = true)
        val canvas = mockk<Canvas>(relaxed = true)
        every { surface.lockCanvas(any()) } returns canvas
        every { surface.isValid } returns true
        return surface to canvas
    }
}
