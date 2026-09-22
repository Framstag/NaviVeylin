package com.naviveylin.auto

import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Template
import com.naviveylin.core.DiagnosticsLog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * Tests the deferred screen push of a host click action (spec: car-host-fault-isolation —
 * Host callbacks answer promptly, "Template row action opens a screen"; design D6).
 *
 * The property that matters is the split: the host click callback arms the action and
 * returns, and the screen construction plus the host mutation happen afterwards. The host
 * callback runs on the library's dispatch path, which rethrows an app exception on the
 * app's main thread, so building a screen there risks the process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShowOnMapSwapTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val carContext = testCarContext()
    private val screenManager = mockk<ScreenManager>(relaxed = true)
    private val pushed = mutableListOf<Screen>()
    private val calls = mutableListOf<String>()

    private val diagnostics = File.createTempFile("showonmap", ".log")

    @Before
    fun setUp() {
        every { carContext.getCarService(ScreenManager::class.java) } returns screenManager
        every { screenManager.push(any<Screen>()) } answers {
            calls += "push"
            pushed += firstArg<Screen>()
        }
        DiagnosticsLog.initForTest(diagnostics)
    }

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
        diagnostics.delete()
    }

    private fun scope() = carScreenScope("DetailsScreen", mainDispatcher.dispatcher)

    /** A real [Screen] for the fakes: the helpers only need an instance to push. */
    private fun screen(): Screen = FakeScreen(carContext)

    @Test
    fun theCallbackPathBuildsNothingAndPushesNothing() = runTest(mainDispatcher.dispatcher) {
        var builds = 0

        armScreenPush(carContext, scope(), "MapScreen") {
            builds++
            screen()
        }

        // The arming call returned: nothing has happened yet, so a host callback that arms
        // the push cannot block on screen construction.
        assertEquals("the callback path builds nothing", 0, builds)
        assertEquals("and pushes nothing", emptyList<Screen>(), pushed)
    }

    @Test
    fun theScreenIsBuiltAndPushedAfterwards() = runTest(mainDispatcher.dispatcher) {
        lateinit var target: Screen

        armScreenPush(carContext, scope(), "MapScreen") {
            target = screen()
            target
        }
        advanceUntilIdle()

        assertEquals(listOf(target), pushed)
        verify(exactly = 1) { screenManager.push(any<Screen>()) }
    }

    @Test
    fun aFailingBuildPushesNothingAndDoesNotPropagate() = runTest(mainDispatcher.dispatcher) {
        armScreenPush<Screen>(carContext, scope(), "MapScreen") {
            throw IllegalStateException("no host")
        }
        advanceUntilIdle()

        assertEquals("a screen that cannot be built is not pushed", emptyList<Screen>(), pushed)
    }

    @Test
    fun aRejectedPushIsConfinedAndReported() = runTest(mainDispatcher.dispatcher) {
        every { screenManager.push(any<Screen>()) } throws
            IllegalStateException("Accessed the car host after it became invalidated")

        armScreenPush(carContext, scope(), "MapScreen") { screen() }
        advanceUntilIdle()

        val rejected = DiagnosticsLog.readEntries().filter { it.contains("rejected") }
        assertEquals("the rejection is recorded: $rejected", 1, rejected.size)
        assertTrue(
            "the entry names the target: $rejected",
            rejected.single().contains("push MapScreen rejected")
        )
    }

    @Test
    fun theSwapPopsToTheRootBeforeItPushes() = runTest(mainDispatcher.dispatcher) {
        every { screenManager.popToRoot() } answers { calls += "popToRoot" }

        armShowOnMapSwap(carContext, scope(), "show on map") { screen() }
        advanceUntilIdle()

        assertEquals(listOf("popToRoot", "push"), calls)
    }

    @Test
    fun aRejectedPopStillPushesAndIsReported() = runTest(mainDispatcher.dispatcher) {
        every { screenManager.popToRoot() } throws IllegalStateException("host gone")

        armShowOnMapSwap(carContext, scope(), "show on map") { screen() }
        advanceUntilIdle()

        assertEquals("the push still happens", 1, pushed.size)
        val rejected = DiagnosticsLog.readEntries().filter { it.contains("rejected") }
        assertTrue(
            "the rejected pop is named: $rejected",
            rejected.any { it.contains("popToRoot (show on map) rejected") }
        )
    }
}

/** Minimal [Screen] the swap tests push; its template is never requested. */
private class FakeScreen(carContext: androidx.car.app.CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = SafeScreen.errorTemplate(carContext, "fake")
}
