package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.data.AmbientLightSensitivity
import com.naviveylin.data.AppSettings
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.DarkModePreference
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
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
    private lateinit var settingsStorage: SettingsStorage
    private lateinit var controller: DarkModeController
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // The settings file lives in the sandbox and every case of this class — and of every class
        // in the JVM — writes it, so a start-up read that saw a predecessor's value would decide
        // this case's presentation state. Start each case from the defaults.
        writeSettings(AppSettings())
        client = FakeOSMScoutClient()
        // One storage instance (production wires it as a singleton), with the file IO on the test's
        // own clock: the start-up read is then drained by `advanceUntilIdle` below instead of
        // landing at an arbitrary point relative to the case's own dark-mode writes.
        settingsStorage = SettingsStorage(context).also {
            it.ioDispatcher = mainDispatcherRule.dispatcher
        }
        controller = DarkModeController(settingsStorage)
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = settingsStorage,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context).also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
            locationService = LocationService(context),
            darkModeController = controller,
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        // Settle init work (persisted-settings load applies its defaults to
        // uiState) so tests observe a stable starting state.
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    /** Writes the settings file directly, so the start-up read sees these values. */
    private fun writeSettings(settings: AppSettings) {
        val file = java.io.File(context.filesDir, "maps/settings.json")
        file.parentFile?.mkdirs()
        file.writeText(
            kotlinx.serialization.json.Json.encodeToString(AppSettings.serializer(), settings)
        )
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    @Test
    fun automaticFollowsEnvironmentSignal() = runTest(mainDispatcherRule.dispatcher) {
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
    fun preferenceOnForcesDark() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSetDarkModePreference(DarkModePreference.ON)
        assertTrue(viewModel.uiState.first { it.isDarkPresentation }.isDarkPresentation)
        assertTrue(client.styleFlags.any { it.first == "daylight" && !it.second })
        assertEquals(
            DarkModePreference.ON,
            viewModel.uiState.value.darkModePreference
        )
    }

    @Test
    fun preferenceOffForcesLight() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSetDarkModePreference(DarkModePreference.OFF)
        assertFalse(viewModel.uiState.first { !it.isDarkPresentation }.isDarkPresentation)
        assertTrue(client.styleFlags.any { it.first == "daylight" && it.second })
        assertEquals(
            DarkModePreference.OFF,
            viewModel.uiState.value.darkModePreference
        )
    }

    @Test
    fun preferenceOnBeatsLightEnvironment() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.setEnvironmentDark(false)
        viewModel.onSetDarkModePreference(DarkModePreference.ON)
        assertTrue(viewModel.uiState.first { it.isDarkPresentation }.isDarkPresentation)
    }

    @Test
    fun sensorWinsWhenSensitivityActive() = runTest(mainDispatcherRule.dispatcher) {
        // System light, sensor dark, sensitivity HIGH -> dark presentation.
        viewModel.setEnvironmentDark(false)
        viewModel.onSetAmbientLightSensitivity(AmbientLightSensitivity.HIGH)
        viewModel.setSensorDark(true)
        assertTrue(viewModel.uiState.first { it.isDarkPresentation }.isDarkPresentation)
        assertTrue(client.styleFlags.any { it.first == "daylight" && !it.second })
    }

    @Test
    fun sensorIgnoredWhenSensitivityOff() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.setEnvironmentDark(false)
        viewModel.setSensorDark(true)
        // Sensitivity OFF -> sensor classification ignored, system signal wins.
        assertFalse(viewModel.uiState.first { !it.isDarkPresentation }.isDarkPresentation)
    }

    @Test
    fun sensorInactiveFallsBackToSystem() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.setEnvironmentDark(true)
        viewModel.onSetAmbientLightSensitivity(AmbientLightSensitivity.MEDIUM)
        // Sensor reports null (inactive/unavailable) -> system signal wins.
        viewModel.setSensorDark(null)
        assertTrue(viewModel.uiState.first { it.isDarkPresentation }.isDarkPresentation)
    }

    @Test
    fun ambientSensitivityPersistsInUiState() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSetAmbientLightSensitivity(AmbientLightSensitivity.HIGH)
        assertEquals(AmbientLightSensitivity.HIGH, viewModel.uiState.value.ambientLightSensitivity)
        viewModel.onSetAmbientLightSensitivity(AmbientLightSensitivity.LOW)
        assertEquals(AmbientLightSensitivity.LOW, viewModel.uiState.value.ambientLightSensitivity)
        viewModel.onSetAmbientLightSensitivity(AmbientLightSensitivity.OFF)
        assertEquals(AmbientLightSensitivity.OFF, viewModel.uiState.value.ambientLightSensitivity)
    }

    @Test
    fun changedPresentationPushesTheFlagExactlyOnce() = runTest(mainDispatcherRule.dispatcher) {
        // This class' SettingsStorage runs its I/O on a real dispatcher, so the collector is
        // awaited through the published uiState rather than by advancing the scheduler: the
        // flag push is the next (non-suspending) statement of the same continuation that sets
        // the state.
        viewModel.setEnvironmentDark(true)
        assertTrue(viewModel.uiState.first { it.isDarkPresentation }.isDarkPresentation)
        val flagsAfterDark = client.styleFlags.size
        val loadsAfterDark = client.styleSheetLoads.size

        viewModel.setEnvironmentDark(false)
        assertFalse(viewModel.uiState.first { !it.isDarkPresentation }.isDarkPresentation)

        assertEquals(
            "one flag push for the changed presentation",
            flagsAfterDark + 1,
            client.styleFlags.size
        )
        assertEquals("the push carries the light variant", "daylight" to true, client.styleFlags.last())
        assertEquals(
            "a presentation change loads no stylesheet",
            loadsAfterDark,
            client.styleSheetLoads.size
        )
    }
}
