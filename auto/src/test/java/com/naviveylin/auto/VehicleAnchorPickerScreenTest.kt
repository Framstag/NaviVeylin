package com.naviveylin.auto

import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.AutoSettingsProvider
import com.naviveylin.core.VehicleAnchorPosition
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
 * Tests for [VehicleAnchorPickerScreen]: the 15-preset 5×3 grid list with the
 * current anchor marked, and the selection persisting to the shared settings
 * for its mode (spec: auto-map-layout — "Anchor rows shown for both modes",
 * "Anchor picker shows the 5×3 grid", "Anchor selection persisted globally").
 * The provider is injected via the test constructor so no Hilt entry point is
 * resolved; selection persistence is verified through [persistSelection]
 * because an unattached [androidx.car.app.Screen] cannot pop its stack.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VehicleAnchorPickerScreenTest {

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
    fun listOffersAllFifteenPresets() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings()

        val screen = VehicleAnchorPickerScreen(carContext, provider, VehicleAnchorPickerScreen.Mode.ROUTING)
        advanceUntilIdle()

        val items = (screen.onGetTemplate().singleList as ItemList).items

        assertEquals(15, items.size)
        assertEquals(
            VehicleAnchorPosition.entries.first().label,
            (items.first() as Row).title.toString()
        )
        assertEquals(
            VehicleAnchorPosition.entries.last().label,
            (items.last() as Row).title.toString()
        )
    }

    @Test
    fun placeholderUntilLoadCompletes() = runTest(testDispatcher) {
        coEvery { provider.load() } coAnswers { delay(300); AutoSettings() }

        val screen = VehicleAnchorPickerScreen(carContext, provider, VehicleAnchorPickerScreen.Mode.ROUTING)
        assertEquals(1, (screen.onGetTemplate().singleList as ItemList).items.size)

        advanceTimeBy(400)
        advanceUntilIdle()
        assertEquals(15, (screen.onGetTemplate().singleList as ItemList).items.size)
    }

    @Test
    fun loadFailureShowsErrorRowThenRetryLoadsContent() = runTest(testDispatcher) {
        var loadCalls = 0
        coEvery { provider.load() } answers {
            if (++loadCalls == 1) throw RuntimeException("storage down") else AutoSettings()
        }

        val screen = VehicleAnchorPickerScreen(carContext, provider, VehicleAnchorPickerScreen.Mode.ROUTING)
        advanceUntilIdle()

        val errorItems = (screen.onGetTemplate().singleList as ItemList).items
        assertEquals(2, errorItems.size)
        assertEquals("Settings unavailable", (errorItems[0] as Row).title.toString())
        assertEquals("Retry", (errorItems[1] as Row).title.toString())

        screen.onRetry()
        advanceUntilIdle()
        assertEquals(15, (screen.onGetTemplate().singleList as ItemList).items.size)
    }

    @Test
    fun currentAnchorIsMarked() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings(routingAnchorId = VehicleAnchorPosition.BOTTOM_RIGHT.id)

        val screen = VehicleAnchorPickerScreen(carContext, provider, VehicleAnchorPickerScreen.Mode.ROUTING)
        advanceUntilIdle()

        val titles = (screen.onGetTemplate().singleList as ItemList).items.map { (it as Row).title.toString() }
        val marked = titles.filter { it.endsWith("(current)") }

        assertEquals(1, marked.size)
        assertEquals("Bottom right (current)", marked[0])
        // Unmarked presets carry no marker (e.g. the first row).
        assertEquals(VehicleAnchorPosition.entries.first().label, titles.first())
    }

    @Test
    fun routingSelectionPersistsToRoutingAnchor() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings()
        coEvery { provider.saveCarAnchor(any(), any()) } returns Unit

        val screen = VehicleAnchorPickerScreen(carContext, provider, VehicleAnchorPickerScreen.Mode.ROUTING)
        advanceUntilIdle()

        screen.persistSelection(VehicleAnchorPosition.TOP_CENTER.id)
        advanceUntilIdle()

        // The car's OWN routing anchor is frozen (per-surface storage); the phone's
        // anchors are not part of this write (spec: auto-map-layout).
        coVerify {
            provider.saveCarAnchor(
                routingAnchorId = VehicleAnchorPosition.TOP_CENTER.id,
                freeDrivingAnchorId = null
            )
        }
        coVerify(exactly = 0) { provider.save(any()) }
    }

    @Test
    fun freeDrivingSelectionPersistsToFreeDrivingAnchor() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings()
        coEvery { provider.saveCarAnchor(any(), any()) } returns Unit

        val screen = VehicleAnchorPickerScreen(carContext, provider, VehicleAnchorPickerScreen.Mode.FREE_DRIVING)
        advanceUntilIdle()

        screen.persistSelection(VehicleAnchorPosition.BOTTOM_FAR_LEFT.id)
        advanceUntilIdle()

        coVerify {
            provider.saveCarAnchor(
                routingAnchorId = null,
                freeDrivingAnchorId = VehicleAnchorPosition.BOTTOM_FAR_LEFT.id
            )
        }
        coVerify(exactly = 0) { provider.save(any()) }
    }

    @Test
    fun anchorRowTitleIsLabelWithCurrentMarker() {
        val screen = VehicleAnchorPickerScreen(carContext, provider)
        assertEquals("Center", screen.anchorRowTitle(VehicleAnchorPosition.CENTER, selected = false))
        assertEquals("Bottom far right (current)", screen.anchorRowTitle(VehicleAnchorPosition.BOTTOM_FAR_RIGHT, selected = true))
    }

    // ── persist-before-pop (spec: auto-map-layout — selection survives
    // immediate dismissal) ──

    @Test
    fun selectionPopsOnlyAfterPersistSucceeds() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings()
        coEvery { provider.saveCarAnchor(any(), any()) } returns Unit

        val screen = spyk(VehicleAnchorPickerScreen(carContext, provider, VehicleAnchorPickerScreen.Mode.ROUTING))
        every { screen.finishSelection() } just Runs
        advanceUntilIdle()

        screen.onSelect(VehicleAnchorPosition.BOTTOM_RIGHT.id)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            provider.saveCarAnchor(
                routingAnchorId = VehicleAnchorPosition.BOTTOM_RIGHT.id,
                freeDrivingAnchorId = null
            )
        }
        verify(exactly = 1) { screen.finishSelection() }
    }

    @Test
    fun doubleTapIgnoresSecondSelectionWhilePersisting() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings()
        coEvery { provider.saveCarAnchor(any(), any()) } coAnswers { delay(200); Unit }

        val screen = spyk(VehicleAnchorPickerScreen(carContext, provider, VehicleAnchorPickerScreen.Mode.ROUTING))
        every { screen.finishSelection() } just Runs
        advanceUntilIdle()

        screen.onSelect(VehicleAnchorPosition.BOTTOM_RIGHT.id)
        screen.onSelect(VehicleAnchorPosition.TOP_CENTER.id)

        advanceTimeBy(100)
        coVerify(exactly = 1) { provider.saveCarAnchor(any(), any()) }
        advanceUntilIdle()
        verify(exactly = 1) { screen.finishSelection() }
    }

    @Test
    fun failedPersistKeepsPickerOpenWithErrorRow() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings()
        coEvery { provider.saveCarAnchor(any(), any()) } throws RuntimeException("write failed")

        val screen = spyk(VehicleAnchorPickerScreen(carContext, provider, VehicleAnchorPickerScreen.Mode.ROUTING))
        every { screen.finishSelection() } just Runs
        advanceUntilIdle()

        screen.onSelect(VehicleAnchorPosition.BOTTOM_RIGHT.id)
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
