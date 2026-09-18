package com.naviveylin.auto

import androidx.car.app.model.ItemList
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
    fun placeholderUntilLoadCompletes() = runTest(testDispatcher) {
        coEvery { provider.load() } coAnswers { delay(300); AutoSettings() }

        val screen = OverspeedDeltaPickerScreen(carContext, provider)
        assertEquals(1, (screen.onGetTemplate().singleList as ItemList).items.size)

        advanceTimeBy(400)
        advanceUntilIdle()
        assertEquals(31, (screen.onGetTemplate().singleList as ItemList).items.size)
    }

    @Test
    fun loadFailureShowsErrorRowThenRetryLoadsContent() = runTest(testDispatcher) {
        var loadCalls = 0
        coEvery { provider.load() } answers {
            if (++loadCalls == 1) throw RuntimeException("storage down") else AutoSettings()
        }

        val screen = OverspeedDeltaPickerScreen(carContext, provider)
        advanceUntilIdle()

        val errorItems = (screen.onGetTemplate().singleList as ItemList).items
        assertEquals(2, errorItems.size)
        assertEquals("Settings unavailable", (errorItems[0] as Row).title.toString())
        assertEquals("Retry", (errorItems[1] as Row).title.toString())

        screen.onRetry()
        advanceUntilIdle()
        assertEquals(31, (screen.onGetTemplate().singleList as ItemList).items.size)
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

    // ── persist-before-pop (spec: auto-map-layout — selection survives
    // immediate dismissal). The spy's fields are copied at creation, so the
    // persist path is stubbed here and the sequencing of onSelect is what is
    // verified; the real persist is covered by the persistSelection tests. ──

    @Test
    fun selectionPopsOnlyAfterPersistSucceeds() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings()

        val screen = spyk(OverspeedDeltaPickerScreen(carContext, provider))
        every { screen.finishSelection() } just Runs
        var persisted: Int? = null
        coEvery { screen.persistSelection(any()) } coAnswers {
            persisted = firstArg()
            delay(100)
            Unit
        }
        advanceUntilIdle()

        screen.onSelect(7)

        // Still persisting after 50 ms: the picker must NOT pop yet.
        advanceTimeBy(50)
        verify(exactly = 0) { screen.finishSelection() }

        // Once the persist completed, the pop runs.
        advanceUntilIdle()
        assertEquals(7, persisted)
        verify(exactly = 1) { screen.finishSelection() }
    }

    @Test
    fun doubleTapIgnoresSecondSelectionWhilePersisting() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings()

        val screen = spyk(OverspeedDeltaPickerScreen(carContext, provider))
        every { screen.finishSelection() } just Runs
        var persistCalls = 0
        coEvery { screen.persistSelection(any()) } coAnswers {
            persistCalls++
            delay(200)
            Unit
        }
        advanceUntilIdle()

        screen.onSelect(7)
        screen.onSelect(9)

        advanceTimeBy(100)
        assertEquals(1, persistCalls)
        advanceUntilIdle()
        verify(exactly = 1) { screen.finishSelection() }
    }

    @Test
    fun failedPersistKeepsPickerOpenWithErrorRow() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings()

        val screen = spyk(OverspeedDeltaPickerScreen(carContext, provider))
        every { screen.finishSelection() } just Runs
        coEvery { screen.persistSelection(any()) } throws RuntimeException("write failed")
        advanceUntilIdle()

        screen.onSelect(7)
        advanceUntilIdle()

        // The picker does NOT pop; the guard error/retry row shows instead —
        // a dropped write is visible, never silent.
        verify(exactly = 0) { screen.finishSelection() }
        val items = (screen.onGetTemplate().singleList as ItemList).items
        assertEquals(2, items.size)
        assertEquals("Settings unavailable", (items[0] as Row).title.toString())
        assertEquals("Retry", (items[1] as Row).title.toString())
    }
}
