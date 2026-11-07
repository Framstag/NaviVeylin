package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.data.AppSettings
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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
 * The start-up read of the persisted settings is a suspension point, so an orientation the user picks
 * while it runs must survive it (spec: `compass-settings` — A user orientation choice outranks an
 * in-flight settings read).
 *
 * The read is placed on a scheduler of its own, which is what turns "the read completes after the
 * choice" into an ordering this case decides instead of a race between a real thread pool and the
 * test's scheduler. That race is what reddened `main` on 2026-11-05: the read of the persisted
 * follow-direction value landed between the compass long press and the next fix, and the map rotated
 * back to the heading-up angle the user had just left.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NorthUpLateRestoreTest {

    private lateinit var context: Context
    private lateinit var readScheduler: TestCoroutineScheduler
    private lateinit var locationService: LocationService
    private lateinit var viewModel: MapCanvasViewModel
    private var fakeNowMs = 1_000L

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        readScheduler = TestCoroutineScheduler()
    }

    @After
    fun tearDown() {
        if (this::viewModel.isInitialized) viewModel.cancelScopeForTest()
    }

    @Test
    fun northUpChosenWhileTheReadIsOpenSurvivesTheRead() = runTest(mainDispatcherRule.dispatcher) {
        // Persisted: follow direction (the app default).
        writeSettings(AppSettings(navNorthUp = false))
        startTheViewModel()

        // Heading-up while driving, then the compass long press: "always north".
        viewModel.onToggleFollowMode(true)
        viewModel.onSetNavOrientation(false)
        locationService.setGpsFixForTest(gpsFix(51.5136, 7.4653, smoothed = 270.0, marker = 270.0, time = 1_000L))
        viewModel.uiState.first { it.gpsLocation?.time == 1_000L }
        assertEquals(Math.toRadians(90.0), viewModel.uiState.value.viewport.angle, 1e-6)

        viewModel.onSetNavOrientation(true)
        assertEquals(0.0, viewModel.uiState.value.viewport.angle, 1e-9)

        // The start-up read completes now, after the choice.
        completeTheRead()

        assertTrue(
            "the persisted follow-direction value must not replace the orientation the user just chose",
            viewModel.uiState.value.navNorthUp
        )

        // A fix with a bearing must not rotate the map back to the heading-up angle.
        fakeNowMs += 250L
        locationService.setGpsFixForTest(gpsFix(51.5156, 7.4673, smoothed = 270.0, marker = 270.0, time = 2_000L))
        viewModel.uiState.first { it.gpsLocation?.time == 2_000L }
        assertEquals(
            "north-up must hold after the start-up read completes",
            0.0, viewModel.uiState.value.viewport.angle, 1e-9
        )
    }

    @Test
    fun orientationTheUserDidNotChooseIsRestoredFromDisk() = runTest(mainDispatcherRule.dispatcher) {
        writeSettings(AppSettings(navNorthUp = true, freeFormNorthUp = false))
        startTheViewModel()

        completeTheRead()

        assertTrue(
            "an orientation the user did not change is restored from disk",
            viewModel.uiState.value.navNorthUp
        )
        assertFalse(
            "a persisted orientation the user did not change must reach the state, not the default",
            viewModel.uiState.value.freeFormNorthUp
        )
    }

    /**
     * Build a ViewModel over a storage whose read is held on [readScheduler], then drain the start-up
     * work: it runs up to that read and suspends there.
     */
    private fun startTheViewModel() {
        val storage = SettingsStorage(context).also {
            it.ioDispatcher = StandardTestDispatcher(readScheduler)
        }
        val client = FakeOSMScoutClient()
        locationService = LocationService(context)
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = storage,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context)
                .also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
            locationService = locationService,
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            timeSource = EngineTimeSource { fakeNowMs },
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
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

    private fun gpsFix(
        lat: Double, lon: Double,
        smoothed: Double, marker: Double,
        time: Long
    ) = GpsFix(
        lat = lat, lon = lon,
        accuracy = 8.0,
        speedKmH = Double.NaN,
        smoothedBearing = smoothed,
        markerBearing = marker,
        time = time
    )
}
