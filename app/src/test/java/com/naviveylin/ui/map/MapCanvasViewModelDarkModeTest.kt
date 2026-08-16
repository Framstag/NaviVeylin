package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.DarkModePreference
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies that resolved dark presentation reaches the native client as a
 * `daylight` style flag push and is reflected in [MapCanvasUiState].
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelDarkModeTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var controller: DarkModeController
    private lateinit var viewModel: MapCanvasViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        controller = DarkModeController(SettingsStorage(context))
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            darkModeController = controller,
            context = context
        )
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
        Dispatchers.resetMain()
    }

    @Test
    fun automaticFollowsEnvironmentSignal() = runTest {
        // Initial: AUTOMATIC + environment light → light presentation
        assertFalse(viewModel.uiState.first { !it.isDarkPresentation }.isDarkPresentation)

        viewModel.setEnvironmentDark(true)
        assertTrue(viewModel.uiState.first { it.isDarkPresentation }.isDarkPresentation)
        assertTrue(client.styleFlags.any { it.first == "daylight" && !it.second })

        viewModel.setEnvironmentDark(false)
        assertFalse(viewModel.uiState.first { !it.isDarkPresentation }.isDarkPresentation)
        assertTrue(client.styleFlags.any { it.first == "daylight" && it.second })
    }

    @Test
    fun preferenceOnForcesDark() = runTest {
        viewModel.onSetDarkModePreference(DarkModePreference.ON)
        assertTrue(viewModel.uiState.first { it.isDarkPresentation }.isDarkPresentation)
        assertTrue(client.styleFlags.any { it.first == "daylight" && !it.second })
        assertEquals(
            DarkModePreference.ON,
            viewModel.uiState.value.darkModePreference
        )
    }

    @Test
    fun preferenceOffForcesLight() = runTest {
        viewModel.onSetDarkModePreference(DarkModePreference.OFF)
        assertFalse(viewModel.uiState.first { !it.isDarkPresentation }.isDarkPresentation)
        assertTrue(client.styleFlags.any { it.first == "daylight" && it.second })
        assertEquals(
            DarkModePreference.OFF,
            viewModel.uiState.value.darkModePreference
        )
    }

    @Test
    fun preferenceOnBeatsLightEnvironment() = runTest {
        viewModel.setEnvironmentDark(false)
        viewModel.onSetDarkModePreference(DarkModePreference.ON)
        assertTrue(viewModel.uiState.first { it.isDarkPresentation }.isDarkPresentation)
    }
}
