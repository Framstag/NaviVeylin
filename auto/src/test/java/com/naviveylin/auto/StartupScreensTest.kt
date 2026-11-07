package com.naviveylin.auto

import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.lifecycle.Lifecycle
import com.naviveylin.core.DiagnosticsLog
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * [ErrorScreen] (message + Retry), and [DiagnosticsScreen]
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

    /**
     * The car display has one transient wait notice, the calculation notice
     * ([RouteCalculatingScreen]); the retired loading screen had no production caller once
     * [NavigationSession] served the real root screen (spec: `route-calculation-feedback` — One
     * wait-notice implementation on the car display). The change *is* the deletion, so the case
     * asserts the absence by name.
     */
    @Test
    fun retiredLoadingScreenClassIsGone() {
        val present = runCatching { Class.forName("com.naviveylin.auto.LoadingScreen") }.isSuccess
        assertFalse("com.naviveylin.auto.LoadingScreen is still on the classpath", present)
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
            // Kept as the reproduction the red run used (change
            // `2026-10-27-fix-a-red-diagnostics-count-awaits-its-capacity`): the screen's own capacity
            // measurement is a diagnostics line too, it is written after this case's entries, and a line
            // another case's unreleased screen wrote lands in this file as well — so the window holds
            // lines this case did not write and must not be counted as a total.
            DiagnosticsLog.log("SCREEN", "list capacity 6 rows (library default, below car API 2)")

            val screen = DiagnosticsScreen(carContext)
            try {
                // The log is read on a background dispatcher (spec: auto-diagnostics —
                // Reading diagnostics does not block the UI), so wait for the load.
                val titles = awaitRows(screen, "3 log rows") { it.count { title -> ENTRY_NAME.containsMatchIn(title) } == 3 }

                // The entries this case wrote, in row order: the log holds 3 entries plus the screen's own
                // measurement line, and the entries follow newest first, inside the host's row capacity
                // (spec: car-list-row-limit).
                val entries = titles.filter { ENTRY_NAME.containsMatchIn(it) }
                assertTrue(
                    "the disclosure opens the list: $titles",
                    titles.first().contains(carContext.getString(com.naviveylin.core.R.string.diagnostics_disclosure))
                )
                assertEquals(3, entries.size)
                assertTrue("newest first: ${entries[0]}", entries[0].contains("third"))
                assertTrue(entries[1].contains("second"))
                assertTrue("oldest last: ${entries[2]}", entries[2].contains("first"))
            } finally {
                // Release the screen the way the host ends one, before the log target goes
                // (spec: unit-test-suite-runtime — Test-created components release their background work).
                screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
                screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
                screen.dispatchLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
        } finally {
            DiagnosticsLog.reset()
            file.delete()
        }
    }

    /**
     * An awaited state that never arrives fails the case by naming the state, not with a row count that the
     * missing load happens to produce (spec `unit-test-suite-runtime` — A helper that awaits a state fails by
     * naming it). The window is forced to zero so the expiry path is taken without waiting for it.
     */
    @Test
    fun awaitRowsNamesTheStateThatNeverArrived() {
        val screen = DiagnosticsScreen(carContext)
        try {
            val failure = try {
                awaitRows(screen, what = "three log rows", windowMs = 0L) { false }
                null
            } catch (e: AssertionError) {
                e
            }
            assertNotNull(
                "awaitRows returned rows for a state that never arrived, so the case fails later on a count",
                failure
            )
            assertTrue(
                "the failure names the state that did not arrive: ${failure!!.message}",
                (failure.message ?: "").contains("three log rows")
            )
        } finally {
            // Release the screen the way the host ends one (spec: unit-test-suite-runtime —
            // Test-created components release their background work).
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    /**
     * Template row titles, awaiting the observable state: the main looper is driven until [ready] holds of the
     * rows the screen publishes, so no sleep and no deadline decides the outcome
     * (spec `unit-test-suite-runtime` — Awaiting state, not a deadline).
     *
     * [windowMs] only ends a genuine hang: when it expires the case fails naming [what] and the rows it saw,
     * rather than returning a list its call site compares as if the load had arrived
     * (spec `unit-test-suite-runtime` — A helper that awaits a state fails by naming it).
     */
    private fun awaitRows(
        screen: DiagnosticsScreen,
        what: String,
        windowMs: Long = 5_000,
        ready: (List<String>) -> Boolean
    ): List<String> {
        var titles = rowTitles(screen)
        val arrived = runBlocking {
            withTimeoutOrNull<Boolean>(windowMs) {
                while (!ready(titles)) {
                    shadowOf(Looper.getMainLooper()).idle()
                    titles = rowTitles(screen)
                    if (!ready(titles)) yield()
                }
                true
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        titles = rowTitles(screen)
        assertNotNull("the startup screen never published $what: $titles", arrived)
        return titles
    }

    /** The screen's row titles as the host would read them. */
    private fun rowTitles(screen: DiagnosticsScreen): List<String> =
        (screen.onGetTemplate() as ListTemplate).singleList!!.items
            .filterIsInstance<Row>().map { it.title.toString() }

    private companion object {
        /** One window entry's tag and message in a row title (`A first`, `B second`, `C third`). */
        val ENTRY_NAME = Regex("\\b[ABC] (?:first|second|third)\\b")
    }
}
