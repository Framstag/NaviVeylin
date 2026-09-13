package com.naviveylin.auto

import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.AutoSettingsProvider
import com.naviveylin.core.VehicleAnchorPosition
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
        coEvery { provider.save(any()) } returns Unit

        val screen = VehicleAnchorPickerScreen(carContext, provider, VehicleAnchorPickerScreen.Mode.ROUTING)
        advanceUntilIdle()

        screen.persistSelection(VehicleAnchorPosition.TOP_CENTER.id)
        advanceUntilIdle()

        coVerify { provider.save(AutoSettings(routingAnchorId = VehicleAnchorPosition.TOP_CENTER.id)) }
    }

    @Test
    fun freeDrivingSelectionPersistsToFreeDrivingAnchor() = runTest(testDispatcher) {
        coEvery { provider.load() } returns AutoSettings()
        coEvery { provider.save(any()) } returns Unit

        val screen = VehicleAnchorPickerScreen(carContext, provider, VehicleAnchorPickerScreen.Mode.FREE_DRIVING)
        advanceUntilIdle()

        screen.persistSelection(VehicleAnchorPosition.BOTTOM_FAR_LEFT.id)
        advanceUntilIdle()

        coVerify { provider.save(AutoSettings(freeDrivingAnchorId = VehicleAnchorPosition.BOTTOM_FAR_LEFT.id)) }
    }

    @Test
    fun anchorRowTitleIsLabelWithCurrentMarker() {
        val screen = VehicleAnchorPickerScreen(carContext, provider)
        assertEquals("Center", screen.anchorRowTitle(VehicleAnchorPosition.CENTER, selected = false))
        assertEquals("Bottom far right (current)", screen.anchorRowTitle(VehicleAnchorPosition.BOTTOM_FAR_RIGHT, selected = true))
    }
}
