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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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

    private val carContext = mockk<CarContext>()
    private val provider = mockk<AutoSettingsProvider>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { carContext.getOnBackPressedDispatcher() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onGetTemplateBuildsWithoutThrowing() {
        coEvery { provider.load() } returns AutoSettings()

        val screen = PreferencesScreen(carContext, provider)
        val template = screen.onGetTemplate()

        assertEquals(1, template.sections.size)
        // All seven car-relevant preferences, delivered lazily via ListDelegate.
        assertEquals(7, template.sections[0].itemsDelegate.size)
    }

    @Test
    fun onToggleSavesFlippedValue() {
        coEvery { provider.load() } returns AutoSettings(followMode = false)
        coEvery { provider.save(any()) } returns Unit

        val screen = PreferencesScreen(carContext, provider)
        screen.onToggle("followMode")

        coVerify { provider.save(AutoSettings(followMode = true)) }
    }
}
