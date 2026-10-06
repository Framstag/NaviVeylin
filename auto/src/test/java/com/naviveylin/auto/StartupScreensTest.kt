package com.naviveylin.auto

import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.model.PaneTemplate
import com.naviveylin.core.DiagnosticsLog
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Tests for the startup-guard screens: [SafeScreen] template error fallback,
 * [LoadingScreen], [ErrorScreen] (message + Retry), and [DiagnosticsScreen]
 * ordering. Default Robolectric sandbox, no @Config.
 *
 * The full [NavigationSession] guard path (exception → error screen) cannot
 * be exercised without a real host-provided CarContext; the screens it
 * produces are covered here.
 */
@RunWith(RobolectricTestRunner::class)
class StartupScreensTest {

    private val carContext = testCarContext()

    @Before
    fun setUp() {
        every { carContext.getOnBackPressedDispatcher() } returns mockk(relaxed = true)
    }

    @Test
    fun errorTemplateContainsMessage() {
        val template = SafeScreen.errorTemplate(testCarContext(), "startup exploded") as PaneTemplate
        assertEquals("Error", template.pane.rows[0].title.toString())
        assertTrue(template.pane.rows[0].texts.first().toString().contains("startup exploded"))
    }

    @Test
    fun safeScreenCatchesTemplateException() {
        val screen = SafeScreen(carContext) { error("template boom") }
        val template = screen.onGetTemplate()
        assertTrue(template is PaneTemplate)
        assertTrue((template as PaneTemplate).pane.rows[0].texts.first().toString().contains("template boom"))
    }

    @Test
    fun safeScreenReturnsDelegateTemplateOnSuccess() {
        val screen = SafeScreen(carContext) { SafeScreen.errorTemplate(carContext, "ok") }
        val template = screen.onGetTemplate() as PaneTemplate
        assertTrue(template.pane.rows[0].texts.first().toString().contains("ok"))
    }

    @Test
    fun loadingScreenShowsLoadingState() {
        val template = LoadingScreen(carContext).onGetTemplate() as PaneTemplate
        assertEquals("Loading map data…", template.pane.rows[0].title.toString())
    }

    @Test
    fun errorScreenShowsMessageAndRetryAction() {
        val screen = ErrorScreen(carContext, "startup exploded", onRetry = {})

        val template = screen.onGetTemplate() as PaneTemplate
        assertTrue(template.pane.rows[0].texts.first().toString().contains("startup exploded"))
        // Retry + Back are row actions now (the action strip is replaced by a
        // Header); assert the row carries both.
        val actions = template.pane.rows[0].actions
        assertEquals(2, actions.size)
        assertTrue(actions.any { it.title.toString() == "Retry" })
        assertTrue(actions.any { it.title.toString() == "Back" })
        // Note: invoking the action's OnClickDelegate requires a real host binder
        // (sendClick dispatches to the host); the lambda wiring is trivially thin.
    }

    @Test
    fun diagnosticsScreenShowsNewestFirst() {
        val dir = File(System.getProperty("java.io.tmpdir"), "diag-startup-test")
        dir.mkdirs()
        val file = File(dir, "app.log")
        file.delete()
        DiagnosticsLog.initForTest(file)
        try {
            DiagnosticsLog.log("A", "first")
            DiagnosticsLog.log("B", "second")
            DiagnosticsLog.log("C", "third")

            val screen = DiagnosticsScreen(carContext)
            // The log is read on a background dispatcher (spec: auto-diagnostics —
            // Reading diagnostics does not block the UI), so wait for the load.
            val titles = awaitRows(screen) { it.size == 4 }

            // Row 0 is the disclosure (spec: auto-diagnostics — Export and viewers
            // disclose the log contents); the entries follow newest first.
            assertTrue(titles.size <= 21)
            assertEquals(4, titles.size)
            assertTrue("newest first: ${titles[1]}", titles[1].contains("third"))
            assertTrue(titles[3].contains("first"))
        } finally {
            DiagnosticsLog.reset()
            file.delete()
        }
    }

    /**
     * Template row titles, driving the main looper until the screen's background load is published
     * (spec `unit-test-suite-runtime` — Awaiting state, not a deadline: no wall-clock window, no sleep).
     */
    private fun awaitRows(
        screen: DiagnosticsScreen,
        ready: (List<String>) -> Boolean
    ): List<String> {
        var titles: List<String> = emptyList()
        runBlocking {
            withTimeoutOrNull<Boolean>(5_000) {
                var done = false
                while (!done) {
                    shadowOf(Looper.getMainLooper()).idle()
                    titles = (screen.onGetTemplate() as PaneTemplate).pane.rows.map { it.title.toString() }
                    done = ready(titles)
                    if (!done) yield()
                }
                true
            }
        }
        return titles
    }
}
