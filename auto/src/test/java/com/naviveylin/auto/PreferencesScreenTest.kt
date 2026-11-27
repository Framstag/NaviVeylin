package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.model.Row
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.AutoSettingsProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [PreferencesScreen]: the root is the topic list, never a setting row, and it carries
 * the render recovery the car's settings screens owe (spec: `auto/preferences` — "The car's
 * preferences are grouped into sub-screens", "Settings screens never wedge on a loading
 * placeholder"). The provider is injected via the primary constructor so no Hilt entry point is
 * resolved in tests.
 *
 * The rows of a topic and the options of a value are covered by [PreferencesTopicScreenTest],
 * [PreferenceChoiceScreenTest] and [PreferencesScreenMapperTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PreferencesScreenTest {

    private val carContext = testCarContext()
    private val provider = mockk<AutoSettingsProvider>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { carContext.getOnBackPressedDispatcher() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onGetTemplateBuildsWithoutThrowing() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings()

        val screen = PreferencesScreen(carContext, provider)
        // Let the init load() launch (Dispatchers.Main) run to completion.
        advanceUntilIdle()

        val template = screen.onGetTemplate()

        // The root lists the topics that hold a car-usable row — one row per topic.
        assertEquals(3, template.singleList!!.items.size)
    }

    /**
     * Spec case: The root lists topics and no setting row.
     */
    @Test
    fun theRootListsTopicsAndNoSettingRow() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings()

        val screen = PreferencesScreen(carContext, provider)
        advanceUntilIdle()

        val titles = screen.onGetTemplate().singleList!!.items.map { (it as Row).title.toString() }

        assertEquals(
            listOf("Karte", "Navigation & Route", "Hinweise & Sprache"),
            titles
        )
        // No setting row is on the root: not one of the car's own row titles, and no current value
        // text beside a topic entry (a topic entry names a group, not a value).
        val settingTitles = PreferencesScreenMapper.rows(AutoSettings()).map { it.title }
        assertEquals(0, titles.count { it in settingTitles })
        screen.onGetTemplate().singleList!!.items.forEach { item ->
            assertEquals(0, (item as Row).texts.orEmpty().size)
        }
    }

    @Test
    fun placeholderUntilLoadCompletes() = runTest(testDispatcher) {
        coEvery { provider.load() } coAnswers { delay(300); AutoSettings() }

        val screen = PreferencesScreen(carContext, provider)

        // Before the load finishes the single row is the loading placeholder.
        assertEquals(1, screen.onGetTemplate().singleList!!.items.size)
        assertEquals("Loading...", (screen.onGetTemplate().singleList!!.items[0] as Row).title.toString())

        advanceTimeBy(400)
        advanceUntilIdle()
        assertEquals(3, screen.onGetTemplate().singleList!!.items.size)
    }

    @Test
    fun loadFailureShowsErrorRowWithRetry() = runTest(testDispatcher) {
        coEvery { provider.load() } throws RuntimeException("storage down")

        val screen = PreferencesScreen(carContext, provider)
        advanceUntilIdle()

        val items = screen.onGetTemplate().singleList!!.items
        assertEquals(2, items.size)
        assertEquals("Settings unavailable", (items[0] as Row).title.toString())
        assertEquals("Retry", (items[1] as Row).title.toString())
    }

    @Test
    fun retryAfterFailureLoadsContent() = runTest(testDispatcher) {
        var loadCalls = 0
        coEvery { provider.load() } answers {
            if (++loadCalls == 1) throw RuntimeException("storage down") else AutoSettings()
        }

        val screen = PreferencesScreen(carContext, provider)
        advanceUntilIdle()
        assertEquals(2, screen.onGetTemplate().singleList!!.items.size) // error row + retry

        screen.onRetry()
        advanceUntilIdle()

        assertEquals(3, screen.onGetTemplate().singleList!!.items.size)
    }

    @Test
    fun stalledLoadEndsInErrorThenLateContentRecovers() = runTest(testDispatcher) {
        // Load hangs far beyond the guard's watchdog budget.
        coEvery { provider.load() } coAnswers { delay(10_000); AutoSettings() }

        val screen = PreferencesScreen(carContext, provider)

        // First watchdog window (t=2000): one re-invalidate, still placeholder.
        advanceTimeBy(2_000L)
        assertEquals(1, screen.onGetTemplate().singleList!!.items.size)

        // t=5000: second recovery attempt done, budget not yet exhausted.
        advanceTimeBy(3_000L)
        assertEquals(1, screen.onGetTemplate().singleList!!.items.size)

        // t=7000: budget exhausted — the error row replaces the placeholder
        // even though the load has not returned yet.
        advanceTimeBy(2_000L)
        assertEquals(2, screen.onGetTemplate().singleList!!.items.size)

        // The load finally completes (t=10000): content comes back, error clears.
        advanceUntilIdle()
        assertEquals(3, screen.onGetTemplate().singleList!!.items.size)
    }
}