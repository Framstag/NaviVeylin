package com.naviveylin.auto

import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
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
 * Tests for [OverspeedDeltaPickerScreen]: the 0-30 value list with the
 * current delta marked, and the selection persisting to the shared settings
 * (spec: auto-map-layout — Overspeed delta presented as a value picker).
 * The provider is injected via the test constructor so no Hilt entry point
 * is resolved; selection persistence is verified through [persistSelection]
 * because an unattached [androidx.car.app.Screen] cannot pop its stack.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OverspeedDeltaPickerScreenTest {

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
    fun listOffersEveryWholeKmhFrom0To30() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings()

        val screen = OverspeedDeltaPickerScreen(carContext, provider)
        advanceUntilIdle()

        val template = screen.onGetTemplate()
        val items = (template.singleList as ItemList).items

        assertEquals(31, items.size)
        assertEquals("0 km/h", (items.first() as Row).title.toString())
        assertEquals("30 km/h", (items.last() as Row).title.toString())
    }

    @Test
    fun currentDeltaIsMarked() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings(overspeedWarningDeltaKmh = 5)

        val screen = OverspeedDeltaPickerScreen(carContext, provider)
        advanceUntilIdle()

        val items = (screen.onGetTemplate().singleList as ItemList).items
        val titles = items.map { (it as Row).title.toString() }
        val marked = titles.filter { it.endsWith("(current)") }

        assertEquals(1, marked.size)
        assertEquals("5 km/h (current)", marked[0])
        // Non-current values carry no marker (e.g. the 0 row).
        assertEquals("0 km/h", titles.first())
    }

    @Test
    fun selectionPersistsToSharedSettings() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings(overspeedWarningDeltaKmh = 5)
        coEvery { provider.save(any()) } returns Unit

        val screen = OverspeedDeltaPickerScreen(carContext, provider)
        advanceUntilIdle()

        screen.persistSelection(12)
        advanceUntilIdle()

        coVerify { provider.save(AutoSettings(overspeedWarningDeltaKmh = 12)) }
    }
}
