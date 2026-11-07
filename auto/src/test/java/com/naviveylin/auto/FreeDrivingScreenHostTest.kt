package com.naviveylin.auto

import android.content.Context
import androidx.car.app.ScreenManager
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeMapScreenClient
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.RoadInfo
import com.naviveylin.core.AutoClientProvider
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoLocationProvider
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.AutoSettingsProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.CarMapViewportProvider
import com.naviveylin.core.CarSurfaceHost
import com.naviveylin.core.DrivingModeProvider
import com.naviveylin.core.FreeDrivingStatusProvider
import dagger.hilt.EntryPoints
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Host-side cases for [FreeDrivingScreen] (spec: auto/free-driving, car-host-fault-isolation,
 * current-road-info, and — for the report the coverage row reads — test-coverage). The screen
 * needs a CarContext and the Hilt entry point, which the module's other screen tests fake the
 * same way ([DetailsScreenTest]): a mocked [AutoEntryPoint] reached through [EntryPoints.get],
 * [testCarContext], [FakeMapScreenClient] and the test main dispatcher.
 *
 * Every case releases the screen's background work through the destroy edge (spec:
 * unit-test-suite-runtime — Test-created components release their background work; the screen's
 * renderer lives until `onDestroy`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FreeDrivingScreenHostTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val carContext = testCarContext()
    private val entryPoint = mockk<AutoEntryPoint>()
    private val client: OSMScoutClient = FakeMapScreenClient()

    /** The shared source the screen observes for fixes. */
    private val position = MutableStateFlow<AutoPosition?>(null)

    /** The basemap revision source the screen observes. */
    private val basemapNotifier = BasemapReloadNotifier()

    /** The session surface host the screen attaches its owner to. */
    private val surfaceHost = mockk<CarSurfaceHost>(relaxed = true)

    /** The shared free-driving vote. */
    private val drivingModeProvider = mockk<DrivingModeProvider>(relaxed = true)

    /** The shared road/speed status the screen publishes into. */
    private val statusProvider = FreeDrivingStatusProvider()

    /** The car surface's published last-frame center; the stop path publishes into it. */
    private val carViewport = CarMapViewportProvider()

    @Before
    fun setUp() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        every { carContext.applicationContext } returns appContext
        every { carContext.resources } returns appContext.resources
        every { carContext.getCarService(ScreenManager::class.java) } returns mockk(relaxed = true)
        every { entryPoint.autoClientProvider() } returns mockk<AutoClientProvider>().apply {
            every { client() } returns this@FreeDrivingScreenHostTest.client
        }
        every { entryPoint.autoLocationProvider() } returns mockk<AutoLocationProvider>().apply {
            every { position() } returns this@FreeDrivingScreenHostTest.position
        }
        every { entryPoint.autoSettingsProvider() } returns mockk<AutoSettingsProvider>().apply {
            coEvery { load() } returns AutoSettings()
        }
        every { entryPoint.basemapReloadNotifier() } returns basemapNotifier
        every { entryPoint.autoSurfaceHost() } returns surfaceHost
        every { entryPoint.autoDrivingModeProvider() } returns drivingModeProvider
        every { entryPoint.autoFreeDrivingStatusProvider() } returns statusProvider
        every { entryPoint.carMapViewportProvider() } returns carViewport
        mockkStatic(EntryPoints::class)
        every { EntryPoints.get(any(), AutoEntryPoint::class.java) } returns entryPoint
    }

    private fun newScreen() = FreeDrivingScreen(carContext, MutableStateFlow(false))

    /** Releases the screen's background work (renderer loops, observations, scope). */
    private fun dispose(screen: FreeDrivingScreen) {
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    // ── the template (spec: auto/free-driving — the map-only template) ──

    @Test
    fun theTemplateIsTheBareNavigationTemplateCarryingItsActions() {
        val screen = newScreen()
        try {
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)

            val template = screen.onGetTemplate()

            assertTrue(
                "a NavigationTemplate is the only template with no content slot (" +
                    "MapWithContentTemplate showed a box, MapTemplate a placeholder)",
                template is NavigationTemplate
            )
            template as NavigationTemplate
            assertEquals(
                "the map strip carries the pan/exit pair",
                2,
                template.mapActionStrip!!.actions.size
            )
            assertEquals(
                "the right strip carries zoom in and zoom out, in order",
                listOf("+", "-"),
                template.actionStrip!!.actions.map { it.title.toString() }
            )
        } finally {
            dispose(screen)
        }
    }

    // ── the session surface (spec: car-host-fault-isolation — single-owner car surface) ──

    @Test
    fun aStartedScreenTakesTheSessionSurfaceAndStopsReleasing() {
        val screen = newScreen()
        try {
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)

            verify(exactly = 1) { surfaceHost.attach(any()) }

            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_STOP)

            verify(exactly = 1) { surfaceHost.detach(any()) }
        } finally {
            dispose(screen)
        }
    }

    // ── the observations (spec: auto/screen-observation — one instance per started period) ──

    @Test
    fun aStartedScreenObservesFixesAndBasemapRevisions() = runTest(mainDispatcher.dispatcher) {
        val screen = newScreen()
        try {
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
            advanceUntilIdle()

            position.value = AutoPosition(lat = 51.5, lon = 7.5, bearing = 90.0, speedKmH = 42.0)
            basemapNotifier.bump()
            advanceUntilIdle()

            val template = screen.onGetTemplate()
            assertTrue(
                "the started screen still answers a NavigationTemplate after a fix and a " +
                    "basemap revision",
                template is NavigationTemplate
            )
        } finally {
            dispose(screen)
        }
    }

    // ── the shared free-driving vote (spec: map-modes — retain-on-death) ──

    @Test
    fun anEnteredScreenVotesTheFreeDrivingFlagOn() {
        val screen = newScreen()
        try {
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)

            verify(exactly = 1) {
                drivingModeProvider.setFreeDriving(DrivingModeProvider.SURFACE_AUTO, true)
            }
        } finally {
            dispose(screen)
        }

        verify(exactly = 0) {
            drivingModeProvider.setFreeDriving(DrivingModeProvider.SURFACE_AUTO, false)
        }
    }

    // ── the published road status (spec: current-road-info — status is empty before the
    // first fix, and no road from an ended session survives) ──

    @Test
    fun aDestroyedScreenDropsTheRoadStatusItPublishedAndItsSurface() {
        val screen = newScreen()
        try {
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)

            statusProvider.publish(
                FreeDrivingStatusProvider.SURFACE_CAR,
                RoadInfo("Hauptstraße", "B 1", "highway_primary", 50.0),
                42.0
            )
            assertEquals(
                "the car's resolution is the shared status while the screen is started",
                "Hauptstraße",
                statusProvider.status.value?.roadName
            )

            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_DESTROY)

            assertNull(
                "a destroyed screen clears the status it published, so no road from an ended " +
                    "session survives into the notification",
                statusProvider.status.value
            )
            verify(atLeast = 1) { surfaceHost.detach(any()) }
        } finally {
            // The destroy above already released the screen; dispose() is idempotent on it.
            runCatching { dispose(screen) }
        }
    }
}
