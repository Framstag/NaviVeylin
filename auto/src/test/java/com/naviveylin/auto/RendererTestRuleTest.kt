package com.naviveylin.auto

import android.graphics.Canvas
import android.view.Surface
import com.framstag.libosmscout.client.FakeAutoRenderClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [RendererTestRule] (change `fix-auto-unit-test-heap-overflow`, spec
 * `unit-test-suite-runtime` — "Test-created components release their background work").
 *
 * Robolectric with the DEFAULT sandbox: the rule hands out renderers backed by
 * `FakeAutoRenderClient`, which triggers `OSMScoutClient`'s static JNI stub load
 * (`AGENTS.md` classloader rule — no `@Config`, no `@GraphicsMode` here).
 */
@RunWith(RobolectricTestRunner::class)
class RendererTestRuleTest {

    private val renderers = RendererTestRule()

    @Test
    fun teardownStopsEveryTrackedRenderer() {
        repeat(3) { renderers.newRenderer() }

        assertEquals(3, renderers.trackedCount())
        assertTrue(
            "expected the renderers' own background work to be live before the teardown",
            renderers.activeBackgroundJobCount() > 0
        )

        val failure = renderers.shutdownAll()

        assertNull(failure)
        assertEquals(0, renderers.activeBackgroundJobCount())
    }

    @Test
    fun anInTestShutdownSurvivesTheTeardown() {
        // The six in-test `renderer.shutdown()` calls in AutoMapRendererTest rely on
        // shutdown() being idempotent.
        val renderer = renderers.newRenderer()
        renderer.shutdown()

        val failure = renderers.shutdownAll()

        assertNull(failure)
        assertEquals(0, renderer.activeBackgroundJobCount())
    }

    @Test
    fun teardownReleasesTheRetainedRenderBuffer() {
        val renderer = renderers.newRenderer()
        val (surface, canvas) = mockSurface()

        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()
        assertNotNull("expected a retained overrun buffer to be released", renderer.overrunSize())

        val failure = renderers.shutdownAll()

        assertNull(failure)
        assertNull(renderer.overrunSize())
        assertEquals(0, renderer.activeBackgroundJobCount())
    }

    @Test
    fun teardownFailsWhenARendererKeepsRunningBackgroundWork() {
        // Models a production regression in which shutdown() no longer stops the
        // loops: the rule must fail the test instead of leaking silently.
        val stubborn = renderers.track(spyk(AutoMapRenderer(FakeAutoRenderClient(), initialProjectionDpi = 240.0)))
        every { stubborn.activeBackgroundJobCount() } returns 1

        val failure = renderers.shutdownAll()

        assertTrue("expected an AssertionError, got $failure", failure is AssertionError)
        assertTrue(
            "failure should name the leak: ${failure?.message}",
            failure?.message.orEmpty().contains("active background work")
        )
    }

    @Test
    fun teardownRunsAndReportsAPassingTest() {
        val renderer = renderers.newRenderer()
        val statement = renderers.apply(
            object : Statement() {
                override fun evaluate() = Unit
            },
            description("teardownRunsAndReportsAPassingTest")
        )

        statement.evaluate()

        assertEquals(0, renderer.activeBackgroundJobCount())
    }

    @Test
    fun testFailureIsPropagatedIncludingTheTeardownFailure() {
        val renderer = renderers.newRenderer()
        val boom = IllegalStateException("boom")
        val statement = renderers.apply(
            object : Statement() {
                override fun evaluate() = throw boom
            },
            description("testFailureIsPropagatedIncludingTheTeardownFailure")
        )

        val thrown = runCatching { statement.evaluate() }.exceptionOrNull()

        assertSame(boom, thrown)
        // The teardown still ran: the renderer owns no live background work.
        assertEquals(0, renderer.activeBackgroundJobCount())
    }

    private fun description(name: String): org.junit.runner.Description =
        org.junit.runner.Description.createTestDescription(RendererTestRuleTest::class.java, name)

    private fun mockSurface(): Pair<Surface, Canvas> {
        val surface = mockk<Surface>(relaxed = true)
        val canvas = mockk<Canvas>(relaxed = true)
        every { surface.lockCanvas(any()) } returns canvas
        every { surface.isValid } returns true
        return surface to canvas
    }
}
