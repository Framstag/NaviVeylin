package com.naviveylin.auto

import androidx.car.app.model.Action
import androidx.car.app.model.Row
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.AutoSettingsProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [PreferenceChoiceScreen]: every permitted value is a row, the value in force is
 * marked, the pick is persisted as exactly that value and only then the screen pops
 * (spec: `auto/preferences` — "A value is chosen, never only cycled"), and a failed persist
 * keeps the options with a Retry that re-runs the pick.
 *
 * The pop is stubbed on a spy because an unattached screen cannot pop its stack — the same
 * pattern the anchor picker's persist-before-pop cases use.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PreferenceChoiceScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val carContext = testCarContext()
    private val provider = mockk<AutoSettingsProvider>()

    @Before
    fun setUp() {
        every { carContext.getOnBackPressedDispatcher() } returns mockk(relaxed = true)
    }

    private fun darkModeOptions(onDarkModeChanged: (String) -> Unit = {}) = spyk(
        PreferenceChoiceScreen(
            carContext = carContext,
            key = PreferencesScreenMapper.KEY_DARK_MODE,
            title = "Dark mode",
            choices = listOf(
                PreferenceChoice("ON", "On"),
                PreferenceChoice("AUTOMATIC", "Automatic"),
                PreferenceChoice("OFF", "Off")
            ),
            currentValue = "AUTOMATIC",
            settingsProvider = provider,
            onDarkModeChanged = onDarkModeChanged
        )
    )

    private fun titles(screen: PreferenceChoiceScreen): List<String> =
        screen.onGetTemplate().singleList!!.items.map { (it as Row).title.toString() }

    /**
     * Spec case: A value is chosen, never only cycled.
     */
    @Test
    fun everyOptionIsAListedRowAndTheCurrentOneIsMarked() = runTest(mainDispatcherRule.dispatcher) {
        val screen = darkModeOptions()

        val template = screen.onGetTemplate()

        assertEquals(
            listOf("On", "Automatic (current)", "Off"),
            titles(screen)
        )
        assertEquals("Dark mode", template.header!!.title.toString())
        assertEquals(Action.BACK, template.header!!.startHeaderAction)
    }

    @Test
    fun aPickedValueIsSavedExactlyAndDiffersFromACycle() = runTest(mainDispatcherRule.dispatcher) {
        // From AUTOMATIC a cycle would land on ON; the pick sets OFF and nothing else.
        coEvery { provider.load() } returns AutoSettings(darkMode = "AUTOMATIC")
        coEvery { provider.save(any()) } returns Unit
        val screen = darkModeOptions()
        every { screen.finishSelection() } just Runs

        screen.onSelect("OFF")
        advanceUntilIdle()

        coVerify { provider.save(AutoSettings(darkMode = "OFF")) }
        verify(exactly = 1) { screen.finishSelection() }
    }

    @Test
    fun aPickOfTheValueInForceSavesNothingAndStillPops() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { provider.load() } returns AutoSettings(darkMode = "OFF")
        val screen = darkModeOptions()
        every { screen.finishSelection() } just Runs

        screen.onSelect("OFF")
        advanceUntilIdle()

        coVerify(exactly = 0) { provider.save(any()) }
        verify(exactly = 1) { screen.finishSelection() }
    }

    @Test
    fun aDarkModePickReportsTheNewValueToTheSession() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { provider.load() } returns AutoSettings(darkMode = "AUTOMATIC")
        coEvery { provider.save(any()) } returns Unit
        val reported = mutableListOf<String>()
        val screen = darkModeOptions(onDarkModeChanged = { reported.add(it) })
        every { screen.finishSelection() } just Runs

        screen.onSelect("OFF")
        advanceUntilIdle()

        assertEquals(listOf("OFF"), reported)
    }

    @Test
    fun aFailedSaveKeepsTheOptionsOpenWithARetryThatReRunsThePick() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { provider.load() } returns AutoSettings(darkMode = "AUTOMATIC")
        var saveCalls = 0
        coEvery { provider.save(any()) } answers {
            if (++saveCalls == 1) throw RuntimeException("storage down")
        }
        val screen = darkModeOptions()
        every { screen.finishSelection() } just Runs

        screen.onSelect("OFF")
        advanceUntilIdle()

        // The failure is shown where the driver still is: an error row, a Retry row, and no pop —
        // the pick is not lost, the Retry carries it.
        assertEquals(listOf("Settings unavailable", "Retry"), titles(screen))
        verify(exactly = 0) { screen.finishSelection() }

        screen.onRetry()
        advanceUntilIdle()

        assertEquals(2, saveCalls)
        verify(exactly = 1) { screen.finishSelection() }
    }

    @Test
    fun aMapStylePickCarriesTheLiveStyleList() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { provider.load() } returns AutoSettings(styleSheet = "standard")
        coEvery { provider.save(any()) } returns Unit

        val screen = PreferenceChoiceScreen(
            carContext = carContext,
            key = "styleSheet",
            title = "Map style",
            choices = listOf(PreferenceChoice("cycle", "cycle"), PreferenceChoice("standard", "standard")),
            currentValue = "standard",
            settingsProvider = provider,
            stylesLoader = { listOf("cycle", "standard") }
        )
        assertTrue(titles(screen).contains("standard (current)"))

        screen.onSelect("cycle")
        advanceUntilIdle()

        coVerify { provider.save(AutoSettings(styleSheet = "cycle")) }
    }
}