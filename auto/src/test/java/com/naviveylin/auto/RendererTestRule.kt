package com.naviveylin.auto

import com.framstag.libosmscout.client.FakeAutoRenderClient
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Teardown rule for tests that construct an [AutoMapRenderer] (change
 * `fix-auto-unit-test-heap-overflow`, spec `unit-test-suite-runtime` — "Test-created
 * components release their background work").
 *
 * Why this exists: `AutoMapRenderer` starts its render and extrapolation loops in
 * `init` and only [AutoMapRenderer.shutdown] ends them. A test that forgets to shut
 * its renderer down leaves a ~30 Hz extrapolation loop on `Dispatchers.Default`, a
 * surface that is never released and a retained overrun bitmap alive for the rest of
 * the JVM run — which is what made the `:auto` suite die with `OutOfMemoryError` once
 * all its classes ran in one JVM (`TODO.md` §33).
 *
 * The rule therefore both hands out renderers and enforces their teardown:
 *
 * ```kotlin
 * @get:Rule val renderers = RendererTestRule()
 *
 * private lateinit var renderer: AutoMapRenderer
 * @Before fun setUp() { renderer = renderers.newRenderer() }
 * ```
 *
 * After every test method the rule shuts down every tracked instance and fails the
 * test when one of them still reports active background work, so a leak is
 * attributable to the class that caused it instead of surfacing as an unattributable
 * failure of unrelated classes. `Job.isActive` is false as soon as `shutdown()`
 * cancels, so no waiting or retry is involved.
 *
 * Constraints:
 * - The rule must not change the adopting class's `@RunWith`/sandbox configuration:
 *   these tests load the committed JNI stub through `FakeAutoRenderClient`, which the
 *   default Robolectric sandbox owns (`AGENTS.md` classloader rule).
 * - `shutdown()` is idempotent, so a test may shut its renderer down itself and the
 *   rule's teardown is still safe.
 * - A fully mocked renderer (not a `spyk`) records only the `shutdown()` call; the
 *   active-job assertion is then vacuous for that instance, which is intentional —
 *   the rule never fails a test because its mock does not implement the real state.
 */
class RendererTestRule : TestRule {

    private val tracked = mutableListOf<AutoMapRenderer>()

    /**
     * Creates an [AutoMapRenderer] with the configuration the renderer tests use and
     * tracks it for teardown. A `client` may be supplied when a test asserts on its
     * fake render client.
     */
    fun newRenderer(
        client: FakeAutoRenderClient = FakeAutoRenderClient(),
        dpi: Double = 240.0
    ): AutoMapRenderer = track(AutoMapRenderer(client, initialProjectionDpi = dpi))

    /**
     * Tracks a renderer the test created itself (e.g.
     * `renderers.track(spyk(AutoMapRenderer(client, initialProjectionDpi = 240.0)))`)
     * so the teardown covers it too.
     */
    fun <T : AutoMapRenderer> track(renderer: T): T {
        tracked += renderer
        return renderer
    }

    /** Number of renderers tracked so far; exposed for the rule's own tests. */
    fun trackedCount(): Int = tracked.size

    /** How many tracked renderers still have active background work. */
    fun activeBackgroundJobCount(): Int = tracked.sumOf { it.activeBackgroundJobCount() }

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val testFailure = runCatching { base.evaluate() }.exceptionOrNull()
                val teardownFailure = shutdownAll()
                when {
                    // Never mask the test's own failure: report the teardown failure
                    // as suppressed so both stay visible.
                    testFailure != null -> {
                        teardownFailure?.let(testFailure::addSuppressed)
                        throw testFailure
                    }

                    teardownFailure != null -> throw teardownFailure
                }
            }
        }

    /**
     * Shuts every tracked renderer down and returns the failure to report, or null
     * when all of them stopped and released their background work. Shuts all of them
     * down even when one of them throws, so a single broken renderer cannot hide the
     * others.
     */
    fun shutdownAll(): Throwable? {
        var failure: Throwable? = null
        tracked.forEach { renderer ->
            runCatching { renderer.shutdown() }.exceptionOrNull()?.let { thrown ->
                if (failure == null) failure = thrown else failure!!.addSuppressed(thrown)
            }
        }

        val stillRunning = tracked.count { it.activeBackgroundJobCount() > 0 }
        if (stillRunning > 0) {
            // Report the leak even when a shutdown also threw: both are actionable.
            val leak = AssertionError(
                "$stillRunning of ${tracked.size} renderer(s) still have active background work " +
                    "after the test teardown — shut the renderer down (RendererTestRule)"
            )
            if (failure == null) failure = leak else failure!!.addSuppressed(leak)
        }
        return failure
    }
}
