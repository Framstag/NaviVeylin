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
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The stylesheet load feeds the shared flag record (`guidelines/MapStyling.md` §15 — "Load budget
 * and dedupe key").
 *
 * A style load carries the enabled flags itself (`loadStyleSheet` hands the load `GetStyleFlags()`),
 * so the load path must record the variant it installed. Without that record the next writer — the
 * main map's next request, or the mini map widget — reads "unknown" and reloads the whole stylesheet
 * set for a variant the client already carries. Deleting `MapDaylightApplier.record(client, daylight)`
 * from `loadMapStyle` leaves every other case green; this one fails, because the applier then reports
 * `APPLIED` (a native call) instead of `UNCHANGED`.
 */
@RunWith(RobolectricTestRunner::class)
class MapDaylightRecordTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        MapDaylightApplier.reset()
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context).also {
                it.ioDispatcher = mainDispatcherRule.dispatcher
            },
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context).also {
                it.defaultDispatcher = mainDispatcherRule.dispatcher
            },
            locationService = LocationService(context),
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        ).also { it.defaultDispatcher = mainDispatcherRule.dispatcher }
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
        MapDaylightApplier.reset()
    }

    @Test
    fun aStyleLoadRecordsTheVariantItInstalled() = runTest(mainDispatcherRule.dispatcher) {
        // Suspends on the test scheduler: the load runs on `defaultDispatcher`, which this test pins to
        // the same scheduler, so wrapping it in `runBlocking` would deadlock the test thread.
        val loaded = viewModel.loadMapStyle("standard.oss", daylight = true, allowDefaultFallback = false)

        assertEquals("the stylesheet loaded", true, loaded)
        val pushesBefore = client.styleFlags.size
        assertEquals(
            "the load itself records the variant, so no writer pushes it again",
            MapDaylightPush.UNCHANGED,
            MapDaylightApplier.push(client, dark = true)
        )
        assertEquals(
            "no flag push for the load's variant — the load carried it",
            pushesBefore,
            client.styleFlags.size
        )
    }
}
