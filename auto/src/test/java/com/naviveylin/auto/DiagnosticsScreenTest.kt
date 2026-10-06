package com.naviveylin.auto

import android.os.Looper
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.model.PaneTemplate
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.DiagnosticsLog
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Tests for [DiagnosticsScreen] (spec: auto-diagnostics — Reading diagnostics
 * does not block the UI): the log is read on a background dispatcher, the loaded
 * entries reach the template, and `onGetTemplate` — a car host callback — never
 * reads the file itself. Default Robolectric sandbox, no @Config (per AGENTS.md
 * classloader rule).
 */
@RunWith(RobolectricTestRunner::class)
class DiagnosticsScreenTest {

    private val appManager = mockk<AppManager>(relaxed = true)
    private val carContext = mockk<CarContext>()
    private lateinit var logFile: File

    @Before
    fun setUp() {
        every { carContext.getOnBackPressedDispatcher() } returns mockk(relaxed = true)
        every { carContext.getCarService(AppManager::class.java) } returns appManager
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        every { carContext.getString(any()) } answers { appContext.getString(firstArg()) }

        logFile = File(appContext.filesDir, "diagnostics/screen-${System.nanoTime()}.log")
        logFile.parentFile?.mkdirs()
        logFile.delete()
        DiagnosticsLog.reset()
        DiagnosticsLog.initForTest(logFile)
        DiagnosticsLog.log("SESSION", "first-entry")
    }

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
        logFile.delete()
    }

    /** Row titles of the template's pane. */
    private fun rows(screen: DiagnosticsScreen): List<String> =
        (screen.onGetTemplate() as PaneTemplate).pane.rows.map { it.title.toString() }

    /**
     * Drive the main looper until the screen's background load published entries. The screen's own thread
     * does real work, so the looper is driven while that work progresses and the observable state is
     * awaited — no wall-clock deadline and no sleep
     * (spec `unit-test-suite-runtime` — Awaiting state, not a deadline).
     */
    private fun awaitEntries(screen: DiagnosticsScreen, expected: String): Boolean {
        val seen = runBlocking {
            withTimeoutOrNull<Boolean>(3_000) {
                var found = false
                while (!found) {
                    shadowOf(Looper.getMainLooper()).idle()
                    found = rows(screen).any { it.contains(expected) }
                    if (!found) yield()
                }
                found
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        return seen ?: rows(screen).any { it.contains(expected) }
    }

    @Test
    fun loadedEntriesReachTheTemplate() {
        val screen = DiagnosticsScreen(carContext)

        assertTrue("the background load publishes entries", awaitEntries(screen, "first-entry"))
    }

    @Test
    fun theTemplateOpensWithTheDisclosure() {
        val screen = DiagnosticsScreen(carContext)
        assertTrue(awaitEntries(screen, "first-entry"))

        val disclosure = carContext.getString(com.naviveylin.core.R.string.diagnostics_disclosure)
        assertEquals(
            "what the log holds and how long it is kept must be readable before the entries",
            disclosure,
            rows(screen).first()
        )
    }

    @Test
    fun onGetTemplateDoesNotReadTheFile() {
        val screen = DiagnosticsScreen(carContext)
        assertTrue(awaitEntries(screen, "first-entry"))

        // Replace the file's content behind the screen's back: a template build
        // that read the file would show the new content.
        logFile.writeText("[x] SESSION rewritten-entry\n")

        val shown = rows(screen)
        assertTrue("the loaded snapshot is rendered", shown.any { it.contains("first-entry") })
        assertFalse("onGetTemplate must not read the log file", shown.any { it.contains("rewritten-entry") })
    }
}
