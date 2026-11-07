package com.naviveylin.auto

import android.os.Looper
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.DiagnosticsLog
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.After
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
 * Tests for [DiagnosticsScreen] (spec: auto-diagnostics — Reading diagnostics
 * does not block the UI; spec `car-list-row-limit` — A car list never presents more rows than the host
 * displays): the log is read on a background dispatcher, the loaded entries reach the template,
 * `onGetTemplate` — a car host callback — never reads the file itself, and the recent-entries window is
 * presented at the connected host's row capacity with the rest behind the page row.
 * Default Robolectric sandbox, no @Config (per AGENTS.md classloader rule).
 */
@RunWith(RobolectricTestRunner::class)
class DiagnosticsScreenTest {

    private val appManager = mockk<AppManager>(relaxed = true)
    private val carContext = testCarContext()
    private lateinit var logFile: File

    /** The host's constraint service: the row capacity of one list (spec: car-list-row-limit). */
    private val constraintManager = mockk<ConstraintManager>()

    /** The screen manager the page row's push lands on, with every pushed screen recorded. */
    private val screenManager = mockk<ScreenManager>(relaxed = true)
    private val pushed = mutableListOf<Screen>()

    /** How often a screen asked the host for its capacity, across all screens of a case. */
    private var capacityQueries = 0

    /** The row capacity the fake host reports for a list. */
    private var hostCapacity = CarListCapacityReader.LIBRARY_DEFAULT

    /**
     * Every screen a case built, released in [tearDown] (spec: unit-test-suite-runtime — Test-created
     * components release their background work): a screen owns a coroutine scope, and an unreleased one
     * goes on writing its own capacity line into whatever log target the next case sets.
     */
    private fun openScreen(page: Int = 0): DiagnosticsScreen =
        DiagnosticsScreen(carContext, page).also { built += it }

