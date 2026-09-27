package com.naviveylin.memory

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.NativeTileDataCache
import com.naviveylin.navigation.CarSessionPresenceImpl
import dagger.Lazy
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for the retention release (spec: `native-tile-data-cache` — Retention is released when the
 * device is low on memory or the app stops using it): which signals act and how much they release, the
 * car-session scoping, that the release reaches the native client and nothing else, that it is recorded,
 * that it never faults, and that the Application delivers the platform signals to the responder.
 *
 * The triggers themselves are the reason this suite exists in this shape: the `onTrimMemory` levels this
 * app first designed against are **not delivered since API 34** (AOSP `ComponentCallbacks2`), so the
 * pressure trigger is a polled `ActivityManager.MemoryInfo` read and the two remaining platform levels
 * are scoped to "no car session". Both seams (`memoryState`, the presence signal, the dispatcher) are
 * injected here, so no device and no real memory pressure is needed.
 *
 * Robolectric with the DEFAULT sandbox config: these tests build a [FakeOSMScoutClient], and
 * `OSMScoutClient`'s static initializer loads the host stub library — see `AGENTS.md`.
 */
@RunWith(RobolectricTestRunner::class)
class MemoryPressureResponderTest {

    private lateinit var logFile: File
    private lateinit var presence: CarSessionPresenceImpl

    /** The deprecated levels the platform stopped delivering — referenced as literals on purpose. */
    private companion object {
        const val OLD_RUNNING_MODERATE = 5
        const val OLD_RUNNING_LOW = 10
        const val OLD_RUNNING_CRITICAL = 15
        const val OLD_MODERATE = 60
        const val OLD_COMPLETE = 80
        const val THRESHOLD = 512L * 1024 * 1024
    }

