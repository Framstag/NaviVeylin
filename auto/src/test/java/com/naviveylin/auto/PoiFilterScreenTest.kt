package com.naviveylin.auto

import androidx.car.app.ScreenManager
import androidx.car.app.model.SearchTemplate
import androidx.lifecycle.Lifecycle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Template tests for the car's POI name filter screen (spec: `auto-search` — A car POI result list can
 * be narrowed by name; change `poi-name-filter`, design D5).
 *
 * The screen is one `SearchTemplate` prefilled with the results screen's term; submitting applies the
 * term to the results screen behind it and pops, so one refinement costs no extra back-stack depth. The
 * cases drive the screen's own search callback (the precedent is [SearchScreenTest]) and the fake host
 * records the pop, so the guard can be shown by a host that refuses it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PoiFilterScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val carContext = testCarContext()

    /** The screen manager the pop lands on; [pops] counts the pops the case observed. */
    private val screenManager = mockk<ScreenManager>(relaxed = true)
    private var pops = 0

    @Before
    fun setUp() {
        every { carContext.getCarService(ScreenManager::class.java) } returns screenManager
        every { screenManager.pop() } answers {
            pops++
            Unit
        }
    }

    /** A filter screen in its started period, as the host starts it. */
    private fun newScreen(term: String, onApply: (String) -> Unit = {}): PoiFilterScreen =
        PoiFilterScreen(carContext, term, onApply, mainDispatcherRule.dispatcher).also { screen ->
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
        }

    @Test
    fun theScreenOpensWithTheCurrentTerm() {
        // Spec: "The filter screen opens with the current term" — the template is prefilled, so a
        // refinement starts from the term that is in effect instead of from an empty field.
        val screen = newScreen("rewe")

        val template = screen.onGetTemplate() as SearchTemplate
        assertEquals("rewe", template.initialSearchText.toString())
    }

    @Test
    fun submittingAppliesTheTermAndPops() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "Submitting applies the term and pops" — the term reaches the results screen behind this
        // one, and the filter screen leaves, so the results screen is the one on top again.
        val applied = mutableListOf<String>()
        val screen = newScreen("rewe", onApply = { applied += it })

        screen.SearchCallbackImpl().onSearchSubmitted("rewe markt")

        assertEquals(listOf("rewe markt"), applied)
        assertEquals("the filter screen pops itself after the submit", 1, pops)
    }

    @Test
    fun anEmptySubmitClearsTheFilter() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "An empty submit clears the filter" — the results screen is handed the empty term, which
        // is the unfiltered list of that category.
        val applied = mutableListOf<String>()
        val screen = newScreen("rewe", onApply = { applied += it })

        screen.SearchCallbackImpl().onSearchSubmitted("")

        assertEquals(listOf(""), applied)
        assertEquals(1, pops)
    }

    @Test
    fun thePopGoesThroughTheGuardedHostCall() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "The pop goes through the guarded host call" — a host that refuses the pop must not fault
        // the car path, and the term is applied before the pop, so a refused pop cannot drop it
        // (spec: `car-host-fault-isolation`).
        every { screenManager.pop() } throws IllegalStateException("host refuses the pop")
        val applied = mutableListOf<String>()
        val screen = newScreen("rewe", onApply = { applied += it })

        screen.SearchCallbackImpl().onSearchSubmitted("markt")

        assertEquals(listOf("markt"), applied)
    }

    @Test
    fun leavingWithoutSubmittingChangesNothing() = runTest(mainDispatcherRule.dispatcher) {
        // Spec: "A screen left without submitting changes nothing" — typing is not applying, and leaving
        // the screen applies nothing either, so the term on the results screen stays as it was.
        val applied = mutableListOf<String>()
        val screen = newScreen("rewe", onApply = { applied += it })

        screen.SearchCallbackImpl().onSearchTextChanged("markt")
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_STOP)
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        assertTrue("leaving applied a term: $applied", applied.isEmpty())
        assertEquals("leaving popped the screen itself", 0, pops)
    }
}
