package com.naviveylin.ui.map

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.DescriptionEntry
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.ObjectDescription
import com.naviveylin.core.ProjectionUtils
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI
import kotlin.math.abs

/**
 * End-to-end long-press conversion: [fireLongPress] must convert the press
 * point with the angle-aware projection so the resolved geo point is under
 * the finger on a rotated map. No @Config — must run in the default
 * Robolectric sandbox so the FakeOSMScoutClient JNI stub loads correctly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapCanvasLongPressTest {

    private lateinit var context: Context
    private lateinit var viewModel: MapCanvasViewModel

    private val screenW = 1080
    private val screenH = 1920

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        val client = FakeOSMScoutClient()
        viewModel = MapCanvasViewModel(
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
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
        Dispatchers.resetMain()
    }

    private fun dpi(): Double = context.resources.displayMetrics.densityDpi.toDouble()

    @Test
    fun `long press on rotated viewport resolves geo under press point`() = runTest {
        val centerLat = 48.2
        val centerLon = 16.4
        val angle = PI / 2
        viewModel.updateCenter(centerLat, centerLon)
        viewModel.updateMagnification(10)
        viewModel.updateAngle(angle)

        val pos = Offset(300.0f, 700.0f)
        fireLongPress(viewModel, context, pos, IntSize(screenW, screenH))

        val expected = ProjectionUtils.viewport(
            centerLat, centerLon, 10, screenW, screenH, dpi(), angle
        ).screenToGeoRotated(pos.x.toDouble(), pos.y.toDouble())

        val entry = checkNotNull(viewModel.uiState.value.selectedLocation) {
            "long-press must set selectedLocation"
        }
        assertEquals(expected.first, entry.lat, 1e-6)
        assertEquals(expected.second, entry.lon, 1e-6)
        assertTrue(viewModel.uiState.value.isLongPress)
    }

    @Test
    fun `long press on rotated viewport differs from north-up conversion`() = runTest {
        val centerLat = 48.2
        val centerLon = 16.4
        val angle = PI / 2
        viewModel.updateCenter(centerLat, centerLon)
        viewModel.updateMagnification(10)
        viewModel.updateAngle(angle)

        val pos = Offset(300.0f, 700.0f)
        fireLongPress(viewModel, context, pos, IntSize(screenW, screenH))

        val northUp = ProjectionUtils.screenToGeo(
            pos.x.toDouble(), pos.y.toDouble(),
            screenW, screenH, 10,
            centerLat, centerLon, dpi()
        )
        val entry = checkNotNull(viewModel.uiState.value.selectedLocation)
        // Regression guard: with the old north-up conversion this test fails.
        assertTrue(
            "rotated conversion must differ from north-up",
            abs(entry.lat - northUp.first) > 1e-3 || abs(entry.lon - northUp.second) > 1e-3
        )
    }

    @Test
    fun `long press on north-up viewport matches north-up conversion`() = runTest {
        val centerLat = 48.2
        val centerLon = 16.4
        viewModel.updateCenter(centerLat, centerLon)
        viewModel.updateMagnification(10)

        val pos = Offset(300.0f, 700.0f)
        fireLongPress(viewModel, context, pos, IntSize(screenW, screenH))

        val expected = ProjectionUtils.screenToGeo(
            pos.x.toDouble(), pos.y.toDouble(),
            screenW, screenH, 10,
            centerLat, centerLon, dpi()
        )
        val entry = checkNotNull(viewModel.uiState.value.selectedLocation)
        assertEquals(expected.first, entry.lat, 1e-6)
        assertEquals(expected.second, entry.lon, 1e-6)
    }

    @Test
    fun `long press with candidates shows picker instead of details`() = runTest {
        val client = FakeOSMScoutClient()
        client.nextCandidateDescriptions = listOf(
            ObjectDescription(
                listOf(DescriptionEntry().apply {
                    sectionKey = "General"
                    labelKey = "Name"
                    value = "Hotel Central"
                }),
                48.21, 16.41, "area", "tourism_hotel", 100L
            )
        )
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
        vm.updateCenter(48.2, 16.4)
        vm.updateMagnification(10)

        vm.onLongPress(48.2, 16.4)
        vm.uiState.first { it.showCandidatePicker }

        assertTrue(vm.uiState.value.showCandidatePicker)
        assertTrue(!vm.uiState.value.showDetailsSheet)
        assertEquals(1, vm.uiState.value.candidateDescriptions.size)
    }

    @Test
    fun `long press with no candidates shows no picker`() = runTest {
        val client = FakeOSMScoutClient()
        client.nextCandidateDescriptions = emptyList()
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
        vm.updateCenter(48.2, 16.4)
        vm.updateMagnification(10)

        vm.onLongPress(48.2, 16.4)
        vm.uiState.first { !it.isLoading }

        assertTrue(!vm.uiState.value.showCandidatePicker)
        assertTrue(!vm.uiState.value.showDetailsSheet)
    }
}
