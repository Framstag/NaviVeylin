package com.naviveylin.auto

import android.content.pm.PackageManager
import androidx.car.app.OnDoneCallback
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.AutoSettingsProvider
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import dagger.hilt.EntryPoints
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider

/**
 * Audits the click path of the car screens that push other screens (spec:
 * car-host-fault-isolation — Host callbacks answer promptly, "Template row action opens a
 * screen"; "Screen push from a click action throws"; design D6).
 *
 * Two properties per screen, both of which the host library punishes: the click callback
 * must not build the target screen (the library rethrows an escaping exception on the app's
 * main thread), and a rejected mutation must degrade to a logged no-op.
 *
 * Every screen's screen-stack mutation goes through [armScreenPush] / [armShowOnMapSwap] /
 * `guardedHostCall` (one call site each, grep-verifiable); this class drives the screens
 * whose rows are reachable in a unit test and asserts the behaviour at the screen boundary.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClickPathDefersAndConfinesTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val carContext = testCarContext()
    private val screenManager = mockk<ScreenManager>(relaxed = true)
    private var pushes = 0

    /**
     * The pushed screens read the navigation state during construction, and a relaxed mock
     * returns `null` for it — the screens need a real flow.
     */
    private fun navigationViewModel(): NavigationViewModel {
        val state = MutableStateFlow(com.naviveylin.core.NavigationState())
        return mockk<NavigationViewModel>(relaxed = true).apply {
            every { this@apply.state } returns state
        }
    }

    private val diagnostics = File.createTempFile("clickpath", ".log")

    @Before
    fun setUp() {
        pushes = 0
        every { carContext.getCarService(ScreenManager::class.java) } returns screenManager
        every { screenManager.push(any<Screen>()) } answers { pushes++ }
        DiagnosticsLog.initForTest(diagnostics)

        // The pushed screens resolve their providers through the Hilt entry point and read a
        // few `Context` members during construction; a relaxed mock plus the real application
        // context keeps every construction working, which is what makes the row-by-row
        // assertions below count all of them.
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        every { carContext.applicationContext } returns app
        every { carContext.resources } returns app.resources
        every { carContext.filesDir } returns app.filesDir
        every { carContext.packageName } returns app.packageName
        io.mockk.mockkStatic(EntryPoints::class)
        every { EntryPoints.get(any(), AutoEntryPoint::class.java) } returns mockk<AutoEntryPoint>(relaxed = true).apply {
            // The pushed screens collect these flows in their init; a relaxed mock of a
            // StateFlow throws KotlinNothingValueException on collect, so real flows are
            // supplied (same reason MapScreenTest does it).
            every { autoFavoritesProvider() } returns mockk<com.naviveylin.core.AutoFavoritesProvider>().apply {
                every { favoriteLocations() } returns MutableStateFlow(emptyMap())
            }
            every { autoLocationProvider() } returns mockk<com.naviveylin.core.AutoLocationProvider>().apply {
                every { position() } returns MutableStateFlow(null)
            }
            every { basemapReloadNotifier() } returns mockk<com.naviveylin.core.BasemapReloadNotifier>().apply {
                every { revision } returns MutableStateFlow(0L)
            }
        }
        every { carContext.isDarkMode() } returns false

        // RootScreen reads READ_CONTACTS through ContextCompat (a real library class, so the
        // static is stubbed rather than the call site mocked) and calls enableBackNavigation.
        io.mockk.mockkStatic("androidx.core.content.ContextCompat")
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(any(), any())
        } returns PackageManager.PERMISSION_DENIED
    }

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
        diagnostics.delete()
    }

    @Test
    fun rootScreenRowsNeverPushOnTheCallbackPath() = runTest(mainDispatcher.dispatcher) {
        val screen = RootScreen(carContext, navigationViewModel())
        val rows = clickableRows(screen.onGetTemplate() as ListTemplate)
        assertTrue("the root screen has shortcuts to audit", rows.isNotEmpty())

        rows.forEach { it.onClickDelegate!!.sendClick(object : OnDoneCallback {}) }

        assertEquals("no row pushes while answering the host", 0, pushes)
        advanceUntilIdle()
        assertEquals(
            "every shortcut pushes after the callback; faults: " +
                DiagnosticsLog.readEntries().filter { !it.contains("rejected") },
            rows.size,
            pushes
        )
    }

    @Test
    fun rootScreenRowSurvivesARejectedPush() = runTest(mainDispatcher.dispatcher) {
        every { screenManager.push(any<Screen>()) } throws
            IllegalStateException("Accessed the car host after it became invalidated")
        val screen = RootScreen(carContext, navigationViewModel())
        val rows = clickableRows(screen.onGetTemplate() as ListTemplate)

        rows.first().onClickDelegate!!.sendClick(object : OnDoneCallback {})
        advanceUntilIdle()

        val rejected = DiagnosticsLog.readEntries().filter { it.contains("rejected") }
        assertEquals("one rejection is recorded: $rejected", 1, rejected.size)
        assertTrue(
            "the entry names the target: $rejected",
            rejected.single().contains("push MapScreen rejected")
        )
    }

    @Test
    fun preferencesRowsNeverPushOnTheCallbackPath() = runTest(mainDispatcher.dispatcher) {
        val provider = mockk<AutoSettingsProvider>(relaxed = true)
        coEvery { provider.load() } returns AutoSettings()
        val screen = PreferencesScreen(carContext, provider)
        // The rows exist only after the settings load (the template shows a loading row until
        // then); the load runs on the screen's own scope.
        advanceUntilIdle()
        val rows = clickableRows(screen.onGetTemplate() as ListTemplate)
        assertTrue("the preferences screen has rows to audit", rows.isNotEmpty())

        rows.forEach { it.onClickDelegate!!.sendClick(object : OnDoneCallback {}) }

        assertEquals("no row pushes while answering the host", 0, pushes)
        advanceUntilIdle()
        assertTrue("the picker rows push after the callback", pushes > 0)
    }

    /** Every row of a list template that carries a click action. */
    private fun clickableRows(template: ListTemplate): List<Row> =
        template.singleList!!.items.filterIsInstance<Row>().filter { it.onClickDelegate != null }
}
