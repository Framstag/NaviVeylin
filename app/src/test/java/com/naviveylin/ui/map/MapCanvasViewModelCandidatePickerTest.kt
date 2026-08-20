package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.DescriptionEntry
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.ObjectDescription
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Candidate-picker flow (spec: long-press-candidate-picker): long-press with
 * multiple objects shows the picker instead of details; selecting a candidate
 * opens details with the marker on the object; dismissing clears everything.
 * No @Config — must run in the default Robolectric sandbox so the
 * FakeOSMScoutClient JNI stub loads correctly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelCandidatePickerTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private var viewModel: MapCanvasViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
    }

    @After
    fun tearDown() {
        viewModel?.cancelScopeForTest()
        viewModel = null
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MapCanvasViewModel {
        val vm = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            darkModeController = DarkModeController(SettingsStorage(context)),
            context = context
        )
        viewModel = vm
        return vm
    }

    private fun candidate(
        name: String,
        type: String,
        lat: Double = Double.NaN,
        lon: Double = Double.NaN,
        offset: Long = 0L
    ): ObjectDescription {
        val entries = mutableListOf<DescriptionEntry>()
        if (name.isNotEmpty()) {
            entries.add(DescriptionEntry().apply {
                sectionKey = "General"
                labelKey = "Name"
                value = name
            })
        }
        entries.add(DescriptionEntry().apply {
            sectionKey = "General"
            labelKey = "Type"
            value = type
        })
        return ObjectDescription(entries, lat, lon, "area", type, offset)
    }

    @Test
    fun `long press with candidates shows picker not details`() = runTest {
        val vm = createViewModel()
        client.nextCandidateDescriptions = listOf(
            candidate("Hotel Central", "tourism_hotel", 51.51, 7.41, 100L),
            candidate("Cafe", "amenity_cafe", 51.52, 7.42, 200L)
        )

        vm.onLongPress(51.5, 7.4)
        vm.uiState.first { it.showCandidatePicker }

        val s = vm.uiState.value
        assertTrue(s.showCandidatePicker)
        assertFalse(s.showDetailsSheet)
        assertEquals(2, s.candidateDescriptions.size)
        assertEquals("tourism_hotel", s.candidateDescriptions[0].objectTypeName)
        assertNull(s.objectDescription)
    }

    @Test
    fun `long press with no candidates shows no picker`() = runTest {
        val vm = createViewModel()
        client.nextCandidateDescriptions = emptyList()

        vm.onLongPress(51.5, 7.4)
        vm.uiState.first { !it.isLoading }

        val s = vm.uiState.value
        assertFalse(s.showCandidatePicker)
        assertFalse(s.showDetailsSheet)
        assertTrue(s.candidateDescriptions.isEmpty())
        assertNull(s.objectDescription)
        // Coordinate entry still selected (existing "no objects" behavior).
        assertEquals(51.5, s.selectedLocation?.lat ?: Double.NaN, 1e-9)
    }

    @Test
    fun `selecting candidate opens details with marker on object`() = runTest {
        val vm = createViewModel()
        client.nextCandidateDescriptions = listOf(
            candidate("Hotel Central", "tourism_hotel", 51.51, 7.41, 100L)
        )

        vm.onLongPress(51.5, 7.4)
        vm.uiState.first { it.showCandidatePicker }

        val desc = vm.uiState.value.candidateDescriptions.first()
        vm.onCandidateSelected(desc)

        val s = vm.uiState.value
        assertFalse(s.showCandidatePicker)
        assertTrue(s.showDetailsSheet)
        assertTrue(s.isLongPress)
        assertEquals(desc, s.objectDescription)
        // Marker on the object center, not the press point.
        assertEquals(51.51, s.selectedLocation?.lat ?: Double.NaN, 1e-9)
        assertEquals(7.41, s.selectedLocation?.lon ?: Double.NaN, 1e-9)
    }

    @Test
    fun `selecting candidate without object coords falls back to press point`() = runTest {
        val vm = createViewModel()
        client.nextCandidateDescriptions = listOf(
            candidate("Unnamed Building", "building", offset = 300L)
        )

        vm.onLongPress(51.5, 7.4)
        vm.uiState.first { it.showCandidatePicker }

        val desc = vm.uiState.value.candidateDescriptions.first()
        vm.onCandidateSelected(desc)

        val s = vm.uiState.value
        assertTrue(s.showDetailsSheet)
        assertEquals(51.5, s.selectedLocation?.lat ?: Double.NaN, 1e-9)
        assertEquals(7.4, s.selectedLocation?.lon ?: Double.NaN, 1e-9)
    }

    @Test
    fun `dismissing picker clears candidates and opens no details`() = runTest {
        val vm = createViewModel()
        client.nextCandidateDescriptions = listOf(
            candidate("Hotel Central", "tourism_hotel", 51.51, 7.41, 100L)
        )

        vm.onLongPress(51.5, 7.4)
        vm.uiState.first { it.showCandidatePicker }

        vm.dismissCandidatePicker()

        val s = vm.uiState.value
        assertFalse(s.showCandidatePicker)
        assertFalse(s.showDetailsSheet)
        assertTrue(s.candidateDescriptions.isEmpty())
        assertNull(s.objectDescription)
        assertFalse(s.isLongPress)
    }
}
