package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The start-up read of the persisted settings is a suspension point, so a dark-mode change the user
 * makes while it runs must survive it (spec: `dark-mode` — A user change outranks an in-flight
 * settings read).
 *
 * The read is placed on a scheduler of its own, which is what turns "the read completes after the
 * change" into an ordering this case decides instead of a race between a real thread pool and the
 * test's scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DarkModeLateRestoreTest {

    private lateinit var context: Context
    private lateinit var readScheduler: TestCoroutineScheduler
    private lateinit var controller: DarkModeController
    private var viewModel: MapCanvasViewModel? = null

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        readScheduler = TestCoroutineScheduler()
    }

    @After
    fun tearDown() {
        viewModel?.cancelScopeForTest()
    }

    @Test
    fun aChangeThatLandsBeforeTheReadKeepsBothFields() = runTest(mainDispatcherRule.dispatcher) {
        // Persisted: Automatic and the sensor off — the values the read carries.
        writeSettings(AppSettings())
        startViewModels()

        // The user changes both fields while the read is still open.
        val vm = viewModel!!
        vm.onSetDarkModePreference(DarkModePreference.ON)
        vm.onSetAmbientLightSensitivity(AmbientLightSensitivity.HIGH)

        // The read completes now, after the change.
        completeTheRead()

        assertEquals(
            "the persisted Automatic must not replace the preference the user just set",
            DarkModePreference.ON,
            controller.preference.value
        )
        assertEquals(
            "the persisted Off must not replace the sensitivity the user just set",
            AmbientLightSensitivity.HIGH,
            controller.sensorSensitivity.value
        )
        assertEquals(DarkModePreference.ON, vm.uiState.value.darkModePreference)
        assertEquals(AmbientLightSensitivity.HIGH, vm.uiState.value.ambientLightSensitivity)
    }

    @Test
    fun untouchedFieldsAreStillRestoredFromDisk() = runTest(mainDispatcherRule.dispatcher) {
        writeSettings(
            AppSettings(
                darkMode = DarkModePreference.OFF,
                ambientLightSensitivity = AmbientLightSensitivity.MEDIUM
            )
        )
        startViewModels()

        completeTheRead()

        assertEquals(
            "a field the user did not change is restored from disk",
            DarkModePreference.OFF,
            controller.preference.value
        )
        assertEquals(AmbientLightSensitivity.MEDIUM, controller.sensorSensitivity.value)
        assertEquals(DarkModePreference.OFF, viewModel!!.uiState.value.darkModePreference)
        assertEquals(
            AmbientLightSensitivity.MEDIUM,
            viewModel!!.uiState.value.ambientLightSensitivity
        )
    }

    /**
     * Build the controller and a ViewModel over one storage whose read is held on [readScheduler],
     * then drain the start-up work: it runs up to that read and suspends there.
     */
    private fun startViewModels() {
        val storage = SettingsStorage(context).also {
            it.ioDispatcher = StandardTestDispatcher(readScheduler)
        }
        controller = DarkModeController(storage)
        val client = FakeOSMScoutClient()
        val vm = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = storage,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context)
                .also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
            locationService = LocationService(context),
            darkModeController = controller,
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        vm.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel = vm
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    /** Let the held read run, then apply what it returned on the ViewModel's own scheduler. */
    private fun completeTheRead() {
        readScheduler.advanceUntilIdle()
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
}
