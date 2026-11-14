package com.naviveylin.auto

import android.content.Context
import androidx.car.app.AppManager
import androidx.car.app.ScreenManager
import androidx.car.app.model.SearchTemplate
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Template tests for [PoiFilterScreen] (spec: `auto-search` — A car POI result list can be narrowed by
 * name; change `poi-name-filter`, design D5).
 *
 * The filter screen is a `SearchTemplate` prefilled with the term the results screen already carries; a
 * submit hands it to that results screen and pops back, so the back stack does not grow per refinement.
 * The case that names the pop's guardedness drives a host whose `pop` throws, which an unguarded call
 * would let escape into the car path.
 */
@RunWith(RobolectricTestRunner::class)
class PoiFilterScreenTest {

    private val carContext = testCarContext()

    /** The screen manager the pop lands on. */
    private val screenManager = mockk<ScreenManager>(relaxed = true)

    /** The terms the screen handed to the results screen underneath. */
    private val applied = mutableListOf<String>()

    private var popCalls = 0

    @Before
    fun setUp() {
        every { carContext.applicationContext } returns ApplicationProvider.getApplicationContext<Context>()
        every { carContext.getCarService(ScreenManager::class.java) } returns screenManager
        every { carContext.getCarService(AppManager::class.java) } returns mockk(relaxed = true)
        every { screenManager.pop() } answers { popCalls++ }
    }

    private fun newScreen(initialTerm: String = ""): PoiFilterScreen = PoiFilterScreen(
        carContext,
        initialTerm = initialTerm,
        onApply = { applied += it }
    ).also {
        it.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
        it.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
    }

    /** Submit [term] the way the host delivers it: through the template's search delegate. */
    private fun submit(screen: PoiFilterScreen, term: String) {
        (screen.onGetTemplate() as SearchTemplate)
            .searchCallbackDelegate!!
            .sendSearchSubmitted(term, mockk(relaxed = true))
    }

    @Test
    fun theScreenOpensWithTheCurrentTerm() {
        val screen = newScreen(initialTerm = "rewe")

        val template = screen.onGetTemplate() as SearchTemplate

        assertEquals("the field opens prefilled with the term in effect", "rewe", template.initialSearchText.toString())
    }

    @Test
    fun submittingAppliesTheTermAndPops() {
        val screen = newScreen(initialTerm = "rewe")

        submit(screen, "aldi")

        assertEquals("the term is handed to the results screen", listOf("aldi"), applied)
        assertEquals("and the filter screen pops back to it", 1, popCalls)
    }

    @Test
    fun anEmptySubmitClearsTheFilter() {
        val screen = newScreen(initialTerm = "rewe")

        submit(screen, "")

        assertEquals(
            "an empty field clears the term rather than keeping it",
            listOf(""),
            applied
        )
        assertEquals(1, popCalls)
    }

    @Test
    fun thePopGoesThroughTheGuardedHostCall() {
        // A host that is already gone throws from every screen-stack call; the guarded call confines it
        // instead of letting it fault the car path (spec: `auto-search` — The pop goes through the
        // guarded host call).
        every { screenManager.pop() } answers {
            popCalls++
            throw IllegalStateException("host gone")
        }
        val screen = newScreen(initialTerm = "rewe")

        submit(screen, "aldi")

        assertEquals("the term was applied before the pop", listOf("aldi"), applied)
        assertEquals("the pop was attempted exactly once", 1, popCalls)
    }

    @Test
    fun leavingWithoutSubmittingChangesNothing() {
        val screen = newScreen(initialTerm = "rewe")

        // The host reports a keystroke but the driver leaves without submitting.
        (screen.onGetTemplate() as SearchTemplate)
            .searchCallbackDelegate!!
            .sendSearchTextChanged("aldi", mockk(relaxed = true))

        assertTrue("nothing is applied", applied.isEmpty())
        assertEquals("and nothing is popped by the leaving itself", 0, popCalls)
    }
}
