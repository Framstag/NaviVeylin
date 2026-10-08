package com.naviveylin.auto

import android.content.Context
import androidx.car.app.CarContext
import androidx.car.app.ScreenManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeMapScreenClient
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.AutoClientProvider
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.core.AutoLocationProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.NavigationState
import com.naviveylin.core.NavigationViewModel
import dagger.hilt.EntryPoints
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [MapScreen]'s host-callback paths (spec: car-host-fault-isolation — Host callbacks
 * answer promptly).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appContext: Context
    private lateinit var carContext: CarContext
    private lateinit var navigationViewModel: NavigationViewModel
    private lateinit var stateFlow: MutableStateFlow<NavigationState>
    private val entryPoint = mockk<AutoEntryPoint>(relaxed = true)

    /** Records the thread name every [AutoClientProvider.client] call ran on. */
    private val clientThreads = mutableListOf<String>()

    /** The thread the case runs on: a resolution on it is a resolution on the host thread. */
    private val mainThread = Thread.currentThread()

    /**
     * True when [AutoClientProvider.client] was ever resolved on the main (host) thread —
     * the one invariant every car path has to keep (spec: car-host-fault-isolation — Host
     * callbacks answer promptly). Compared by thread **identity**, never by name: under
     * Robolectric a coroutine's thread name carries a `@coroutine#N` suffix, so a name
     * comparison against the bare main-thread name can never match (measured 2026-10-08:
     * `SDK 34 Main Thread @coroutine#6` against `SDK 34 Main Thread` — the assertion this
     * feeds was therefore vacuous until it compared identity).
     */
    @Volatile
    private var clientResolvedOnMainThread = false

    /** The last thread that resolved the client (scalar, safe to poll concurrently). */
    @Volatile
    private var lastClientThread: Thread? = null

    /**
     * True while a host callback's synchronous body is running: set by the case around the
     * callback under test. The screen's background work must never resolve the client inside
     * that body (spec: car-host-fault-isolation — Host callbacks answer promptly).
     */
    @Volatile
    private var insideHostCallback = false

    /** True when the client was resolved inside a host callback's synchronous body. */
    @Volatile
    private var clientResolvedInsideHostCallback = false

    private val client: OSMScoutClient = FakeMapScreenClient()
    private val clientProvider = object : AutoClientProvider {
        override fun client(): OSMScoutClient {
            val thread = Thread.currentThread()
            clientThreads += thread.name
            lastClientThread = thread
            if (thread === mainThread) clientResolvedOnMainThread = true
            if (insideHostCallback) clientResolvedInsideHostCallback = true
            return client
        }

        override suspend fun openMapDatabases(filesDir: String) = Unit
    }

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        carContext = testCarContext()
        every { carContext.applicationContext } returns appContext
        every { carContext.resources } returns appContext.resources
        every { carContext.filesDir } returns appContext.filesDir
        every { carContext.getCarService(ScreenManager::class.java) } returns mockk(relaxed = true)
        every { carContext.isDarkMode() } returns false
        every { entryPoint.autoClientProvider() } returns clientProvider
        // The screens collect these flows in their init; a relaxed mock of a StateFlow throws
        // KotlinNothingValueException on collect (SharedFlow.collect returns Nothing), so the
        // test supplies real flows.
        every { entryPoint.autoFavoritesProvider() } returns mockk<AutoFavoritesProvider>().apply {
            every { favoriteLocations() } returns MutableStateFlow(emptyMap())
        }
        every { entryPoint.autoLocationProvider() } returns mockk<AutoLocationProvider>().apply {
            every { position() } returns MutableStateFlow(null)
        }
        every { entryPoint.basemapReloadNotifier() } returns mockk<BasemapReloadNotifier>().apply {
            every { revision } returns MutableStateFlow(0L)
        }
        mockkStatic(EntryPoints::class)
        every { EntryPoints.get(any(), AutoEntryPoint::class.java) } returns entryPoint

        stateFlow = MutableStateFlow(NavigationState())
        navigationViewModel = mockk(relaxed = true)
        every { navigationViewModel.state } returns stateFlow
    }

    @Test
    fun theSurfaceDeliveryPushesNoNativeStylesheetFlagOnTheHostThread() = runTest(mainDispatcherRule.dispatcher) {
        // The surface-delivery callback used to push the day/night stylesheet flag itself,
        // i.e. it scheduled a style-sheet reload on the native DB thread while answering the
        // host (spec: car-host-fault-isolation — Host callbacks answer promptly). It now
        // publishes the request and the flag is applied by the screen's background collector.
        val host = SessionCarSurfaceHost()
        every { entryPoint.autoSurfaceHost() } returns host
        val fake = client as FakeMapScreenClient
        val dark = MutableStateFlow(false)
        val screen = MapScreen(carContext, navigationViewModel, resolvedDark = dark)
        val lifecycle = screen.lifecycle as LifecycleRegistry
        val hostThread = Thread.currentThread().name

        // The renderer must exist and the readiness push must be through before the delivery,
        // so the delivery is the callback that has to decide about the pending night variant.
        val dayApplied = awaitDaylightPush(fake, value = true)
        assertTrue("the readiness path applies the day variant, got ${fake.styleSheetFlags}", dayApplied >= 1)
        fake.styleSheetFlags.clear()
        fake.styleSheetFlagThreads.clear()
        dark.value = true

        // The host already holds a surface when the screen starts: attach hands it over.
        host.onSurfaceAvailable(surfaceContainer())
        lifecycle.currentState = Lifecycle.State.CREATED
        lifecycle.currentState = Lifecycle.State.STARTED

        // dark = true means the daylight flag is unset (night variant). The delivery callback
        // must not push it synchronously — it publishes the request.
        assertEquals(
            "the delivery callback pushes no native flag, got ${fake.styleSheetFlags}",
            0,
            fake.styleSheetFlags.count { it.first == "daylight" && it.second == false }
        )

        val pushed = awaitDaylightPush(fake, value = false)
        assertEquals("the flag is applied once", 1, pushed)
        val index = fake.styleSheetFlags.indexOfFirst { it.first == "daylight" && it.second == false }
        assertFalse(
            "the flag is applied off the host thread, got ${fake.styleSheetFlagThreads}",
            fake.styleSheetFlagThreads[index] == hostThread
        )

        destroy(screen)
    }

    /**
     * Count the daylight-flag pushes with [value], bounded in real time: the collector applies
     * the request on a real background dispatcher, which the test scheduler cannot drain, and the
     * renderer's init uses one too. `advanceUntilIdle` runs first so the queued main-thread work
     * (renderer publish, readiness observer) progresses as well.
     */
    private fun TestScope.awaitDaylightPush(
        fake: FakeMapScreenClient,
        value: Boolean,
        timeoutMs: Long = 3_000
    ): Int {
        var count = fake.styleSheetFlags.count { it.first == "daylight" && it.second == value }
        // The collector applies the request on a real background dispatcher, so the test scheduler is
        // driven while that work progresses and the observable state is awaited — no wall-clock deadline
        // and no sleep (spec `unit-test-suite-runtime` — Awaiting state, not a deadline).
        runBlocking {
            withTimeoutOrNull<Boolean>(timeoutMs) {
                while (count == 0) {
                    advanceUntilIdle()
                    count = fake.styleSheetFlags.count { it.first == "daylight" && it.second == value }
                    if (count == 0) yield()
                }
                true
            }
        }
        advanceUntilIdle()
        return fake.styleSheetFlags.count { it.first == "daylight" && it.second == value }
    }

    /** A host-delivered surface a renderer can lock (see [SessionCarSurfaceHostTest]). */
    private fun surfaceContainer(): androidx.car.app.SurfaceContainer {
        val surface = mockk<android.view.Surface>(relaxed = true).apply {
            every { isValid } returns true
            every { lockCanvas(any()) } returns mockk<android.graphics.Canvas>(relaxed = true)
        }
        return mockk<androidx.car.app.SurfaceContainer>(relaxed = true).apply {
            every { this@apply.surface } returns surface
            every { width } returns 1920
            every { height } returns 720
            every { this@apply.dpi } returns 240
        }
    }

    @Test
    fun theTapPathDoesNotResolveTheNativeClientOnTheHostThread() = runTest(mainDispatcherRule.dispatcher) {
        // The native client's first touch builds it (stylesheet sync, dlopen, native setup), and
        // the car-app library runs the surface click on the app's main thread: resolving it there
        // blocks the answer to the host (spec: car-host-fault-isolation — Host callbacks answer
        // promptly).
        //
        // The screen's background hops run on the case's own test dispatcher, so `advanceUntilIdle`
        // owns the candidate lookup and `clientThreads` is only ever read here, on the test thread
        // (spec `unit-test-suite-runtime` — A case's own bookkeeping is read only on the test
        // thread).
        val screen = MapScreen(
            carContext,
            navigationViewModel,
            backgroundDispatcher = mainDispatcherRule.dispatcher
        )
        // Drain the renderer init's own client resolution first: the assertion after the tap is
        // about the tap, not about the init.
        advanceUntilIdle()
        val beforeTap = clientThreads.toList()

        insideHostCallback = true
        try {
            screen.onLocationSelected(51.5, 7.4)
        } finally {
            insideHostCallback = false
        }
        val onCallbackReturn = clientThreads.toList()

        assertFalse(
            "the click callback resolves no client inline, got $onCallbackReturn",
            clientResolvedInsideHostCallback
        )
        assertEquals(
            "the click callback returns before any client work, got $onCallbackReturn",
            beforeTap,
            onCallbackReturn
        )

        advanceUntilIdle()
        val afterLookup = clientThreads.toList()

        assertTrue(
            "the candidate lookup resolves the client on the case's own dispatcher, got $afterLookup",
            afterLookup.size > onCallbackReturn.size
        )
        destroy(screen)
    }

    @Test
    fun theProductionDefaultResolvesTheNativeClientOnABackgroundThread() = runTest(mainDispatcherRule.dispatcher) {
        // Every production call site (RootScreen, DetailsScreen, NavigationSession) passes no
        // dispatcher, so the default must keep the native client off the host thread: that is the
        // risk the additive parameter carries (design D3; spec `unit-test-suite-runtime` — A case
        // awaits the state it needs, not a proxy of it).
        val screen = MapScreen(carContext, navigationViewModel)
        val resolvedOn = awaitBackgroundClientResolution()

        assertNotNull("the production default resolves the native client", resolvedOn)
        assertNotSame(
            "the production default resolves the native client off the main thread",
            mainThread,
            resolvedOn
        )
        assertFalse(
            "the production default never resolves the native client on the main thread",
            clientResolvedOnMainThread
        )
        destroy(screen)
    }

    /**
     * Await the client resolution a real background dispatcher performs, reading only the scalars
     * the provider publishes — never `clientThreads`, which that thread appends to. The test
     * scheduler is driven while the real pool progresses and the published state is awaited; the
     * bound is a backstop, not the assertion (spec `unit-test-suite-runtime` — A case awaits the
     * state it needs, not a proxy of it).
     */
    private fun TestScope.awaitBackgroundClientResolution(timeoutMs: Long = 3_000): Thread? {
        var resolved: Thread? = lastClientThread
        runBlocking {
            withTimeoutOrNull<Boolean>(timeoutMs) {
                while (resolved == null) {
                    advanceUntilIdle()
                    resolved = lastClientThread
                    if (resolved == null) yield()
                }
                true
            }
        }
        advanceUntilIdle()
        return lastClientThread
    }

    @Test
    fun aTapAfterTheScreenIsDestroyedStartsNoWork() = runTest(mainDispatcherRule.dispatcher) {
        // A per-tap CoroutineScope was never reclaimed (spec: car-host-fault-isolation — Host
        // callbacks answer promptly). The screen's scope is cancelled in onDestroy, so a tap that
        // arrives after the screen is gone cannot start client work.
        val screen = MapScreen(
            carContext,
            navigationViewModel,
            backgroundDispatcher = mainDispatcherRule.dispatcher
        )
        advanceUntilIdle()
        destroy(screen)
        val resolvedBefore = clientThreads.size

        screen.onLocationSelected(51.5, 7.4)
        advanceUntilIdle()

        assertEquals(
            "a destroyed screen starts no client work, got ${clientThreads.toList()}",
            resolvedBefore,
            clientThreads.size
        )
    }

    /**
     * Drive the screen to DESTROYED so its scope (and the renderer it built) stops: a screen
     * leaked into the next test keeps a render loop running against this test's fake client.
     */
    private fun destroy(screen: MapScreen) {
        val lifecycle = screen.lifecycle as LifecycleRegistry
        // INITIALIZED -> DESTROYED is not a legal lifecycle transition; go through CREATED.
        lifecycle.currentState = Lifecycle.State.CREATED
        lifecycle.currentState = Lifecycle.State.DESTROYED
    }
}
