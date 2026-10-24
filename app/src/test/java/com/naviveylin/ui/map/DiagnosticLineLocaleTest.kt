package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.FollowPrediction
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.DefaultLocaleRule
import com.naviveylin.test.MainDispatcherRule
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/**
 * The phone half of the locale rule (spec: i18n-l10n — Log and diagnostic numbers are
 * locale-independent, issue #44): a log or diagnostic line is evidence a recipe greps, so its numbers
 * must not follow the device locale. With the default-locale formatter a German device printed
 * `acc=5,0` and `off=3,4,-1,4`, where the decimal comma reads as a field separator.
 *
 * Every case runs on a decimal-comma locale ([DefaultLocaleRule]), because that is the locale the rule
 * is about; the `follow` case adds the dot-locale line beside it, so the two are compared rather than
 * each being trusted on its own.
 *
 * The car sibling is asserted in `AutoMapRendererTest.followDiagnosticLineKeepsNumbersLocaleIndependent`
 * (spec: auto-diagnostics); this class holds the phone lines — the `follow` builder (the screen's line,
 * which no test can reach through a mount) and the GPS-fix line the ViewModel writes.
 *
 * Default Robolectric sandbox (no `@Config`) — this class instantiates [FakeOSMScoutClient] and
 * therefore loads the JNI stub, per the AGENTS.md rule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DiagnosticLineLocaleTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var viewModel: MapCanvasViewModel

    private var fakeNowMs: Long = System.currentTimeMillis()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val localeRule = DefaultLocaleRule(Locale.GERMANY)

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
            searchHistoryRepository = SearchHistoryRepository(context)
                .also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
            locationService = locationService,
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel.setScreenSize(SCREEN_W, SCREEN_H)
        viewModel.updateMagnification(MAG)
        locationService.setLocationSourceReadForTest { true }
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    // --- the follow line (the screen's builder) ---

    @Test
    fun theFollowLineKeepsItsNumbersOnADecimalCommaLocale() {
        val dbg = FollowPrediction.DebugState(
            avgSpeedMs = 8.0556,
            gpsSpeedMs = 8.3333,
            effectiveSpeedMs = 7.5,
            smoothAvgMs = 8.0,
            decelerating = false,
            stopped = false
        )
        val offset = FollowPrediction.Companion.DisplayOffset(
            rawX = 3.42, rawY = -1.44, clampedX = 3.42, clampedY = -1.44
        )

        val german = followDiagnosticLine(FIX_TIME, 50.0, 87.0, dbg, offset)
        val english = DefaultLocaleRule.withLocale(Locale.US) {
            followDiagnosticLine(FIX_TIME, 50.0, 87.0, dbg, offset)
        }

        assertEquals(
            "the same state must print the same line on a decimal-comma locale",
            english,
            german
        )
        assertEquals(
            "every decimal field prints with a dot, and the field set is unchanged",
            "follow t=1700000000000 spd=50.0 brg=87 avg=29.0 gps=30.0 eff=27.0 savg=28.8 " +
                "dec=false stp=false off=3.4,-1.4 clamped=false",
            german
        )
    }

    @Test
    fun theFollowLineKeepsItsUnknownFields() {
        // A missing speed or bearing stays "-", and no number is invented for it. The average fields
        // carry no placeholder of their own (they print `NaN`), which this case pins unchanged.
        val line = followDiagnosticLine(
            FIX_TIME, Double.NaN, Double.NaN,
            FollowPrediction.DebugState(
                avgSpeedMs = Double.NaN,
                gpsSpeedMs = Double.NaN,
                effectiveSpeedMs = 0.0,
                smoothAvgMs = 0.0,
                decelerating = false,
                stopped = true
            ),
            FollowPrediction.Companion.DisplayOffset(
                rawX = 0.0, rawY = 0.0, clampedX = 0.0, clampedY = 0.0
            )
        )

        assertEquals(
            "follow t=1700000000000 spd=- brg=- avg=NaN gps=- eff=0.0 savg=0.0 " +
                "dec=false stp=true off=0.0,0.0 clamped=false",
            line
        )
    }

    // --- the ViewModel's GPS-fix line ---

    @Test
    fun theGpsFixLineKeepsItsNumbersOnADecimalCommaLocale() = runTest(mainDispatcherRule.dispatcher) {
        locationService.setGpsFixForTest(
            GpsFix(
                lat = 52.51, lon = 13.40,
                accuracy = 5.0, speedKmH = 30.0,
                smoothedBearing = 45.0, markerBearing = 45.0,
                time = fakeNowMs
            )
        )
        advanceUntilIdle()

        val line = ShadowLog.getLogs().map { it.msg }.firstOrNull { it.startsWith("GPS fix") }

        assertEquals(
            "the fix line is read off a German install's logcat, so its numbers carry dots",
            "GPS fix acc=5.0 bearing=45.0 speed=30.0 follow=false",
            line
        )
    }

    private companion object {
        const val SCREEN_W = 1080
        const val SCREEN_H = 1920
        const val MAG = 15.0
        const val FIX_TIME = 1700000000000L
    }
}