    @Before
    fun setUp() {
        NativeTileDataCache.resetForTest()
        presence = CarSessionPresenceImpl()
        val context = ApplicationProvider.getApplicationContext<Context>()
        logFile = File(context.filesDir, "diagnostics/memory-pressure-test.log")
        logFile.parentFile?.mkdirs()
        logFile.delete()
        DiagnosticsLog.initForTest(logFile)
    }

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
        logFile.delete()
    }

    /** A responder with inline execution, no poll loop, bound to [client] and the test presence. */
    private fun responderFor(client: FakeOSMScoutClient): MemoryPressureResponder =
        MemoryPressureResponder(Lazy { client }, presence, ApplicationProvider.getApplicationContext())
            .apply {
                dispatcher = Dispatchers.Unconfined
                pollEnabled = false
            }

    private fun configure(client: FakeOSMScoutClient, tiles: Int = NativeTileDataCache.PHONE_TILES) {
        NativeTileDataCache.apply(client, tiles)
    }

    private fun entries(): List<String> = DiagnosticsLog.readEntries()

    private fun lowMemoryState(avail: Long) = DeviceMemoryState(
        lowMemory = avail <= THRESHOLD,
        availMemBytes = avail,
        thresholdBytes = THRESHOLD
    )

    private fun healthyState() = DeviceMemoryState(
        lowMemory = false,
        availMemBytes = THRESHOLD * 4,
        thresholdBytes = THRESHOLD
    )

    // --- the decision mapping (pure) -----------------------------------------------------------

    @Test
    fun aModerateReleaseHalvesAndNeverGoesBelowTheLibraryDefault() {
        assertEquals(256, halfTargetFor(512))
        assertEquals(64, halfTargetFor(128))
        assertEquals(NativeTileDataCache.LIBRARY_DEFAULT_TILES, halfTargetFor(NativeTileDataCache.LIBRARY_DEFAULT_TILES))
        assertEquals(NativeTileDataCache.LIBRARY_DEFAULT_TILES, halfTargetFor(30))
    }

    @Test
    fun aSevereReleaseFloorsAtTheLibraryDefaultAndNeverRaises() {
        assertEquals(NativeTileDataCache.LIBRARY_DEFAULT_TILES, floorTargetFor(512))
        assertEquals(NativeTileDataCache.LIBRARY_DEFAULT_TILES, floorTargetFor(128))
        assertEquals(NativeTileDataCache.LIBRARY_DEFAULT_TILES, floorTargetFor(NativeTileDataCache.LIBRARY_DEFAULT_TILES))
        assertEquals(20, floorTargetFor(20))
    }

    @Test
    fun thePollTriggerNamesTheBandItSaw() {
        assertEquals("poll-low", pollTrigger(lowMemoryState(THRESHOLD)))
        assertEquals("poll-severe", pollTrigger(lowMemoryState(THRESHOLD / 4)))
    }

    // --- the poll ------------------------------------------------------------------------------

    @Test
    fun lowMemoryHalvesTheCapacityAndRecordsTheTrigger() {
        val client = FakeOSMScoutClient()
        configure(client)
        val responder = responderFor(client)
        responder.memoryState = { lowMemoryState(THRESHOLD) }

        responder.pollRelease()

        assertEquals(listOf(NativeTileDataCache.PHONE_TILES, 256), client.nativeDataCacheSizes)
        val logged = entries().filter { it.contains("retention released") }
        assertEquals(1, logged.size)
        assertTrue(
            "the record names the trigger, the memory state and the capacity: ${logged.single()}",
            logged.single().contains("trigger=poll-low") &&
                logged.single().contains("512 -> 256 tiles/db")
        )
    }

    @Test
    fun theSevereBandFloorsTheCapacity() {
        val client = FakeOSMScoutClient()
        configure(client)
        val responder = responderFor(client)
        responder.memoryState = { lowMemoryState(THRESHOLD / 4) }

        responder.pollRelease()

        assertEquals(
            listOf(NativeTileDataCache.PHONE_TILES, NativeTileDataCache.LIBRARY_DEFAULT_TILES),
            client.nativeDataCacheSizes
        )
    }

    @Test
    fun aHealthyDeviceReleasesNothing() {
        val client = FakeOSMScoutClient()
        configure(client)
        val responder = responderFor(client)
        responder.memoryState = { healthyState() }

        responder.pollRelease()
        responder.pollRelease()

        assertEquals(listOf(NativeTileDataCache.PHONE_TILES), client.nativeDataCacheSizes)
        assertEquals(0, entries().count { it.contains("retention released") })
    }

    @Test
    fun aMissingMemoryStateReleasesNothing() {
        val client = FakeOSMScoutClient()
        configure(client)
        val responder = responderFor(client)
        responder.memoryState = { null }

        responder.pollRelease()

        assertEquals(listOf(NativeTileDataCache.PHONE_TILES), client.nativeDataCacheSizes)
    }

    @Test
    fun repeatedSignalsConvergeAtTheFloor() {
        val client = FakeOSMScoutClient()
        configure(client)
        val responder = responderFor(client)
        responder.memoryState = { lowMemoryState(THRESHOLD) }

        repeat(8) { responder.pollRelease() }

        assertEquals(
            "each warning halves the working set until the floor, where it is a no-op: 512 256 128 64 32 25",
            listOf(
                NativeTileDataCache.PHONE_TILES,
                256,
                128,
                64,
                32,
                NativeTileDataCache.LIBRARY_DEFAULT_TILES
            ),
            client.nativeDataCacheSizes
        )
        assertEquals(
            "one record per release, none for the no-ops at the floor",
            5,
            entries().count { it.contains("retention released") }
        )
    }

    // --- the platform's remaining levels, scoped ------------------------------------------------

    @Test
    fun aHiddenUiHalvesAndABackgroundedProcessFloorsWithoutACarSession() {
        val client = FakeOSMScoutClient()
        configure(client)
        val responder = responderFor(client)

        responder.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        responder.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)

        assertEquals(
            "512 -> 256 on the hidden UI, then the floor on the backgrounded process",
            listOf(NativeTileDataCache.PHONE_TILES, 256, NativeTileDataCache.LIBRARY_DEFAULT_TILES),
            client.nativeDataCacheSizes
        )
    }

    @Test
    fun aLiveCarSessionKeepsTheCacheForThePlatformLevels() {
        val client = FakeOSMScoutClient()
        configure(client)
        val responder = responderFor(client)
        presence.setActive(true)

        responder.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        responder.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)

        assertEquals(
            "the car renders from these caches while the phone UI is hidden: no release",
            listOf(NativeTileDataCache.PHONE_TILES),
            client.nativeDataCacheSizes
        )
        assertEquals(0, entries().count { it.contains("retention released") })
    }

    @Test
    fun thePollIsNotScopedByTheCarSession() {
        // A live car session does not make the *device* less low: the poll still releases.
        val client = FakeOSMScoutClient()
        configure(client)
        val responder = responderFor(client)
        presence.setActive(true)
        responder.memoryState = { lowMemoryState(THRESHOLD) }

        responder.pollRelease()

        assertEquals(listOf(NativeTileDataCache.PHONE_TILES, 256), client.nativeDataCacheSizes)
    }

    @Test
    fun theDeprecatedLevelsAndOtherCallbacksDoNotRelease() {
        val client = FakeOSMScoutClient()
        configure(client)
        val responder = responderFor(client)

        // The platform stopped delivering these to apps in API 34; they are not triggers.
        responder.onTrimMemory(OLD_RUNNING_MODERATE)
        responder.onTrimMemory(OLD_RUNNING_LOW)
        responder.onTrimMemory(OLD_RUNNING_CRITICAL)
        responder.onTrimMemory(OLD_MODERATE)
        responder.onTrimMemory(OLD_COMPLETE)
        responder.onLowMemory()
        responder.onConfigurationChanged(Configuration())

        assertEquals(listOf(NativeTileDataCache.PHONE_TILES), client.nativeDataCacheSizes)
        assertEquals(0, entries().count { it.contains("retention released") })
    }

    // --- release hygiene -----------------------------------------------------------------------

    @Test
    fun theReleaseTouchesNothingButTheCacheCapacity() {
        val client = FakeOSMScoutClient()
        configure(client)
        val responder = responderFor(client)

        responder.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)

        assertEquals(
            "a release is one cache-configuration call: no render, no other native work",
            0,
            client.renderCount.get()
        )
        assertEquals(0, client.renderWithRouteAndPoisCount.get())
    }

    @Test
    fun renderedOutputIsUnchangedByARelease() {
        // The release is a cache-capacity knob only (spec: cache sizing must not change rendering
        // output): the same viewport renders the same pixels after it.
        val client = FakeOSMScoutClient()
        configure(client)
        val responder = responderFor(client)
        val before = client.renderWithRouteAndPois(
            64, 64, 51.5, 7.4, 0.0, 65536.0, 160.0, null, null, null, null,
            Double.NaN, Double.NaN, null, null
        )

        responder.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)

        val after = client.renderWithRouteAndPois(
            64, 64, 51.5, 7.4, 0.0, 65536.0, 160.0, null, null, null, null,
            Double.NaN, Double.NaN, null, null
        )
        assertTrue("the release must not change rendered content", before.contentEquals(after))
    }

    @Test
    fun aFailedReleaseIsRecordedAndKeepsTheCapacity() {
        val client = FakeOSMScoutClient()
        configure(client)
        client.nativeDataCacheSizeFails = true
        val responder = responderFor(client)

        responder.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)

        val failed = entries().filter { it.contains("retention release failed") }
        assertEquals(1, failed.size)
        assertTrue(
            "the record names the trigger and what was kept: ${failed.single()}",
            failed.single().contains("trigger=ui-hidden") && failed.single().contains("(keeping 512)")
        )
        assertEquals(NativeTileDataCache.PHONE_TILES, NativeTileDataCache.currentCapacity(client))

        // And it can be retried once the bridge is back.
        client.nativeDataCacheSizeFails = false
        responder.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertEquals(
            "the configuration (512) and the successful retry (256) are what reached the client",
            listOf(NativeTileDataCache.PHONE_TILES, 256),
            client.nativeDataCacheSizes
        )
    }

    @Test
    fun anUnconfiguredClientIsNeverBuilt() {
        // No surface configured a capacity: nothing is retained, and a release must not build the native
        // client from a lifecycle callback or the poll.
        val responder = MemoryPressureResponder(
            Lazy { error("the client must not be built") },
            presence,
            ApplicationProvider.getApplicationContext()
        ).apply {
            dispatcher = Dispatchers.Unconfined
            pollEnabled = false
        }
        responder.memoryState = { lowMemoryState(THRESHOLD / 4) }

        responder.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        responder.pollRelease()

        assertFalse(NativeTileDataCache.isConfigured())
    }

    /** The production threading contract: the release runs on the render dispatcher, never inline. */
    @Test
    fun theReleaseRunsOnTheDefaultDispatcherInProduction() {
        val responder = MemoryPressureResponder(
            Lazy { FakeOSMScoutClient() },
            presence,
            ApplicationProvider.getApplicationContext()
        )

        try {
            assertEquals(Dispatchers.Default, responder.dispatcher)
            assertTrue("the poll runs in production unless a test disables it", responder.pollEnabled)
        } finally {
            // The poll loop lives on the JVM-wide Dispatchers.Default: leaving it enabled would let it
            // fire 30 s later, inside an unrelated test, against this test's torn-down context.
            responder.pollEnabled = false
        }
    }

    /**
     * A fault inside a release is confined and recorded, not left to the thread's uncaught-exception
     * handler: the release runs on a process-wide dispatcher, where an escaping throwable kills the app
     * (and poisons an unrelated test's coroutine harness in a test JVM).
     */
    @Test
    fun aFaultInTheReleaseIsConfinedAndRecorded() {
        // The capacity must exist for the release to proceed; the client the responder builds throws.
        configure(FakeOSMScoutClient())
        val responder = MemoryPressureResponder(
            Lazy { error("client build failed") },
            presence,
            ApplicationProvider.getApplicationContext()
        ).apply {
            dispatcher = Dispatchers.Unconfined
            pollEnabled = false
        }

        // Would throw out of this call without the confinement.
        responder.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)

        val entry = entries().singleOrNull { it.contains("retention release confined fault") }
        assertNotNull("the confined fault is recorded, so it is not hidden", entry)
        assertTrue(
            "the record names the throwable class, not its message: $entry",
            entry!!.contains("IllegalStateException")
        )
    }

    // --- the Application seam ------------------------------------------------------------------

    @Test
    fun theApplicationDeliversPlatformLevelsToTheResponder() {
        val client = FakeOSMScoutClient()
        configure(client)
        val responder = responderFor(client)
        val application = ApplicationProvider.getApplicationContext<Application>()

        application.registerComponentCallbacks(responder)
        try {
            application.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        } finally {
            application.unregisterComponentCallbacks(responder)
        }

        assertEquals(
            "the Application forwards onTrimMemory to a registered responder",
            listOf(NativeTileDataCache.PHONE_TILES, NativeTileDataCache.LIBRARY_DEFAULT_TILES),
            client.nativeDataCacheSizes
        )
    }
}
