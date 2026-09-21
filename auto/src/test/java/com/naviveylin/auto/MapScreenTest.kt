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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
@RunWith(RobolectricTestRunner::class)
class MapScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appContext: Context
    private lateinit var carContext: CarContext
    private lateinit var navigationViewModel: NavigationViewModel
    private lateinit var stateFlow: MutableStateFlow<NavigationState>
    private val entryPoint = mockk<AutoEntryPoint>(relaxed = true)

    /** Records the thread every [AutoClientProvider.client] call ran on. */
    private val clientThreads = mutableListOf<String>()
    private val client: OSMScoutClient = FakeMapScreenClient()
    private val clientProvider = object : AutoClientProvider {
        override fun client(): OSMScoutClient {
            clientThreads += Thread.currentThread().name
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
    fun theTapPathDoesNotResolveTheNativeClientOnTheHostThread() = runTest(mainDispatcherRule.dispatcher) {
        // The native client's first touch builds it (stylesheet sync, dlopen, native setup), and
        // the car-app library runs the surface click on the app's main thread: resolving it there
        // blocks the answer to the host (spec: car-host-fault-isolation — Host callbacks answer
        // promptly).
        val hostThread = Thread.currentThread().name
        val screen = MapScreen(carContext, navigationViewModel)
        clientThreads.clear()

        screen.onLocationSelected(51.5, 7.4)

        assertTrue(
            "the click callback returns without building the native client, got $clientThreads",
            clientThreads.isEmpty()
        )

        advanceUntilIdle()

        assertTrue("the candidate lookup resolves the client, got $clientThreads", clientThreads.isNotEmpty())
        assertEquals(
            "the client is resolved off the host thread, got $clientThreads",
            listOf(false),
            clientThreads.map { it == hostThread }.distinct()
        )
    }

    @Test
    fun aTapAfterTheScreenIsDestroyedStartsNoWork() = runTest(mainDispatcherRule.dispatcher) {
        // A per-tap CoroutineScope was never reclaimed (spec: car-host-fault-isolation — Host
        // callbacks answer promptly). The screen's scope is cancelled in onDestroy, so a tap that
        // arrives after the screen is gone cannot start client work.
        val screen = MapScreen(carContext, navigationViewModel)
        advanceUntilIdle()
        clientThreads.clear()
        val lifecycle = screen.lifecycle as LifecycleRegistry
        lifecycle.currentState = Lifecycle.State.CREATED
        lifecycle.currentState = Lifecycle.State.DESTROYED

        screen.onLocationSelected(51.5, 7.4)
        advanceUntilIdle()

        assertTrue("a destroyed screen starts no work, got $clientThreads", clientThreads.isEmpty())
    }
}
