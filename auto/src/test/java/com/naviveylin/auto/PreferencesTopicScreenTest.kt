package com.naviveylin.auto

import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.Row
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.AutoSettingsProvider
import com.naviveylin.core.VehicleAnchorPosition
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [PreferencesTopicScreen]: one settings topic as one row list, one level below the
 * root (spec: `auto/preferences` — "A topic opens its rows", "Back leaves one level"), the
 * control kinds a row opens, and the render recovery the level carries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PreferencesTopicScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val carContext = testCarContext()
    private val provider = mockk<AutoSettingsProvider>()

    /** The screen manager the pushes land on, with every pushed screen recorded. */
    private val screenManager = mockk<ScreenManager>(relaxed = true)
    private val pushed = mutableListOf<Screen>()

    @Before
    fun setUp() {
        every { carContext.getCarService(ScreenManager::class.java) } returns screenManager
        every { screenManager.push(any<Screen>()) } answers { pushed += firstArg<Screen>() }
        every { carContext.getOnBackPressedDispatcher() } returns mockk(relaxed = true)
    }

    private fun screen(topic: PreferenceTopic) = PreferencesTopicScreen(
        carContext = carContext,
        topic = topic,
        settingsProvider = provider,
        stylesLoader = { listOf("cycle", "standard") }
    )

    private fun titles(screen: PreferencesTopicScreen): List<String> =
        screen.onGetTemplate().singleList!!.items.map { (it as Row).title.toString() }

    @Test
    fun aTopicListsItsCarUsableRows() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { provider.load() } returns AutoSettings()

        val screen = screen(PreferenceTopic.MAP)
        advanceUntilIdle()

        assertEquals(
            listOf("Dark mode", "Render mode", "Map style", "North-up (browse)"),
            titles(screen)
        )
        // The header names the topic, and back leaves one level rather than the surface.
        val template = screen.onGetTemplate()
        assertEquals("Karte", template.header!!.title.toString())
        assertEquals(Action.BACK, template.header!!.startHeaderAction)
    }

    @Test
    fun anotherTopicListsItsOwnRows() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { provider.load() } returns AutoSettings()

        val screen = screen(PreferenceTopic.HINTS_AND_LANGUAGE)
        advanceUntilIdle()

        assertEquals(
            listOf("Lane hints", "Voice guidance", "GPS announcements"),
            titles(screen)
        )
        assertEquals("Hinweise & Sprache", screen.onGetTemplate().header!!.title.toString())
    }

    @Test
    fun aSwitchRowFlipsAndSaves() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { provider.load() } returns AutoSettings(followMode = false)
        coEvery { provider.save(any()) } returns Unit

        val screen = screen(PreferenceTopic.NAVIGATION_AND_ROUTE)
        advanceUntilIdle()
        screen.onToggle("followMode")
        advanceUntilIdle()

        coVerify { provider.save(AutoSettings(followMode = true)) }
    }

    /**
     * Spec case: A value is chosen, never only cycled.
     */
    @Test
    fun aChoiceRowOpensItsOptions() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { provider.load() } returns AutoSettings(darkMode = "AUTOMATIC")

        val screen = screen(PreferenceTopic.MAP)
        advanceUntilIdle()
        val darkMode = PreferencesScreenMapper.rowsFor(PreferenceTopic.MAP, AutoSettings()).first()
        screen.onRowSelected(darkMode)
        advanceUntilIdle()

        assertEquals(1, pushed.size)
        val options = pushed.single() as PreferenceChoiceScreen
        assertEquals(PreferencesScreenMapper.KEY_DARK_MODE, options.key)
        assertEquals("AUTOMATIC", options.currentValue)
        assertEquals(listOf("ON", "AUTOMATIC", "OFF"), options.choices.map { it.value })
    }

    @Test
    fun theMapStyleRowOffersTheLiveStyleList() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { provider.load() } returns AutoSettings(styleSheet = "standard")

        val screen = screen(PreferenceTopic.MAP)
        advanceUntilIdle()
        val styleRow = PreferencesScreenMapper
            .rowsFor(PreferenceTopic.MAP, AutoSettings(styleSheet = "standard"), listOf("cycle", "standard"))
            .first { it.key == "styleSheet" }
        screen.onRowSelected(styleRow)
        advanceUntilIdle()

        val options = pushed.single() as PreferenceChoiceScreen
        assertEquals(listOf("cycle", "standard"), options.choices.map { it.value })
        assertEquals("standard", options.currentValue)
    }

    @Test
    fun aPickerRowKeepsItsDedicatedScreen() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { provider.load() } returns AutoSettings()

        val screen = screen(PreferenceTopic.NAVIGATION_AND_ROUTE)
        advanceUntilIdle()
        val anchorRow = PreferencesScreenMapper
            .rowsFor(PreferenceTopic.NAVIGATION_AND_ROUTE, AutoSettings())
            .first { it.key == PreferencesScreenMapper.KEY_ROUTING_ANCHOR }
        screen.onRowSelected(anchorRow)
        advanceUntilIdle()

        assertTrue(pushed.single() is VehicleAnchorPickerScreen)
        assertEquals(
            "the picker opens for the routing anchor",
            "Vehicle position (navigation)",
            (pushed.single() as VehicleAnchorPickerScreen).onGetTemplate().header!!.title.toString()
        )
    }

    @Test
    fun placeholderUntilLoadCompletes() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { provider.load() } coAnswers { delay(300); AutoSettings() }

        val screen = screen(PreferenceTopic.HINTS_AND_LANGUAGE)

        assertEquals(listOf("Loading..."), titles(screen))
        advanceTimeBy(400)
        advanceUntilIdle()
        assertEquals(3, titles(screen).size)
    }

    /**
     * Spec case: Settings screens never wedge on a loading placeholder.
     */
    @Test
    fun aLoadFailureOffersRetryAtTheTopicLevel() = runTest(mainDispatcherRule.dispatcher) {
        var loadCalls = 0
        coEvery { provider.load() } answers {
            if (++loadCalls == 1) throw RuntimeException("storage down") else AutoSettings()
        }

        val screen = screen(PreferenceTopic.MAP)
        advanceUntilIdle()
        assertEquals(listOf("Settings unavailable", "Retry"), titles(screen))

        // The Retry row's action re-runs the load and the rows come back (no wedge).
        screen.onRetry()
        advanceUntilIdle()
        assertEquals(4, titles(screen).size)
    }

    /**
     * Spec case: Settings screens never wedge on a loading placeholder — at the topic level too,
     * where the guard is a second instance.
     */
    @Test
    fun aStalledTopicLoadEndsInErrorThenLateContentRecovers() = runTest(mainDispatcherRule.dispatcher) {
        // Load hangs far beyond the guard's watchdog budget.
        coEvery { provider.load() } coAnswers { delay(10_000); AutoSettings() }

        val screen = screen(PreferenceTopic.MAP)

        // First watchdog window (t=2000): one re-invalidate, still placeholder.
        advanceTimeBy(2_000L)
        assertEquals(listOf("Loading..."), titles(screen))

        // t=7000: the two recovery attempts are spent — the error row replaces the placeholder
        // even though the load has not returned yet.
        advanceTimeBy(5_000L)
        assertEquals(listOf("Settings unavailable", "Retry"), titles(screen))

        // The load finally completes (t=10000): content comes back, error clears.
        advanceUntilIdle()
        assertEquals(4, titles(screen).size)
    }

    @Test
    fun reVisibilityReReadsSettingsAndShowsNewAnchor() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { provider.load() } returnsMany listOf(
            AutoSettings(routingAnchorId = VehicleAnchorPosition.CENTER.id),
            AutoSettings(routingAnchorId = VehicleAnchorPosition.BOTTOM_RIGHT.id)
        )

        val screen = screen(PreferenceTopic.NAVIGATION_AND_ROUTE)
        advanceUntilIdle()
        // First render: the persisted value at the first load.
        assertTrue(vehiclePositionNavigationValue(screen).contains("Center"))
        assertFalse(vehiclePositionNavigationValue(screen).contains("Bottom right"))

        // Re-visibility (picker pop-back): the screen re-reads the persisted settings and the row
        // shows the NEW anchor, not the first-load snapshot (spec: auto/preferences — "Re-read on
        // re-visibility", auto-map-layout — "Anchor settings reflect the persisted value on
        // re-visibility").
        (screen.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.STARTED
        advanceUntilIdle()
        assertTrue(vehiclePositionNavigationValue(screen).contains("Bottom right"))

        (screen.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.DESTROYED
    }

    /** The value text of the "Vehicle position (navigation)" row. */
    private fun vehiclePositionNavigationValue(screen: PreferencesTopicScreen): String {
        val rows = screen.onGetTemplate().singleList!!.items
        val row = rows.first { (it as Row).title.toString() == "Vehicle position (navigation)" } as Row
        return row.texts?.joinToString(",") { it.toString() } ?: ""
    }
}