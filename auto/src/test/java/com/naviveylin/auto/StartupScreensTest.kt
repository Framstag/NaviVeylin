package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.model.PaneTemplate
import com.naviveylin.core.DiagnosticsLog
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for the startup-guard screens: [SafeScreen] template error fallback,
 * [LoadingScreen], [ErrorScreen] (message + Retry), and [DiagnosticsScreen]
 * ordering. Default Robolectric sandbox, no @Config.
 *
 * The full [NavigationSession] guard path (warmup-incomplete → loading screen,
 * exception → error screen) cannot be exercised without a real host-provided
 * CarContext; the screens it produces are covered here.
 */
@RunWith(RobolectricTestRunner::class)
class StartupScreensTest {

    private val carContext = mockk<CarContext>()

    @Test
    fun errorTemplateContainsMessage() {
        val template = SafeScreen.errorTemplate("startup exploded") as PaneTemplate
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
        val screen = SafeScreen(carContext) { SafeScreen.errorTemplate("ok") }
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
        assertEquals("Retry", template.actionStrip!!.actions[0].title.toString())
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

            val template = DiagnosticsScreen(carContext).onGetTemplate() as PaneTemplate
            val titles = template.pane.rows.map { it.title.toString() }

            assertTrue(titles.size <= 20)
            assertEquals(3, titles.size)
            assertTrue("newest first: ${titles[0]}", titles[0].contains("third"))
            assertTrue(titles[2].contains("first"))
        } finally {
            DiagnosticsLog.reset()
            file.delete()
        }
    }
}
