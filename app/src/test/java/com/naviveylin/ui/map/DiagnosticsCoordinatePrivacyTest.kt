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
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/**
 * The privacy rule from the diagnostics side: a position-dependent log or
 * diagnostics line carries precision-free identity instead of a coordinate (spec:
 * auto-diagnostics — Diagnostics carry no coordinates / Long-press mapping evidence
 * stays usable).
 *
 * The logcat stream is captured through Robolectric's `ShadowLog` and the file-backed
 * entry through [DiagnosticsLog], so both halves of the rule are asserted on the real
 * call paths rather than on a source scan (which the build gate
 * `checkNoCoordinatesInLogs` owns).
 *
 * Default Robolectric sandbox (no `@Config`) — this class instantiates
 * [FakeOSMScoutClient] and therefore loads the JNI stub, per the AGENTS.md rule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DiagnosticsCoordinatePrivacyTest {

    private lateinit var context: Context
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var logFile: File

    /** A coordinate-shaped token: 1-3 integer digits followed by 4+ decimals. */
    private val coordinateShaped = Regex("""-?\b\d{1,3}\.\d{4,}\b""")

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
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
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        logFile = File(context.filesDir, "diagnostics/privacy-${System.nanoTime()}.log")
        DiagnosticsLog.reset()
        DiagnosticsLog.initForTest(logFile)
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
        DiagnosticsLog.reset()
        logFile.parentFile?.deleteRecursively()
    }

    private fun TestScope.capturedLogcat(): List<String> {
        val logs = ShadowLog.getLogs().map { "${it.tag}: ${it.msg}" }
        ShadowLog.clear()
        return logs
    }

    @Test
    fun longPressLogsThePixelAndMagnificationInsteadOfAPosition() = runTest {
        viewModel.onLongPress(51.51391, 7.47434, screenX = 123, screenY = 456)

        val logcat = capturedLogcat()
        val fileEntries = DiagnosticsLog.readEntries()

        assertFalse(
            "no logcat line may carry a coordinate-shaped token: $logcat",
            logcat.any { coordinateShaped.containsMatchIn(it) }
        )
        val longPressEntry = fileEntries.firstOrNull { it.contains("LONGPRESS") }
        assertTrue(
            "the file-backed entry must exist and name the press point: $fileEntries",
            longPressEntry != null &&
                longPressEntry.contains("x=123") &&
                longPressEntry.contains("y=456") &&
                longPressEntry.contains("mag=")
        )
        assertFalse(
            "the file-backed entry must not carry a coordinate: $longPressEntry",
            coordinateShaped.containsMatchIn(longPressEntry!!)
        )
    }

    @Test
    fun theIdentityFieldsSurviveWithoutAScreenPoint() = runTest {
        // Callers without a screen point (tests, non-gesture callers) still get an
        // entry that identifies the event without a coordinate.
        viewModel.onLongPress(51.51391, 7.47434)

        val longPressEntry = DiagnosticsLog.readEntries().firstOrNull { it.contains("LONGPRESS") }

        assertTrue("the entry exists", longPressEntry != null)
        assertTrue("it carries the magnification", longPressEntry!!.contains("mag="))
        assertFalse(
            "and no coordinate",
            coordinateShaped.containsMatchIn(longPressEntry)
        )
    }
}
