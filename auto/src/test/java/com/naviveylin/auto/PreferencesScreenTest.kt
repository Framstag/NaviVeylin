package com.naviveylin.auto

import androidx.car.app.CarContext
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.AutoSettingsProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * Tests for [PreferencesScreen]: template structure and the tap-to-toggle →
 * save flow. The provider is injected via the primary constructor so no Hilt
 * entry point is resolved in tests.
 *
 * Row content (titles/values) is covered by [PreferencesScreenMapperTest];
 * the row → [PreferencesScreen.onToggle] wiring is a one-line lambda.
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

        // All eight car-relevant preferences on the single list.
        assertEquals(8, template.singleList!!.items.size)
    }

    @Test
    fun onToggleSavesFlippedValue() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings(followMode = false)
        coEvery { provider.save(any()) } returns Unit

        val screen = PreferencesScreen(carContext, provider)
        advanceUntilIdle()
        screen.onToggle("followMode")
        advanceUntilIdle()

        coVerify { provider.save(AutoSettings(followMode = true)) }
    }

    @Test
    fun onToggleStyleSheetCyclesAndSaves() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings(styleSheet = "standard")
        coEvery { provider.save(any()) } returns Unit

        val screen = PreferencesScreen(carContext, provider)
        advanceUntilIdle()
        screen.onToggle("styleSheet")
        advanceUntilIdle()

        // DEFAULT_STYLES is sorted: standard → winter-sports.
        coVerify { provider.save(AutoSettings(styleSheet = "winter-sports")) }
    }
}