    @Before
    fun setUp() {
        every { carContext.getCarService(AppManager::class.java) } returns appManager
        every { carContext.getCarService(ScreenManager::class.java) } returns screenManager
        every { screenManager.push(any<Screen>()) } answers { pushed += firstArg<Screen>() }
        every { carContext.carAppApiLevel } returns 7
        every { carContext.getCarService(ConstraintManager::class.java) } returns constraintManager
        every { constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST) } answers {
            capacityQueries++
            hostCapacity
        }

        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        logFile = File(appContext.filesDir, "diagnostics/screen-${System.nanoTime()}.log")
        logFile.parentFile?.mkdirs()
        logFile.delete()
        DiagnosticsLog.reset()
        DiagnosticsLog.initForTest(logFile)
        DiagnosticsLog.log("SESSION", "first-entry")
    }

    private val built = mutableListOf<Screen>()

    /**
     * Release a screen the way the host ends one: the lifecycle down to `DESTROYED`, which is what cancels
     * the screen's own scope (spec: unit-test-suite-runtime — Test-created components release their
     * background work). `DESTROYED` cannot be reached from `INITIALIZED` in one step, so the host's own
     * sequence is dispatched.
     */
    private fun release(screens: List<Screen>) {
        screens.forEach { screen ->
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    @After
    fun tearDown() {
        // Release every screen the case built and every page its page row opened, before the log target
        // is dropped: a screen whose scope is still alive writes into the next case's file
        // (spec: unit-test-suite-runtime — Test-created components release their background work).
        release(built + pushed.filterIsInstance<DiagnosticsScreen>())
        DiagnosticsLog.reset()
        logFile.delete()
    }

    /** Row titles of whichever template the screen serves (a pane before the change, a list after). */
    private fun rows(screen: DiagnosticsScreen): List<String> {
        val template: Template = screen.onGetTemplate()
        return when (template) {
            is PaneTemplate -> template.pane.rows.map { it.title.toString() }
            is ListTemplate -> template.singleList!!.items.filterIsInstance<Row>().map { it.title.toString() }
            else -> error("unexpected template ${template::class.java.name}")
        }
    }

    private fun disclosure(): String =
        carContext.getString(com.naviveylin.core.R.string.diagnostics_disclosure)

    private fun more(count: Int): String = carContext.getString(R.string.more_items, count)

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

    /** Drive the looper until [ready] holds of the screen's row titles, then return them. */
    private fun awaitRows(
        screen: DiagnosticsScreen,
        what: String,
        ready: (List<String>) -> Boolean
    ): List<String> {
        var titles: List<String> = rows(screen)
        val ok = runBlocking {
            withTimeoutOrNull<Boolean>(5_000) {
                while (!ready(titles)) {
                    shadowOf(Looper.getMainLooper()).idle()
                    titles = rows(screen)
                    if (!ready(titles)) yield()
                }
                true
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        titles = rows(screen)
        assertNotNull("the diagnostics screen never published $what: $titles", ok)
        return titles
    }

    /** Tap the row titled [title] — the way the host delivers it: through the row's click delegate. */
    private fun tapRow(screen: DiagnosticsScreen, title: String) {
        val template: Template = screen.onGetTemplate()
        assertTrue(
            "the diagnostics screen must be a car list to page its entries, was " +
                "${template::class.java.simpleName} (spec: car-list-row-limit)",
            template is ListTemplate
        )
        val row = (template as ListTemplate).singleList!!.items.filterIsInstance<Row>()
            .firstOrNull { it.title.toString() == title }
        assertNotNull("no row titled $title in ${rows(screen)}", row)
        row!!.onClickDelegate!!.sendClick(mockk(relaxed = true))
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Log [count] extra entries, so the recent-entries window holds more than a host displays. */
    private fun logEntries(count: Int) {
        for (i in 1..count) DiagnosticsLog.log("SESSION", "entry-$i")
    }

    /**
     * The window entries a rendered page holds, in row order, read out of the row titles.
     *
     * The screen's own capacity measurement is a diagnostics line too (`CarListCapacityReader`), so it can
     * sit inside the newest-entries window; it carries no `entry-<n>`, and neither does a page row or the
     * disclosure, so this is the entry sequence of the page.
     */
    private fun entryNames(titles: List<String>): List<String> =
        titles.mapNotNull { ENTRY_NAME.find(it)?.value }

    private fun entryNumber(name: String): Int = name.substringAfter("entry-").toInt()

    @Test
    fun loadedEntriesReachTheTemplate() {
        val screen = openScreen()

        assertTrue("the background load publishes entries", awaitEntries(screen, "first-entry"))
    }

    @Test
    fun theTemplateOpensWithTheDisclosure() {
        val screen = openScreen()
        assertTrue(awaitEntries(screen, "first-entry"))

        assertEquals(
            "what the log holds and how long it is kept must be readable before the entries",
            disclosure(),
            rows(screen).first()
        )
    }

    @Test
    fun onGetTemplateDoesNotReadTheFile() {
        val screen = openScreen()
        assertTrue(awaitEntries(screen, "first-entry"))

        // Replace the file's content behind the screen's back: a template build
        // that read the file would show the new content.
        logFile.writeText("[x] SESSION rewritten-entry\n")

        val shown = rows(screen)
        assertTrue("the loaded snapshot is rendered", shown.any { it.contains("first-entry") })
        assertFalse("onGetTemplate must not read the log file", shown.any { it.contains("rewritten-entry") })
    }

    // --- The row capacity (spec: car-list-row-limit — A car list never presents more rows than the host
    // displays; Items beyond the capacity stay reachable) ---

    @Test
    fun theDiagnosticsListFitsTheHostsCapacity() {
        // Three facts at once: the page holds the host's 8 rows — not the library's 6 and not the screen's
        // own 20 — the entries it does not show are counted in its page row instead of being dropped
        // silently, and what it does show is newest first. A screen that kept the fixed constant would
        // show 20 entries with no page row; one that used the library default would show 6 rows with
        // `More (14)`.
        hostCapacity = 8
        logEntries(30)
        val screen = openScreen()

        val shown = awaitRows(screen, "the host's ${hostCapacity + 2} rows") { titles ->
            titles.size == 1 + hostCapacity + 1
        }
        assertEquals("the disclosure opens the list", disclosure(), shown.first())
        assertEquals(
            "the page holds the host's 8 rows between the disclosure and its page row",
            1 + hostCapacity + 1,
            shown.size
        )
        assertEquals(
            "the entries the page does not show are counted, never dropped",
            more(12),
            shown.last()
        )
        val names = entryNames(shown)
        assertEquals("the newest entry comes first", "entry-30", names.first())
        assertEquals(
            "the page is newest-first throughout",
            names.map(::entryNumber).sortedDescending(),
            names.map(::entryNumber)
        )
        assertEquals("one capacity query per screen start", 1, capacityQueries)
        repeat(3) { rows(screen) }
        assertEquals("a template build queried the host", 1, capacityQueries)
    }

    @Test
    fun thePageRowOpensTheNextSliceOfTheWindow() {
        // Spec: "The final row opens the next page" / "Items beyond the capacity stay reachable": the
        // page the row opens presents the next capacity-many rows of the same newest-first window and
        // names what remains after them, so following the page rows reaches the window's oldest entry.
        // The rows are the assertion rather than a particular line: a page re-reads the window (a newer
        // entry may since have been logged) and the mapper's partition holds for the list it is given.
        hostCapacity = 8
        logEntries(30)
        val screen = openScreen()
        awaitRows(screen, "the host's ${hostCapacity + 2} rows") { titles ->
            titles.size == 1 + hostCapacity + 1
        }

        tapRow(screen, more(12))
        val next = pushed.filterIsInstance<DiagnosticsScreen>().single()
        val page = awaitRows(next, "its page") { titles ->
            titles.size == 1 + hostCapacity + 1
        }

        assertEquals("the page opens with the same disclosure", disclosure(), page.first())
        assertEquals(
            "the page presents the next capacity-many rows of the window",
            1 + hostCapacity + 1,
            page.size
        )
        assertEquals(
            "the entries after the second page are counted, not dropped",
            more(4),
            page.last()
        )
        assertEquals(
            "the page is newest-first throughout",
            entryNames(page).map(::entryNumber).sortedDescending(),
            entryNames(page).map(::entryNumber)
        )
    }

    private companion object {
        /** One window entry's name in a row title (`... SESSION entry-<n>`). */
        val ENTRY_NAME = Regex("entry-\\d+")
    }
}
