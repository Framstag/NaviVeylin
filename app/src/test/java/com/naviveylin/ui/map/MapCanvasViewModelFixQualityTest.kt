package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the GPS fix quality of [MapCanvasViewModel] (spec: `gps-fix-quality`).
 *
 * The quality used to be computed only when a fix arrived, so it stayed latched at its last tier
 * forever while the location source was silent — location services switched off, or the
 * minimum-distance throttling at standstill. These cases age a fix out, disable the source and
 * restore a fix **without delivering a new fix in between**, which is exactly what the old code
 * could not see.
 *
 * Deterministic: the tick dispatcher, the tick period, the age limit and the clock are pointed at
 * the test's scheduler/state, so no case waits real seconds or races a thread pool.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapCanvasViewModelFixQualityTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var viewModel: MapCanvasViewModel

    /** The clock the quality reads; moved by a case to age the last fix out. */
    private var fakeNow = 1_700_000_000_000L

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
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel.fixQualityTickDispatcher = mainDispatcherRule.dispatcher
        viewModel.fixQualityTickMs = 50L
        viewModel.fixAgeLimitMs = 200L
        viewModel.nowMs = { fakeNow }
        // Robolectric starts with location services off; the platform read itself is covered by
        // LocationServiceTest — a case flips this to false for the disabled-source case.
        locationService.setLocationSourceReadForTest { true }
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    /** Delivers a fix with the given accuracy, timestamped at the test clock. */
    private fun installFix(accuracy: Double) {
        locationService.setGpsFixForTest(
            GpsFix(
                lat = 51.5136,
                lon = 7.4653,
                accuracy = accuracy,
                speedKmH = 0.0,
                smoothedBearing = Double.NaN,
                markerBearing = Double.NaN,
                time = fakeNow
            )
        )
    }

    private fun quality(): GpsFixQuality = viewModel.uiState.value.gpsFixQuality

    /**
     * Runs a case and cancels the ViewModel's scope before `runTest` returns: the fix-quality tick is
     * an endless delay loop on this scheduler, so a live scope would keep the scheduler non-idle and
     * hang the test (the rule the stale-speed ticker documents, `TODO.md` §40.C.16).
     */
    private fun fixQualityTest(body: suspend TestScope.() -> Unit): Unit =
        runTest(mainDispatcherRule.dispatcher) {
            try {
                body()
            } finally {
                viewModel.cancelScopeForTest()
            }
        }

    @Test
    fun aGoodFixReportsGood() = fixQualityTest {
        installFix(accuracy = 10.0)
        advanceTimeBy(2_500)

        assertEquals(GpsFixQuality.GOOD, quality())
    }

    @Test
    fun aPoorAccuracyReportsPoor() = fixQualityTest {
        installFix(accuracy = 80.0)
        advanceTimeBy(2_500)

        assertEquals(GpsFixQuality.POOR, quality())
    }

    @Test
    fun noFixAtAllIsNone() = fixQualityTest {
        advanceTimeBy(2_500)

        assertEquals(GpsFixQuality.NONE, quality())
    }

    @Test
    fun anAgedOutFixBecomesNoneWithoutANewFix() = fixQualityTest {
        installFix(accuracy = 10.0)
        advanceTimeBy(2_500)
        assertEquals(GpsFixQuality.GOOD, quality())

        // The fix ages out. No new fix is delivered — the old code could not see this at all.
        fakeNow += 1_000L
        advanceTimeBy(2_500)

        assertEquals(GpsFixQuality.NONE, quality())
    }

    @Test
    fun aDisabledSourceIsNoneWithoutANewFix() = fixQualityTest {
        installFix(accuracy = 10.0)
        advanceTimeBy(2_500)
        assertEquals(GpsFixQuality.GOOD, quality())

        // The platform reports that location services are switched off; the fix itself stays young.
        locationService.setLocationSourceReadForTest { false }
        advanceTimeBy(2_500)

        assertEquals(GpsFixQuality.NONE, quality())
    }

    @Test
    fun aNewFixRestoresTheTier() = fixQualityTest {
        installFix(accuracy = 10.0)
        advanceTimeBy(2_500)
        assertEquals(GpsFixQuality.GOOD, quality())

        fakeNow += 1_000L
        advanceTimeBy(2_500)
        assertEquals(GpsFixQuality.NONE, quality())

        installFix(accuracy = 10.0)
        advanceTimeBy(2_500)

        assertEquals(GpsFixQuality.GOOD, quality())
    }

    @Test
    fun anUnchangedQualityPublishesNoNewValue() = fixQualityTest {
        val published = mutableListOf<GpsFixQuality>()
        val job = backgroundScope.launch {
            viewModel.uiState
                .map { it.gpsFixQuality }
                .distinctUntilChanged()
                .collect { published += it }
        }

        installFix(accuracy = 10.0)
        advanceTimeBy(2_500)
        assertEquals(listOf(GpsFixQuality.NONE, GpsFixQuality.GOOD), published)

        // Several ticks with nothing changing must not republish the quality.
        advanceTimeBy(2_000)
        assertEquals(listOf(GpsFixQuality.NONE, GpsFixQuality.GOOD), published)

        job.cancel()
    }

    /**
     * Every fix-quality consumer resolves the same value (spec: `gps-fix-quality` — One definition for
     * every fix-quality consumer). The inventory (grep of `gpsFixQuality`) is: compass fill
     * (`CompassButton.compassFillColor`), the re-center gates (`MapCanvasScreen.kt:1686`, `:1823`,
     * `:2318`, each `shouldShowReCenterButton(...) && quality != NONE`), the speed widget's
     * `gpsAvailable` (`:1891`), and the browse re-center derivation inside the ViewModel (`:3095`).
     */
    @Test
    fun everyConsumerReadsTheSharedQuality() = fixQualityTest {
        installFix(accuracy = 10.0)
        advanceTimeBy(2_500)
        assertEquals(GpsFixQuality.GOOD, quality())

        locationService.setLocationSourceReadForTest { false }
        advanceTimeBy(2_500)

        val state = viewModel.uiState.value
        assertEquals(GpsFixQuality.NONE, state.gpsFixQuality)

        // The ViewModel's own browse re-center derivation reads the quality, not a fix age.
        assertFalse(state.browseReCenterVisible)

        // The screen's gate is `shouldShowReCenterButton(...) && quality != NONE`: NONE hides the
        // button in every mode, whatever the helper returns.
        assertFalse(
            MapCanvasViewModel.shouldShowReCenterButton(
                MapMode.BROWSE,
                driveSuspended = true,
                browseReCenterVisible = true
            ) && state.gpsFixQuality != GpsFixQuality.NONE
        )

        // Compass fill: the no-fix hue family is not the GOOD tone (the tone itself is pinned by
        // CompassPaletteTest) and the speed widget's `gpsAvailable` is the same comparison.
        assertNotEquals(
            compassFillColor(GpsFixQuality.GOOD, isDarkPresentation = false),
            compassFillColor(GpsFixQuality.NONE, isDarkPresentation = false)
        )
        assertFalse(state.gpsFixQuality != GpsFixQuality.NONE)
    }
}
