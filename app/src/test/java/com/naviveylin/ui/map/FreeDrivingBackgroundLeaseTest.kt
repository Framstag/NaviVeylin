package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationConsumers
import com.naviveylin.location.LocationService
import com.naviveylin.navigation.DrivingModeProviderImpl
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The deliberate no-lease state of a backgrounded free drive (spec:
 * `location-updates-lease` — An invisible, non-navigating driving mode holds no
 * location lease). The phone screen's `ON_PAUSE` releases the map lease and
 * `ON_RESUME` re-acquires it ([MapCanvasViewModel.startLocationUpdates] /
 * [MapCanvasViewModel.stopLocationUpdates] are exactly those bodies); the ongoing
 * notification is not part of this lease and stays posted.
 *
 * No production change in [LocationService] is expected or made — the assertion is
 * an expectation on the existing behaviour. If it fails because something *does*
 * hold a lease, the pinned case is wrong and must be reported, not weakened.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class FreeDrivingBackgroundLeaseTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var logFile: File

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        locationService = LocationService(context)
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = locationService,
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            drivingModeProvider = DrivingModeProviderImpl(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher

        logFile = File(context.filesDir, "diagnostics/free-drive-lease-${System.nanoTime()}.log")
        DiagnosticsLog.reset()
        DiagnosticsLog.initForTest(logFile)
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
        DiagnosticsLog.reset()
        logFile.parentFile?.deleteRecursively()
    }

    /** Free driving is active but the surface is not visible: no lease may remain. */
    @Test
    fun backgroundedFreeDriveHoldsNoLeaseAndResumeAcquiresIt() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onToggleFollowMode(true)
        advanceUntilIdle()

        // ON_RESUME body.
        viewModel.startLocationUpdates()
        assertTrue(
            "a visible free-driving map holds the phone map lease",
            locationService.heldLeaseConsumers().contains(LocationConsumers.PHONE_MAP)
        )

        // ON_PAUSE body: the free-driving mode does not lease for the background.
        viewModel.stopLocationUpdates()

        assertTrue(
            "an invisible, non-navigating free drive holds no lease",
            locationService.heldLeaseConsumers().isEmpty()
        )
        assertEquals(0, locationService.heldLeaseCount())

        val lines = DiagnosticsLog.readEntries()
        assertTrue(
            "the diagnostics stream shows the release with a lease count of zero",
            lines.any { it.contains("location lease release: ${LocationConsumers.PHONE_MAP} (held=0)") }
        )

        // Returning to the foreground acquires the lease again and fixes resume.
        viewModel.startLocationUpdates()

        assertTrue(
            "a returning surface re-acquires the lease",
            locationService.heldLeaseConsumers().contains(LocationConsumers.PHONE_MAP)
        )
        assertTrue(
            "the diagnostics stream shows the re-acquire",
            DiagnosticsLog.readEntries().any {
                it.contains("location lease acquire: ${LocationConsumers.PHONE_MAP} (held=1)")
            }
        )
    }
}
